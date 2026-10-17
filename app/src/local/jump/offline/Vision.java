package local.jump.offline;

/** Pure Java local pixel geometry. No models, network, or Android dependencies. */
public final class Vision {
    public static final class Result {
        public boolean valid;
        public boolean clipped;
        public String reason;
        public int imageWidth;
        public float pieceX, pieceY, targetX, targetY, targetHalfWidth, distance1080;
        public Result(String reason) { this.reason = reason; }
        public long duration(float coefficient) { return Math.round(distance1080 * coefficient); }
        public boolean stableWith(Result other, int width) {
            return other != null && other.valid && Math.abs(pieceX-other.pieceX)<width*.012f
                && Math.abs(pieceY-other.pieceY)<width*.012f && Math.abs(targetX-other.targetX)<width*.012f
                && Math.abs(targetY-other.targetY)<width*.012f;
        }
        public String toString() { return reason + " piece="+pieceX+","+pieceY+" target="+targetX+","+targetY+" distance1080="+distance1080; }
    }
    private static int r(int c) { return c>>16&255; }
    private static int g(int c) { return c>>8&255; }
    private static int b(int c) { return c&255; }
    private static boolean piece(int c) {
        return b(c)>35 && b(c)<145 && b(c)>r(c)+7 && b(c)>g(c)+12 && r(c)<105 && g(c)<105;
    }
    private static boolean surface(int c, int bg) {
        int rr=r(c), gg=g(c), bb=b(c);
        int delta=Math.abs(rr-r(bg))+Math.abs(gg-g(bg))+Math.abs(bb-b(bg));
        // Segment by local background contrast, not platform hue. Gray roofs
        // and textured brown blocks must not be replaced by their side logos.
        // The top-face outline below separates a later cast shadow/side flap.
        return delta>=24;
    }
    static int background(int[] p,int w,int h,int y){
        int edge=p[y*w+2],reference=p[(int)(h*.30)*w+w/2];
        if(Math.abs(r(edge)-r(reference))+Math.abs(g(edge)-g(reference))+Math.abs(b(edge)-b(reference))>45)
            edge=p[y*w+w-3];
        return edge;
    }
    public static Result analyze(int[] pixels, int w, int h) {return analyze(pixels,w,h,false);}
    public static Result analyze(int[] pixels, int w, int h,boolean allowClipped) {
        if(w<240 || h<w*1.35 || pixels.length!=w*h) return new Result("需要竖屏完整游戏画面");
        // The score is a second independent guard against menu/chat screens.
        int score=0;
        for(int y=(int)(h*.175);y<h*.23;y++) for(int x=(int)(w*.08);x<w*.37;x++) {
            int c=pixels[y*w+x];
            // The score ink is theme-tinted: the yellow board uses RGB(79,74,65),
            // rather than neutral gray. Keep the location/area guard, but accept
            // dark low-chroma ink in warm and cool themes.
            int lo=Math.min(r(c),Math.min(g(c),b(c))),hi=Math.max(r(c),Math.max(g(c),b(c)));
            if(lo>25 && hi<140 && hi-lo<40) score++;
        }
        if(score<w*w*.0007) return new Result("未找到游戏计分区");
        boolean[] seen=new boolean[w*h]; int[] queue=new int[w*h];
        int bestArea=0, px=0, py=0;
        int y0=(int)(h*.34), y1=(int)(h*.69);
        for(int y=y0;y<y1;y++) for(int x=1;x<w-1;x++) {
            int start=y*w+x;
            if(seen[start] || !piece(pixels[start])) continue;
            int head=0,tail=0;queue[tail++]=start;seen[start]=true;
            int minX=x,maxX=x,minY=y,maxY=y;
            while(head<tail) {
                int p=queue[head++], xx=p%w, yy=p/w;
                minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                for(int k=0;k<4;k++) {
                    int nx=xx+(k==0?-1:k==1?1:0),ny=yy+(k==2?-1:k==3?1:0);
                    if(nx<1 || nx>=w-1 || ny<y0 || ny>=y1) continue;
                    int q=ny*w+nx;
                    if(!seen[q] && piece(pixels[q])) {seen[q]=true;queue[tail++]=q;}
                }
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            int headPixels=0;
            if(cw>w*.023 && cw<w*.10 && ch>cw*1.15 && ch<w*.22 && tail>bestArea && tail>w*w*.0006) {
                int center=(minX+maxX)/2;
                for(int hy=Math.max(y0,minY-(int)(w*.085));hy<minY-w*.004;hy++)for(int hx=Math.max(1,center-(int)(w*.035));hx<Math.min(w-1,center+w*.035);hx++)if(piece(pixels[hy*w+hx]))headPixels++;
            }
            if(headPixels>w*w*.0002) {
                bestArea=tail;px=(minX+maxX)/2;py=maxY-(int)(w*.012);
            }
        }
        if(bestArea==0) return new Result("未找到静止棋子，请使用默认皮肤");
        // The next platform is the topmost surface outside the pawn column.
        int tx=-1, top=-1;float ty=0,halfWidth=0;
        outer: for(int y=(int)(h*.35);y<py-w*.045;y++) {
            int bg=background(pixels,w,h,y);
            for(int x=3;x<w-3;x++) {
                if(Math.abs(x-px)<w*.05 || !surface(pixels[y*w+x],bg)) continue;
                int start=x;
                while(x<w-3 && Math.abs(x-px)>=w*.05 && surface(pixels[y*w+x],bg))x++;
                int length=x-start;
                if(length>=Math.max(4,w*.010)) {
                    int candidateX=(start+x-1)/2;
                    float[] center=platformCenter(pixels,w,h,candidateX,y,px,allowClipped);
                    if(center==null)continue;
                    if(center[0]<0){Result uncertain=new Result("目标平台超出画面或轮廓不完整");uncertain.clipped=true;return uncertain;}
                    float candidateDistance=Math.abs(center[0]-px);
                    if(candidateDistance<w*.065f||candidateDistance>w*.82f||center[1]<y+w*.006f||center[1]>y+w*.23f)continue;
                    tx=Math.round(center[0]);top=y;ty=center[1];halfWidth=center[2];break outer;
                }
            }
        }
        if(tx<0) return new Result("未找到下一个平台");
        float dx=Math.abs(tx-px);
        if(dx<w*.065 || dx>w*.82 || ty<top-w*.025 || ty>top+w*.23) return new Result("平台几何不确定，已停止");
        Result out=new Result("识别成功");out.valid=true;
        out.imageWidth=w;
        out.pieceX=px;out.pieceY=py;out.targetX=tx;out.targetY=ty;
        out.targetHalfWidth=halfWidth;
        // Platform height and an off-center previous landing change the vertical
        // distance. Inferring it from dx alone systematically undershoots raised
        // platforms, especially on short jumps.
        out.distance1080=(float)Math.hypot(tx-px,ty-py)*1080/w;
        return out;
    }
    private static float[] platformCenter(int[] pixels,int w,int h,int seedX,int top,int pawnX,boolean allowClipped){
        int largest=0,peakLeft=seedX,peakRight=seedX,leftY=top,rightY=top,emptyRows=0;
        int[] widths=new int[(int)(w*.24f)+2];
        int previousLeft=seedX,previousRight=seedX;boolean leftOccluded=false,rightOccluded=false;
        boolean leftClipped=false,rightClipped=false;
        int gapLimit=Math.max(2,Math.round(w*.008f));
        int face=pixels[Math.min(h-1,top+Math.max(3,Math.round(w*.008f)))*w+seedX];
        for(int y=top;y<Math.min(h,top+w*.24f);y++){
            int bg=background(pixels,w,h,y),left=seedX,right=seedX;
            boolean connected=false;
            for(int dx=-2;dx<=2;dx++)if(seedX+dx>2&&seedX+dx<w-3&&surface(pixels[y*w+seedX+dx],bg))connected=true;
            if(connected)emptyRows=0;else if(++emptyRows>gapLimit*2)return null;
            // Bridge a few antialiased pixels around a center marking; later
            // shadows are rejected by the first-side expansion stop below.
            for(int direction=-1;direction<=1;direction+=2){
                int last=seedX,gaps=0;boolean reached=false;
                int previousEdge=direction<0?previousLeft:previousRight;
                for(int x=seedX;x>2&&x<w-3;x+=direction){
                    int c=pixels[y*w+x];
                    // Distant colored tops can contain stripes across the face.
                    // Nearby/white faces keep the color boundary that separates
                    // touching platforms and their gray side faces.
                    if(surface(c,bg)&&((Math.abs(pawnX-seedX)>=w*.25f&&!white(face))
                            ||Math.abs(r(c)-r(face))+Math.abs(g(c)-g(face))+Math.abs(b(c)-b(face))<=60)){last=x;gaps=0;reached=true;}
                    else if(++gaps>(reached?gapLimit:Math.max(gapLimit,Math.round(w*.03f)))
                            &&direction*(x-previousEdge)>gapLimit)break;
                }
                if(direction<0)left=last;else right=last;
            }
            int span=right-left+1;
            widths[y-top]=span;
            leftClipped|=left<=3;rightClipped|=right>=w-4;
            if(!allowClipped&&(leftClipped||rightClipped))return new float[]{-1,-1};
            if((leftClipped&&rightClipped)||((leftClipped||rightClipped)
                    &&(seedX<w*.05f||seedX>w*.95f)))return new float[]{-1,-1};
            if(span>w*.65f)break;
            if(y-top>w*.015f){
                if(previousLeft-left>w*.018f)leftOccluded=true;
                if(right-previousRight>w*.018f)rightOccluded=true;
            }
            previousLeft=left;previousRight=right;
            largest=Math.max(largest,span);
            if(left<peakLeft){peakLeft=left;leftY=y;}
            if(right>peakRight){peakRight=right;rightY=y;}
            // The first side to stop expanding marks the top face's center.
            // A carton flap or a side logo can extend the other side later.
            int settle=Math.max(6,Math.round(w*.015f));
            int centerY=leftClipped?rightY:rightClipped?leftY:
                Math.abs(pawnX-seedX)<w*.25f?(seedX<pawnX?leftY:rightY):Math.min(leftY,rightY);
            if(largest>w*.045f&&y-centerY>=settle)break;
        }
        if(largest<w*.045f||(leftOccluded&&rightOccluded))return null;
        // A cropped outer edge does not hide the apex or the opposite edge.
        // Keep the original apex as x, and locate the center using only the
        // complete side. Never scan lower rows as if they were another platform.
        if((leftClipped&&rightOccluded)||(rightClipped&&leftOccluded))return new float[]{-1,-1};
        int centerY=leftClipped?rightY:rightClipped?leftY:
            Math.abs(pawnX-seedX)<w*.25f?(seedX<pawnX?leftY:rightY):Math.min(leftY,rightY);
        // A projected circular face reaches its widest pixel span several rows
        // before its mathematical center. Its isometric height/width is 1/sqrt(3).
        // Distinguish the curved upper half from a diamond's linear expansion.
        int halfDepth=Math.max(0,(centerY-top)/2);
        int halfWidth=leftClipped?peakRight-seedX:rightClipped?seedX-peakLeft:(peakRight-peakLeft)/2;
        float roundCenter=top+halfWidth/(float)Math.sqrt(3);
        if(!leftOccluded&&!rightOccluded&&widths[halfDepth]>largest*.70f
                &&Math.abs((seedX-peakLeft)-(peakRight-seedX))<w*.012f
                &&Math.abs(roundCenter-centerY)<w*.015f)centerY=Math.round(roundCenter);
        float[] center=markedCenter(pixels,w,h,seedX,centerY+1);
        return new float[]{center[0],center[1],halfWidth};
    }
    private static boolean white(int c){return Math.min(r(c),Math.min(g(c),b(c)))>235
        &&Math.max(r(c),Math.max(g(c),b(c)))-Math.min(r(c),Math.min(g(c),b(c)))<25;}
    private static float[] markedCenter(int[] p,int w,int h,int cx,int cy){
        int radius=Math.max(8,Math.round(w*.025f)),size=radius*2+1;
        boolean[] seen=new boolean[size*size];int[] q=new int[size*size];
        float[] result={cx,cy};float nearest=radius*radius;
        for(int yy=0;yy<size;yy++)for(int xx=0;xx<size;xx++){
            int start=yy*size+xx,x=cx-radius+xx,y=cy-radius+yy;
            if(seen[start]||x<1||x>=w-1||y<1||y>=h-1||!white(p[y*w+x]))continue;
            int head=0,tail=0,minX=xx,maxX=xx,minY=yy,maxY=yy;q[tail++]=start;seen[start]=true;
            while(head<tail){int a=q[head++],ax=a%size,ay=a/size;
                minX=Math.min(minX,ax);maxX=Math.max(maxX,ax);minY=Math.min(minY,ay);maxY=Math.max(maxY,ay);
                for(int k=0;k<4;k++){int nx=ax+(k==0?-1:k==1?1:0),ny=ay+(k==2?-1:k==3?1:0);
                    if(nx<0||nx>=size||ny<0||ny>=size)continue;
                    int at=ny*size+nx,px=cx-radius+nx,py=cy-radius+ny;
                    if(!seen[at]&&px>0&&px<w-1&&py>0&&py<h-1&&white(p[py*w+px])){seen[at]=true;q[tail++]=at;}
                }
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            if(minX==0||maxX==size-1||minY==0||maxY==size-1||cw<3||cw>w*.045f||ch<2||ch>w*.026f||tail<cw*ch*.45f)continue;
            float mx=cx-radius+(minX+maxX)*.5f,my=cy-radius+(minY+maxY)*.5f;
            int ix=Math.round(mx),iy=Math.round(my),rx=cw/2+3,ry=ch/2+3;
            if(ix-rx<1||ix+rx>=w-1||iy-ry<1||iy+ry>=h-1)continue;
            int[] ring={p[iy*w+ix-rx],p[iy*w+ix+rx],p[(iy-ry)*w+ix],p[(iy+ry)*w+ix]};boolean isolated=true;
            for(int c:ring)if(white(c)||!surface(c,p[iy*w+2]))isolated=false;
            float distance=(mx-cx)*(mx-cx)+(my-cy)*(my-cy);
            if(isolated&&distance<nearest){nearest=distance;result=new float[]{mx,my};}
        }
        return result;
    }
}
