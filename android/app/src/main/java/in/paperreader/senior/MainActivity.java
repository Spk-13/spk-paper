package in.paperreader.senior;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.Locale;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView status, dateLabel;
    private Button open;
    private int generation;
    private boolean busy, destroyed;
    private final int green=Color.rgb(21,92,73), ink=Color.rgb(25,43,36);
    private static final String DEFAULT_FEED="https://spk-13.github.io/spk-paper/latest.json";
    private static final long MAX_PDF=150L*1024*1024;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("paper",MODE_PRIVATE); home(); refreshToday(false);
    }
    private String today(){ return LocalDate.now(ZoneId.of("Asia/Kolkata")).toString(); }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);v.setPadding(dp(4),dp(8),dp(4),dp(8));return v;}
    private Button button(String value,boolean primary){
        Button b=new Button(this);b.setText(value);b.setTextSize(20);b.setAllCaps(false);b.setMinHeight(dp(64));b.setPadding(dp(12),dp(10),dp(12),dp(10));
        b.setTextColor(primary?Color.WHITE:green);
        GradientDrawable bg=new GradientDrawable();bg.setColor(primary?green:Color.WHITE);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),primary?green:Color.rgb(195,211,201));b.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));b.setLayoutParams(lp);return b;
    }
    private void base(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,245,238));root.setPadding(dp(16),dp(12),dp(16),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);
    }
    private void home(){
        generation++;busy=false;
        base();
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        TextView title=text("SPK Paper",36);title.setTypeface(null,Typeface.BOLD);content.addView(title);
        content.addView(text("ఈనాడు • గ్రేటర్ హైదరాబాద్\nEenadu • Greater Hyderabad",18));
        dateLabel=text("ఈ రోజు  ·  "+today(),22);content.addView(dateLabel);
        status=text(prefs.getString("feed",DEFAULT_FEED).isEmpty()?"మొదటిసారి ఏర్పాటు చేయాలి\nOne-time setup is needed.":"ఈ రోజు పేపర్ కోసం బటన్ నొక్కండి\nTap below to check today’s paper.",20);content.addView(status);
        open=button("ఈ రోజు పేపర్ తెరవండి\nOpen today’s paper",true);content.addView(open);open.setOnClickListener(v->refreshToday(true));
        Button offline=button("సేవ్ అయిన పేపర్\nRead saved issue",false);content.addView(offline);offline.setOnClickListener(v->openCached());
        if(prefs.getString("feed",DEFAULT_FEED).isEmpty()){
            Button demo=button("నమూనా చూడండి\nOpen sample PDF",false);content.addView(demo);demo.setOnClickListener(v->sample());
        }
        TextView hint=text("పేపర్ PDF వ్యూయర్‌లో తెరుచుకుంటుంది.\nOpens with Google Drive or your PDF viewer.",17);content.addView(hint);
        Button settings=button("ఏర్పాటు / Settings",false);content.addView(settings);settings.setOnClickListener(v->setup());
    }
    private void setup(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(prefs.getString("feed",DEFAULT_FEED));input.setHint("https://…/latest.json");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("One-time paper feed setup").setMessage("A family member sets this once. Paste the HTTPS latest.json address from the daily publisher.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{safeUrl(input.getText().toString().trim());prefs.edit().putString("feed",input.getText().toString().trim()).apply();dialog.dismiss();home();}catch(Exception e){input.setError("Enter a valid HTTPS feed address");}}));dialog.show();
    }
    private static URL safeUrl(String address)throws Exception{URL u=new URL(address);if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null)throw new IOException("HTTPS address required");return u;}
    private void message(String s){if(!destroyed){status.setText(s);busy=false;open.setEnabled(true);}}
    private void refreshToday(boolean openViewer){
        if(busy)return;
        String feed=prefs.getString("feed",DEFAULT_FEED);if(feed.isEmpty()){setup();return;}
        busy=true;open.setEnabled(false);status.setText("పేపర్ సిద్ధం చేస్తున్నాం…\nChecking today’s issue…");int ticket=++generation;
        worker.execute(()->{
            File temp=new File(getCacheDir(),"incoming-"+java.util.UUID.randomUUID()+".pdf");
            try{
                URL endpoint=safeUrl(feed+(feed.contains("?")?"&":"?")+"t="+System.currentTimeMillis());
                ByteArrayOutputStream meta=new ByteArrayOutputStream();fetch(endpoint,meta,65536);
                JSONObject data=new JSONObject(meta.toString("UTF-8"));String date=data.getString("date");
                if(!"GREATER HYDERABAD".equals(data.getString("edition")))throw new IOException("Wrong edition in feed");
                if(!today().equals(date)){runOnUiThread(()->{if(ticket==generation)message("ఈ రోజు పేపర్ ఇంకా రాలేదు.\nToday’s issue is not available yet. Latest: "+date+". Please try again later.");});return;}
                String hash=data.getString("sha256");if(!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid PDF checksum");
                File cached=new File(getFilesDir(),"paper.pdf");
                if(!(cached.isFile() && date.equals(prefs.getString("date","")) && hash.equals(sha(cached)))){
                    URL pdf=safeUrl(new URL(safeUrl(feed),data.getString("pdf")).toString());
                    try(FileOutputStream out=new FileOutputStream(temp)){fetch(pdf,out,MAX_PDF);}
                    if(!hash.equals(sha(temp)))throw new IOException("Paper was incomplete; please retry");
                    try(PdfRenderer check=new PdfRenderer(ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY))){if(check.getPageCount()!=data.getInt("pages")||check.getPageCount()<1)throw new IOException("Page count mismatch");}
                    java.nio.file.Files.move(temp.toPath(),cached.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    prefs.edit().putString("date",date).putString("sha",hash).apply();
                }
                if(openViewer) launchPdf(cached,date,ticket);
                else runOnUiThread(()->{if(ticket==generation)message("ఈ రోజు పేపర్ సిద్ధంగా ఉంది\nToday’s paper is ready • "+date);});
            }catch(Exception e){runOnUiThread(()->{if(ticket==generation)message("పేపర్ తెరవలేకపోయాం. మళ్లీ ప్రయత్నించండి.\nCould not load the paper. Check internet or feed setup, then retry.\n"+e.getMessage());});}
            finally{temp.delete();}
        });
    }
    private void openCached(){
        if(busy)return;File pdf=new File(getFilesDir(),"paper.pdf");String date=prefs.getString("date","");
        if(!pdf.isFile()){message("ఇంకా పేపర్ సేవ్ కాలేదు.\nOpen today’s paper online first.");return;}
        String label=date+" • Saved copy";
        if(!date.equals(today()))new AlertDialog.Builder(this).setTitle("ఇది పాత పేపర్ / Older issue").setMessage("Saved issue: "+date+". This is not today’s newspaper.").setPositiveButton("Open saved issue",(d,w)->startFile(pdf,label)).setNegativeButton("Cancel",null).show();
        else startFile(pdf,label);
    }
    private void sample(){
        if(busy)return;busy=true;int ticket=++generation;
        worker.execute(()->{try{File f=new File(getCacheDir(),"sample.pdf");try(InputStream in=getAssets().open("sample.pdf");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}launchPdf(f,"SAMPLE • నమూనా మాత్రమే",ticket);}catch(Exception e){runOnUiThread(()->message("Sample could not open"));}});
    }
    private void startFile(File file,String label){busy=true;int ticket=++generation;worker.execute(()->{try{launchPdf(file,label,ticket);}catch(Exception e){runOnUiThread(()->message("Saved paper could not open. Please load today’s paper again."));}});}
    private void launchPdf(File file,String label,int ticket)throws Exception{
        // Verify the local PDF before granting a viewer read-only access.
        try(PdfRenderer check=new PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY))){
            if(check.getPageCount()<1)throw new IOException("PDF has no pages");
        }
        Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".papers")
            .appendPath(file.getName().equals("sample.pdf")?"sample.pdf":"paper.pdf")
            .appendQueryParameter("version",file.getName().equals("sample.pdf")?"sample-v2":prefs.getString("date","")+"-"+prefs.getString("sha","")).build();
        runOnUiThread(()->{
            if(destroyed||ticket!=generation)return;
            message(label);
            Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setClipData(ClipData.newRawUri("SPK Paper",uri));
            Intent drive=new Intent(view).setPackage("com.google.android.apps.docs");
            try{
                startActivity(drive);
                return;
            }catch(ActivityNotFoundException|SecurityException unavailable){
                // Drive may be absent or this version may not expose a PDF viewer.
            }
            try{
                startActivity(Intent.createChooser(view,"Open paper with"));
            }catch(ActivityNotFoundException unavailable){
                new AlertDialog.Builder(this).setTitle("PDF viewer needed")
                    .setMessage("Install Google Drive or another PDF reader, then tap the paper again.")
                    .setPositiveButton("OK",null).show();
            }
        });
    }
    private static void fetch(URL url,OutputStream out,long limit)throws Exception{
        URL current=url;
        for(int redirect=0;redirect<5;redirect++){
            HttpURLConnection c=(HttpURLConnection)current.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setRequestProperty("Cache-Control","no-cache");
            try{int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");current=safeUrl(new URL(current,location).toString());continue;}if(code!=200)throw new IOException("Server returned "+code);
                if(c.getContentLengthLong()>limit)throw new IOException("File too large");
                try(InputStream in=c.getInputStream()){byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("File too large");out.write(b,0,n);}}return;
            }finally{c.disconnect();}
        }throw new IOException("Too many redirects");
    }
    private static String sha(File file)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    @Override protected void onResume(){super.onResume();if(dateLabel!=null)dateLabel.setText("ఈ రోజు  ·  "+today());}
    @Override protected void onDestroy(){destroyed=true;generation++;worker.shutdown();super.onDestroy();}
}
