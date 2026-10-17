package in.paperreader.senior;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.graphics.BitmapFactory;

/** Streams original JPEGs into PDF, avoiding a full decoded newspaper in RAM. */
final class JpegPdf implements Closeable {
    private final RandomAccessFile out;
    private final Map<Integer,Long> offsets=new TreeMap<>();
    private final List<Integer> pages=new ArrayList<>();
    private int next=3;
    JpegPdf(File file)throws IOException{out=new RandomAccessFile(file,"rw");out.setLength(0);write("%PDF-1.4\n%SPK-Paper\n");}
    private void write(String s)throws IOException{out.write(s.getBytes(StandardCharsets.ISO_8859_1));}
    private void object(int id)throws IOException{offsets.put(id,out.getFilePointer());write(id+" 0 obj\n");}
    void add(byte[] jpeg)throws IOException{
        if(jpeg.length<4||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new IOException("Not a JPEG page");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,opts);
        int w=opts.outWidth,h=opts.outHeight;
        if(w<1000||h<1000||w>12000||h>16000)throw new IOException("Invalid page dimensions");
        int page=next++,image=next++,stream=next++;pages.add(page);
        object(page);write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "+w+" "+h+"] /Resources << /XObject << /Im "+image+" 0 R >> >> /Contents "+stream+" 0 R >>\nendobj\n");
        object(image);write("<< /Type /XObject /Subtype /Image /Width "+w+" /Height "+h+" /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "+jpeg.length+" >>\nstream\n");out.write(jpeg);write("\nendstream\nendobj\n");
        String draw="q "+w+" 0 0 "+h+" 0 0 cm /Im Do Q\n";
        object(stream);write("<< /Length "+draw.length()+" >>\nstream\n"+draw+"endstream\nendobj\n");
    }
    void finish()throws IOException{
        if(pages.isEmpty())throw new IOException("No newspaper pages");
        object(1);write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        object(2);write("<< /Type /Pages /Count "+pages.size()+" /Kids [");for(int id:pages)write(id+" 0 R ");write("] >>\nendobj\n");
        long xref=out.getFilePointer();write("xref\n0 "+next+"\n0000000000 65535 f \n");
        for(int id=1;id<next;id++)write(String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(id)));
        write("trailer\n<< /Size "+next+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");out.getFD().sync();
    }
    public void close()throws IOException{out.close();}
}
