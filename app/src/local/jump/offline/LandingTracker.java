package local.jump.offline;

/** In-memory, bounded feedback from the same platform before and after a jump. */
public final class LandingTracker {
    private static final class Patch {
        int w,h,bucket,count,foreground;
        int[] dx,dy,color;boolean[] mask;
        float ux,uy,coefficient;
    }
    private Patch pending;
    private float base;
    private final float[] bias=new float[3];
    public synchronized void reset(float coefficient){pending=null;base=coefficient;java.util.Arrays.fill(bias,0);}
    public synchronized void restore(float coefficient,float a,float b,float c){
        reset(coefficient);float[] saved={a,b,c};
        for(int i=0;i<3;i++)if(!Float.isNaN(saved[i])&&!Float.isInfinite(saved[i]))bias[i]=clamp(saved[i],60);
    }
    public synchronized float[] snapshot(){return new float[]{base,bias[0],bias[1],bias[2]};}
    public synchronized void begin(float coefficient){pending=null;if(Math.abs(base-coefficient)>.0001f){base=coefficient;java.util.Arrays.fill(bias,0);}}
    public synchronized void discard(){pending=null;}
    private int bucket(float distance){return distance<300?0:distance<550?1:2;}
    public synchronized long duration(Vision.Result r,float coefficient){return Math.round(r.distance1080*coefficient+bias[bucket(r.distance1080)]);}
    public synchronized float correction(int index){return bias[index];}
    private static int difference(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
    private static float clamp(float v,float limit){return Math.max(-limit,Math.min(limit,v));}
    public synchronized void record(int[] pixels,int w,int h,Vision.Result r,float coefficient){
        pending=null;if(!r.valid||pixels==null||pixels.length!=w*h)return;
        Patch p=new Patch();p.w=w;p.h=h;p.bucket=bucket(r.distance1080);p.coefficient=coefficient;
        float length=(float)Math.hypot(r.targetX-r.pieceX,r.targetY-r.pieceY);if(length<w*.065f)return;
        p.ux=(r.targetX-r.pieceX)/length;p.uy=(r.targetY-r.pieceY)/length;
        // A fixed small inner patch is blank on a large white platform, so it
        // cannot identify where that platform moved. Include its outer outline.
        int halfX=Math.max(Math.round(w*.12f),Math.round(r.targetHalfWidth+w*.02f));
        int halfY=Math.max(Math.round(w*.075f),Math.round(r.targetHalfWidth/(float)Math.sqrt(3)+w*.02f));
        int step=Math.max(2,(int)Math.ceil(Math.sqrt(4.0*halfX*halfY/1800)));
        int capacity=(halfX*2/step+2)*(halfY*2/step+2);
        p.dx=new int[capacity];p.dy=new int[capacity];p.color=new int[capacity];p.mask=new boolean[capacity];
        int cx=Math.round(r.targetX),cy=Math.round(r.targetY);
        for(int y=-halfY;y<=halfY;y+=step)for(int x=-halfX;x<=halfX;x+=step){
            int xx=cx+x,yy=cy+y;
            if(Math.abs(x)<w*.046f||Math.abs(xx-r.pieceX)<w*.05f||xx<2||xx>=w-2||yy<2||yy>=h-2)continue;
            int i=p.count++;p.dx[i]=x;p.dy[i]=y;p.color[i]=pixels[yy*w+xx];p.mask[i]=difference(p.color[i],Vision.background(pixels,w,h,yy))>=24;
            if(p.mask[i])p.foreground++;
        }
        if(p.foreground<30||p.count<80)return;pending=p;
    }
    private static float cost(Patch p,int[] pixels,int x,int y){
        long sum=0;
        for(int i=0;i<p.count;i++){
            int xx=x+p.dx[i],yy=y+p.dy[i];if(xx<2||xx>=p.w-2||yy<2||yy>=p.h-2)return 1000;
            int c=pixels[yy*p.w+xx];boolean fg=difference(c,Vision.background(pixels,p.w,p.h,yy))>=24;
            sum+=fg!=p.mask[i]?100:fg?Math.min(90,difference(c,p.color[i])):0;
        }
        return sum/(float)p.count;
    }
    public synchronized boolean observe(int[] pixels,int w,int h,Vision.Result landed){
        Patch p=pending;pending=null;if(p==null||!landed.valid||pixels==null||p.w!=w||p.h!=h)return false;
        int radius=Math.round(w*.045f),step=Math.max(1,w/540),cx=Math.round(landed.pieceX),cy=Math.round(landed.pieceY);
        float best=1000;int bx=cx,by=cy;
        for(int y=cy-radius;y<=cy+radius;y+=step)for(int x=cx-radius;x<=cx+radius;x+=step){float c=cost(p,pixels,x,y);if(c<best){best=c;bx=x;by=y;}}
        int ox=bx,oy=by;
        for(int y=oy-step;y<=oy+step;y++)for(int x=ox-step;x<=ox+step;x++){float c=cost(p,pixels,x,y);if(c<best){best=c;bx=x;by=y;}}
        if(best>22)return false;
        float alternative=1000;int separation=Math.max(3,Math.round(w*.006f));
        for(int y=cy-radius;y<=cy+radius;y+=step)for(int x=cx-radius;x<=cx+radius;x+=step){
            if(Math.abs(x-bx)<=separation&&Math.abs(y-by)<=separation)continue;
            alternative=Math.min(alternative,cost(p,pixels,x,y));
        }
        if(alternative-best<1.5f)return false;
        // The visible pawn base is an ellipse: its contact center is above the
        // bottom contour used by the coarse recognizer. Center-hit pairs give
        // this small, resolution-scaled offset; it is never learned from score.
        float ex=bx-landed.pieceX,ey=by-(landed.pieceY-w*.0092f);
        float along=ex*p.ux+ey*p.uy,cross=ex*p.uy-ey*p.ux;
        if(Math.abs(along)>w*.035f||Math.abs(cross)>w*.02f)return false;
        float delta=clamp(along*1080/w*p.coefficient*.4f,5);
        bias[p.bucket]=clamp(bias[p.bucket]+delta,60);return true;
    }
}
