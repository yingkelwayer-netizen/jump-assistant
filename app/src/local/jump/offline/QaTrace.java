package local.jump.offline;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** Opt-in local QA build only. Production builds compile DIAGNOSTICS=false. */
final class QaTrace {
    private static int sequence;
    static volatile String lastFrame="";
    static synchronized void event(Context context,String type,String detail){
        if(!NetworkConfig.DIAGNOSTICS)return;
        try{
            JSONObject item=new JSONObject();item.put("type",type);item.put("time",System.currentTimeMillis());item.put("detail",detail);
            Log.i("JumpQa",item.toString());
        }catch(Exception ignored){}
    }
    static synchronized void frame(Context context,Bitmap bitmap,Vision.Result result){
        if(!NetworkConfig.DIAGNOSTICS||(!result.valid&&!result.clipped))return;
        try{
            File parent=context.getExternalFilesDir(null);if(parent==null)return;
            File dir=new File(parent,"qa");if(!dir.isDirectory()&&!dir.mkdirs())return;
            String name=String.format(java.util.Locale.ROOT,"frame-%03d.png",sequence++%128);
            try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            lastFrame=name;
            JSONObject item=new JSONObject();item.put("type","FRAME");item.put("time",System.currentTimeMillis());item.put("file",name);item.put("sequence",sequence);item.put("valid",result.valid);item.put("clipped",result.clipped);item.put("geometry",result.toString());
            Log.i("JumpQa",item.toString());
        }catch(Exception e){event(context,"TRACE_ERROR",e.getClass().getSimpleName());}
    }
    private QaTrace(){}
}
