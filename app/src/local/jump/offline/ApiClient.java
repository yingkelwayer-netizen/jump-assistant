package local.jump.offline;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
final class ApiClient {
 static final class Failure extends Exception {final int status;Failure(int s,String m){super(m);status=s;}}
 static HttpsURLConnection connection(String value) throws Exception {
  URL url=new URL(value),base=new URL(NetworkConfig.ORIGIN);
  if(!"https".equals(url.getProtocol())||!url.getHost().equals(base.getHost())||url.getPort()!=base.getPort()||url.getUserInfo()!=null)throw new Failure(0,"服务地址无效");
  HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","JumpAssistant/"+Brand.VERSION);return c;
 }
 static JSONObject request(String path,JSONObject body) throws Exception {
  HttpsURLConnection c=connection(NetworkConfig.ORIGIN+path);
  try{
   if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(b);}}
   int status=c.getResponseCode();String raw;try(InputStream in=status>=400?c.getErrorStream():c.getInputStream()){raw=read(in,24000);}
   JSONObject result;try{result=new JSONObject(raw);}catch(Exception e){throw new Failure(status,"服务暂不可用，请稍后重试");}
   if(status!=200)throw new Failure(status,result.optString("message","请求失败，请稍后重试"));return result;
  }finally{c.disconnect();}
 }
 static String read(InputStream in,int max) throws Exception {if(in==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new Failure(0,"服务响应过大");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
 static String message(Exception e){return e instanceof Failure||e instanceof IllegalArgumentException?e.getMessage():"网络连接失败，请检查网络后重试";}
 private ApiClient(){}
}
