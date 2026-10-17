import local.jump.offline.Vision;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public final class VisionTest {
    private static int passed;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;}
    private static int[] board(int width,int height,int px,int tx){
        return board(width,height,px,tx,0);
    }
    private static int[] board(int width,int height,int px,int tx,int lift){
        int[] p=new int[width*height];java.util.Arrays.fill(p,0xffcdd0d9);
        for(int y=(int)(height*.19);y<height*.215;y++)for(int x=(int)(width*.11);x<width*.15;x++)p[y*width+x]=0xff484848;
        int py=(int)(height*.56),targetY=(int)(py-Math.abs(tx-px)/Math.sqrt(3))-lift;
        for(int y=py-62;y<=py+6;y++)for(int x=px-18;x<=px+18;x++)p[y*width+x]=0xff34284e;
        for(int y=py-100;y<=py-70;y++)for(int x=px-15;x<=px+15;x++)if((x-px)*(x-px)+(y-py+85)*(y-py+85)<225)p[y*width+x]=0xff34284e;
        for(int y=targetY-55;y<=targetY+55;y++)for(int x=tx-95;x<=tx+95;x++)if(x>0&&x<width&&Math.abs(x-tx)/95.0+Math.abs(y-targetY)/55.0<1)p[y*width+x]=0xffffffff;
        return p;
    }
    public static void main(String[] args)throws Exception{
        int w=540,h=1188;
        Vision.Result a=Vision.analyze(board(w,h,160,410),w,h);
        check(a.valid,"right platform: "+a);check(Math.abs(a.targetX-410)<8,"right target center");
        check(Math.abs(a.duration(1.38f)-797)<30,"normalized duration "+a.duration(1.38f));
        Vision.Result b=Vision.analyze(board(w,h,410,150),w,h);
        check(b.valid,"left platform: "+b);check(Math.abs(b.targetX-150)<8,"left target center");
        check(a.stableWith(a,w),"stable pair");check(!a.stableWith(b,w),"motion rejected");
        Vision.Result raised=Vision.analyze(board(w,h,160,350,35),w,h);
        check(raised.valid,"raised short platform: "+raised);
        check(Math.abs(raised.targetY-520)<5,"use observed raised top center, not inferred diagonal: "+raised);
        check(Math.abs(raised.distance1080-(float)(Math.hypot(190,145)*2))<8,"height contributes to actual distance");
        Vision.Result verticalMotion=new Vision.Result("moving");verticalMotion.valid=true;
        verticalMotion.pieceX=a.pieceX;verticalMotion.pieceY=a.pieceY;verticalMotion.targetX=a.targetX;verticalMotion.targetY=a.targetY+20;
        check(!a.stableWith(verticalMotion,w),"vertical target motion rejected");
        int[] tintedScore=board(w,h,160,410);
        for(int i=0;i<tintedScore.length;i++)if(tintedScore[i]==0xff484848)tintedScore[i]=0xff4f4a41;
        check(Vision.analyze(tintedScore,w,h).valid,"warm theme score ink accepted");
        int[] noScore=board(w,h,160,410);
        for(int i=0;i<noScore.length;i++)if(noScore[i]==0xff484848)noScore[i]=0xffcdd0d9;
        check(!Vision.analyze(noScore,w,h).valid,"pawn and platform without score rejected");
        int[] blank=new int[w*h];java.util.Arrays.fill(blank,0xffcdd0d9);
        check(!Vision.analyze(blank,w,h).valid,"blank screen rejected");
        check(!Vision.analyze(new int[400*300],400,300).valid,"landscape rejected");
        check(Vision.analyze(board(w,h,410,80),w,h).clipped,"normal pass reports clipping before recovery");
        Vision.Result clipLeft=Vision.analyze(board(w,h,410,80),w,h,true);
        check(clipLeft.valid&&Math.abs(clipLeft.targetX-80)<3,"left edge cropped with visible center: "+clipLeft);
        check(Math.abs(clipLeft.targetY-475)<4,"use complete right edge of left-cropped face: "+clipLeft);
        Vision.Result clipRight=Vision.analyze(board(w,h,160,460),w,h,true);
        check(clipRight.valid&&Math.abs(clipRight.targetX-460)<3,"right edge cropped with visible center: "+clipRight);
        check(Math.abs(clipRight.targetY-491)<4,"use complete left edge of right-cropped face: "+clipRight);
        check(!Vision.analyze(board(w,h,160,535,-70),w,h,true).valid,"center too close to frame edge still pauses");
        int[] noPiece=board(w,h,160,410);for(int y=(int)(h*.48);y<h*.59;y++)for(int x=130;x<190;x++)noPiece[y*w+x]=0xffcdd0d9;
        check(!Vision.analyze(noPiece,w,h).valid,"missing piece rejected");
        int[] noHead=board(w,h,160,410);for(int y=(int)(h*.56)-110;y<(int)(h*.56)-65;y++)for(int x=130;x<190;x++)noHead[y*w+x]=0xffcdd0d9;
        check(!Vision.analyze(noHead,w,h).valid,"purple column without pawn head rejected");
        if(args.length>0){File dir=new File(args[0]);File[] files=dir.listFiles((d,n)->n.endsWith(".png"));if(files!=null)for(File f:files){
            BufferedImage full=ImageIO.read(f);int hh=Math.round(full.getHeight()*540f/full.getWidth());
            BufferedImage im=new BufferedImage(540,hh,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics=im.createGraphics();graphics.drawImage(full,0,0,540,hh,null);graphics.dispose();
            boolean recovery=f.getName().startsWith("game-clipped-");
            int[] frame=im.getRGB(0,0,540,hh,null,0,540);
            if(recovery)check(Vision.analyze(frame,540,hh).clipped,"real clipped target first uses normal pass: "+f.getName());
            Vision.Result result=Vision.analyze(frame,540,hh,recovery);
            boolean expected=!f.getName().startsWith("reject-");
            check(result.valid==expected,"real screenshot "+f.getName()+": "+result);
            if(f.getName().equals("game-striped-roof.png")){
                check(Math.abs(result.targetX-419)<3&&Math.abs(result.targetY-524)<3,"roof stripe cannot truncate the top face: "+result);
                check(result.targetHalfWidth>70,"platform outline size is returned for landing feedback");
            }
            if(f.getName().equals("game-clipped-right.png")){
                check(Math.abs(result.targetX-437)<3,"visible apex remains horizontal center when right edge is cropped: "+result);
                check(Math.abs(result.targetY-515)<4,"complete left edge defines center height: "+result);
            }
            if(f.getName().equals("game-clipped-right-wide.png")){
                check(Math.abs(result.targetX-458)<3,"cropped wide platform must not use a lower row as a new apex: "+result);
                check(Math.abs(result.targetY-503)<4,"wide platform center from complete left outline: "+result);
            }
            if(f.getName().equals("game-ring-cylinder.png")){
                check(Math.abs(result.targetX-172)<3,"ring cylinder horizontal center");
                check(Math.abs(result.targetY-541)<3,"outer ring defines the top face, not the inner color gap: "+result);
            }
            if(f.getName().equals("game-short-raised.png")){
                check(Math.abs(result.targetY-569)<2,"real short raised platform center marker: "+result);
                check(result.duration(1.38f)>345,"short raised jump no longer uses 338ms underestimate");
            }
            if(f.getName().equals("game-narrow-cap.png")){
                check(Math.abs(result.targetX-180)<2,"small round pillar is a platform");
                check(Math.abs(result.targetY-543)<2,"small pillar center marker: "+result);
            }
            if(f.getName().equals("game-very-close.png")){
                check(Math.abs(result.targetX-250)<2,"near target apex must not be clipped by pawn exclusion");
                check(Math.abs(result.targetY-587)<2,"use unoccluded side of close target: "+result);
                check(result.duration(1.38f)>195&&result.duration(1.38f)<220,"close jump duration without inflated distance");
            }
            if(f.getName().equals("game-gray-roof.png")){
                check(Math.abs(result.targetX-190)<5,"gray roof, not green side logo: "+result);
                check(Math.abs(result.targetY-551)<5,"gray roof top center: "+result);
                check(result.duration(1.38f)<650,"gray roof must not receive erroneous 842ms jump");
            }
            if(f.getName().equals("game-adjacent-white.png")){
                check(Math.abs(result.targetX-226)<2,"adjacent white platform apex");
                check(Math.abs(result.targetY-573)<2,"top face separated from connected neighboring white platform: "+result);
                check(result.duration(1.38f)>340&&result.duration(1.38f)<365,"connected sides must not shorten jump to 310ms");
            }
            if(f.getName().equals("game-yellow-zero.png"))check(Math.abs(result.targetX-394)<4&&Math.abs(result.targetY-539)<4,"warm-theme shadow excluded from center: "+result);
            if(f.getName().equals("game-pink.png"))check(Math.abs(result.targetX-173)<4&&Math.abs(result.targetY-541)<4,"carton flap excluded from top center: "+result);
            if(f.getName().equals("game-136.png"))check(Math.abs(result.targetX-421)<4&&Math.abs(result.targetY-521)<4,"bottle cap center, not side rim: "+result);
            System.out.println(f.getName()+": "+result+" duration="+result.duration(1.38f));
            for(int resolution:new int[]{720,1080}){
                int scaledHeight=Math.round(full.getHeight()*resolution/(float)full.getWidth());
                BufferedImage precise=new BufferedImage(resolution,scaledHeight,BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D painter=precise.createGraphics();painter.drawImage(full,0,0,resolution,scaledHeight,null);painter.dispose();
                Vision.Result high=Vision.analyze(precise.getRGB(0,0,resolution,scaledHeight,null,0,resolution),resolution,scaledHeight,recovery);
                check(high.valid==expected,"resolution "+resolution+" "+f.getName()+": "+high);
                if(high.valid){
                    check(high.imageWidth==resolution,"track actual recognition width");
                    check(Math.abs(high.duration(1.38f)-result.duration(1.38f))<12,"duration consistency at "+resolution+" "+f.getName());
                }
            }
        }}
        System.out.println("PASS: "+passed+" assertions");
    }
}
