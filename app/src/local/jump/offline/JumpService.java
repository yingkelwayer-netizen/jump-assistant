package local.jump.offline;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JumpService extends AccessibilityService {
    static volatile JumpService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LandingTracker landing=new LandingTracker();
    private WindowManager windows;
    private LinearLayout panel;
    private TextView label;
    private Button runButton;
    private boolean running=false,continuous=false,capturing=false,panelSuppressed=false;
    private volatile boolean recoverClipped=false;
    private int retries=0;
    private String foregroundPackage="";
    private int generation=0,jumps=0;
    private Vision.Result previous;
    private String status="就绪 · 打开跳一跳后点单步";
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){ pause("屏幕关闭，已停止"); }
    };

    @Override protected void onServiceConnected(){
        instance=this;
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        android.content.SharedPreferences learned=getSharedPreferences("jump_feedback_v2",MODE_PRIVATE);
        landing.restore(learned.getFloat("base",0),learned.getFloat("short",0),learned.getFloat("medium",0),learned.getFloat("long",0));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        // The user opens the control panel from the app; never cover the app's own pages.
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String text,Runnable action){
        return Ui.button(this,text,"连续".equals(text),action);
    }
    void preparePanel(){panelSuppressed=false;status="就绪 · 进入对局后点单步";if("com.tencent.mm".equals(foregroundPackage))showPanel();}
    void showPanel(){
        if(!getSharedPreferences("jump",MODE_PRIVATE).getBoolean("disclosure",false)||!new AccessPass(this).active())return;
        panelSuppressed=false;
        if(panel!=null)return;
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(11),dp(12),dp(10));
        GradientDrawable bg=Ui.shape(this,Color.WHITE,20);bg.setStroke(dp(1),Ui.LINE);panel.setBackground(bg);panel.setElevation(dp(8));
        label=Ui.text(this,status,12,Ui.MUTED,false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(label);Ui.gap(panel,10);
        LinearLayout row=new LinearLayout(this);
        addControl(row,button("单步",()->start(false)));
        runButton=button("连续",()->{if(running)pause("已暂停");else start(true);});
        addControl(row,runButton);
        addControl(row,button("暂停",()->pause("已暂停")));
        addControl(row,button("收起",()->{pause("已收起，返回首页重新打开");panelSuppressed=true;hidePanel();}));
        panel.addView(row);
        int panelWidth=Math.min(dp(350),getResources().getDisplayMetrics().widthPixels-dp(24));
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(panelWidth,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=dp(12);lp.y=0;
        try { windows.addView(panel,lp); } catch(RuntimeException e){panel=null;status="悬浮条无法显示，请重新启用服务";}
    }
    private void addControl(LinearLayout row,Button control){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.leftMargin=dp(2);lp.rightMargin=dp(2);control.setTextSize(12);control.setPadding(dp(3),0,dp(3),0);row.addView(control,lp);}
    private void hidePanel(){if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;label=null;runButton=null;}}
    private void update(String message){status=message;if(label!=null)label.setText(message);if(runButton!=null)runButton.setText(running&&continuous?"运行中":"连续");}
    String status(){return status;}
    private void saveLanding(){float[] v=landing.snapshot();getSharedPreferences("jump_feedback_v2",MODE_PRIVATE).edit().putFloat("base",v[0]).putFloat("short",v[1]).putFloat("medium",v[2]).putFloat("long",v[3]).apply();}
    void clearLearning(){landing.reset(getSharedPreferences("jump",MODE_PRIVATE).getFloat("coefficient",1.38f));saveLanding();}
    void enterAssistant(){foregroundPackage=getPackageName();if(running)pause("已返回助手，操作已停止");hidePanel();}
    // Old scheduled captures stop at the generation guard. Keep completion
    // callbacks so a pause cannot discard the reset of an in-flight capture.
    void pause(String why){generation++;running=false;previous=null;recoverClipped=false;retries=0;landing.discard();saveLanding();update(why);QaTrace.event(this,"STOP",why);}
    private boolean isWechat(){
        if(((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked() || !((PowerManager)getSystemService(POWER_SERVICE)).isInteractive())return false;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        // Some WeChat game canvases expose no accessibility root. In that case use
        // the system window-change package, then independently validate game pixels.
        if(root==null)return "com.tencent.mm".equals(foregroundPackage);
        CharSequence pkg=root.getPackageName();String name=pkg==null?"":pkg.toString();root.recycle();
        if("com.tencent.mm".equals(name))return true;
        return getPackageName().equals(name)&&"com.tencent.mm".equals(foregroundPackage);
    }
    private void start(boolean auto){
        if(running||capturing)return;
        if(UpdateGate.blocked(this)){pause("请先在助手中完成版本检查或更新");return;}if(!new AccessPass(this).active()){pause("周卡未激活或已到期，请返回首页");return;}
        if(!getSharedPreferences("jump",MODE_PRIVATE).getBoolean("disclosure",false)){pause("请先在首页阅读并确认权限用途");return;}
        if(!isWechat()){update("请先进入微信跳一跳的对局");return;}
        landing.begin(getSharedPreferences("jump",MODE_PRIVATE).getFloat("coefficient",1.38f));
        saveLanding();
        generation++;running=true;continuous=auto;jumps=0;previous=null;recoverClipped=false;retries=0;update("确认静止画面…");schedule(generation,120);
    }
    private void schedule(int token,long delay){handler.postDelayed(()->capture(token),delay);}
    private void retry(int token,String why){
        previous=null;
        if(++retries>3){pause(why+" · 重试后仍未恢复，已暂停");return;}
        update(why+" · 3秒后重试（"+retries+"/3）");schedule(token,3000);
        QaTrace.event(this,"RETRY",why+" attempt="+retries);
    }
    private void capture(final int token){
        if(!running||token!=generation||capturing)return;
        if(UpdateGate.blocked(this)){pause("请先在助手中完成版本检查或更新");return;}if(!new AccessPass(this).active()){pause("周卡已到期，请返回首页续期");return;}
        if(!isWechat()){pause("离开微信或锁屏，已停止");return;}
        capturing=true;
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,worker,new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    Vision.Result found;Bitmap hw=null,soft=null,small=null;
                    HardwareBuffer buffer=result.getHardwareBuffer();
                    int originalWidth=0,originalHeight=0,analysisWidth=0,analysisHeight=0;int[] analysisPixels=null;
                    try{
                        hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                        if(hw==null)throw new IllegalStateException("无有效图像");
                        originalWidth=hw.getWidth();originalHeight=hw.getHeight();
                        soft=hw.copy(Bitmap.Config.ARGB_8888,false);
                        long heap=Runtime.getRuntime().maxMemory();
                        int maximum=heap>=192L*1024*1024?1080:720;
                        int width=Math.min(maximum,soft.getWidth()),height=Math.round(soft.getHeight()*(width/(float)soft.getWidth()));
                        small=Bitmap.createScaledBitmap(soft,width,height,false);
                        int[] pixels=new int[width*height];small.getPixels(pixels,0,width,0,0,width,height);
                        found=Vision.analyze(pixels,width,height,recoverClipped);
                        QaTrace.frame(JumpService.this,small,found);
                        if(landing.observe(pixels,width,height,found))saveLanding();
                        analysisPixels=pixels;analysisWidth=width;analysisHeight=height;
                    }catch(RuntimeException e){found=new Vision.Result("截图分析失败，请重新开启服务");}
                    finally{if(small!=null&&small!=soft)small.recycle();if(soft!=null)soft.recycle();if(hw!=null)hw.recycle();buffer.close();}
                    final Vision.Result analysis=found;final int ow=originalWidth,oh=originalHeight;
                    final int[] frame=analysisPixels;final int fw=analysisWidth,fh=analysisHeight;
                    handler.post(()->{capturing=false;if(running&&token==generation)analyzed(token,analysis,ow,oh,frame,fw,fh);});
                }
                @Override public void onFailure(int error){handler.post(()->{capturing=false;if(token==generation)pause("截图不可用（"+error+"），已停止");});}
            });
        }catch(RuntimeException e){capturing=false;pause("系统拒绝截图，请检查无障碍权限");}
    }
    private void analyzed(int token,Vision.Result result,int width,int height,int[] pixels,int frameWidth,int frameHeight){
        if(UpdateGate.blocked(this)){pause("请先在助手中完成版本检查或更新");return;}if(!new AccessPass(this).active()){pause("周卡已到期，请返回首页续期");return;}
        if(!result.valid){
            if(result.clipped){recoverClipped=true;retry(token,"平台超出画面");}
            else pause(result.reason);
            return;
        }
        if(previous==null){previous=result;update("已定位，等待画面稳定…");schedule(token,850);return;}
        if(!result.stableWith(previous,result.imageWidth)){retry(token,"画面不稳定");return;}
        float coefficient=getSharedPreferences("jump",MODE_PRIVATE).getFloat("coefficient",1.38f);
        long duration=landing.duration(result,coefficient);
        QaTrace.event(this,"JUMP","n="+jumps+" frame="+QaTrace.lastFrame+" ms="+duration+" "+result+" bias="+landing.correction(0)+","+landing.correction(1)+","+landing.correction(2));
        if(duration<100||duration>1450){pause("长按时长超出范围，请校准");return;}
        if(!isWechat()){pause("当前不是微信，已停止");return;}
        previous=null;
        recoverClipped=false;retries=0;
        landing.record(pixels,frameWidth,frameHeight,result,coefficient);
        Path p=new Path();p.moveTo(width*.52f,height*.77f);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,duration)).build();
        update("长按 "+duration+" ms · 已跳 "+jumps+" 次");
        try{
            boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){
                    if(token!=generation||!running)return;
                    jumps++;
                    if(continuous){update("已跳 "+jumps+" 次 · 等待落地");schedule(token,1500);}
                    else pause("单步完成 · "+duration+" ms");
                }
                @Override public void onCancelled(GestureDescription g){if(token==generation)pause("手势被取消，已停止");}
            },handler);
            if(!accepted)pause("系统未接受手势，请重新启用服务");
        }catch(RuntimeException e){pause("手势执行失败，已停止");}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        CharSequence pkg=event.getPackageName();if(pkg==null)return;
        String name=pkg.toString();
        boolean ownPage=getPackageName().equals(name)&&MainActivity.class.getName().contentEquals(event.getClassName()==null?"":event.getClassName());
        if(!getPackageName().equals(name)||ownPage)foregroundPackage=name;
        if(ownPage){if(running)pause("已返回助手，操作已停止");hidePanel();}
        else if(!"com.tencent.mm".equals(name)&&!getPackageName().equals(name)){
            if(running)pause("离开微信，已停止");hidePanel();
        }else if("com.tencent.mm".equals(name)&&!running&&!panelSuppressed)showPanel();
    }
    @Override public void onInterrupt(){pause("无障碍被中断，已停止");}
    @Override public void onDestroy(){pause("服务已关闭");hidePanel();try{unregisterReceiver(screenOff);}catch(RuntimeException ignored){}worker.shutdownNow();instance=null;super.onDestroy();}
}
