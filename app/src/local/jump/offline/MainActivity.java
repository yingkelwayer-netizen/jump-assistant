package local.jump.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity {
    private LinearLayout root,header,page,nav;
    private ScrollView scroll;
    private int route=0;
    private TextView stateTitle,stateDetail,coefficientValue;
    private TextView accessibilityStatus;
    private Button homePrimary;
    private SeekBar coefficientBar;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,1000);}};
    private AccessPass pass;
    private UpdateManager updates;
    private boolean activating;
    private boolean rewardLoading,rewardClaiming;
    private Button rewardButton;
    private TextView rewardState,rewardNotice,rewardExpiry;
    private final java.util.concurrent.ExecutorService network=java.util.concurrent.Executors.newSingleThreadExecutor();
    private long lastLicenseCheck=-60000;
    private int dp(float value){return Ui.dp(this,value);}
    private TextView text(String s,int size,int color,boolean bold){return Ui.text(this,s,size,color,bold);}
    private void gap(LinearLayout parent,int value){Ui.gap(parent,value);}
    private void title(String headline,String description){page.addView(text(headline,28,Ui.INK,true));gap(page,8);page.addView(text(description,14,Ui.MUTED,false));gap(page,24);}
    private void body(LinearLayout card,String content){TextView t=text(content,14,Ui.MUTED,false);gap(card,10);card.addView(t);}
    private LinearLayout card(String heading,String subtitle){LinearLayout c=Ui.card(this);c.addView(text(heading,17,Ui.INK,true));if(subtitle!=null)body(c,subtitle);return c;}
    private void action(LinearLayout parent,String label,boolean primary,Runnable action){Ui.action(parent,Ui.button(this,label,primary,action));}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);pass=new AccessPass(this);updates=new UpdateManager(this);
        getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()),ime=insets.getInsets(WindowInsets.Type.ime());
            boolean keyboard=insets.isVisible(WindowInsets.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            if(nav!=null)nav.setVisibility(keyboard?View.GONE:View.VISIBLE);
            if(keyboard&&scroll!=null)scroll.post(()->{View focus=getCurrentFocus();if(focus instanceof EditText){android.graphics.Rect rect=new android.graphics.Rect();focus.getDrawingRect(rect);rect.bottom+=dp(70);focus.requestRectangleOnScreen(rect,false);}});
            return insets;
        });
        setContentView(root);render(saved==null?0:saved.getInt("route",0));
    }
    private void render(int next){
        route=next;root.removeAllViews();stateTitle=null;stateDetail=null;homePrimary=null;coefficientValue=null;coefficientBar=null;accessibilityStatus=null;rewardButton=null;rewardState=null;rewardNotice=null;rewardExpiry=null;
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(12),dp(20),dp(12));header.setBackgroundColor(Color.WHITE);
        if(route>=4){TextView back=text("‹",31,Ui.BLUE,false);back.setGravity(Gravity.CENTER);back.setContentDescription("返回");back.setOnClickListener(v->render(route==4||route==7||route==8?0:3));header.addView(back,new LinearLayout.LayoutParams(dp(34),dp(40)));}
        else {Ui.Logo logo=new Ui.Logo(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(36),dp(36));lp.rightMargin=dp(10);header.addView(logo,lp);}
        TextView name=text(Brand.NAME,17,Ui.INK,true);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));header.addView(Ui.badge(this,"本机运行"));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        page=Ui.column(this);page.setPadding(dp(22),dp(26),dp(22),dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(route==0)home();else if(route==1)tuning();else if(route==2)help();else if(route==3)about();else if(route==4)membership();else if(route==5)privacy();else if(route==7)accessibilityGuide();else if(route==8)shareReward();else terms();
        nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(Color.WHITE);
        String[] names={"首页","校准","帮助","关于"},icons={"home","tune","help","about"};
        for(int i=0;i<4;i++){
            final int index=i;boolean selected=route==i;LinearLayout tab=Ui.column(this);tab.setGravity(Gravity.CENTER);tab.setPadding(0,dp(5),0,dp(5));tab.setBackground(Ui.shape(this,selected?Ui.PALE:Color.WHITE,14));
            int color=selected?Ui.BLUE:Ui.MUTED;tab.addView(new Ui.Glyph(this,icons[i],color),new LinearLayout.LayoutParams(dp(23),dp(23)));gap(tab,4);TextView tabLabel=text(names[i],11,color,selected);tabLabel.setGravity(Gravity.CENTER);tab.addView(tabLabel);tab.setContentDescription(names[i]+(selected?"，已选中":""));tab.setFocusable(true);tab.setOnClickListener(v->render(index));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1);lp.leftMargin=dp(3);lp.rightMargin=dp(3);nav.addView(tab,lp);
        }
        root.addView(nav);refreshStatus();
    }
    private void home(){
        page.addView(text("每次起跳，都更从容。",13,Ui.BLUE,true));gap(page,9);page.addView(text("准备好，开始下一跳。",27,Ui.INK,true));gap(page,24);
        LinearLayout status=Ui.card(this);status.setBackground(Ui.shape(this,Ui.BLUE,24));
        TextView small=text("辅助状态",12,0xffcdddff,false);status.addView(small);gap(status,12);
        stateTitle=text("",24,Color.WHITE,true);status.addView(stateTitle);gap(status,10);stateDetail=text("",14,0xffe7eeff,false);status.addView(stateDetail);gap(status,18);
        LinearLayout pills=new LinearLayout(this);for(String tag:new String[]{"本机识别","无需 Root","随时暂停"}){TextView t=text(tag,11,Color.WHITE,false);t.setPadding(dp(8),dp(6),dp(8),dp(6));t.setBackground(Ui.shape(this,0x22ffffff,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(7);pills.addView(t,lp);}status.addView(pills);Ui.addCard(page,status);
        homePrimary=Ui.button(this,"",true,this::primaryAction);page.addView(homePrimary,new LinearLayout.LayoutParams(-1,dp(54)));gap(page,12);
        Button secondary=Ui.button(this,"查看上手指南",false,()->render(2));page.addView(secondary,new LinearLayout.LayoutParams(-1,dp(49)));gap(page,24);
        LinearLayout week=card("周卡权益",null);LinearLayout price=new LinearLayout(this);price.setGravity(Gravity.CENTER_VERTICAL);price.addView(text(Brand.WEEK_PRICE_LABEL,30,Ui.INK,true));TextView unit=text(" / 周",14,Ui.MUTED,false);price.addView(unit,new LinearLayout.LayoutParams(0,-2,1));price.addView(Ui.badge(this,pass.active()?"使用中":"兑换码激活"));gap(week,14);week.addView(price);
        body(week,pass.active()?"有效期至 "+pass.expiry():"每次激活 7 天，支持单步与连续跳跃。无自动续费。请通过反馈邮箱联系购买。");
        action(week,"限时免费领 7 天",true,()->render(8));action(week,pass.active()?"查看我的周卡":"管理周卡与兑换码",false,()->render(4));Ui.addCard(page,week);
        LinearLayout share=card("分享应用","把下载链接或二维码图片分享给朋友，按页面说明安装使用。");action(share,"分享给朋友",false,()->AppShare.menu(this));Ui.addCard(page,share);
        LinearLayout notice=card("按你的节奏使用", "使用默认紫色棋子并保持竖屏。先单步试跳，落点合适后再开始连续模式。特殊平台或游戏更新可能影响识别效果。");Ui.addCard(page,notice);
    }
    private void refreshStatus(){
        if(accessibilityStatus!=null){boolean enabled=JumpService.instance!=null;accessibilityStatus.setText(enabled?"辅助服务已开启，可返回首页开始":"辅助服务尚未开启，请按下方步骤设置");accessibilityStatus.setTextColor(enabled?Ui.GREEN:Ui.BLUE);}
        if(stateTitle==null)return;JumpService service=JumpService.instance;
        if(UpdateGate.blocked(this)){stateTitle.setText(UpdateGate.checking?"正在检查版本":"需要更新应用");stateDetail.setText("发现新版后，需要完成更新才能继续使用。");homePrimary.setText("检查并更新");return;}
        if(!pass.active()){stateTitle.setText(pass.exists()?"周卡未生效":"尚未激活周卡");stateDetail.setText("激活后，即可使用本机识别与跳跃控制。");homePrimary.setText("激活周卡");}
        else if(service==null){stateTitle.setText("还差一步，即可开始");stateDetail.setText("周卡已生效。开启辅助服务后，进入游戏即可操作。");homePrimary.setText("开启辅助服务");}
        else{stateTitle.setText("已准备就绪");stateDetail.setText("周卡有效 · "+service.status());homePrimary.setText("打开微信，准备开始");}
    }
    private void primaryAction(){if(UpdateGate.blocked(this)){updates.check(true);return;}if(!pass.active()){render(4);return;}if(JumpService.instance==null){requestAccessibility();return;}prepareAndOpen();}
    private void prepareAndOpen(){
        if(!pass.active()){render(4);return;}
        if(!getSharedPreferences("jump",MODE_PRIVATE).getBoolean("disclosure",false)){requestAccessibility();return;}
        if(JumpService.instance==null){requestAccessibility();return;}
        JumpService.instance.preparePanel();Intent launch=getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if(launch==null){toast("未找到微信，请先安装或从桌面打开");return;}startActivity(launch);
    }
    private void requestAccessibility(){
        render(7);
    }
    private void accessibilityDisclosure(Runnable next){
        new AlertDialog.Builder(this).setTitle("开启前，请了解权限用途")
            .setMessage("跳一跳助手使用系统无障碍服务完成屏幕识别和长按手势。\n\n仅在你主动点击单步或连续时，读取微信当前屏幕并模拟长按。屏幕可能包含其他可见内容，请勿在敏感页面启动；图片只在本机内存处理，不保存、不上传。应用读取前台应用身份，不提取聊天文字或联系人。\n\n你可以随时暂停、收起，或在系统设置中关闭服务。请按自己的意愿开启。")
            .setNegativeButton("暂不开启",null).setNeutralButton("隐私说明",(d,w)->render(5))
            .setPositiveButton("了解并继续",(d,w)->{getSharedPreferences("jump",MODE_PRIVATE).edit().putBoolean("disclosure",true).apply();next.run();}).show();
    }
    private void accessibilitySettings(boolean appInfo){
        Runnable open=()->{Intent intent=appInfo?new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())):new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){toast("请在手机系统设置中搜索“应用信息”或“无障碍”，按本页说明手动打开");}};
        if(!getSharedPreferences("jump",MODE_PRIVATE).getBoolean("disclosure",false))accessibilityDisclosure(open);else open.run();
    }
    private void accessibilityGuide(){
        title("开启辅助服务", "所有权限由你在系统页面确认，返回后会检查实际开启状态。");
        LinearLayout status=card("当前状态",null);gap(status,12);accessibilityStatus=text("",15,Ui.BLUE,true);status.addView(accessibilityStatus);body(status,android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE);Ui.addCard(page,status);
        LinearLayout normal=card("01 先打开无障碍设置","在系统无障碍中找到“已下载的应用”“已安装的服务”或类似入口，选择“跳一跳助手辅助”，阅读系统提示并按你的意愿开启。设置名称和位置会因品牌、系统版本不同而变化。");action(normal,"打开无障碍设置",true,()->accessibilitySettings(false));Ui.addCard(page,normal);
        LinearLayout restricted=card("02 如果开关是灰色或提示受限","Android 13 及以上的部分手机，会先限制通过 APK 安装的应用。请打开本应用的“应用信息”，在右上角“更多”菜单中查找“允许受限设置”或“允许受限制的设置”，确认信任本应用后按系统提示操作。完成后，再回到上一步开启无障碍。");action(restricted,"打开本应用设置",false,()->accessibilitySettings(true));Ui.addCard(page,restricted);
        LinearLayout missing=card("没有“允许受限设置”选项？","可从系统“设置 → 应用 → 应用管理／全部应用 → 跳一跳助手”手动进入，查看右上角菜单。部分系统没有该菜单或使用其他名称；如果无障碍已能开启，无需额外操作。若仍受限，可按系统提示处理，或向手机厂商及开发者咨询，不必反复卸载、清除数据。");body(missing,"“允许来自此来源”用于安装 APK 和更新；“允许受限设置”用于开放受限制的能力，请按当前系统提示选择。");Ui.addCard(page,missing);
        LinearLayout finish=card("03 返回助手确认状态","开启后返回本页，顶部会显示实际状态。周卡有效且辅助服务开启后，回首页点击“打开微信，准备开始”。如系统刚完成设置，可等待片刻再查看。");action(finish,"返回首页",true,()->render(0));Ui.addCard(page,finish);
        LinearLayout background=card("使用中服务又被关闭？","正常停止请使用“暂停”或“收起”。部分系统在“强行停止”、省电或后台清理后会关闭辅助服务。需要时可自行在应用信息中查看后台运行与电池设置；不要求关闭系统安全防护。");action(background,"查看本应用设置",false,()->accessibilitySettings(true));action(background,"邮件联系开发者",false,this::email);Ui.addCard(page,background);
    }
    private float coefficient(){return getSharedPreferences("jump",MODE_PRIVATE).getFloat("coefficient",1.38f);}
    private void setCoefficient(float value){value=Math.max(.9f,Math.min(1.8f,value));getSharedPreferences("jump",MODE_PRIVATE).edit().putFloat("coefficient",value).apply();if(JumpService.instance!=null)JumpService.instance.clearLearning();if(coefficientValue!=null)coefficientValue.setText(String.format(Locale.CHINA,"%.3f",value));if(coefficientBar!=null)coefficientBar.setProgress(Math.round(value*1000)-900);}
    private void tuning(){
        title("找到合适的力度", "根据实际落点微调，每次改一点，先用单步确认。");
        LinearLayout c=Ui.card(this);c.addView(text("长按系数",13,Ui.MUTED,false));gap(c,14);coefficientValue=text(String.format(Locale.CHINA,"%.3f",coefficient()),48,Ui.BLUE,true);c.addView(coefficientValue);gap(c,8);c.addView(text("默认 1.380 · 自动适配屏幕宽度",12,Ui.MUTED,false));gap(c,24);
        LinearLayout buttons=new LinearLayout(this);Button minus=Ui.button(this,"− 0.020",false,()->setCoefficient(coefficient()-.02f)),plus=Ui.button(this,"+ 0.020",false,()->setCoefficient(coefficient()+.02f));LinearLayout.LayoutParams one=new LinearLayout.LayoutParams(0,dp(49),1);one.rightMargin=dp(12);buttons.addView(minus,one);buttons.addView(plus,new LinearLayout.LayoutParams(0,dp(49),1));c.addView(buttons);gap(c,16);
        coefficientBar=new SeekBar(this);coefficientBar.setMax(900);coefficientBar.setProgress(Math.round(coefficient()*1000)-900);coefficientBar.setProgressTintList(ColorStateList.valueOf(Ui.BLUE));coefficientBar.setThumbTintList(ColorStateList.valueOf(Ui.BLUE));coefficientBar.setContentDescription("长按系数，范围零点九到一点八");c.addView(coefficientBar,new LinearLayout.LayoutParams(-1,dp(48)));
        coefficientBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){if(user)setCoefficient((n+900)/1000f);}});
        action(c,"恢复默认值",false,()->{setCoefficient(1.38f);toast("已恢复默认系数");});Ui.addCard(page,c);
        Ui.addCard(page,card("跳短了", "把系数略微调高，增加长按时间。建议每次增加 0.020。"));
        Ui.addCard(page,card("跳过了", "把系数略微调低，缩短长按时间。建议每次减少 0.020。"));
        Ui.addCard(page,card("怎么确认效果", "先在游戏中点“单步”，观察棋子落点；调整后再试一次。校准只影响后续跳跃，暂停时也会保留设置。"));
    }
    private void step(String number,String name,String detail){LinearLayout c=Ui.card(this);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView n=Ui.badge(this,number);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(30));lp.rightMargin=dp(12);row.addView(n,lp);row.addView(text(name,16,Ui.INK,true));c.addView(row);body(c,detail);Ui.addCard(page,c);}
    private void faq(String question,String answer){LinearLayout c=Ui.card(this);TextView q=text(question+"  ＋",15,Ui.INK,true);q.setMinHeight(dp(34));q.setGravity(Gravity.CENTER_VERTICAL);TextView a=text(answer,14,Ui.MUTED,false);a.setPadding(0,dp(12),0,0);a.setVisibility(View.GONE);q.setContentDescription(question+"，点击展开");q.setOnClickListener(v->{boolean open=a.getVisibility()!=View.VISIBLE;a.setVisibility(open?View.VISIBLE:View.GONE);q.setText(question+(open?"  −":"  ＋"));q.setContentDescription(question+(open?"，点击收起":"，点击展开"));});c.addView(q);c.addView(a);Ui.addCard(page,c);}
    private void help(){
        title("三步，轻松上手", "先了解操作，再开始你的下一跳。");
        step("01","准备周卡与辅助服务","输入数字兑换码激活周卡，阅读权限用途说明，然后按系统提示开启“跳一跳助手辅助”。");
        step("02","进入跳一跳对局","打开微信，进入跳一跳并开始游戏。保持竖屏，使用默认紫色棋子，等待画面稳定。");
        step("03","先单步，再连续","点悬浮条“单步”试跳，落点合适后点“连续”。随时点“暂停”停止后续动作，或点“收起”隐藏控制条。");
        LinearLayout permission=card("无障碍开启不了？","灰色开关、受限设置和不同手机的菜单说明，均可在完整引导中查看。");action(permission,"查看完整开启指南",false,this::requestAccessibility);Ui.addCard(page,permission);
        gap(page,6);page.addView(text("常见问题",20,Ui.INK,true));gap(page,16);
        faq("控制条不见了怎么办？","返回首页，点击“打开微信，准备开始”。控制条会在微信中显示。若服务被系统关闭，请重新开启；需要时可在手机系统中允许本应用保持后台运行。");
        faq("为什么有时会自动停止？","只有平台超出画面或画面不稳定会等待 3 秒重试，每跳最多 3 次。手动暂停、收起、离开微信、锁屏、周卡到期或其他识别错误不会自动恢复。恢复默认系数会同时清除已学习的落点修正。");
        faq("如何提高落点准确度？","在校准页根据落点调整长按系数。仅识别默认紫色棋子；特殊平台和游戏版本变化可能影响效果，不能保证每次命中。");
        faq("需要网络或电脑吗？","首次激活、重新核验和检查更新需要网络；已激活周卡可在 7 天有效期内离线使用。画面识别与跳跃控制全部在手机完成，无需电脑。微信游戏本身可能需要网络。");
        faq("周卡如何计时与续期？","每个兑换码绑定一台设备，提供 7×24 小时有效期。重复输入同一码不会增加期限。新版激活新码或领取分享福利时，已有有效期会顺延 7 天；已经到期则从领取或激活时重新计时。不自动扣款或续费。");
        faq("怎样领取免费周卡？","在首页或我的周卡进入“限时免费领 7 天”，从该页面发起链接或图片分享，成功打开系统分享面板后联网领取。每台设备每 7 天可领取一次，已有效的周卡顺延 7 天，到期后也可再次领取。发送对象及是否发送由你在接收应用中决定，活动是否开放以领取页状态为准。");
        faq("有更新时怎样处理？","启动时会检查应用版本。发现新版后，更新提示不能跳过，需要在应用内完成下载并由安卓系统确认安装，才能继续使用。可以退出应用，之后再更新；不能使用尚未完成更新的旧版本。尚未发现更新且网络不可用时，已激活周卡仍可在期限内离线使用。");
        faq("哪些手机可以使用？","需要 Android 11 或更高版本及系统无障碍服务。当前已在 OnePlus 8 Pro 的 Android 13 上验证，其他机型的后台限制和游戏画面可能不同。");
        LinearLayout support=card("还有疑问？", "购买、激活或使用问题，可通过邮箱联系我们。不要发送密码、验证码或无关的聊天截图。");action(support,"邮件联系",false,this::email);Ui.addCard(page,support);
    }
    private void about(){
        LinearLayout identity=Ui.card(this);identity.setGravity(Gravity.CENTER_HORIZONTAL);Ui.Logo logo=new Ui.Logo(this);identity.addView(logo,new LinearLayout.LayoutParams(dp(76),dp(76)));gap(identity,18);TextView identityName=text(Brand.NAME,24,Ui.INK,true);identityName.setGravity(Gravity.CENTER);identity.addView(identityName);gap(identity,8);TextView version=text("版本 "+Brand.VERSION,13,Ui.MUTED,false);version.setGravity(Gravity.CENTER);identity.addView(version);gap(identity,16);TextView tagline=text("更轻松的操作，更清楚的控制。",14,Ui.MUTED,false);tagline.setGravity(Gravity.CENTER);identity.addView(tagline);Ui.addCard(page,identity);
        LinearLayout share=card("分享应用","下载地址固定，朋友可按页面说明完成安装。");action(share,"分享下载链接",true,()->AppShare.link(this));action(share,"分享二维码图片",false,()->AppShare.image(this));Ui.addCard(page,share);
        LinearLayout company=card("开发者",Brand.COMPANY);Ui.addCard(page,company);
        LinearLayout update=card("应用更新","启动时检查版本。发现新版后，请完成下载和系统安装，再继续使用。");action(update,"检查更新",false,()->updates.check(true));Ui.addCard(page,update);
        LinearLayout contact=card("反馈与购买联系",Brand.EMAIL);action(contact,"发送邮件",true,this::email);action(contact,"复制邮箱地址",false,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("联系邮箱",Brand.EMAIL));toast("邮箱地址已复制");});Ui.addCard(page,contact);
        link("我的周卡","查看有效期与兑换码激活",()->render(4));link("隐私说明","屏幕、权限与本机数据的处理方式",()->render(5));link("使用与周卡说明","价格、有效期及使用范围",()->render(6));
        LinearLayout controls=card("辅助服务控制","你可以随时在系统设置中关闭本应用的辅助服务。");action(controls,"查看权限用途",false,this::requestAccessibility);action(controls,"关闭辅助服务",false,()->{if(JumpService.instance!=null)JumpService.instance.disableSelf();toast("辅助服务已关闭");});Ui.addCard(page,controls);
        TextView footer=text("独立开发的第三方工具，并非微信官方产品。\n© 2026 "+Brand.COMPANY,11,Ui.MUTED,false);footer.setGravity(Gravity.CENTER);page.addView(footer);
    }
    private void link(String title,String subtitle,Runnable action){LinearLayout c=card(title,subtitle);c.setFocusable(true);c.setContentDescription(title+"，点击查看");c.setOnClickListener(v->action.run());Ui.addCard(page,c);}
    private void membership(){
        title("我的周卡", "按周使用，清楚掌握每一段有效期。");
        LinearLayout offer=Ui.card(this);offer.addView(Ui.badge(this,pass.active()?"周卡使用中":pass.exists()?"周卡未生效":"周卡"));gap(offer,20);LinearLayout price=new LinearLayout(this);price.setGravity(Gravity.BOTTOM);price.addView(text(Brand.WEEK_PRICE_LABEL,44,Ui.BLUE,true));TextView unit=text(" / 7 天",16,Ui.MUTED,false);unit.setPadding(dp(3),0,0,dp(5));price.addView(unit);offer.addView(price);body(offer,"单步跳跃 · 连续控制 · 长按校准\n不自动续费，不在应用内扣款。");gap(offer,18);
        action(offer,"限时免费领 7 天",true,()->render(8));
        TextView expiry=text(pass.active()?"有效期至 "+pass.expiry():pass.exists()?"授权到期时间 "+pass.expiry():"尚未激活",14,pass.active()?Ui.GREEN:Ui.MUTED,true);offer.addView(expiry);Ui.addCard(page,offer);
        LinearLayout activation=card("兑换码激活", "输入 24 位数字兑换码，联网核验后绑定当前手机。有效期内可离线使用。");
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});input.setTextSize(18);input.setTextColor(Ui.INK);input.setHintTextColor(0xff8a99ad);input.setHint("输入数字兑换码");input.setContentDescription("数字兑换码");input.setPadding(dp(16),0,dp(16),0);input.setBackground(Ui.shape(this,Ui.BG,13));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(54));ip.topMargin=dp(18);activation.addView(input,ip);
        Button redeem=Ui.button(this,"立即激活",true,()->{});redeem.setOnClickListener(v->{
            if(activating)return;String code=input.getText().toString().trim();if(!code.matches("[0-9]{24}")){input.setError("请输入 24 位数字兑换码");input.requestFocus();return;}
            Runnable activate=()->{activating=true;redeem.setEnabled(false);redeem.setText("正在核验…");network.execute(()->{try{pass.activate(code);handler.post(()->{activating=false;if(isFinishing()||isDestroyed())return;redeem.setEnabled(true);redeem.setText("立即激活");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.setText("");expiry.setText("有效期至 "+pass.expiry());expiry.setTextColor(Ui.GREEN);new AlertDialog.Builder(this).setTitle("激活成功").setMessage("周卡已绑定本机\n有效期至 "+pass.expiry()+"\n\n有效期内可离线使用，同一码重复核验不会延长有效期。").setPositiveButton("开始使用",(d,w)->render(0)).show();});}catch(Exception e){handler.post(()->{activating=false;if(isFinishing()||isDestroyed())return;redeem.setEnabled(true);redeem.setText("立即激活");new AlertDialog.Builder(this).setTitle("未能激活").setMessage(ApiClient.message(e)).setPositiveButton("知道了",null).show();});}});};
            if(pass.active())new AlertDialog.Builder(this).setTitle("当前周卡仍有效").setMessage("激活新兑换码后，会在现有有效期上顺延 7 天。同一码重复核验不会延期。").setNegativeButton("取消",null).setPositiveButton("继续核验",(d,w)->activate.run()).show();else activate.run();
        });Ui.action(activation,redeem);Ui.addCard(page,activation);
        LinearLayout purchase=card("如何购买？","联系 "+Brand.EMAIL+" 咨询周卡购买与兑换码。付款通过你与开发者确认的渠道进行，本应用没有支付或自动扣款功能。");action(purchase,"邮件联系购买",false,this::email);Ui.addCard(page,purchase);
        page.addView(text("首次激活后 7×24 小时有效，每码限一台设备。卸载或清除数据后，可在原设备用同一码联网恢复剩余期限。系统重置可能改变设备标识，需要协助请联系开发者。",12,Ui.MUTED,false));gap(page,16);TextView terms=text("查看使用与周卡说明 ›",13,Ui.BLUE,true);terms.setMinHeight(dp(48));terms.setGravity(Gravity.CENTER_VERTICAL);terms.setOnClickListener(v->render(6));page.addView(terms);
    }
    private void section(String title,String content){Ui.addCard(page,card(title,content));}
    private void shareReward(){
        title("限时免费周卡","发起分享，领取 7 天有效期。已有周卡自动顺延，到期后也可再次领取。");
        LinearLayout offer=card("每 7 天可领取一次","从本页选择分享下载链接或二维码图片，成功打开系统分享面板后即可联网领取。发送对象及是否发送，由你在接收应用中决定。每台设备每 7 天可领取一次，奖励会绑定当前手机。");
        gap(offer,14);rewardExpiry=text(pass.exists()?"当前有效期至 "+pass.expiry():"尚未激活周卡",14,Ui.INK,true);offer.addView(rewardExpiry);gap(offer,12);rewardNotice=text("",14,Ui.GREEN,true);offer.addView(rewardNotice);gap(offer,8);rewardState=text("正在查询领取状态…",14,Ui.MUTED,false);offer.addView(rewardState);
        rewardButton=Ui.button(this,"分享并领取 7 天",true,this::shareRewardChoice);rewardButton.setEnabled(false);Ui.action(offer,rewardButton);action(offer,"刷新领取状态",false,this::loadReward);Ui.addCard(page,offer);
        Ui.addCard(page,card("领取规则","领取成功后会显示实际到期时间。有效期按服务器时间计算，重复点击、返回页面或重试不会重复增加同一份奖励。当前活动是否开放以本页查询结果为准。"));loadReward();
    }
    private void loadReward(){
        if(rewardLoading||rewardClaiming)return;rewardLoading=true;if(rewardButton!=null)rewardButton.setEnabled(false);
        network.execute(()->{try{if(pass.pendingShareClaim())pass.claimShare();final org.json.JSONObject status=pass.shareStatus();handler.post(()->{rewardLoading=false;if(isDestroyed()||route!=8||rewardState==null)return;boolean enabled=status.optBoolean("enabled"),eligible=status.optBoolean("eligible");rewardState.setText(status.optBoolean("blocked")?"此福利授权已停用，请联系开发者":!enabled?"本期免费领取活动已结束":eligible?"当前可领取，发起分享后即可获得 7 天周卡":"已领取，下次可领："+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new java.util.Date(status.optLong("nextEligibleAt"))));rewardButton.setEnabled(enabled&&eligible);rewardButton.setText(eligible?"分享并领取 7 天":"暂不可领取");rewardExpiry.setText(pass.exists()?"当前有效期至 "+pass.expiry():"尚未激活周卡");});}catch(Exception e){handler.post(()->{rewardLoading=false;if(!isDestroyed()&&route==8&&rewardState!=null){rewardState.setText(ApiClient.message(e)+"，可点击刷新重试");rewardButton.setEnabled(false);}});}});
    }
    private void shareRewardChoice(){
        if(rewardLoading||rewardClaiming)return;if(UpdateGate.blocked(this)){updates.check(true);return;}
        new AlertDialog.Builder(this).setTitle("发起分享并领取周卡").setItems(new String[]{"分享下载链接","分享二维码图片"},(d,which)->{
            boolean opened=which==0?AppShare.link(this):AppShare.image(this);if(!opened)return;rewardClaiming=true;rewardButton.setEnabled(false);rewardState.setText("已发起分享，正在领取周卡…");
            network.execute(()->{try{pass.claimShare();handler.post(()->{rewardClaiming=false;if(isDestroyed())return;if(route==8&&rewardNotice!=null)rewardNotice.setText("7 天周卡已到账，有效期至 "+pass.expiry());loadReward();});}catch(Exception e){handler.post(()->{rewardClaiming=false;if(!isDestroyed()&&route==8&&rewardState!=null){rewardState.setText(ApiClient.message(e)+"，点击刷新可重试领取");loadReward();}});}});
        }).setNegativeButton("取消",null).show();
    }
    private void privacy(){
        title("隐私说明","更新日期：2026 年 10 月 18 日");
        section("运营者与联系",Brand.NAME+"由"+Brand.COMPANY+"开发。隐私与数据问题请联系 "+Brand.EMAIL+"。");
        section("屏幕与辅助服务","只有在你主动点击单步或连续后，本应用才会通过无障碍接口获取微信当前屏幕，用于识别棋子和平台并模拟长按。屏幕可能包含其他可见内容；图像仅在本机内存处理，使用后释放，不保存截图、不录屏、不上传。前台应用身份用于判断是否应当停止，不提取聊天文字或联系人。");
        section("联网核验","激活时通过 HTTPS 向开发者的授权服务提交兑换码和经过散列处理的本应用设备标识，用于验证、绑定设备及计算有效期。服务器保存兑换码散列、设备标识散列、激活与到期时间；本机保存带签名的授权凭据。打开应用时会尝试核验已有授权，网络不可用时，已验证周卡在期限内仍可使用。不提交原始 Android 标识、账号、手机号、位置、截图或聊天内容。服务端停用记录在下次成功联网核验后生效。");
        section("应用更新","打开应用时会通过 HTTPS 检查更新，服务端会收到正常网络连接所需的 IP 地址和应用版本。发现新版后需要完成更新才能继续使用，也可以退出应用。安装包下载到本应用缓存，完成完整性及应用签名校验后，由安卓系统请求你确认安装。不含广告、统计或云端识别组件。");
        section("授权数据保存","有效期和校准系数保存在本机。服务端保存授权记录，用于售后、恢复有效期和防止一码重复绑定；如需查询或删除请联系开发者。删除会影响授权恢复。服务器访问日志按运维设置保留，应用不将兑换码放入访问地址或日志。");
        section("你的控制权","你可以取消权限引导、随时暂停、收起控制条，或在系统设置中关闭辅助服务。卸载或清除应用数据会删除本地设置和激活记录。暂停时不获取屏幕；已开始的长按可能需要最多约 1.5 秒结束。");
        section("主动分享","只有你点击分享时，才会打开系统分享面板，由你选择接收应用和对象。分享内容仅为公开下载链接或下载二维码图片，不包含你的兑换码、授权凭据或设备标识。二维码图片随应用提供，无需联网生成。");
        section("分享福利","在福利页发起分享后，会向授权服务器提交本应用设备标识散列、应用版本和随机领取请求号，用于每 7 天限领、幂等重试和签发可离线核验的授权。不读取、识别或上传你的联系人、聊天内容或发送记录。活动按发起系统分享面板计入领取条件，不声称检测第三方应用内最终发送结果。");
        section("邮件与其他应用","只有你主动点击邮件联系时，才会打开你选择的邮件应用；本应用不会自动发送邮件。购买或反馈邮件由你与开发者在邮件渠道中处理。微信及邮件应用按其自身规则处理数据。");
    }
    private void terms(){
        title("使用与周卡说明","请在使用前了解功能、价格与有效期。");
        section("产品与使用范围",Brand.NAME+"提供本机屏幕识别与自动长按控制，需 Android 11 或以上、竖屏及默认紫色棋子。它是独立第三方工具，并非微信官方产品，也不保证任何游戏运营方允许其使用。");
        section("价格与有效期","周卡价格为人民币 "+Brand.WEEK_PRICE+" 元／周。每个兑换码提供 7×24 小时有效期，以服务端时间为准；每码限一台设备，同一码重复核验不延长。新版激活新码或领取免费周卡，会在当前有效期上顺延 7 天，已经到期则从本次激活或领取起计算。本应用不自动续费、扣款或处理支付。购买请联系 "+Brand.EMAIL+"，以双方确认的交付安排为准。");
        section("限时免费周卡","活动开放期间，每台设备每 7 天可领取一次。从免费领取页发起链接或图片分享，成功打开系统分享面板后联网领取；发送对象和是否发送由你在接收应用中决定。活动不承诺永久开放，是否可领以领取页的实际状态为准。领取后已有的有效期保留，到期后也可再次领取。");
        section("联网激活与离线使用","兑换码须经服务器核验后使用，已激活周卡可在有效期内离线运行。卸载、清除数据后可在原设备重新核验同一码，恢复剩余期限；更换设备或重置系统需要联系开发者。系统时间异常时可能需要联网重新核验。旧版本机激活记录不作为本版有效授权。");
        section("应用版本更新","启动时检查新版。发现新版后，更新提示不能跳过，需完成下载及安卓系统确认安装后继续使用，也可以退出应用。安装包在本应用内下载，仅接受签名一致、版本更高且完整性验证通过的安装包。已发现需要更新的版本，在离线状态也不能绕过；尚未发现更新且网络不可用时，已有有效授权仍可离线使用。");
        section("效果与停止","识别效果受游戏画面、版本、手机性能和平台形状影响，不能保证每次命中或达到特定分数。识别不确定、离开微信、锁屏或系统中断时停止后续操作。使用前请了解并遵守游戏运营方规则。");
        section("咨询与售后","如遇兑换、使用或购买问题，请通过 "+Brand.EMAIL+" 联系开发者，说明问题及购买情况。请勿通过反馈发送密码、验证码或无关的私人信息。");
    }
    private void email(){Intent intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Brand.EMAIL));intent.putExtra(Intent.EXTRA_SUBJECT,Brand.NAME+"咨询");try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){toast("未安装邮件应用，可在关于页复制邮箱地址");}}
    @Override protected void onResume(){super.onResume();if(JumpService.instance!=null)JumpService.instance.enterAssistant();handler.removeCallbacks(ticker);handler.post(ticker);updates.resume();long time=android.os.SystemClock.elapsedRealtime();if(time-lastLicenseCheck>=60000&&pass.exists()){lastLicenseCheck=time;network.execute(()->{try{pass.refresh();}catch(Exception ignored){}handler.post(()->{if(!isDestroyed())refreshStatus();});});}}
    @Override protected void onPause(){handler.removeCallbacks(ticker);if(updates!=null)updates.pause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);network.shutdownNow();if(updates!=null)updates.destroy();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==UpdateManager.INSTALL_PERMISSION)updates.installationPermissionResult();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("route",route);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(route>=4)render(route==4||route==7||route==8?0:3);else if(route!=0)render(0);else super.onBackPressed();}
}
