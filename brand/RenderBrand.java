import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class RenderBrand {
    public static void main(String[] args)throws Exception{
        BufferedImage im=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(512/108.0,512/108.0);
        g.setColor(new Color(0x2563eb));g.fill(new RoundRectangle2D.Double(0,0,108,108,52,52));
        Path2D p=new Path2D.Double();p.moveTo(18,72);p.lineTo(37,62);p.lineTo(56,72);p.lineTo(37,83);p.closePath();g.setColor(new Color(0xb9d1ff));g.fill(p);
        p.reset();p.moveTo(61,49);p.lineTo(77,40);p.lineTo(93,49);p.lineTo(77,58);p.closePath();g.setColor(Color.WHITE);g.fill(p);
        p.reset();p.moveTo(39,57);p.curveTo(42,26,64,22,76,31);g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(p);g.fill(new Ellipse2D.Double(30,47,14,14));g.dispose();
        ImageIO.write(im,"PNG",new File(args[0]));
    }
}
