'use strict';
// Read-only public release verification; no panel access or private credentials.
const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path');
(async()=>{
 const origin='https://jump.junxinghuatai.com.cn';
 const response=await fetch(origin+'/api/v1/update',{redirect:'error',signal:AbortSignal.timeout(20000)});
 if(!response.ok)throw Error('Update HTTP '+response.status);
 const {manifest}=await response.json(),parts=manifest.split('.');
 if(parts.length!==2||!crypto.verify('RSA-SHA256',Buffer.from(parts[0]),fs.readFileSync(path.join(__dirname,'../server/signing-public.pem')),Buffer.from(parts[1],'base64url')))throw Error('Manifest signature invalid');
 const release=JSON.parse(Buffer.from(parts[0],'base64url'));
 if(release.kind!=='update'||release.product!=='jump-weekly-v1'||release.packageName!=='local.jump.offline'||release.versionName!=='1.3.3'||release.versionCode!==10||release.size<1||release.size>100*1024*1024||release.url!==origin+'/downloads/'+release.sha256+'.apk')throw Error('Unexpected release');
 const download=await fetch(release.url,{redirect:'error',signal:AbortSignal.timeout(20000)});
 if(!download.ok)throw Error('Download HTTP '+download.status);
 const bytes=Buffer.from(await download.arrayBuffer()),hash=crypto.createHash('sha256').update(bytes).digest('hex');
 const local=fs.readFileSync(path.join(__dirname,'../dist/JumpAssistant-v1.3.3.apk'));
 if(bytes.length!==release.size||hash!==release.sha256||!bytes.equals(local))throw Error('Published APK differs');
 console.log(JSON.stringify({version:release.versionName,versionCode:release.versionCode,size:bytes.length,sha256:hash,url:release.url,manifestSignature:'valid',matchesLocal:true},null,2));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
