package local.jump.offline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BLUE=0xff2563eb,INK=0xff172b4d,MUTED=0xff61738d,BG=0xfff4f7fc,LINE=0xffe3eaf4,PALE=0xffeaf2ff,GREEN=0xff12836b;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,3),1);t.setIncludeFontPadding(false);return t;}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout card(Context c){LinearLayout v=column(c);v.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20));GradientDrawable d=shape(c,Color.WHITE,22);d.setStroke(dp(c,1),LINE);v.setBackground(d);return v;}
    static void gap(LinearLayout parent,int n){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),n)));}
    static void addCard(LinearLayout parent,View card){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(parent.getContext(),16);parent.addView(card,lp);}
    static Button button(Context c,String value,boolean primary,Runnable action){Button b=new Button(c);b.setText(value);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?Color.WHITE:BLUE);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,14),0,dp(c,14),0);GradientDrawable bg=shape(c,primary?BLUE:PALE,14);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226b91ff),bg,null));b.setOnClickListener(v->action.run());return b;}
    static void action(LinearLayout parent,Button button){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(parent.getContext(),52));lp.topMargin=dp(parent.getContext(),12);parent.addView(button,lp);}
    static TextView badge(Context c,String label){TextView t=text(c,label,11,BLUE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));t.setBackground(shape(c,PALE,20));return t;}
    static final class Glyph extends View {
        private final Paint paint=new Paint(3);private final String kind;private final int color;
        Glyph(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
            if("home".equals(kind)){p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);}
            else if("tune".equals(kind)){canvas.drawLine(5,3,5,21,paint);canvas.drawLine(12,3,12,21,paint);canvas.drawLine(19,3,19,21,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(5,8,2.7f,paint);canvas.drawCircle(12,16,2.7f,paint);canvas.drawCircle(19,10,2.7f,paint);}
            else if("help".equals(kind)){canvas.drawCircle(12,12,9,paint);p.moveTo(9,9);p.cubicTo(9,5,16,5,15,10);p.cubicTo(14,12,12,11,12,14);canvas.drawPoint(12,17,paint);}
            else if("about".equals(kind)){canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,11,12,17,paint);canvas.drawPoint(12,7,paint);}
            else if("shield".equals(kind)){p.moveTo(12,2);p.lineTo(21,6);p.lineTo(20,14);p.quadTo(18,20,12,22);p.quadTo(6,20,4,14);p.lineTo(3,6);p.close();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(16,9);}
            else if("arrow".equals(kind)){p.moveTo(9,5);p.lineTo(16,12);p.lineTo(9,19);}
            else if("check".equals(kind)){p.moveTo(5,12);p.lineTo(10,17);p.lineTo(20,6);}
            else {canvas.drawCircle(12,12,8,paint);}
            canvas.drawPath(p,paint);canvas.restore();
        }
    }
    static final class Logo extends View {
        private final Paint paint=new Paint(3);
        Logo(Context c){super(c);setContentDescription(Brand.NAME+"标识");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/108f,getHeight()/108f);paint.setColor(BLUE);c.drawRoundRect(0,0,108,108,26,26,paint);
            paint.setColor(0xffb9d1ff);Path base=new Path();base.moveTo(18,72);base.lineTo(37,62);base.lineTo(56,72);base.lineTo(37,83);base.close();c.drawPath(base,paint);
            paint.setColor(Color.WHITE);Path next=new Path();next.moveTo(61,49);next.lineTo(77,40);next.lineTo(93,49);next.lineTo(77,58);next.close();c.drawPath(next,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);Path arc=new Path();arc.moveTo(39,57);arc.cubicTo(42,26,64,22,76,31);c.drawPath(arc,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(37,54,7,paint);c.restore();
        }
    }
}
