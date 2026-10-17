'use strict';
// Public endpoints only; status probes never grant a card or redeem a code.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),origin='https://jump.junxinghuatai.com.cn';
async function get(route){const r=await fetch(origin+route,{redirect:'error',signal:AbortSignal.timeout(20000)});assert.equal(r.status,200);return Buffer.from(await r.arrayBuffer());}
(async()=>{
 const [page,poster,health]=await Promise.all([get('/'),get('/share.png'),get('/health')]);
 assert.ok(page.equals(fs.readFileSync(path.join(root,'server/web/public/index.html'))));
 assert.ok(poster.equals(fs.readFileSync(path.join(root,'brand/share-poster.png'))));
 assert.ok(!/[¥￥]|\d+\s*元|价格|周卡售价/.test(page.toString('utf8')),'Price remains in public page');
 assert.equal(JSON.parse(health).version,'1.3.0');
 const device=crypto.createHash('sha256').update('public-release-status-probe-v1').digest('hex');
 async function status(versionCode){const r=await fetch(origin+'/api/v1/share/status',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({device,versionCode}),signal:AbortSignal.timeout(20000)});return {status:r.status,body:await r.json()};}
 const current=await status(10),old=await status(9);
 assert.equal(current.status,200);assert.equal(current.body.enabled,true);assert.equal(current.body.eligible,true);assert.equal(current.body.expiresAt,0);assert.equal(current.body.token,undefined);assert.equal(old.status,426);
 const report={version:'1.3.3',pageMatchesLocal:true,pageHasPrice:false,posterMatchesLocal:true,health:JSON.parse(health),shareStatus:'enabled and eligible without grant',oldShareVersionStatus:old.status,verifiedAt:new Date().toISOString()};
 fs.mkdirSync(path.join(root,'dist/qa-share'),{recursive:true});fs.writeFileSync(path.join(root,'dist/qa-share/live-1.3.3.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
