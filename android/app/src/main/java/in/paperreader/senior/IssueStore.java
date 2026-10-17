package in.paperreader.senior;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Publish only verified PDFs; retain the old issue until the pointer is durable. */
final class IssueStore {
    static File current(Context c){
        String name=c.getSharedPreferences("paper",0).getString("current","");
        if(!name.matches("issue-[0-9-]+-[a-f0-9]+\\.pdf"))return new File(c.getFilesDir(),"paper.pdf");
        return new File(c.getFilesDir(),name);
    }
    static synchronized void install(Context c,File pending,String date,int pages)throws Exception{
        java.time.LocalDate.parse(date);
        try(PdfRenderer pdf=new PdfRenderer(ParcelFileDescriptor.open(pending,ParcelFileDescriptor.MODE_READ_ONLY))){
            if(pages<1||pdf.getPageCount()!=pages)throw new IOException("Incomplete PDF; previous paper retained");
        }
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(pending)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)digest.update(buf,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b));
        File destination=new File(c.getFilesDir(),"issue-"+date+"-"+hex.substring(0,12)+".pdf");
        Files.move(pending.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
        boolean committed=c.getSharedPreferences("paper",0).edit().putString("current",destination.getName()).putString("date",date).putString("sha",hex.toString()).commit();
        if(!committed)throw new IOException("Could not save issue details");
        prune(c);
    }
    static synchronized void prune(Context c){
        File active=current(c);File[] files=c.getFilesDir().listFiles();
        if(files!=null)for(File f:files)if(!f.equals(active)&&(f.getName().matches("issue-[0-9-]+-[a-f0-9]+\\.pdf")||f.getName().equals("paper.pdf")))f.delete();
    }
}
