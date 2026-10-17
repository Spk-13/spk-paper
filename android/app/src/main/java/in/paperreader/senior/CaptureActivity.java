package in.paperreader.senior;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Uses the visible official reader. No proxy, hidden login or restriction bypass. */
public final class CaptureActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<String> seen=new HashSet<>();
    private WebView web;private TextView status;private String script,issue,agent;
    private int index,total;private String previous="";private long deadline;
    private volatile boolean stopped;private boolean processing;
    private File pending;private JpegPdf pdf;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,20,16,16);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(16+i.getSystemWindowInsetLeft(),20+i.getSystemWindowInsetTop(),16+i.getSystemWindowInsetRight(),16+i.getSystemWindowInsetBottom());return i;});
        status=new TextView(this);status.setTextSize(21);status.setText("పేపర్ సిద్ధం చేస్తున్నాం…\nPreparing today’s paper. Please keep this screen open.");root.addView(status);
        Button cancel=new Button(this);cancel.setText("రద్దు / Cancel");root.addView(cancel);cancel.setOnClickListener(v->fail("Preparation cancelled. Your saved paper is unchanged."));
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        agent=settings.getUserAgentString();android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        issue=LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        try(InputStream in=getAssets().open("capture.js")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);script=bytes.toString("UTF-8");}
        catch(Exception e){fail("Reader setup failed: "+e.getMessage());return;}
        pending=new File(getCacheDir(),"capture-"+UUID.randomUUID()+".pdf");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !official(request.getUrl().toString());}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400)fail("Eenadu returned HTTP "+response.getStatusCode()+" on this connection. No paper was replaced.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())fail("The official reader could not load. Check internet and retry. "+error.getDescription());}
        });
        deadline=SystemClock.elapsedRealtime()+180000;
        web.loadUrl("https://epaper.eenadu.net/");handler.postDelayed(this::poll,1500);
    }
    private static boolean official(String address){try{URI u=URI.create(address);return "https".equals(u.getScheme())&&"epaper.eenadu.net".equals(u.getHost())&&u.getUserInfo()==null;}catch(Exception e){return false;}}
    private void poll(){
        if(stopped||processing)return;
        if(SystemClock.elapsedRealtime()>deadline){fail("The reader did not provide today’s pages in time. If login is required, complete it on the official page and try again. Saved paper retained.");return;}
        if(!official(web.getUrl())){handler.postDelayed(this::poll,1000);return;}
        try{
            LocalDate date=LocalDate.parse(issue);
            JSONObject args=new JSONObject().put("date",date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).put("label",date.format(DateTimeFormatter.ofPattern("dd-MMM-yy",Locale.ENGLISH)).toUpperCase(Locale.ROOT)).put("index",index).put("previous",previous);
            web.evaluateJavascript("("+script+")("+args+")",raw->{
                if(stopped)return;
                try{
                    JSONObject result=new JSONObject(raw);String state=result.getString("state");
                    if("error".equals(state)){fail(result.getString("message"));return;}
                    if("page".equals(state)){
                        int count=result.getInt("count");if(count<1||count>120||(total!=0&&total!=count))throw new IOException("Unexpected page count");total=count;
                        String url=result.getString("url");if(!seen.add(url))throw new IOException("Repeated page; incomplete paper was not saved");
                        processing=true;status.setText("పేజీ "+(index+1)+" / "+total+"\nPreparing page "+(index+1)+" of "+total);
                        String cookie=android.webkit.CookieManager.getInstance().getCookie(url);worker.execute(()->capture(url,cookie));return;
                    }
                    if("login".equals(state))status.setText("Official reader: complete any required sign-in below.\nThe app cannot skip access restrictions.");
                    handler.postDelayed(this::poll,1000);
                }catch(Exception e){fail("Could not prepare paper: "+e.getMessage());}
            });
        }catch(Exception e){fail("Reader error: "+e.getMessage());}
    }
    private void capture(String address,String cookie){
        try{
            URI uri=URI.create(address);
            if(!"https".equals(uri.getScheme())||!"d66zsp32hue2v.cloudfront.net".equals(uri.getHost())||uri.getUserInfo()!=null||!uri.getPath().startsWith("/Eenadu/")||!uri.getPath().endsWith("_hr.jpg"))throw new IOException("Unexpected newspaper image address");
            if(stopped)return;
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(20000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent",agent);connection.setRequestProperty("Referer","https://epaper.eenadu.net/");if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
            byte[] image;
            try{
                if(connection.getResponseCode()!=200)throw new IOException("Page server returned "+connection.getResponseCode());
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1){if(stopped)return;if(out.size()+n>20*1024*1024)throw new IOException("Page exceeds size limit");out.write(b,0,n);}image=out.toByteArray();
                }
            }finally{connection.disconnect();}
            if(stopped)return;if(pdf==null)pdf=new JpegPdf(pending);pdf.add(image);
            if(pending.length()>250L*1024*1024)throw new IOException("Paper exceeds phone storage limit");
            boolean last=index+1==total;
            if(last){
                pdf.finish();pdf.close();pdf=null;
                if(stopped)return;
                if(!issue.equals(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()))throw new IOException("Date changed. Please prepare the new day’s paper.");
                IssueStore.install(this,pending,issue,total);
                runOnUiThread(()->{if(!stopped){setResult(RESULT_OK);finish();}});
            }else runOnUiThread(()->{if(!stopped){previous=address;index++;processing=false;deadline=SystemClock.elapsedRealtime()+90000;handler.postDelayed(this::poll,500);}});
        }catch(Exception e){runOnUiThread(()->fail("Paper preparation failed. Saved issue retained.\n"+e.getMessage()));}
    }
    private void fail(String message){if(stopped)return;stopped=true;setResult(RESULT_CANCELED,new Intent().putExtra("error",message));finish();}
    @Override public void onBackPressed(){fail("Preparation cancelled. Your saved paper is unchanged.");}
    @Override protected void onDestroy(){
        stopped=true;handler.removeCallbacksAndMessages(null);
        if(web!=null){web.stopLoading();web.destroy();}
        worker.execute(()->{try{if(pdf!=null)pdf.close();}catch(Exception ignored){}if(pending!=null)pending.delete();});worker.shutdown();super.onDestroy();
    }
}
