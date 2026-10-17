package local.jump.offline;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
final class TokenCrypto {
 static String verify(String token,String publicKey) throws Exception {
  if(token==null||token.length()>20000)throw new IllegalArgumentException("签名数据无效");String[] parts=token.split("\\.",-1);
  if(parts.length!=2||!parts[0].matches("[A-Za-z0-9_-]+")||!parts[1].matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("签名数据无效");
  Signature v=Signature.getInstance("SHA256withRSA");v.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))));v.update(parts[0].getBytes(StandardCharsets.US_ASCII));
  if(!v.verify(Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException("签名验证失败");return new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);
 }
 private TokenCrypto(){}
}
