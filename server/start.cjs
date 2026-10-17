'use strict';
const path=require('node:path'),config=require('./service-config.json');
require('./service.cjs').createService({dir:path.join(__dirname,'..','data'),origin:config.origin}).listen(config.port,'127.0.0.1',()=>console.log('Jump service listening on loopback:'+config.port));
