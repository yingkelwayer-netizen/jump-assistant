from pathlib import Path
import hashlib
import argparse
from datetime import date

parser=argparse.ArgumentParser(description='Build the download page for an existing signed APK.')
parser.add_argument('--version',default='1.3.3')
args=parser.parse_args()
import re
if not re.fullmatch(r'\d+\.\d+\.\d+',args.version):
    parser.error('Invalid release version')

root=Path(__file__).resolve().parents[2]
apk=root/'dist'/f'JumpAssistant-v{args.version}.apk'
data=apk.read_bytes()
digest=hashlib.sha256(data).hexdigest()
page=(root/'server'/'web'/'index.template.html').read_text(encoding='utf-8')
page=page.replace('__VERSION__',args.version).replace('__RELEASE_DATE__',date.today().strftime('%Y.%m.%d'))
page=page.replace('__APK_URL__','/downloads/'+digest+'.apk').replace('__APK_SIZE__',str(round(len(data)/1024))).replace('__QR_SVG__',(root/'brand'/'download-qr.svg').read_text(encoding='utf-8'))
assert '__APK_' not in page and '__QR_' not in page
out=root/'server'/'web'/'public'
out.mkdir(exist_ok=True)
(out/'index.html').write_text(page,encoding='utf-8')
(out/'share.png').write_bytes((root/'brand'/'share-poster.png').read_bytes())
print('APK SHA256:',digest)
print('Page bytes:',len(page.encode('utf-8')))
