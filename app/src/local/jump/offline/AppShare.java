package local.jump.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class AppShare {
    static final String PAGE="https://jump.junxinghuatai.com.cn";
    private static final String MESSAGE="跳一跳助手安卓版下载与安装说明：\n"+PAGE+"\n\n支持 Android 11 及以上。安装后在“我的周卡”输入兑换码联网激活，每码限一台设备，首次激活起 7 天有效。";
    static void menu(Activity activity){new AlertDialog.Builder(activity).setTitle("分享应用").setItems(new String[]{"分享下载链接","分享二维码图片"},(d,which)->{if(which==0)link(activity);else image(activity);}).setNegativeButton("取消",null).show();}
    static boolean link(Activity activity){Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain");intent.putExtra(Intent.EXTRA_SUBJECT,"跳一跳助手下载");intent.putExtra(Intent.EXTRA_TEXT,MESSAGE);return choose(activity,intent,"分享下载链接");}
    static boolean image(Activity activity){
        try{
            File target=new File(activity.getCacheDir(),"download-share.png");
            try(InputStream input=activity.getResources().openRawResource(R.raw.share_poster);FileOutputStream output=new FileOutputStream(target)){
                byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            }
            Uri uri=Uri.parse("content://"+activity.getPackageName()+".shares/download.png");
            Intent intent=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TEXT,MESSAGE);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(),"下载二维码",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return choose(activity,intent,"分享二维码图片");
        }catch(java.io.IOException e){Toast.makeText(activity,"暂时无法准备图片，请重试或分享下载链接",Toast.LENGTH_SHORT).show();return false;}
    }
    private static boolean choose(Activity activity,Intent intent,String title){if(intent.resolveActivity(activity.getPackageManager())==null){Toast.makeText(activity,"未找到可接收分享的应用",Toast.LENGTH_SHORT).show();return false;}try{activity.startActivity(Intent.createChooser(intent,title));return true;}catch(android.content.ActivityNotFoundException e){Toast.makeText(activity,"未找到可接收分享的应用",Toast.LENGTH_SHORT).show();return false;}}
    private AppShare(){}
}
