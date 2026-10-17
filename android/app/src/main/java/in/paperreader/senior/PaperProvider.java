package in.paperreader.senior;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Only the two app-owned PDF files can be shared through temporary URI grants. */
public final class PaperProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri)throws FileNotFoundException{
        if(!"content".equals(uri.getScheme()) || !(getContext().getPackageName()+".papers").equals(uri.getAuthority())
            || !java.util.Collections.singleton("version").containsAll(uri.getQueryParameterNames()) || uri.getFragment()!=null)throw new FileNotFoundException("Invalid paper URI");
        if("/paper.pdf".equals(uri.getPath()))return new File(getContext().getFilesDir(),"paper.pdf");
        if("/sample.pdf".equals(uri.getPath()))return new File(getContext().getCacheDir(),"sample.pdf");
        throw new FileNotFoundException("Unknown paper");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only paper");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return "application/pdf";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{
            File file=resolve(uri);
            String date=getContext().getSharedPreferences("paper",0).getString("date","");
            String name=file.getName().equals("sample.pdf")?"SPK-Paper-SAMPLE.pdf":"SPK-Paper-"+date+".pdf";
            String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=name;
                else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=file.length();
            }
            result.addRow(values);return result;
        }catch(FileNotFoundException e){throw new IllegalArgumentException(e);}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
