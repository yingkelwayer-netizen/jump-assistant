"""Observe opt-in QA frames; pause the game on a clear miss, stop or goal."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
from score import read,train

parser=argparse.ArgumentParser()
parser.add_argument('--adb',default='adb',help='Path to Android platform-tools adb')
parser.add_argument('--serial',required=True,help='Explicit test device serial from adb devices')
parser.add_argument('--run',required=True)
parser.add_argument('--baseline',type=int,required=True)
parser.add_argument('--gain',type=int,default=10000)
parser.add_argument('--calibration',action='store_true',help='Record misses while calibrating; never counts as a passing run')
args=parser.parse_args()
assert re.fullmatch(r'[a-z0-9_-]+',args.run)
root=Path(__file__).resolve().parents[2];out=root/'dist'/'qa-tenk'/args.run
out.mkdir(parents=True,exist_ok=False)
adb=args.adb;serial=args.serial
def call(*parts):
    return subprocess.run([adb,'-s',serial,*parts],capture_output=True,timeout=20)
templates=train();started=time.time();last=args.baseline;highest=last;jumps=0;frames=0;misses=0;active=False
stream=subprocess.Popen([adb,'-s',serial,'logcat','-v','raw','-T','1','-s','JumpQa:I','*:S'],stdout=subprocess.PIPE,text=True,encoding='utf-8',errors='replace')
log=(out/'events.jsonl').open('w',encoding='utf-8',buffering=1)
def finish(status,detail,pause=True):
    if pause:call('shell','input','tap','820','335')
    result={'status':status,'detail':detail,'baseline':args.baseline,'score':last,'gain':last-args.baseline,'jumps':jumps,'frames':frames,'misses':misses,'calibration':args.calibration,'elapsedSeconds':round(time.time()-started,1)}
    (out/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False),flush=True);stream.terminate();raise SystemExit(0)
print(json.dumps({'status':'watching','run':args.run,'baseline':last,'target':last+args.gain}),flush=True)
try:
    for line in stream.stdout:
        try:item=json.loads(line)
        except (ValueError,TypeError):continue
        if item.get('time',0)<started*1000-1000:continue
        kind=item.get('type')
        if kind=='JUMP':jumps+=1
        if kind=='FRAME':
            active=True;name=item.get('file','');assert re.fullmatch(r'frame-\d{3}\.png',name)
            target=out/f'{frames:05d}.png';frames+=1
            copied=call('pull','/sdcard/Android/data/local.jump.offline/files/qa/'+name,str(target))
            if copied.returncode:finish('FAIL','QA frame transfer failed')
            value,error=read(target,templates);item.update(score=value,ocrError=round(error,4),localFrame=target.name)
            if value is not None:
                change=value-last
                if change!=0:print(json.dumps({'score':value,'change':change,'jumps':jumps,'frames':frames}),flush=True)
                if value<last:log.write(json.dumps(item,ensure_ascii=False)+'\n');finish('FAIL','score decreased or game restarted')
                last=value;highest=max(highest,value)
                if change==1 and jumps>0:
                    misses+=1
                    if not args.calibration:log.write(json.dumps(item,ensure_ascii=False)+'\n');finish('FAIL','one-point landing: center streak lost')
                if value>=args.baseline+args.gain:
                    log.write(json.dumps(item,ensure_ascii=False)+'\n');finish('CALIBRATED' if args.calibration else 'PASS','continuous score target reached')
        log.write(json.dumps(item,ensure_ascii=False)+'\n')
        if kind=='STOP' and active:finish('FAIL','assistant stopped: '+item.get('detail',''),pause=False)
finally:
    stream.terminate();log.close()
