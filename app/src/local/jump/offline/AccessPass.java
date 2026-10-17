package local.jump.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Server-signed licenses are verified before the accessibility worker may run. */
final class AccessPass {
    private final Context context;
    private final SharedPreferences preferences;
    private final String device;
    AccessPass(Context c){context=c.getApplicationContext();preferences=c.getSharedPreferences("signed_access_v1",Context.MODE_PRIVATE);device=deviceId(c);}
    private static String deviceId(Context c){try{String id=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ANDROID_ID);if(id==null||id.isEmpty())throw new IllegalStateException();byte[] b=MessageDigest.getInstance("SHA-256").digest((c.getPackageName()+":"+id).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.US,"%02x",v&255));return out.toString();}catch(Exception e){throw new IllegalStateException("无法获取本机授权标识");}}
    private int boot(){return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private JSONObject payload(String token) throws Exception {
        JSONObject p=new JSONObject(TokenCrypto.verify(token,NetworkConfig.PUBLIC_KEY));long a=p.getLong("activatedAt"),e=p.getLong("expiresAt"),s=p.getLong("checkedAt");
        if(!"license".equals(p.getString("kind"))||!Brand.PRODUCT.equals(p.getString("product"))||!device.equals(p.getString("device"))||!LicenseTerms.valid(p.optInt("licenseVersion",1),p.optString("source",""),a,e,s))throw new IllegalArgumentException("授权信息无效");return p;
    }
    boolean active(){synchronized(AccessPass.class){try{
        if(UpdateGate.blocked(context)||preferences.getBoolean("blocked",false))return false;JSONObject p=payload(preferences.getString("token",""));long wall=System.currentTimeMillis();
        long time=LicenseClock.now(p.getLong("checkedAt"),preferences.getLong("wall",0),preferences.getLong("elapsed",-1),preferences.getInt("boot",-2),wall,SystemClock.elapsedRealtime(),boot(),preferences.getLong("maxWall",0));
        if(wall>preferences.getLong("maxWall",0)+60000)preferences.edit().putLong("maxWall",wall).apply();return time<p.getLong("expiresAt");
    }catch(Exception e){return false;}}}
    boolean exists(){return preferences.contains("token");}
    String expiry(){try{return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(payload(preferences.getString("token","")).getLong("expiresAt")));}catch(Exception e){return "尚未激活";}}
    private void save(String token) throws Exception {JSONObject incoming=payload(token);synchronized(AccessPass.class){try{JSONObject current=payload(preferences.getString("token",""));if(current.getLong("activatedAt")>incoming.getLong("activatedAt")||current.getLong("checkedAt")>incoming.getLong("checkedAt"))return;}catch(Exception ignored){}long wall=System.currentTimeMillis();if(!preferences.edit().putString("token",token).putBoolean("blocked",false).putLong("wall",wall).putLong("elapsed",SystemClock.elapsedRealtime()).putInt("boot",boot()).putLong("maxWall",wall).commit())throw new ApiClient.Failure(0,"授权保存失败，请重试");}}
    void activate(String code) throws Exception {
        if(!code.matches("[0-9]{24}"))throw new IllegalArgumentException("请输入 24 位数字兑换码");save(ApiClient.request("/api/v1/redeem",new JSONObject().put("code",code).put("device",device).put("versionCode",NetworkConfig.VERSION_CODE)).getString("token"));
    }
    JSONObject shareStatus() throws Exception {JSONObject result=ApiClient.request("/api/v1/share/status",new JSONObject().put("device",device).put("versionCode",NetworkConfig.VERSION_CODE));if(result.has("token"))save(result.getString("token"));return result;}
    boolean pendingShareClaim(){return preferences.contains("shareRequest");}
    void claimShare() throws Exception {
        if(UpdateGate.blocked(context))throw new IllegalArgumentException("请先完成版本检查或更新");
        String request=preferences.getString("shareRequest","");if(request.isEmpty()){request=java.util.UUID.randomUUID().toString();if(!preferences.edit().putString("shareRequest",request).commit())throw new IllegalArgumentException("领取请求保存失败，请重试");}
        try{JSONObject response=ApiClient.request("/api/v1/share/claim",new JSONObject().put("device",device).put("requestId",request).put("versionCode",NetworkConfig.VERSION_CODE));save(response.getString("token"));preferences.edit().remove("shareRequest").commit();}
        catch(ApiClient.Failure e){if(e.status>=400&&e.status<500)preferences.edit().remove("shareRequest").commit();throw e;}
    }
    void refresh() throws Exception {
        String token=preferences.getString("token","");if(token.isEmpty())return;
        try{save(ApiClient.request("/api/v1/check",new JSONObject().put("token",token).put("device",device)).getString("token"));}
        catch(ApiClient.Failure e){synchronized(AccessPass.class){if((e.status==401||e.status==403||e.status==410)&&token.equals(preferences.getString("token","")))preferences.edit().putBoolean("blocked",true).commit();}throw e;}
    }
}
