'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const WEEK=604800000, PRODUCT='jump-weekly-v1';
function fail(message,status=400){const e=new Error(message);e.status=status;throw e;}
function digest(s){return crypto.createHash('sha256').update(s).digest('hex');}
function device(s){if(typeof s!=='string'||!/^[a-f0-9]{64}$/.test(s))fail('设备标识无效');return s;}
function code(s){if(typeof s!=='string'||!/^\d{24}$/.test(s))fail('请输入 24 位数字兑换码');return s;}
function sign(payload,key){const p=Buffer.from(JSON.stringify(payload)).toString('base64url');return p+'.'+crypto.sign('RSA-SHA256',Buffer.from(p),key).toString('base64url');}
function verify(token,key){try{const [p,s,...rest]=token.split('.');if(rest.length||!p||!s||!crypto.verify('RSA-SHA256',Buffer.from(p),key,Buffer.from(s,'base64url')))fail('授权签名无效',401);return JSON.parse(Buffer.from(p,'base64url').toString());}catch(e){fail('授权签名无效',401);}}
class Store{
 constructor(dir){this.dir=dir;fs.mkdirSync(dir,{recursive:true,mode:0o700});this.file=path.join(dir,'licenses.json');this.lock=path.join(dir,'transaction.lock');}
 transaction(fn){let fd;try{fd=fs.openSync(this.lock,'wx',0o600);}catch(e){if(e.code==='EEXIST')fail('授权服务繁忙，请稍后重试',503);throw e;}
  try{const db=fs.existsSync(this.file)?JSON.parse(fs.readFileSync(this.file,'utf8')):{schema:1,codes:{}};if(db.schema!==1)throw Error('Unsupported database schema');const out=fn(db);const tmp=this.file+'.'+crypto.randomBytes(6).toString('hex')+'.tmp';const w=fs.openSync(tmp,'wx',0o600);try{fs.writeFileSync(w,JSON.stringify(db));fs.fsyncSync(w);}finally{fs.closeSync(w);}fs.renameSync(tmp,this.file);if(process.platform!=='win32'){const directory=fs.openSync(this.dir,'r');try{fs.fsyncSync(directory);}finally{fs.closeSync(directory);}}return out;}finally{fs.closeSync(fd);fs.unlinkSync(this.lock);}}
}
function generate(store,n=1){if(!Number.isInteger(n)||n<1||n>1000)fail('数量范围为 1 至 1000');return store.transaction(db=>{const values=[];for(let i=0;i<n;i++){let c;do{c=Array.from({length:24},()=>crypto.randomInt(10)).join('');}while(db.codes[digest(c)]);db.codes[digest(c)]={id:crypto.randomUUID(),createdAt:Date.now(),revoked:false};values.push(c);}return values;});}
function account(db,d,now,excludeId){
 db.accounts=db.accounts||{};let row=db.accounts[d];if(row&&row.revoked)fail('此授权已停用，请联系开发者',403);
 if(row&&Object.values(db.codes).some(x=>x.accountId===row.id&&x.revoked))fail('此授权已停用，请联系开发者',403);
 const paidRows=Object.values(db.codes).filter(x=>x.id!==excludeId&&x.device===d&&!x.revoked&&x.expiresAt>now),paid=paidRows.map(x=>x.expiresAt);
 if(!row)row=db.accounts[d]={id:crypto.randomUUID(),source:'account',device:d,activatedAt:now,expiresAt:now,revoked:false};
 row.expiresAt=Math.max(row.expiresAt,now,...paid);for(const x of paidRows)x.accountId=row.id;return row;
}
function redeem(store,rawCode,rawDevice,key,now=Date.now(),stack=false){
  const hash=digest(code(rawCode)),d=device(rawDevice);
 return store.transaction(db=>{const row=db.codes[hash];if(!row)fail('兑换码不存在，请核对后重试',404);if(row.revoked)fail('此周卡已停用，请联系开发者',403);if(row.device&&row.device!==d)fail('此兑换码已绑定其他设备',409);
  if(row.expiresAt&&row.expiresAt<=now){const existing=(db.accounts||{})[d];if(stack&&existing&&row.accountId===existing.id&&existing.expiresAt>now){account(db,d,now);return issue(existing,key,now);}fail('此兑换码已到期，请购买新的周卡',410);}
  const fresh=!row.device;if(fresh){row.device=d;row.activatedAt=now;row.expiresAt=now+WEEK;}
  if(stack){const a=account(db,d,now,fresh?row.id:null);if(row.accountId!==a.id){if(fresh)a.expiresAt+=WEEK;a.activatedAt=now;row.accountId=a.id;}return issue(a,key,now);}
  return issue(row,key,now);
 });
}
function issue(row,key,now){return {token:sign({kind:'license',product:PRODUCT,id:row.id,device:row.device,activatedAt:row.activatedAt,expiresAt:row.expiresAt,checkedAt:now,...(row.source==='account'?{licenseVersion:2,source:'account'}:{})},key)};}
function check(store,token,rawDevice,publicKey,privateKey,now=Date.now()){
 const d=device(rawDevice),p=verify(token,publicKey);if(p.kind!=='license'||p.product!==PRODUCT||p.device!==d)fail('授权与设备不匹配',401);
 return store.transaction(db=>{const a=p.source==='account'&&p.licenseVersion===2?(db.accounts||{})[d]:null;const row=a&&a.id===p.id?a:p.source==='account'?null:Object.values(db.codes).find(x=>x.id===p.id);if(!row||row.revoked||(a&&Object.values(db.codes).some(x=>x.accountId===a.id&&x.revoked)))fail('此周卡已停用，请联系开发者',403);if(row.device!==d)fail('授权与设备不匹配',401);if(row.expiresAt<=now)fail('周卡已到期',410);return issue(row,privateKey,now);});
}
module.exports={WEEK,PRODUCT,Store,generate,redeem,check,sign,verify,digest,fail,issue,account};
