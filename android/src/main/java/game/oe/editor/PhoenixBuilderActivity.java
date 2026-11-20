package game.oe.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JsResult;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * UV44 is the ORIGINAL stable builder, hosted as an OE editor tool.
 * No second installed APK, no modified UV44 source, and no second persistent
 * renderer: the OE EditorActivity is paused while this WebView is visible.
 * Finished GLB passes through existing OE GlbReader/native scene pipeline.
 *
 * First milestone: transfer finished static buildings, preserving UV44 edit
 * project privately. Not a claim of shared UV44 player/drone physics yet.
 */
public final class PhoenixBuilderActivity extends Activity {
    static final String EXTRA_GLB_PATH="game.oe.editor.UV44_GLB_PATH";
    private static final int PICK_UV44_APK=1,PICK_UV44_FILE=2;
    private static final long MAX_GLTF=24L*1024*1024;
    private static final long MAX_EXPORT=160L*1024*1024;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WebView builder;
    private LinearLayout panel;
    private TextView status;
    private Button transfer;
    private volatile File latestGlb,latestBackup;
    private String editingModelKey=null;
    private int editingEntityId=0;
    private boolean editableReloadInProgress=false;
    private ValueCallback<Uri[]> fileChoice;
    private final Uv44Bridge bridge=new Uv44Bridge();

    private static int dip(Activity ctx,float n){
        return Math.round(n*ctx.getResources().getDisplayMetrics().density);
    }
    private Button action(String label, View.OnClickListener onclick){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setOnClickListener(onclick);
        return b;
    }
    private void note(String text){
        handler.post(()->{
            if(status!=null)status.setText(text);
            Toast.makeText(this,text,Toast.LENGTH_LONG).show();
        });
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.rgb(14,19,27));
        setContentView(panel);
        editingModelKey=getIntent().getStringExtra(PhoenixHouseLinks.EXTRA_EDIT_MODEL);
        editingEntityId=getIntent().getIntExtra(PhoenixHouseLinks.EXTRA_EDIT_ENTITY,0);
        if(PhoenixHouseLinks.sourceFor(this,editingModelKey)==null){
            editingModelKey=null;editingEntityId=0;
        }
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(26,37,48));
        Button back=action("← OE · город",v->exitBuilder(false));
        toolbar.addView(back,new LinearLayout.LayoutParams(dip(this,116),dip(this,44)));
        transfer=action("🏠 В город",v->exitBuilder(true));
        transfer.setEnabled(false);
        toolbar.addView(transfer,new LinearLayout.LayoutParams(dip(this,123),dip(this,44)));
        if(editingModelKey!=null){
            Button edit=action("✏ Дом OE",v->openExistingHouse());
            toolbar.addView(edit,new LinearLayout.LayoutParams(dip(this,111),dip(this,44)));
        }
        status=new TextView(this);
        status.setTextColor(Color.rgb(215,228,235));
        status.setTextSize(11);
        status.setText("UV44: неизменённая стройка · сначала сохрани проект");
        status.setPadding(dip(this,6),0,dip(this,6),0);
        toolbar.addView(status,new LinearLayout.LayoutParams(0,dip(this,42),1));
        panel.addView(toolbar,new LinearLayout.LayoutParams(-1,dip(this,45)));
        if(PhoenixUv44Bundle.ready(this))startBuilder();
        else promptInstall();
    }
    private void promptInstall(){
        new AlertDialog.Builder(this).setTitle("🏗 Phoenix Builder UV44 внутри OE")
            .setMessage("Выбери оригинальный APK Phoenix Builder UV44. "+
                "Он будет распакован ТОЛЬКО в закрытую память этого OE. "+
                "Приложение Builder отдельно устанавливать не нужно. "+
                "Оригинальные JS и модели не изменяются и не публикуются.")
            .setPositiveButton("Выбрать UV44 APK",(d,w)->{
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try{startActivityForResult(pick,PICK_UV44_APK);}
                catch(Exception ex){note("Не открыть выбор файла: "+ex.getMessage());finish();}
            })
            .setNegativeButton("Назад в OE",(d,w)->finish())
            .setCancelable(false).show();
    }
    private void installApk(Uri apk){
        note("Импортирую ресурсы UV44 в закрытую папку OE…");
        new Thread(()->{
            try{
                PhoenixUv44Bundle.install(this,apk);
                handler.post(this::startBuilder);
            }catch(Exception e){
                handler.post(()->new AlertDialog.Builder(this)
                    .setTitle("UV44 не импортирован")
                    .setMessage("Оригинальный Builder не изменён. "+
                        "Ошибка: "+e.getMessage())
                    .setPositiveButton("Повторить",(d,w)->promptInstall())
                    .setNegativeButton("Назад",(d,w)->finish()).show());
            }
        },"oe-uv44-extract").start();
    }
    private void startBuilder(){
        if(isFinishing()||builder!=null)return;
        if(!PhoenixUv44Bundle.ready(this)){promptInstall();return;}
        builder=new WebView(this);
        WebSettings s=builder.getSettings();
        s.setJavaScriptEnabled(true); // required by the original UV44 Three.js
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        // SAF chooser returns granted content:// URIs for original UV44 imports.
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        builder.setBackgroundColor(Color.BLACK);
        builder.addJavascriptInterface(bridge,"NativeBridge");
        builder.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(
                WebView view,WebResourceRequest req) {
                if(!"GET".equals(req.getMethod()))
                    return PhoenixUv44Bundle.resolve(
                        PhoenixBuilderActivity.this,"https://invalid/");
                if(editingModelKey!=null &&
                    PhoenixHouseLinks.EDIT_ROUTE.equals(req.getUrl().getPath()) &&
                    PhoenixUv44Bundle.ORIGIN.equals(req.getUrl().getScheme()+
                        "://"+req.getUrl().getHost()))
                    return PhoenixHouseLinks.serveHouse(
                        PhoenixBuilderActivity.this,editingModelKey);
                return PhoenixUv44Bundle.resolve(PhoenixBuilderActivity.this,
                    req.getUrl().toString());
            }
            @Override public void onPageFinished(WebView view,String url){
                if(editingModelKey!=null)
                    note("UV44 загружен · нажми «✏ Дом OE», чтобы открыть "+
                        "редактируемый дом из общей карты");
            }
            @Override public boolean shouldOverrideUrlLoading(
                WebView view,WebResourceRequest req) {
                return !req.getUrl().toString()
                    .startsWith(PhoenixUv44Bundle.ORIGIN+"/");
            }
        });
        builder.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(
                    WebView web,String origin,String text,JsResult result){
                new AlertDialog.Builder(PhoenixBuilderActivity.this)
                    .setTitle("Подтвердить действие Builder UV44")
                    .setMessage(text)
                    .setPositiveButton("Да",(d,w)->result.confirm())
                    .setNegativeButton("Отмена",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
            @Override public boolean onShowFileChooser(WebView web,
                ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(fileChoice!=null)fileChoice.onReceiveValue(null);
                fileChoice=callback;
                try{
                    Intent intent=params.createIntent();
                    startActivityForResult(intent,PICK_UV44_FILE);
                }catch(Exception e){
                    fileChoice=null;callback.onReceiveValue(null);
                    note("Не открыть файл UV44: "+e.getMessage());
                }
                return true;
            }
        });
        panel.addView(builder,new LinearLayout.LayoutParams(-1,0,1f));
        builder.loadUrl(PhoenixUv44Bundle.ORIGIN+"/");
        note("UV44 готов: строй, сохраняй проект и нажми 📦 GLB в Builder");
    }
    private void openExistingHouse(){
        if(builder==null||editingModelKey==null||editableReloadInProgress)return;
        if(PhoenixHouseLinks.sourceFor(this,editingModelKey)==null){
            note("Нет сохранённой редактируемой копии этого дома");
            return;
        }
        editableReloadInProgress=true;
        // Original UV44 importBackup(File) is used unchanged. Fetch is served
        // from OE private storage, not remote HTTP; no 100 MB JS bridge string.
        final String javascript="(async()=>{"+
            "if(!window.PhoenixClean||typeof window.PhoenixClean.importBackup!=='function')"+
            "throw Error('Редактор UV44 ещё не готов');"+
            "const response=await fetch('"+PhoenixHouseLinks.EDIT_ROUTE+"',{cache:'no-store'});"+
            "if(!response.ok)throw Error('Не найден исходный проект дома');"+
            "const bytes=await response.blob();"+
            "const file=new File([bytes],'OE_House.phoenix.json',"+
            "{type:'application/json'});"+
            "await window.PhoenixClean.importBackup(file);"+
            "return 'done';})().catch(error=>'error: '+String(error?.message||error))";
        builder.evaluateJavascript(javascript,result->{
            editableReloadInProgress=false;
            if(result!=null&&result.contains("error:"))
                note("Проверь загрузку дома UV44: "+result);
            else note("Проект запрошен · подтверди замену текущего дома "+
                      "в исходном Builder UV44");
        });
    }

    private void exitBuilder(boolean importFinished){
        File glb=latestGlb;
        if(importFinished){
            if(glb==null||!glb.isFile()||glb.length()<20||glb.length()>MAX_GLTF){
                note("Сначала экспортируй GLB из UV44 · лимит OE 24 МБ");
                return;
            }
            if(latestBackup==null||!latestBackup.isFile()){
                new AlertDialog.Builder(this).setTitle("Нет редактируемой копии дома")
                    .setMessage("В UV44 сначала нажми «💾 Сохранить проект "+
                        "(.phoenix.json)», затем ещё раз «📦 GLB». "+
                        "Иначе дом попадёт в OE как обычная GLB-модель "+
                        "и восстановить его строительные детали будет нельзя.")
                    .setPositiveButton("Продолжить стройку",null)
                    .setNegativeButton("Передать только GLB",(d,w)->{
                        Intent only=new Intent();
                        only.putExtra(EXTRA_GLB_PATH,glb.getAbsolutePath());
                        setResult(RESULT_OK,only);
                        finish();
                    }).show();
                return;
            }
            Intent result=new Intent();
            result.putExtra(EXTRA_GLB_PATH,glb.getAbsolutePath());
            if(latestBackup!=null&&latestBackup.isFile())
                result.putExtra(PhoenixHouseLinks.EXTRA_BACKUP_PATH,
                    latestBackup.getAbsolutePath());
            if(editingModelKey!=null&&editingEntityId>0){
                result.putExtra(PhoenixHouseLinks.EXTRA_EDIT_MODEL,editingModelKey);
                result.putExtra(PhoenixHouseLinks.EXTRA_EDIT_ENTITY,editingEntityId);
            }
            setResult(RESULT_OK,result);
            finish();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Вернуться в город OE?")
            .setMessage("Перед возвратом сохрани редактируемый дом "+
                "кнопкой 💾 Сохранить в UV44. Несохранённые изменения "+
                "строительства могут потеряться.")
            .setPositiveButton("Вернуться",(d,w)->finish())
            .setNegativeButton("Остаться",null).show();
    }
    @Override public void onBackPressed(){exitBuilder(false);}
    @Override protected void onResume(){
        super.onResume();
        if(builder!=null)builder.onResume();
    }
    @Override protected void onPause(){
        if(builder!=null)builder.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(fileChoice!=null){fileChoice.onReceiveValue(null);fileChoice=null;}
        if(builder!=null){
            builder.removeJavascriptInterface("NativeBridge");
            builder.stopLoading();
            panel.removeView(builder);
            builder.destroy();
            builder=null;
        }
        bridge.abort();
        super.onDestroy();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_UV44_APK){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)
                installApk(data.getData());
            else finish();
            return;
        }
        if(request==PICK_UV44_FILE){
            ValueCallback<Uri[]> callback=fileChoice;
            fileChoice=null;
            if(callback!=null)
                callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(
                    result,data));
        }
    }
    private File files() throws IOException {
        File out=new File(getFilesDir(),"uv44_files");
        if(!out.isDirectory()&&!out.mkdirs())
            throw new IOException("Не создать папку UV44 проекта");
        return out;
    }
    private static boolean validFileName(String name){
        if(name==null||name.length()<1||name.length()>90)return false;
        if(name.equals(".")||name.equals(".."))return false;
        for(int i=0;i<name.length();i++){
            char c=name.charAt(i);
            if(c=='/'||c=='\\'||c<32||c==127||c=='%'||c==':')return false;
        }
        return true;
    }

    /**
     * Names and method signatures EXACTLY match stable UV44 NativeBridge.
     * Chunked saves avoid 100 MB JavaScript base64 allocation.
     * JS interface called from the WebView bridge thread; all I/O serialised.
     */
    private final class Uv44Bridge {
        private File part,target;
        private OutputStream output;
        private long bytes=0;
        @JavascriptInterface public boolean saveCleanProject(String json){
            return writeProject(json,"phoenix44_clean_project.json");
        }
        @JavascriptInterface public String loadCleanProject(){
            return readProject("phoenix44_clean_project.json");
        }
        @JavascriptInterface public boolean saveProject(String json){
            return writeProject(json,"phoenix44_legacy_project.json");
        }
        @JavascriptInterface public String loadProject(){
            return readProject("phoenix44_legacy_project.json");
        }
        @JavascriptInterface public boolean saveManualProject(String json){
            return writeProject(json,"phoenix44_manual_project.json");
        }
        private boolean writeProject(String json,String name){
            if(json==null||json.length()>90_000_000)return false;
            try{
                AtomicFile atomic=new AtomicFile(new File(files(),name));
                FileOutputStream out=atomic.startWrite();
                try{
                    byte[] utf8=json.getBytes(StandardCharsets.UTF_8);
                    if(utf8.length>MAX_EXPORT)throw new IOException("Проект UV44 слишком большой");
                    out.write(utf8);
                    atomic.finishWrite(out);return true;
                }catch(Exception err){atomic.failWrite(out);return false;}
            }catch(Exception ex){return false;}
        }
        private String readProject(String name){
            try{
                File file=new File(files(),name);
                if(!file.isFile()||file.length()<1||file.length()>MAX_EXPORT)return null;
                return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            }catch(Exception e){return null;}
        }
        @JavascriptInterface public synchronized boolean beginSave(String name,String mime){
            if(output!=null||!validFileName(name))return false;
            try{
                target=new File(files(),name);
                part=new File(files(),name+".part");
                output=new BufferedOutputStream(new FileOutputStream(part));
                bytes=0;
                return true;
            }catch(Exception ex){abort();return false;}
        }
        @JavascriptInterface public synchronized boolean appendSaveChunk(String data){
            if(output==null||data==null||data.length()>100000)return false;
            try{
                byte[] chunk=Base64.decode(data,Base64.DEFAULT);
                if(bytes+chunk.length>MAX_EXPORT){abort();return false;}
                output.write(chunk);bytes+=chunk.length;
                return true;
            }catch(Exception e){abort();return false;}
        }
        @JavascriptInterface public synchronized boolean finishSave(){
            if(output==null||part==null||target==null)return false;
            File ready=target;
            try{
                output.close();output=null;
                if(bytes==0)throw new IOException("Пустой экспорт UV44");
                Files.move(part.toPath(),ready.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                part=null;target=null;bytes=0;
                if(ready.getName().toLowerCase().endsWith(".phoenix.json")){
                    latestBackup=ready;
                    latestGlb=null; // Save BEFORE a new GLB so it matches source.
                    handler.post(()->{
                        transfer.setEnabled(false);
                        status.setText("💾 UV44-проект готов · теперь экспортируй 📦 GLB");
                    });
                }
                if(ready.getName().toLowerCase().endsWith(".glb")){
                    try(InputStream in=new FileInputStream(ready)){
                        byte[] magic=new byte[4];
                        if(in.read(magic)!=4||magic[0]!='g'||magic[1]!='l'||
                           magic[2]!='T'||magic[3]!='F')
                            throw new IOException("Экспорт не GLB");
                    }
                    if(ready.length()>MAX_GLTF)
                        note("GLB сохранён, но больше 24 МБ для OE. "+
                            "Уменьши качество текстур экспорта до 1024.");
                    else{
                        latestGlb=ready;
                        handler.post(()->{
                            transfer.setEnabled(true);
                            status.setText("🏠 GLB готов · нажми «В город»");
                        });
                    }
                }else note("Проект UV44 сохранён внутри OE: "+ready.getName());
                return true;
            }catch(Exception ex){abort();note("Ошибка сохранения UV44: "+ex.getMessage());return false;}
        }
        synchronized void abort(){
            try{if(output!=null)output.close();}catch(Exception ignored){}
            output=null;
            if(part!=null)part.delete();
            part=null;target=null;bytes=0;
        }
    }
}
