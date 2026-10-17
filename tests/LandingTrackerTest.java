import local.jump.offline.Vision;
import local.jump.offline.LandingTracker;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
public final class LandingTrackerTest {
    static int passed;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;}
    static int[] image(String name,int width)throws Exception{
        BufferedImage raw=ImageIO.read(new File(name));int height=Math.round(raw.getHeight()*width/(float)raw.getWidth());
        BufferedImage im=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);im.createGraphics().drawImage(raw,0,0,width,height,null);
        return im.getRGB(0,0,width,height,null,0,width);
    }
    public static void main(String[] args)throws Exception{
        String dir=args[0];
        for(int w:new int[]{540,720,1080}){
            int[] before=image(dir+"/before-miss.png",w),after=image(dir+"/after-miss.png",w);int h=before.length/w;
            Vision.Result start=Vision.analyze(before,w,h),landed=Vision.analyze(after,w,h);
            check(start.valid&&landed.valid,"actual game pair recognized");
            LandingTracker tracker=new LandingTracker();tracker.begin(1.38f);tracker.record(before,w,h,start,1.38f);
            check(tracker.observe(after,w,h,landed),"same platform matched at "+w);
            check(tracker.correction(1)>0&&tracker.correction(1)<=5,"underjump learns a bounded positive correction: "+tracker.correction(1));
            check(!tracker.observe(after,w,h,landed),"one observation per jump");
            for(int i=0;i<15;i++){tracker.record(before,w,h,start,1.38f);tracker.observe(after,w,h,landed);}
            check(tracker.correction(1)<=60,"correction hard bound");
            tracker.begin(1.4f);check(tracker.correction(1)==0,"manual coefficient change resets feedback");
            tracker.record(before,w,h,start,1.4f);check(!tracker.observe(new int[before.length],w,h,landed),"unrelated frame cannot calibrate");
            before=image(dir+"/before-center.png",w);after=image(dir+"/after-center.png",w);
            start=Vision.analyze(before,w,h);landed=Vision.analyze(after,w,h);tracker=new LandingTracker();tracker.begin(1.38f);tracker.record(before,w,h,start,1.38f);
            check(tracker.observe(after,w,h,landed),"center-hit pair matched");
            check(Math.abs(tracker.correction(0))+Math.abs(tracker.correction(1))+Math.abs(tracker.correction(2))<3,"center hit does not cause a large correction");
            before=image(dir+"/before-large.png",w);after=image(dir+"/after-large.png",w);
            start=Vision.analyze(before,w,h,true);landed=Vision.analyze(after,w,h,true);
            tracker=new LandingTracker();tracker.begin(1.38f);tracker.record(before,w,h,start,1.38f);
            check(tracker.observe(after,w,h,landed),"large blank white top is matched using its outer boundary");
            check(tracker.correction(2)<0,"large-platform overjump learns a negative correction");
            for(int i=0;i<20;i++){tracker.record(before,w,h,start,1.38f);tracker.observe(after,w,h,landed);}
            check(tracker.correction(2)<-24&&tracker.correction(2)>=-60,"long-jump calibration can exceed the former 24ms ceiling safely");
            float[] state=tracker.snapshot();LandingTracker restored=new LandingTracker();restored.restore(state[0],state[1],state[2],state[3]);
            check(restored.duration(start,1.38f)==tracker.duration(start,1.38f),"local calibration snapshot survives service recreation");
            restored.begin(1.4f);check(restored.correction(2)==0,"changing manual coefficient discards restored learning");
            restored.restore(1.38f,Float.NaN,Float.POSITIVE_INFINITY,1000);
            check(restored.correction(0)==0&&restored.correction(1)==0&&restored.correction(2)==60,"saved calibration is finite and bounded");
        }
        System.out.println("Landing feedback: "+passed+" assertions passed");
    }
}
