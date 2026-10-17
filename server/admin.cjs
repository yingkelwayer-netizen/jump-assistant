'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {Store,generate,digest,sign,PRODUCT}=require('./core.cjs');
const [command,dir,...args]=process.argv.slice(2);if(!dir)throw Error('Usage: node admin.cjs init|generate|revoke|publish|list DATA_DIR [arguments]');
const store=new Store(path.resolve(dir));
if(command==='init'){
 const privatePath=path.join(store.dir,'signing-private.pem'),publicPath=path.join(store.dir,'signing-public.pem');
 if(fs.existsSync(privatePath)||fs.existsSync(publicPath))throw Error('Signing key exists; refusing overwrite');
 const keys=crypto.generateKeyPairSync('rsa',{modulusLength:3072,publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}});
 fs.writeFileSync(privatePath,keys.privateKey,{mode:0o600,flag:'wx'});fs.writeFileSync(publicPath,keys.publicKey,{mode:0o644,flag:'wx'});console.log('Initialized signing keys. Public key:\n'+keys.publicKey);
}else if(command==='generate'){console.log(generate(store,Number(args[0]||1)).join('\n'));
}else if(command==='revoke'){
 const id=args[0];store.transaction(db=>{const row=[...Object.values(db.codes),...Object.values(db.accounts||{})].find(x=>x.id===id);if(!row)throw Error('License id not found');row.revoked=true;});console.log('Revoked '+id);
}else if(command==='list'){console.log(store.transaction(db=>[...Object.values(db.codes),...Object.values(db.accounts||{})].map(x=>({id:x.id,source:x.source||'code',activatedAt:x.activatedAt||null,expiresAt:x.expiresAt||null,revoked:x.revoked}))))
}else if(command==='publish'){
 const [apk,versionCode,versionName,origin,notesFile]=args,c=Number(versionCode),u=new URL(origin);
 if(u.protocol!=='https:'||u.pathname!=='/'||!Number.isSafeInteger(c)||c<3||!/^\d+\.\d+\.\d+$/.test(versionName))throw Error('Invalid release arguments');
 const bytes=fs.readFileSync(apk);if(bytes.length>100*1024*1024||bytes[0]!==0x50||bytes[1]!==0x4b)throw Error('Invalid APK');const hash=digest(bytes),releases=path.join(store.dir,'releases');fs.mkdirSync(releases,{recursive:true,mode:0o700});
 const target=path.join(releases,hash+'.apk');if(!fs.existsSync(target))fs.writeFileSync(target,bytes,{flag:'wx',mode:0o600});
 const key=fs.readFileSync(path.join(store.dir,'signing-private.pem')),notes=notesFile?fs.readFileSync(notesFile,'utf8').slice(0,4000):'优化使用体验与稳定性。';
 const token=sign({kind:'update',product:PRODUCT,packageName:'local.jump.offline',versionCode:c,versionName,minSdk:30,size:bytes.length,sha256:hash,url:u.origin+'/downloads/'+hash+'.apk',notes,publishedAt:Date.now()},key);
 fs.writeFileSync(path.join(store.dir,'release.json.tmp'),token,{mode:0o600});fs.renameSync(path.join(store.dir,'release.json.tmp'),path.join(store.dir,'release.json'));console.log('Published '+versionName+' '+hash);
}else throw Error('Unknown command');
