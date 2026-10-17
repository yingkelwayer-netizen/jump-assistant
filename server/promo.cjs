'use strict';
const crypto=require('node:crypto');
const {WEEK,issue,fail,account}=require('./core.cjs');
function identify(value){if(typeof value!=='string'||!/^[a-f0-9]{64}$/.test(value))fail('设备标识无效');return value;}
function status(store,rawDevice,enabled,now=Date.now(),key){
 const d=identify(rawDevice);return store.transaction(db=>{const row=(db.shareRewards||{})[d],a=(db.accounts||{})[d],blocked=!!(a&&(a.revoked||Object.values(db.codes).some(x=>x.accountId===a.id&&x.revoked)));const next=row?row.lastClaimAt+WEEK:0;return {enabled:!!enabled,eligible:!!enabled&&!blocked&&now>=next,blocked,serverTime:now,nextEligibleAt:next,expiresAt:a?a.expiresAt:0,...(key&&a&&!blocked&&a.expiresAt>now?issue(a,key,now):{})};});
}
function claim(store,rawDevice,requestId,key,enabled,now=Date.now()){
 const d=identify(rawDevice);if(!enabled)fail('本期免费领取活动已结束',403);
 if(typeof requestId!=='string'||!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(requestId))fail('领取请求无效');
 return store.transaction(db=>{
  db.shareRewards=db.shareRewards||{};let row=db.shareRewards[d];
  const a=account(db,d,now);
  if(row&&row.lastRequestId===requestId){if(a.expiresAt<=now)fail('上次领取的周卡已到期，请重新发起领取',410);return {...issue(a,key,now),nextEligibleAt:row.lastClaimAt+WEEK};}
  if(row&&now<row.lastClaimAt+WEEK)fail('每台设备每 7 天可领取一次，请到可领取时间再试',409);
  a.activatedAt=now;a.expiresAt+=WEEK;
  row={lastClaimAt:now,lastRequestId:requestId};
  db.shareRewards[d]=row;return {...issue(a,key,now),nextEligibleAt:now+WEEK};
 });
}
module.exports={status,claim};
