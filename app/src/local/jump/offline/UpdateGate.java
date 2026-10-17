package local.jump.offline;
import android.content.Context;
final class UpdateGate {
    static volatile boolean checking;
    static boolean required(Context c){return c.getSharedPreferences("mandatory_update",Context.MODE_PRIVATE).getLong("requiredCode",0)>NetworkConfig.VERSION_CODE;}
    static boolean blocked(Context c){return checking||required(c);}
    private UpdateGate(){}
}
