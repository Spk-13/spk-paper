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
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("paper",MODE_PRIVATE); IssueStore.prune(this);
        File[] leftovers=getCacheDir().listFiles();
        if(leftovers!=null)for(File f:leftovers)if(f.getName().matches("(capture|incoming)-[a-f0-9-]+\\.pdf"))f.delete();
        home();
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
        status=text("ఈ రోజు పేపర్ కోసం బటన్ నొక్కండి\nTap below to prepare today’s paper on this phone.",20);content.addView(status);
        open=button("ఈ రోజు పేపర్ తెరవండి\nOpen today’s paper",true);content.addView(open);open.setOnClickListener(v->refreshToday(true));
        Button offline=button("సేవ్ అయిన పేపర్\nRead saved issue",false);content.addView(offline);offline.setOnClickListener(v->openCached());
        content.addView(text("Google Drive PDF viewerలో తెరుచుకుంటుంది.\nOnly the latest prepared issue is kept. Keep this app open while preparing.",17));
    }
    private void message(String s){if(!destroyed){status.setText(s);busy=false;open.setEnabled(true);}}
    private void refreshToday(boolean openViewer){
        if(busy)return;
        File cached=IssueStore.current(this);
        if(cached.isFile() && today().equals(prefs.getString("date",""))){startFile(cached,today());return;}
        startActivityForResult(new Intent(this,CaptureActivity.class),100);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==100){
            if(result==RESULT_OK)startFile(IssueStore.current(this),prefs.getString("date",""));
            else message(data==null?"Preparation cancelled. Saved paper is unchanged.":data.getStringExtra("error"));
        }
    }
    private void openCached(){
        if(busy)return;File pdf=IssueStore.current(this);String date=prefs.getString("date","");
        if(!pdf.isFile()){message("ఇంకా పేపర్ సేవ్ కాలేదు.\nOpen today’s paper online first.");return;}
        String label=date+" • Saved copy";
        if(!date.equals(today()))new AlertDialog.Builder(this).setTitle("ఇది పాత పేపర్ / Older issue").setMessage("Saved issue: "+date+". This is not today’s newspaper.").setPositiveButton("Open saved issue",(d,w)->startFile(pdf,label)).setNegativeButton("Cancel",null).show();
        else startFile(pdf,label);
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
    @Override protected void onResume(){super.onResume();if(dateLabel!=null)dateLabel.setText("ఈ రోజు  ·  "+today());}
    @Override protected void onDestroy(){destroyed=true;generation++;worker.shutdown();super.onDestroy();}
}
