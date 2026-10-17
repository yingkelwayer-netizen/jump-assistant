package local.jump.offline;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class LicenseSecurityTest {
 static int assertions;
 static void ok(boolean value){assertions++;if(!value)throw new AssertionError("assertion "+assertions);}
 static void bad(String t,String k){try{TokenCrypto.verify(t,k);throw new AssertionError("accepted invalid signature");}catch(AssertionError e){throw e;}catch(Exception expected){assertions++;}}
 public static void main(String[] args)throws Exception{
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();String pub=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),p=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"kind\":\"license\"}".getBytes(StandardCharsets.UTF_8));Signature s=Signature.getInstance("SHA256withRSA");s.initSign(pair.getPrivate());s.update(p.getBytes(StandardCharsets.US_ASCII));String token=p+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
  ok(TokenCrypto.verify(token,pub).equals("{\"kind\":\"license\"}"));bad("e30."+token.split("\\.")[1],pub);bad(token+".extra",pub);bad("",pub);bad(null,pub);bad("*.*",pub);bad(token,Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()));
  long server=1000000000,wall=2000000000,elapsed=10000;
  ok(LicenseClock.now(server,wall,elapsed,1,wall+2000,elapsed+2000,1,wall)==server+2000);
  ok(LicenseClock.now(server,wall,elapsed,1,wall-9999999,elapsed+2000,1,wall)==server+2000);
  ok(LicenseClock.now(server,wall,elapsed,1,wall+9999999,elapsed+2000,1,wall)==server+2000);
  ok(LicenseClock.now(server,wall,elapsed,1,wall+60000,500,2,wall)==server+60000);
  ok(LicenseClock.now(server,wall,elapsed,1,wall-600000,500,2,wall)==Long.MAX_VALUE);
  ok(LicenseClock.now(server,wall,elapsed,1,wall+60000,500,2,wall+600000)==Long.MAX_VALUE);
  ok(LicenseClock.now(0,wall,elapsed,1,wall,elapsed,1,wall)==Long.MAX_VALUE);
  long week=LicenseTerms.WEEK,a=1000;
  ok(LicenseTerms.valid(1,"",a,a+week,a));
  ok(!LicenseTerms.valid(1,"",a,a+2*week,a));
  ok(LicenseTerms.valid(2,"account",a,a+week,a));
  ok(LicenseTerms.valid(2,"account",a,a+2*week,a));
  ok(LicenseTerms.valid(2,"account",a,a+52*week,a));
  ok(LicenseTerms.valid(2,"account",a,a+100,a));
  ok(!LicenseTerms.valid(2,"other",a,a+week,a));
  ok(!LicenseTerms.valid(3,"account",a,a+week,a));
  ok(!LicenseTerms.valid(2,"account",a,a+week,a-1));
  ok(!LicenseTerms.valid(2,"account",a,a+week,a+week));
  System.out.println("License security: "+assertions+" assertions passed");
 }
}
