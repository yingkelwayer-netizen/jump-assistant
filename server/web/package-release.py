"""Prepare the APK, matching download page and release notes for panel upload."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--version', required=True)
args = parser.parse_args()
if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
    parser.error('Invalid version')
root = Path(__file__).resolve().parents[2]
apk = root / 'dist' / f'JumpAssistant-v{args.version}.apk'
notes = root / 'server' / f'release-notes-{args.version}.txt'
if not apk.is_file() or not notes.is_file():
    parser.error('Build the signed APK and write its release notes first')
subprocess.run([sys.executable, str(Path(__file__).with_name('build-page.py')),
                '--version', args.version], check=True)
page = root / 'server' / 'web' / 'public' / 'index.html'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
html = page.read_text(encoding='utf-8')
assert f'/downloads/{digest}.apk' in html and f'v{args.version}' in html
assert not re.search(r'[¥￥]|\d+\s*元|价格|周卡售价', html)
bundle = root / 'dist' / f'JumpAssistant-release-{args.version}.zip'
with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
    for source, name in [(apk, apk.name), (page, 'index.html'), (notes, 'release-notes.txt')]:
        archive.write(source, name)
with zipfile.ZipFile(bundle) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist()) == 3
print('Bundle:', bundle)
print('Bundle SHA256:', hashlib.sha256(bundle.read_bytes()).hexdigest())
