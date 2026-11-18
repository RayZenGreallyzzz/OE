package game.oe.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Locale;

/**
 * Owner-only Phoenix Builder UV44 embedded as a SEPARATE building mode in OE 0.7.13.
 *
 * Stable Phoenix HTML/JS/GLB libraries are packaged privately at
 * assets/phoenix/assets/ (never published to the public GitHub repository).
 * OE terrain/roads/native GLES rendering and world scene serialization are
 * unchanged. The Phoenix bridge handles editor JSON, original GLB export,
 * and Android Storage Access Framework without requesting broad storage access.
 */
public final class PhoenixBuilderActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private static final String PREFIX="/phoenix/";
    private static final int PICK_FILE=7241,SAVE_FILE=7242;
    private static final long MAX_EXPORT_BYTES=256L*1024*1024;
    private WebView web;
    private android.webkit.ValueCallback<Uri[]> fileChooser;
    private final PhoenixBridge bridge=new PhoenixBridge();
    private File pendingSave;
    private String pendingName,pendingMime;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try(InputStream ignored=getAssets().open("phoenix/assets/index.html")){
            // The CI code-only APK deliberately omits the owner's UV44 assets.
        }catch(IOException unavailable) {
            TextView label=new TextView(this);
            label.setText("Phoenix Builder UV44\n\nВ этой сборке пока нет частных " +
                "веб-ресурсов Phoenix. В полной сборке они вшиты в APK. " +
                "Вернись назад в город Orvetra Engine.");
            label.setTextSize(18);label.setPadding(25,40,25,25);
            setContentView(label);return;
        }
        web=new WebView(this);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Android SAF document picker imports via granted content:// URIs
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                return !isPhoenix(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,
                    WebResourceRequest request) {
                return localResource(request.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> result,
                    FileChooserParams params) {
                if(fileChooser!=null)fileChooser.onReceiveValue(null);
                fileChooser=result;
                try {
                    startActivityForResult(params.createIntent(),PICK_FILE);
                    return true;
                }catch(Exception error) {
                    fileChooser=null;
                    result.onReceiveValue(null);
                    return false;
                }
            }
        });
        web.addJavascriptInterface(bridge,"NativeBridge");
        web.addJavascriptInterface(new Object(){
            @JavascriptInterface public void closeBuilder(){
                runOnUiThread(PhoenixBuilderActivity.this::confirmExit);
            }
        },"OEHost");
        setContentView(web);
        web.loadUrl(ORIGIN+PREFIX+"index.html");
    }

    private static boolean isPhoenix(Uri uri){
        return "https".equals(uri.getScheme())&&
            "appassets.androidplatform.net".equals(uri.getHost())&&
            uri.getPath()!=null&&uri.getPath().startsWith(PREFIX);
    }

    private static String mime(String path) {
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".html"))return "text/html";
        if(lower.endsWith(".css"))return "text/css";
        if(lower.endsWith(".js"))return "text/javascript";
        if(lower.endsWith(".json"))return "application/json";
        if(lower.endsWith(".wasm"))return "application/wasm";
        if(lower.endsWith(".glb"))return "model/gltf-binary";
        if(lower.endsWith(".png"))return "image/png";
        if(lower.endsWith(".jpg")||lower.endsWith(".jpeg"))return "image/jpeg";
        if(lower.endsWith(".webp"))return "image/webp";
        if(lower.endsWith(".svg"))return "image/svg+xml";
        if(lower.endsWith(".woff2"))return "font/woff2";
        return "application/octet-stream";
    }

    private WebResourceResponse localResource(Uri uri) {
        byte[] empty=new byte[0];
        if(!isPhoenix(uri))return new WebResourceResponse("text/plain","UTF-8",
            403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream(empty));
        String relative=uri.getPath().substring(PREFIX.length());
        if(relative.isEmpty()||relative.contains("..")||relative.contains("\\")||
           relative.startsWith("/")||relative.indexOf('\u0000')>=0)
            return new WebResourceResponse("text/plain","UTF-8",400,"Bad Request",
                Collections.emptyMap(),new ByteArrayInputStream(empty));
        try {
            InputStream data=getAssets().open("phoenix/assets/"+relative);
            return new WebResourceResponse(mime(relative),"UTF-8",data);
        }catch(IOException error) {
            return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",
                Collections.emptyMap(),new ByteArrayInputStream(empty));
        }
    }

    private void confirmExit(){
        new AlertDialog.Builder(this)
            .setTitle("Вернуться в город OE?")
            .setMessage("Сохрани здание в Phoenix (JSON) или экспортируй GLB. " +
                "Готовый GLB можно импортировать в городской редактор OE.")
            .setPositiveButton("В город",(dialog,which)->finish())
            .setNegativeButton("Продолжить строительство",null)
            .show();
    }

    @Override public void onBackPressed(){confirmExit();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){
        if(fileChooser!=null){fileChooser.onReceiveValue(null);fileChooser=null;}
        if(web!=null){
            web.removeJavascriptInterface("NativeBridge");
            web.removeJavascriptInterface("OEHost");
            web.destroy();web=null;
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==PICK_FILE){
            if(fileChooser!=null){
                fileChooser.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(result,data));
                fileChooser=null;
            }
            return;
        }
        if(req==SAVE_FILE){
            File source=pendingSave;pendingSave=null;
            if(result!=RESULT_OK||data==null||data.getData()==null){
                if(source!=null)source.delete();
                return;
            }
            Uri uri=data.getData();
            if(source==null||!source.isFile())return;
            new Thread(()->{
                try(InputStream in=new FileInputStream(source);
                    OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IOException("Выбранная папка недоступна");
                    byte[] buf=new byte[65536];
                    for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);
                    out.flush();
                    runOnUiThread(()->Toast.makeText(this,
                        "✅ Файл Phoenix сохранён",Toast.LENGTH_LONG).show());
                }catch(Exception error){
                    runOnUiThread(()->new AlertDialog.Builder(this)
                        .setTitle("Ошибка экспорта Phoenix")
                        .setMessage(error.getMessage())
                        .setPositiveButton("ОК",null).show());
                }finally{source.delete();}
            },"phoenix-saf-export").start();
        }
    }

    // The public API matches the original stable UV44 JS calls:
    // beginSave / appendSaveChunk / finishSave / saveProject /
    // saveManualProject / saveCleanProject / loadCleanProject.
    private final class PhoenixBridge {
        private FileOutputStream stream;
        private File staging;
        private long savedBytes;
        @JavascriptInterface public synchronized boolean beginSave(String name,String type){
            if(stream!=null||pendingSave!=null)return false;
            String safe=name==null?"building.glb":
                name.replaceAll("[^\\p{L}\\p{N}._-]","_");
            if(safe.isEmpty()||safe.length()>110)safe="phoenix_export.glb";
            try {
                File dir=new File(getCacheDir(),"phoenix-export");
                if(!dir.isDirectory()&&!dir.mkdirs())return false;
                staging=File.createTempFile("phoenix-",".part",dir);
                stream=new FileOutputStream(staging);
                savedBytes=0;
                pendingName=safe;
                pendingMime=type==null||type.length()>100?
                    "application/octet-stream":type;
                return true;
            }catch(IOException error){
                stream=null;staging=null;return false;
            }
        }
        @JavascriptInterface public synchronized boolean appendSaveChunk(String base64){
            if(stream==null||base64==null||base64.length()>100000)return false;
            try {
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                if(savedBytes+bytes.length>MAX_EXPORT_BYTES)return false;
                stream.write(bytes);savedBytes+=bytes.length;
                return true;
            }catch(Exception error){return false;}
        }
        @JavascriptInterface public synchronized boolean finishSave(){
            if(stream==null)return false;
            try {stream.getFD().sync();stream.close();stream=null;}
            catch(IOException error){stream=null;return false;}
            pendingSave=staging;staging=null;
            runOnUiThread(()->{
                Intent request=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                request.addCategory(Intent.CATEGORY_OPENABLE);
                request.setType(pendingMime);
                request.putExtra(Intent.EXTRA_TITLE,pendingName);
                try{startActivityForResult(request,SAVE_FILE);}
                catch(Exception error){
                    new AlertDialog.Builder(PhoenixBuilderActivity.this)
                        .setTitle("Не удалось открыть выбор папки")
                        .setMessage(error.getMessage())
                        .setPositiveButton("ОК",null).show();
                }
            });
            return true;
        }
        private File projectFile(){return new File(getFilesDir(),"phoenix_uv44_project.json");}
        @JavascriptInterface public synchronized boolean saveCleanProject(String json){
            if(json==null||json.length()>18*1024*1024)return false;
            File target=projectFile(),tmp=new File(target.getPath()+".part");
            try(FileOutputStream out=new FileOutputStream(tmp)){
                byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>24*1024*1024)return false;
                out.write(bytes);out.getFD().sync();
            }catch(IOException error){tmp.delete();return false;}
            try {
                Files.move(tmp.toPath(),target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                return true;
            }catch(IOException error){tmp.delete();return false;}
        }
        @JavascriptInterface public synchronized String loadCleanProject(){
            File source=projectFile();
            try{
                if(!source.isFile()||source.length()>24*1024*1024)return null;
                return new String(Files.readAllBytes(source.toPath()),
                    StandardCharsets.UTF_8);
            }catch(IOException error){return null;}
        }
        @JavascriptInterface public String loadProject(){
            return loadCleanProject(); // stable Phoenix persistence.js expects this
        }
        @JavascriptInterface public boolean saveProject(String json){
            return saveCleanProject(json);
        }
        @JavascriptInterface public boolean saveManualProject(String json){
            return saveCleanProject(json);
        }
    }
}
