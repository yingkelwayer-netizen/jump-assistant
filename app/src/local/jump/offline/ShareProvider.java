package local.jump.offline;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Only the public download poster may be shared with a user-selected recipient. */
public final class ShareProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        if(!getContext().getPackageName().concat(".shares").equals(uri.getAuthority())||!"/download.png".equals(uri.getEncodedPath())||uri.getQuery()!=null||uri.getFragment()!=null)throw new FileNotFoundException();
        File f=new File(getContext().getCacheDir(),"download-share.png");if(!f.isFile())throw new FileNotFoundException();return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public String getType(Uri uri){try{file(uri);return "image/png";}catch(FileNotFoundException e){return null;}}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{
        File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"跳一跳助手下载二维码.png":OpenableColumns.SIZE.equals(columns[i])?f.length():null;
        cursor.addRow(row);return cursor;
    }catch(FileNotFoundException e){return null;}}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
