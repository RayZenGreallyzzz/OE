package game.oe.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
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
                    // ACTION_OPEN_DOCUMENT supports PNG/WebP, ZIP and multiple files
                    // across Android document providers. The WebView-generated
                    // intent can be treated as image capture or single-selection
                    // on some tablets even for <input type=file multiple>.
                    Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    boolean image=false;
                    String[] accepted=params.getAcceptTypes();
                    if(accepted!=null){
                        for(String a:accepted){
                            String t=a==null?"":a.toLowerCase(Locale.ROOT);
                            if(t.startsWith("image/")||t.equals(".png")||
                               t.equals(".jpg")||t.equals(".jpeg")||
                               t.equals(".webp")||t.equals(".bmp")||
                               t.equals(".gif")){image=true;break;}
                        }
                    }
                    pick.setType(image?"image/*":"*/*");
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(pick,PICK_FILE);
                    return true;
                }catch(Exception error) {
                    fileChooser=null;
                    result.onReceiveValue(null);
                    Toast.makeText(PhoenixBuilderActivity.this,
                        "Не открыть файлы: "+error.getMessage(),Toast.LENGTH_LONG).show();
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
                Uri[] selected=null;
                if(result==RESULT_OK&&data!=null){
                    ClipData clips=data.getClipData();
                    if(clips!=null&&clips.getItemCount()>0){
                        int n=Math.min(256,clips.getItemCount());
                        selected=new Uri[n];
                        for(int i=0;i<n;i++)selected[i]=clips.getItemAt(i).getUri();
                    }else if(data.getData()!=null){
                        selected=new Uri[]{data.getData()};
                    }
                }
                if(selected==null)
                    selected=WebChromeClient.FileChooserParams.parseResult(result,data);
                fileChooser.onReceiveValue(selected);
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
                final boolean isGlb="model/gltf-binary".equals(pendingMime)||
                    pendingName.toLowerCase(Locale.ROOT).endsWith(".glb");
                if(isGlb){
                    new AlertDialog.Builder(PhoenixBuilderActivity.this)
                        .setTitle("📦 Готовый дом GLB")
                        .setMessage("Куда сохранить построенное здание? "+
                            "«В город OE» сразу вернёт тебя на карту в режим размещения. "+
                            "«На устройство» откроет выбор папки Android.")
                        .setItems(new String[]{"🏰 В город Orvetra Engine",
                                               "📱 На устройство"},(d,choice)->{
                            if(choice==0){
                                File exported=pendingSave;
                                pendingSave=null;
                                if(exported==null||!exported.isFile()||
                                    exported.length()<20){
                                    Toast.makeText(PhoenixBuilderActivity.this,
                                        "GLB не найден",Toast.LENGTH_LONG).show();
                                    return;
                                }
                                // OE's validated GLB import limit is 24 MiB.
                                // Never discard a larger completed Phoenix GLB;
                                // allow the owner to save it to their device.
                                if(exported.length()>24L*1024*1024){
                                    new AlertDialog.Builder(PhoenixBuilderActivity.this)
                                        .setTitle("Дом слишком большой для импорта OE")
                                        .setMessage("Готовый GLB занимает "+
                                            (exported.length()/1048576)+" МБ, "+
                                            "а текущий движок принимает GLB до 24 МБ. "+
                                            "Сохрани дом на устройство и уменьши его "+
                                            "перед размещением в городе.")
                                        .setPositiveButton("📱 На устройство",
                                            (aDialog,which)->startDeviceSave())
                                        .setNegativeButton("Отмена",null).show();
                                    return;
                                }
                                Intent result=new Intent();
                                result.setData(Uri.fromFile(exported));
                                setResult(RESULT_OK,result);
                                finish();
                            }else startDeviceSave();
                        })
                        .setOnCancelListener(d->{
                            if(pendingSave!=null){pendingSave.delete();pendingSave=null;}
                        }).show();
                }else startDeviceSave();
            });
            return true;
        }
        private void startDeviceSave(){
            Intent request=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            request.addCategory(Intent.CATEGORY_OPENABLE);
            request.setType(pendingMime==null?"application/octet-stream":pendingMime);
            request.putExtra(Intent.EXTRA_TITLE,pendingName);
            try{startActivityForResult(request,SAVE_FILE);}
            catch(Exception error){
                new AlertDialog.Builder(PhoenixBuilderActivity.this)
                    .setTitle("Не удалось открыть выбор папки")
                    .setMessage(error.getMessage())
                    .setPositiveButton("ОК",null).show();
            }
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
