"""Local QA score reader trained from the game's own block digits; no network."""
from pathlib import Path
from PIL import Image
import numpy as np
import json
import sys
import glob

ROOT=Path(__file__).resolve().parents[2]
QA=ROOT/'dist'/'qa-accuracy'
TRAIN={'r10-1':560,'r10-2':578,'r10-3':598,'r10-4':620,'r10-5':644,
       'r10-6':670,'r10-7':671,'r10-8':672,'r10-9':674,'r10-10':678,
       'r10-11':692,'r10-12':702,'r9-12':183,'tenk-start':2077}

def glyphs(path):
    im=Image.open(path).convert('RGB'); w,h=im.size
    a=np.array(im.resize((540,round(h*540/w)),Image.Resampling.NEAREST)).astype(np.int16)
    hh=len(a); a=a[round(hh*.175):round(hh*.23),round(540*.08):round(540*.65)]
    lo=a.min(2); hi=a.max(2); mask=(lo>25)&(hi<140)&(hi-lo<40)
    columns=mask.sum(0)>4; out=[]; x=0
    while x<len(columns):
        if not columns[x]: x+=1; continue
        start=x
        while x<len(columns) and columns[x]: x+=1
        g=mask[:,start:x]; yy=np.where(g.any(1))[0]
        if len(yy)<12 or x-start<3: continue
        g=g[yy[0]:yy[-1]+1]
        out.append(np.array(Image.fromarray(g).resize((20,28),Image.Resampling.NEAREST)))
    return out

def train():
    cached=Path(__file__).with_name('score-templates.json')
    if cached.is_file():
        return [(d,np.array(g,dtype=bool)) for d,g in json.loads(cached.read_text())]
    templates=[]
    for name,value in TRAIN.items():
        gs=glyphs(QA/(name+'.png')); digits=str(value)
        if len(gs)!=len(digits): raise ValueError((name,len(gs),value))
        templates.extend(zip(digits,gs))
    return templates

def read(path,templates=None):
    templates=templates or train(); digits=[]; errors=[]
    for g in glyphs(path):
        error,digit=min((float((g!=t).mean()),d) for d,t in templates)
        digits.append(digit);errors.append(error)
    return (int(''.join(digits)) if digits and max(errors)<.12 else None,max(errors,default=1))

if __name__=='__main__':
    templates=train()
    if '--export' in sys.argv:
        Path(__file__).with_name('score-templates.json').write_text(json.dumps([(d,g.astype(int).tolist()) for d,g in templates]),encoding='utf-8')
        print('Exported digit templates');sys.exit(0)
    for pattern in sys.argv[1:]:
        for path in glob.glob(pattern):
            p=Path(path); value,error=read(p,templates)
            print(json.dumps({'file':p.name,'score':value,'error':round(error,4)}))
