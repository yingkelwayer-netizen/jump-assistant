'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {Store,redeem,check,verify}=require('./core.cjs');
const promo=require('./promo.cjs');
function createService({dir,origin,now=()=>Date.now()}){
 const u=new URL(origin);if(u.protocol!=='https:'||u.pathname!=='/'||u.username||u.password)throw Error('PUBLIC_ORIGIN must be an HTTPS origin');
 const store=new Store(dir),key=fs.readFileSync(path.join(dir,'signing-private.pem')),pub=fs.readFileSync(path.join(dir,'signing-public.pem'));
 const rate=new Map();
 const server=http.createServer(async(req,res)=>{
  res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Cache-Control','no-store');
  const json=(status,obj)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(obj));};
  try{
   const p=new URL(req.url,'http://localhost').pathname;
   if(p==='/health'&&req.method==='GET')return json(200,{ok:true,service:'jump-assistant',version:'1.3.0'});
   // The process binds loopback; only the TLS reverse proxy may reach the API.
   if(req.headers['x-forwarded-proto']!=='https'||req.headers.host!==u.host)return json(400,{message:'请使用安全连接'});
   if(req.method==='GET'&&p==='/api/v1/update'){
    const f=path.join(dir,'release.json');return json(200,fs.existsSync(f)?{manifest:fs.readFileSync(f,'utf8').trim()}:{manifest:null});
   }
   if(req.method==='GET'&&/^\/downloads\/[a-f0-9]{64}\.apk$/.test(p)){
    const f=path.join(dir,'releases',path.basename(p));if(!fs.existsSync(f))return json(404,{message:'安装包暂不可用'});
    res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Length':fs.statSync(f).size,'Cache-Control':'public, max-age=31536000, immutable'});fs.createReadStream(f).on('error',()=>res.destroy()).pipe(res);return;
   }
   if(req.method!=='POST'||!['/api/v1/redeem','/api/v1/check','/api/v1/share/status','/api/v1/share/claim'].includes(p))return json(404,{message:'接口不存在'});
   const ip=String(req.headers['x-real-ip']||req.socket.remoteAddress).slice(0,80),time=now();
   if(rate.size>10000){for(const [k,v] of rate)if(time-v.start>60000)rate.delete(k);if(rate.size>10000)return json(503,{message:'服务繁忙，请稍后重试'});}
   const r=rate.get(ip);if(r&&time-r.start<60000){if(++r.count>30)return json(429,{message:'请求较频繁，请稍后重试'});}else rate.set(ip,{start:time,count:1});
   if(!String(req.headers['content-type']).startsWith('application/json'))return json(415,{message:'请求格式无效'});
   let body='',size=0;for await(const chunk of req){size+=chunk.length;if(size>8192){json(413,{message:'请求过大'});req.destroy();return;}body+=chunk.toString('utf8');}
   let data;try{data=JSON.parse(body);}catch(e){return json(400,{message:'请求格式无效'});}
   let out;
   if(p.startsWith('/api/v1/share/')){
    let minimum=7;const release=path.join(dir,'release.json');if(fs.existsSync(release))minimum=Math.max(minimum,verify(fs.readFileSync(release,'utf8').trim(),pub).versionCode);
    if(!Number.isSafeInteger(data.versionCode)||data.versionCode<minimum)return json(426,{message:'请先更新应用，再领取周卡'});
    const configFile=path.join(dir,'promo-config.json'),config=fs.existsSync(configFile)?JSON.parse(fs.readFileSync(configFile,'utf8')):{};
    out=p.endsWith('/claim')?promo.claim(store,data.device,data.requestId,key,config.shareEnabled===true,time):promo.status(store,data.device,config.shareEnabled===true,time,key);
   }else out=p.endsWith('redeem')?redeem(store,data.code,data.device,key,time,Number.isSafeInteger(data.versionCode)&&data.versionCode>=7):check(store,data.token,data.device,pub,key,time);
   json(200,out);
  }catch(e){json(e.status||500,{message:e.status?e.message:'授权服务暂不可用，请稍后重试'});if(!e.status)console.error('Service error:',e.code||e.name);}
 });
 server.requestTimeout=15000;server.headersTimeout=10000;return server;
}
if(require.main===module){const dir=process.env.DATA_DIR;if(!dir)throw Error('DATA_DIR is required');const port=Number(process.env.PORT||18761);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid PORT');createService({dir,origin:process.env.PUBLIC_ORIGIN}).listen(port,'127.0.0.1',()=>console.log('Jump service listening on loopback:'+port));}
module.exports={createService};
