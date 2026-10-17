from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from reportlab.graphics.barcode.qr import QrCodeWidget

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://jump.junxinghuatai.com.cn'
widget = QrCodeWidget(URL, barLevel='M')
widget.qr.make()
matrix = widget.qr.modules
size = len(matrix)
border = 4
total = size + border * 2
paths = ' '.join(f'M{x+border} {y+border}h1v1h-1z' for y,row in enumerate(matrix) for x,dark in enumerate(row) if dark)
svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total} {total}" role="img" aria-label="跳一跳助手下载页二维码" shape-rendering="crispEdges"><rect width="{total}" height="{total}" fill="white"/><path d="{paths}" fill="#142c50"/></svg>'
(ROOT/'brand'/'download-qr.svg').write_text(svg,encoding='utf-8')
qr = Image.new('RGB',(total*16,total*16),'white')
qd = ImageDraw.Draw(qr)
for y,row in enumerate(matrix):
    for x,dark in enumerate(row):
        if dark:
            qd.rectangle(((x+border)*16,(y+border)*16,(x+border+1)*16-1,(y+border+1)*16-1),fill='#142c50')
qr.save(ROOT/'brand'/'download-qr.png')
poster = Image.new('RGB',(1080,1500),'#f3f7ff')
d = ImageDraw.Draw(poster)
regular = 'C:/Windows/Fonts/msyh.ttc'
bold = 'C:/Windows/Fonts/msyhbd.ttc'
def text(s,y,size=36,color='#142c50',weight=False):
    font=ImageFont.truetype(bold if weight else regular,size)
    box=d.textbbox((0,0),s,font=font)
    d.text(((1080-(box[2]-box[0]))/2,y),s,font=font,fill=color)
d.rounded_rectangle((64,64,1016,1436),radius=44,fill='white')
logo=Image.open(ROOT/'brand'/'icon-512.png').convert('RGBA').resize((154,154),Image.Resampling.LANCZOS)
poster.paste(logo,(463,130),logo)
text('跳一跳助手',326,64,weight=True)
text('安卓下载与安装指南',425,35,'#526780')
poster.paste(qr,(int((1080-qr.width)/2),550))
text('扫码下载，按页面说明安装',1190,38,weight=True)
text('jump.junxinghuatai.com.cn',1260,31,'#2563eb')
text('Android 11 及以上 · 周卡激活',1330,27,'#526780')
poster.save(ROOT/'brand'/'share-poster.png',optimize=True)
raw=ROOT/'app'/'res'/'raw'
raw.mkdir(exist_ok=True)
(raw/'share_poster.png').write_bytes((ROOT/'brand'/'share-poster.png').read_bytes())
print(f'QR matrix {size}x{size}, four-module quiet zone, URL: {URL}')
