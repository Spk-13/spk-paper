package in.paperreader.senior;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.webkit.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class PaperTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private byte[] jpeg(){Bitmap b=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(Color.WHITE);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,out);b.recycle();return out.toByteArray();}
    private File make(int count)throws Exception{
        File f=File.createTempFile("test-paper",".pdf",context().getCacheDir());
        try(JpegPdf p=new JpegPdf(f)){byte[] image=jpeg();for(int i=0;i<count;i++)p.add(image);p.finish();}return f;
    }
    @Test public void homeStartsWithoutFeedSetup()throws Exception{
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new android.content.Intent(context(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        assertNotNull(activity);instrumentation.runOnMainSync(activity::finish);
    }
    @Test public void truncatedJpegIsRejected()throws Exception{
        File file=File.createTempFile("bad-image",".pdf",context().getCacheDir());
        try(JpegPdf pdf=new JpegPdf(file)){
            byte[] bytes=jpeg();
            try{pdf.add(java.util.Arrays.copyOf(bytes,bytes.length-10));fail("Truncated image accepted");}catch(IOException expected){}
        }finally{file.delete();}
    }
    @Test public void pdfRendersAllPages()throws Exception{
        File f=make(3);
        try(PdfRenderer reader=new PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY))){
            assertEquals(3,reader.getPageCount());
            for(int i=0;i<3;i++)try(PdfRenderer.Page page=reader.openPage(i)){
                Bitmap preview=Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888);page.render(preview,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);assertEquals(Color.WHITE,preview.getPixel(60,80));preview.recycle();
            }
        }finally{f.delete();}
    }
    @Test public void replaceOnlyAfterValidation()throws Exception{
        Context c=context();IssueStore.install(c,make(2),"2026-10-16",2);File old=IssueStore.current(c);assertTrue(old.exists());
        File bad=make(1);
        try{IssueStore.install(c,bad,"2026-10-17",2);fail("Incomplete issue accepted");}catch(IOException expected){}finally{bad.delete();}
        assertEquals(old,IssueStore.current(c));assertTrue(old.exists());assertEquals("2026-10-16",c.getSharedPreferences("paper",0).getString("date",""));
        IssueStore.install(c,make(3),"2026-10-17",3);assertFalse(old.exists());assertTrue(IssueStore.current(c).exists());
        File[] issues=c.getFilesDir().listFiles((dir,name)->name.startsWith("issue-")&&name.endsWith(".pdf"));assertEquals(1,issues.length);
    }
    private String asset()throws Exception{try(InputStream in=context().getAssets().open("capture.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    @Test public void readerSelectsPagesAndRejectsStaleDates()throws Exception{
        String encoded=android.util.Base64.encodeToString(jpeg(),android.util.Base64.NO_WRAP);
        String html="<body>GREATER HYDERABAD 17-Oct-26<select id='myPageList'><option>1</option><option>2</option></select><img class='img_jpg' src='data:image/jpeg;base64,"+encoded+"'></body>";
        AtomicReference<WebView> ref=new AtomicReference<>();CountDownLatch loaded=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{WebView web=new WebView(context());web.getSettings().setJavaScriptEnabled(true);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){loaded.countDown();}});ref.set(web);web.loadDataWithBaseURL("https://epaper.eenadu.net/",html,"text/html","UTF-8",null);});
        assertTrue(loaded.await(30,TimeUnit.SECONDS));String script=asset();
        try{
            JSONObject args=new JSONObject().put("date","17/10/2026").put("label","17-OCT-26").put("index",0).put("previous","");
            JSONObject first=eval(ref.get(),"("+script+")("+args+")");assertEquals("page",first.getString("state"));assertEquals(2,first.getInt("count"));
            args.put("previous",first.getString("url"));args.put("index",1);
            assertEquals("wait",eval(ref.get(),"("+script+")("+args+")").getString("state"));
            assertEquals("wait",eval(ref.get(),"("+script+")("+args+")").getString("state"));
            args.put("label","18-OCT-26");assertEquals("error",eval(ref.get(),"("+script+")("+args+")").getString("state"));
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ref.get().destroy());}
    }
    private JSONObject eval(WebView web,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->web.evaluateJavascript(script,value->{result.set(value);done.countDown();}));
        assertTrue(done.await(10,TimeUnit.SECONDS));return new JSONObject(result.get());
    }
}
