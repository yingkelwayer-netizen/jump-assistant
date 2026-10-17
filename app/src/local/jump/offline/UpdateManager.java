package local.jump.offline;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

final class UpdateManager {
 private final Activity activity;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean cancelled=new AtomicBoolean();
 private volatile HttpsURLConnection downloadConnection;
 private boolean checking,downloading,foreground;
 private long lastCheck=-60000;
 private JSONObject ready,downloaded;
 private AlertDialog prompt;
 private ProgressDialog progress;
 private ProgressDialog checkingProgress;
 static final int INSTALL_PERMISSION=42;
 UpdateManager(Activity a){activity=a;try{String token=a.getSharedPreferences("mandatory_update",Activity.MODE_PRIVATE).getString("manifest","");if(!token.isEmpty()){JSONObject r=new JSONObject(TokenCrypto.verify(token,NetworkConfig.PUBLIC_KEY));validate(r);if(r.getLong("versionCode")>NetworkConfig.VERSION_CODE)ready=r;else a.getSharedPreferences("mandatory_update",Activity.MODE_PRIVATE).edit().clear().commit();}}catch(Exception e){/* The saved requirement still blocks use until it is checked again. */}}
 private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
 private void toast(String s){if(alive())Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
 void resume(){foreground=true;if(pendingInstall!=null||downloading)return;if(downloaded!=null){offerInstall();return;}if(ready!=null){offer(ready);return;}check(false);}
 void pause(){foreground=false;}
 void destroy(){cancelled.set(true);HttpsURLConnection c=downloadConnection;if(c!=null)c.disconnect();worker.shutdownNow();UpdateGate.checking=false;if(prompt!=null)prompt.dismiss();if(progress!=null)progress.dismiss();if(checkingProgress!=null)checkingProgress.dismiss();}
 void check(boolean manual){
  if(checking||downloading||(prompt!=null&&prompt.isShowing()))return;long now=SystemClock.elapsedRealtime();if(!manual&&now-lastCheck<60000)return;checking=true;UpdateGate.checking=true;lastCheck=now;checkingProgress=new ProgressDialog(activity);checkingProgress.setMessage("正在检查应用版本…");checkingProgress.setCancelable(false);checkingProgress.setButton(ProgressDialog.BUTTON_NEGATIVE,"退出应用",(d,w)->activity.finishAndRemoveTask());checkingProgress.show();
  worker.execute(()->{try{
   JSONObject response=ApiClient.request("/api/v1/update",null),release=null;
   if(!response.isNull("manifest")){String token=response.getString("manifest");release=new JSONObject(TokenCrypto.verify(token,NetworkConfig.PUBLIC_KEY));validate(release);long code=release.getLong("versionCode");android.content.SharedPreferences prefs=activity.getSharedPreferences("mandatory_update",Activity.MODE_PRIVATE);if(code>NetworkConfig.VERSION_CODE&&code>=prefs.getLong("requiredCode",0)&&!prefs.edit().putLong("requiredCode",code).putString("manifest",token).commit())throw new java.io.IOException("无法保存更新要求");}
   final JSONObject r=release;main.post(()->{checking=false;UpdateGate.checking=false;if(checkingProgress!=null)checkingProgress.dismiss();if(!alive())return;try{if(r==null||r.getLong("versionCode")<=installed().getLongVersionCode()){if(UpdateGate.required(activity)){showRetry("需要完成应用更新后使用，请联网重新检查。");return;}if(manual)toast("当前已是最新版本");return;}ready=r;if(foreground)offer(r);}catch(Exception e){if(UpdateGate.required(activity))showRetry("更新信息暂时不可用，请重新检查。");else if(manual)toast("更新信息无效，请稍后重试");}});
  }catch(Exception e){main.post(()->{checking=false;UpdateGate.checking=false;if(checkingProgress!=null)checkingProgress.dismiss();if(!alive())return;if(ready!=null){if(foreground)offer(ready);}else if(UpdateGate.required(activity)){if(foreground)showRetry(ApiClient.message(e));}else if(manual)toast(ApiClient.message(e));});}});
 }
 private void showRetry(String message){if(!alive()||!foreground||(prompt!=null&&prompt.isShowing()))return;prompt=new AlertDialog.Builder(activity).setTitle("需要更新应用").setMessage(message).setCancelable(false).setNegativeButton("退出应用",(d,w)->activity.finishAndRemoveTask()).setPositiveButton("重新检查",(d,w)->main.post(()->check(true))).create();prompt.show();}
 private PackageInfo installed() throws Exception {return activity.getPackageManager().getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);}
 private void validate(JSONObject r) throws Exception {
  long size=r.getLong("size"),code=r.getLong("versionCode");URL url=new URL(r.getString("url")),base=new URL(NetworkConfig.ORIGIN);
  if(!"update".equals(r.getString("kind"))||!Brand.PRODUCT.equals(r.getString("product"))||!activity.getPackageName().equals(r.getString("packageName"))||code<1||r.getInt("minSdk")>Build.VERSION.SDK_INT||size<1||size>100*1024*1024||!r.getString("sha256").matches("[a-f0-9]{64}")||!"https".equals(url.getProtocol())||!url.getHost().equals(base.getHost())||url.getPort()!=base.getPort()||url.getUserInfo()!=null||!url.getPath().equals("/downloads/"+r.getString("sha256")+".apk")||url.getQuery()!=null||url.getRef()!=null||r.getString("notes").length()>4000||r.getString("versionName").length()>50)throw new IllegalArgumentException("更新信息无效");
 }
 private void offer(JSONObject r){
  if(!alive()||!foreground||(prompt!=null&&prompt.isShowing()))return;
  prompt=new AlertDialog.Builder(activity).setTitle("发现新版本 "+r.optString("versionName"))
   .setMessage(r.optString("notes")+"\n\n安装包 "+String.format(java.util.Locale.CHINA,"%.1f MB",r.optLong("size")/1048576.0)+"\n完成更新后才能继续使用。安装仍需你在安卓系统中确认。")
   .setCancelable(false).setNegativeButton("退出应用",(d,w)->activity.finishAndRemoveTask()).setPositiveButton("下载更新",(d,w)->download(r)).create();prompt.show();
 }
 private void download(JSONObject r){
  downloading=true;cancelled.set(false);progress=new ProgressDialog(activity);progress.setTitle("正在下载更新");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setMessage("完成更新后才能使用，下载后由系统确认安装");progress.setCancelable(false);progress.setButton(ProgressDialog.BUTTON_NEGATIVE,"退出应用",(d,w)->{cancelDownload();activity.finishAndRemoveTask();});progress.show();
  worker.execute(()->{File partial=new File(activity.getCacheDir(),"update.part"),apk=new File(activity.getCacheDir(),"update.apk");try{
   HttpsURLConnection c=ApiClient.connection(r.getString("url"));downloadConnection=c;c.setRequestProperty("Accept","application/vnd.android.package-archive");
   try{if(cancelled.get())throw new java.io.InterruptedIOException();if(c.getResponseCode()!=200)throw new ApiClient.Failure(0,"安装包暂不可用，请稍后重试");long expected=r.getLong("size"),length=c.getContentLengthLong();if(length!=-1&&length!=expected)throw new IllegalArgumentException("安装包大小校验失败");
    MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;
    try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();total+=n;if(total>expected)throw new IllegalArgumentException("安装包大小校验失败");out.write(b,0,n);hash.update(b,0,n);int value=(int)(total*100/expected);if(value!=previous){previous=value;main.post(()->{if(alive()&&progress!=null)progress.setProgress(value);});}}out.getFD().sync();}
    if(total!=expected||!hex(hash.digest()).equals(r.getString("sha256")))throw new IllegalArgumentException("安装包完整性校验失败");verifyApk(partial,r);if(cancelled.get())throw new java.io.InterruptedIOException();if(apk.exists()&&!apk.delete())throw new java.io.IOException();if(!partial.renameTo(apk))throw new java.io.IOException();
   }finally{c.disconnect();downloadConnection=null;}
   main.post(()->{downloading=false;if(progress!=null)progress.dismiss();if(!cancelled.get()&&alive()){downloaded=r;if(foreground)offerInstall();}});
  }catch(Exception e){partial.delete();main.post(()->{downloading=false;if(progress!=null)progress.dismiss();if(!cancelled.get()){toast(ApiClient.message(e));if(foreground)offer(r);}});}});
 }
 private void cancelDownload(){cancelled.set(true);HttpsURLConnection c=downloadConnection;if(c!=null)c.disconnect();}
 private void offerInstall(){if(!alive()||!foreground||downloaded==null||(prompt!=null&&prompt.isShowing()))return;final JSONObject r=downloaded;prompt=new AlertDialog.Builder(activity).setTitle("更新已准备好").setMessage("安装包已通过完整性与应用签名检查。请完成安装后继续使用。").setCancelable(false).setNegativeButton("退出应用",(d,w)->activity.finishAndRemoveTask()).setPositiveButton("安装",(d,w)->install(r)).create();prompt.show();}
 private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
 private Set<String> certificates(PackageInfo info) throws Exception {if(info.signingInfo==null)throw new IllegalArgumentException("安装包签名无效");Set<String> result=new HashSet<>();for(Signature s:info.signingInfo.getApkContentsSigners())result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;}
 private void verifyApk(File apk,JSONObject release) throws Exception {
  PackageInfo incoming=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),current=installed();
  if(incoming==null||!activity.getPackageName().equals(incoming.packageName)||incoming.getLongVersionCode()!=release.getLong("versionCode")||incoming.getLongVersionCode()<=current.getLongVersionCode()||!release.getString("versionName").equals(incoming.versionName)||!certificates(current).equals(certificates(incoming))||certificates(incoming).isEmpty())throw new IllegalArgumentException("安装包版本或应用签名不匹配");
 }
 private JSONObject pendingInstall;
 private void install(JSONObject release){
  try{verifyApk(new File(activity.getCacheDir(),"update.apk"),release);if(!activity.getPackageManager().canRequestPackageInstalls()){
   pendingInstall=release;new AlertDialog.Builder(activity).setTitle("允许安装应用更新").setMessage("请在系统页面允许“跳一跳助手”安装更新。返回后仍需由你在系统安装器中确认。你可以退出应用，完成更新后再使用。")
    .setCancelable(false).setNegativeButton("退出应用",(d,w)->activity.finishAndRemoveTask()).setPositiveButton("前往设置",(d,w)->activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),INSTALL_PERMISSION)).show();return;
  }
  Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("应用更新",uri));activity.startActivity(i);
  }catch(Exception e){toast("无法启动安装，请重新检查更新");if(foreground)showRetry("安装未完成，请重试。完成更新后才能继续使用。");}
 }
 void installationPermissionResult(){JSONObject r=pendingInstall;pendingInstall=null;if(r==null)return;if(activity.getPackageManager().canRequestPackageInstalls())install(r);else{toast("未开启安装权限，需要完成更新后使用");offerInstall();}}
}
