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
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
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
import java.nio.file.AtomicMoveNotSupportedException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Locale;

/**
 * Owner-only Phoenix UV44 + UV56 live build/terrain studio embedded in OE R5.7.
 *
 * Stable Phoenix HTML/JS/GLB libraries are packaged privately at
 * assets/phoenix/assets/ (never published to the public GitHub repository).
 * R5.7 roads/lakes/walker/native GLES renderer and v1-v8 scene format stay
 * UNCHANGED. The Phoenix bridge handles its own JSON, original GLB export,
 * and Android Storage Access Framework without requesting broad storage access.
 */
public final class PhoenixBuilderActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    // Two modes of ONE OE app. Original UV44 assets are NEVER rewritten.
    public static final String EXTRA_MODE="oe_phoenix_mode";
    public static final String MODE_BUILD="build";
    public static final String MODE_LANDSCAPE="landscape";
    // Only a guarded recovery option uses the exact, original UV44 scene.
    // Both BUILD and LANDSCAPE use UV56's existing ONE live Three.js canvas,
    // where all original UV44 library/model bytes are served from the base pack.
    private static final String MODE_ORIGINAL_UV44="original_uv44";
    private static final String PREFIX="/phoenix/";
    private static final String LANDSCAPE_PREFIX="/phoenix-landscape/";
    private boolean landscapeMode=true;
    private String requestedMode=MODE_BUILD;
    private static final int PICK_FILE=7241,SAVE_FILE=7242;
    private static final long MAX_EXPORT_BYTES=1024L*1024*1024;
    private static final long DISK_RESERVE=16L*1024*1024;
    private static final int SAVE_HISTORY=3;
    private static final int MAX_PROJECT_JSON_BYTES=48*1024*1024;
    private WebView web;
    private android.webkit.ValueCallback<Uri[]> fileChooser;
    private final PhoenixBridge bridge=new PhoenixBridge();
    private File pendingSave;
    private String pendingName,pendingMime;
    private boolean fileCopyInProgress=false;
    private static final int VERIFY_BUFFER_BYTES=65536;
    private static final class ReadyFile {
        final File file;
        final String name,mime;
        ReadyFile(File file,String name,String mime){
            this.file=file;this.name=name;this.mime=mime;
        }
    }
    private final ArrayDeque<ReadyFile> pendingQueue=new ArrayDeque<>();
    private void dropPending(){
        if(pendingSave!=null)pendingSave.delete();
        pendingSave=null;pendingName=null;pendingMime=null;
        showNextSave();
    }
    private void showNextSave(){
        if(pendingSave!=null||fileCopyInProgress)return;
        ReadyFile next=pendingQueue.poll();
        if(next==null)return;
        pendingSave=next.file;pendingName=next.name;pendingMime=next.mime;
        if(next.name.toLowerCase(Locale.ROOT).endsWith(".glb")||
           "model/gltf-binary".equals(next.mime)){
            new AlertDialog.Builder(this)
                .setTitle("📦 Готовый дом GLB")
                .setMessage("Готовый GLB сохранён во временной папке. Выбери действие:")
                .setPositiveButton("🏰 В город OE",(dialog,choice)->{
                    // The OE city validates actual GLB geometry/texture memory.
                    // Never reject 26+ MiB finished houses at the old 24 MiB
                    // UI-only ceiling. Files beyond current in-memory decode
                    // capacity can still be exported unchanged to Android.
                    if(next.file.length()>GlbReader.MAX_BYTES){
                        new AlertDialog.Builder(this)
                            .setTitle("Большой оригинальный GLB")
                            .setMessage("Файл "+(next.file.length()/1048576)+
                                " МиБ. Город пока может загрузить за один раз до "+
                                (GlbReader.MAX_BYTES/(1024*1024))+
                                " МиБ. Большие замки потребуют потоковой загрузки. "+
                                "Оригинальный дом не изменён: сохрани GLB на устройство.")
                            .setPositiveButton("📱 Сохранить GLB",(d,w)->startDeviceSave())
                            .setNegativeButton("Остаться в стройке",(d,w)->dropPending()).show();
                        return;
                    }
                    pendingSave=null;pendingName=null;pendingMime=null;
                    Intent data=new Intent();
                    data.setData(Uri.fromFile(next.file));
                    setResult(RESULT_OK,data);
                    finish();
                })
                .setNeutralButton("📱 На устройство",(dialog,which)->startDeviceSave())
                .setNegativeButton("Отмена",(dialog,which)->dropPending())
                .setOnCancelListener(d->dropPending()).show();
        }else startDeviceSave();
    }
    private void startDeviceSave(){
        Intent request=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE);
        request.setType(pendingMime==null?"application/octet-stream":pendingMime);
        request.putExtra(Intent.EXTRA_TITLE,pendingName);
        try{startActivityForResult(request,SAVE_FILE);}
        catch(Exception error){
            new AlertDialog.Builder(this).setTitle("Не удалось открыть папку")
                .setMessage(error.getMessage())
                .setPositiveButton("Повторить",(d,w)->startDeviceSave())
                .setNegativeButton("Отмена",(d,w)->dropPending())
                .show();
        }
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        String mode=getIntent().getStringExtra(EXTRA_MODE);
        requestedMode=MODE_LANDSCAPE.equals(mode)?MODE_LANDSCAPE:MODE_BUILD;
        landscapeMode=!MODE_ORIGINAL_UV44.equals(mode);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // Private packaging overlays ONLY UV56's seven changed/new web files.
        // The complete, byte-identical UV44 bundle remains the immutable base.
        String entry=landscapeMode?"phoenix-uv56-overlay/assets/index.html":
            "phoenix/assets/index.html";
        try(InputStream base=getAssets().open("phoenix/assets/index.html");
            InputStream page=getAssets().open(entry)){
            // The CI code-only APK deliberately omits the owner's UV44 and UV56
            // private assets. Both must be staged from owner APKs to launch.
        }catch(IOException unavailable) {
            TextView label=new TextView(this);
            label.setText("Phoenix Builder UV44 / UV56\n\nВ этой сборке пока нет частных " +
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
            @Override public void onPageFinished(WebView view,String url){
                // Mode selection happens within ONE UV56 Three.js scene; no
                // second application/canvas and no reload on tool switching.
                String page=ORIGIN+(landscapeMode?LANDSCAPE_PREFIX:PREFIX)+"index.html";
                if(!page.equals(url))return;
                String tab=(landscapeMode&&MODE_LANDSCAPE.equals(requestedMode))?
                    "modeLandscape":"modeBuild";
                String js="(async function(){try{"+
                    "const editor=window.PhoenixClean;"+
                    "if(!editor||typeof editor.load!=='function')return;"+
                    "await editor.load();"+
                    "const tab=document.getElementById('"+tab+"');"+
                    "if(tab)tab.click();"+
                    "}catch(error){console.error('OE startup restore',error);}})();";
                view.evaluateJavascript(js,null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                return !isPhoenix(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,
                    WebResourceRequest request) {
                // Only intercept our own bundled HTTPS assets. Everything else,
                // especially Android content:// document-provider streams from
                // <input type=file multiple>, must be handled by WebView.
                // Returning a 403 here made File.arrayBuffer()/FileReader
                // fail after the system picker had already returned images.
                return isPhoenix(request.getUrl())?
                    localResource(request.getUrl()):null;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage message){
                // Original standalone UV44 logs individual failed imports
                // even when the batch UI only shows "0 / N". Preserve those
                // diagnostics on the device (without changing Phoenix JS).
                if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR||
                   message.messageLevel()==ConsoleMessage.MessageLevel.WARNING)
                    android.util.Log.w("OE-Phoenix",
                        "JS ("+message.lineNumber()+"): "+message.message());
                return true;
            }
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
                    boolean png=false,webp=false,unrestricted=false;
                    String[] accepted=params.getAcceptTypes();
                    if(accepted!=null){
                        for(String a:accepted){
                            String t=a==null?"":a.toLowerCase(Locale.ROOT).trim();
                            if(t.startsWith("image/")||t.equals(".png")||
                               t.equals(".jpg")||t.equals(".jpeg")||
                               t.equals(".webp")||t.equals(".bmp")||
                               t.equals(".gif"))image=true;
                            if(t.equals(".png")||t.equals("image/png"))png=true;
                            if(t.equals(".webp")||t.equals("image/webp"))webp=true;
                            if(t.equals("image/*")||t.equals(".jpg")||
                               t.equals(".jpeg")||t.equals("image/jpeg")||
                               t.equals(".bmp")||t.equals(".gif"))unrestricted=true;
                        }
                    }
                    pick.setType(image?"image/*":"*/*");
                    // Graffiti accepts only transparent PNG/WebP. Keep JPG
                    // out of that picker instead of selecting files that
                    // Phoenix would then silently reject in batch mode.
                    if(image&&png&&webp&&!unrestricted)
                        pick.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"image/png","image/webp"});
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
        // A single Android Activity/WebView hosts both modes. Switching mode
        // reopens the shared Phoenix scene after a verified atomic JSON save.
        FrameLayout container=new FrameLayout(this);
        container.addView(web,new FrameLayout.LayoutParams(-1,-1));
        Button modeButton=new Button(this);
        modeButton.setText("OE ▾");
        modeButton.setTextSize(11f);
        modeButton.setAllCaps(false);
        modeButton.setContentDescription("Режимы OE: стройка, ландшафт, город");
        modeButton.setOnClickListener(v->showModeMenu());
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(64,48,
            Gravity.TOP|Gravity.RIGHT);
        mp.topMargin=2;mp.rightMargin=2;
        container.addView(modeButton,mp);
        setContentView(container);
        web.loadUrl(ORIGIN+(landscapeMode?LANDSCAPE_PREFIX:PREFIX)+"index.html");
    }

    private void showModeMenu(){
        String[] items={"🏗 Стройка (общая сцена)",
            "🌿 Ландшафт и дороги (та же сцена)",
            "🛟 Оригинал UV44 — только резервный режим",
            "🏙 Сохранить и вернуться в город OE",
            "🛟 Копии проекта"};
        new AlertDialog.Builder(this).setTitle("Единое рабочее пространство OE")
            .setItems(items,(dialog,which)->{
                if(which==0)requestModeSwitch(MODE_BUILD);
                else if(which==1)requestModeSwitch(MODE_LANDSCAPE);
                else if(which==2)requestModeSwitch(MODE_ORIGINAL_UV44);
                else if(which==3)confirmExit();
                else showProjectRecovery();
            }).show();
    }
    private void selectTabInLiveScene(String mode){
        if(web==null||!landscapeMode)return;
        requestedMode=mode;
        String id=MODE_LANDSCAPE.equals(mode)?"modeLandscape":"modeBuild";
        web.evaluateJavascript("(function(){const el=document.getElementById('"+
            id+"');if(el){el.click();return true}return false})()",result->{
                if(!"true".equals(result))
                    Toast.makeText(this,"Вкладка редактора ещё не готова",
                        Toast.LENGTH_SHORT).show();
            });
    }
    private void requestModeSwitch(String mode){
        if(web==null)return;
        if(landscapeMode&&!MODE_ORIGINAL_UV44.equals(mode)){
            // UV56 has native modeBuild and modeLandscape inside the SAME
            // CleanBuilder instance. A tab switch must not serialize/reload.
            selectTabInLiveScene(mode);
            return;
        }
        if(!landscapeMode&&MODE_ORIGINAL_UV44.equals(mode))return;
        if(pendingSave!=null||fileCopyInProgress||!pendingQueue.isEmpty()){
            Toast.makeText(this,"Заверши экспорт перед переключением",
                Toast.LENGTH_LONG).show();return;
        }
        new AlertDialog.Builder(this).setTitle("Сохранить перед сменой редактора?")
            .setMessage("Оригинальный UV44 открыт только для сравнения и "+
                "восстановления. Рабочая стройка и ландшафт уже в одной сцене. "+
                "Переключение сохранит общий JSON с резервными копиями.")
            .setPositiveButton("💾 Сохранить и перейти",(d,w)->
                saveCurrentProject(()->{
                    landscapeMode=!MODE_ORIGINAL_UV44.equals(mode);
                    requestedMode=MODE_LANDSCAPE.equals(mode)?
                        MODE_LANDSCAPE:MODE_BUILD;
                    web.loadUrl(ORIGIN+(landscapeMode?LANDSCAPE_PREFIX:PREFIX)+
                        "index.html");
                }))
            .setNegativeButton("Остаться",null).show();
    }
    private void saveCurrentProject(Runnable onSaved){
        if(web==null)return;
        final String js="(function(){try{"+
            "if(!window.PhoenixClean||typeof window.PhoenixClean.project!=='function')"+
                "return 'not_ready';"+
            "const json=JSON.stringify(window.PhoenixClean.project());"+
            "return window.NativeBridge.saveCleanProject(json)?'ok':"+
                "('save_failed: '+window.NativeBridge.getLastSaveError());"+
            "}catch(e){return 'exception: '+String(e.message||e);}})();";
        web.evaluateJavascript(js,result->{
            if(!"\"ok\"".equals(result)){
                new AlertDialog.Builder(this).setTitle("Проект не сохранён")
                    .setMessage("Данные оставлены в текущем редакторе. "+
                        "Переход отменён. Причина: "+result)
                    .setPositiveButton("Понятно",null).show();
                return;
            }
            onSaved.run();
        });
    }
    private static boolean isPhoenix(Uri uri){
        return "https".equals(uri.getScheme())&&
            "appassets.androidplatform.net".equals(uri.getHost())&&
            uri.getPath()!=null&&
            (uri.getPath().startsWith(PREFIX)||
             uri.getPath().startsWith(LANDSCAPE_PREFIX));
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
        boolean overlay=uri.getPath().startsWith(LANDSCAPE_PREFIX);
        String relative=uri.getPath().substring(
            overlay?LANDSCAPE_PREFIX.length():PREFIX.length());
        if(relative.isEmpty()||relative.contains("..")||relative.contains("\\")||
           relative.startsWith("/")||relative.indexOf('\u0000')>=0)
            return new WebResourceResponse("text/plain","UTF-8",400,"Bad Request",
                Collections.emptyMap(),new ByteArrayInputStream(empty));
        if(overlay){
            try{
                // The UV56 variant contains only the small owned delta:
                // index, CSS, clean app, exporter and three landscape modules.
                InputStream update=getAssets().open(
                    "phoenix-uv56-overlay/assets/"+relative);
                return new WebResourceResponse(mime(relative),"UTF-8",update);
            }catch(IOException unchanged){
                // All 1911+ identical library/model files are reused from
                // the pristine UV44 package, no duplicate paid packs.
            }
        }
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
            .setTitle("Сохранить стройку и ландшафт перед выходом?")
            .setMessage("Один проект хранит здания и землю UV56. "+
                "Сохраним его с резервными копиями до возвращения в город OE. "+
                "Полный JSON с изображениями и GLB экспортируется из вкладки "+
                "Файлы в редакторе.")
            .setPositiveButton("💾 Сохранить и в город",
                (dialog,which)->saveCurrentProject(this::finish))
            .setNeutralButton("🛟 Копии", (dialog,which)->showProjectRecovery())
            .setNegativeButton("Продолжить",null)
            .show();
    }

    // Accessible from Android Back -> 🛟 Копии. A JSON snapshot stores
    // object placements and material references, NOT the contents of IndexedDB
    // textures/GLBs. Full portable backups are exported in Phoenix's Files tab.
    private void showProjectRecovery(){
        final java.util.ArrayList<File> files=new java.util.ArrayList<>();
        final java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        File current=bridge.projectFile();
        if(bridge.readValidProject(current)!=null){
            files.add(current);labels.add("💾 Текущий JSON");
        }
        for(int i=1;i<=SAVE_HISTORY;i++){
            File older=bridge.projectBackup(i);
            if(bridge.readValidProject(older)==null)continue;
            files.add(older);labels.add("⏪ Копия №"+i);
        }
        if(files.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Копии проекта")
                .setMessage("Внутренних JSON-копий пока нет. "+
                    "Сохрани проект кнопкой 💾 в Phoenix.")
                .setPositiveButton("Хорошо",null).show();
            return;
        }
        String[] items=new String[files.size()];
        for(int i=0;i<files.size();i++){
            File file=files.get(i);
            String date=new SimpleDateFormat("dd.MM HH:mm",
                Locale.getDefault()).format(new Date(file.lastModified()));
            items[i]=labels.get(i)+" · "+date;
        }
        new AlertDialog.Builder(this)
            .setTitle("🛟 Копии Phoenix — JSON без текстур")
            .setItems(items,(dialog,which)->{
                File selected=files.get(which);
                String label=labels.get(which);
                new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setItems(new String[]{
                        "📱 Экспортировать JSON на устройство",
                        "⏪ Восстановить эту версию проекта"
                    },(d,action)->{
                        if(action==0)exportRecoveryJson(selected);
                        else confirmRestoreJson(selected);
                    }).show();
            })
            .setNegativeButton("Закрыть",null).show();
    }
    private void exportRecoveryJson(File source){
        new Thread(()->{
            try{
                File folder=new File(getCacheDir(),"phoenix-export");
                if(!folder.isDirectory()&&!folder.mkdirs())
                    throw new IOException("Нет папки для экспорта");
                File copy=File.createTempFile("phoenix-recovery-",".json",folder);
                Files.copy(source.toPath(),copy.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                runOnUiThread(()->{
                    pendingQueue.add(new ReadyFile(copy,
                        "PhoenixBuilder_Recovery.phoenix.json","application/json"));
                    showNextSave();
                });
            }catch(Exception error){
                runOnUiThread(()->Toast.makeText(this,
                    "Не удалось экспортировать: "+error.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        },"phoenix-recovery-export").start();
    }
    private void confirmRestoreJson(File source){
        new AlertDialog.Builder(this)
            .setTitle("Восстановить прежнюю постройку?")
            .setMessage("Несохранённые изменения будут потеряны. "+
                "Текущий файл останется в истории копий. "+
                "Текстуры должны оставаться во внутренней библиотеке Phoenix.")
            .setPositiveButton("Восстановить",(dialog,which)->{
                new Thread(()->{
                    try{
                        String json=bridge.readValidProject(source);
                        if(json==null||!bridge.saveCleanProject(json))
                            throw new IOException("Копия повреждена или нет места");
                        runOnUiThread(()->{
                            if(web!=null)web.reload();
                            Toast.makeText(this,"Копия восстановлена. "+
                                "Открой проект в Phoenix.",Toast.LENGTH_LONG).show();
                        });
                    }catch(Exception error){
                        runOnUiThread(()->Toast.makeText(this,
                            "Ошибка восстановления: "+error.getMessage(),
                            Toast.LENGTH_LONG).show());
                    }
                },"phoenix-recovery-restore").start();
            })
            .setNegativeButton("Отмена",null).show();
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
                if(selected!=null&&selected.length>0){
                    final int count=selected.length;
                    Toast.makeText(this,
                        "📁 Android передал "+count+
                        (count==1?" файл":" файлов")+
                        ". Проверь итог загрузки в папке Phoenix.",
                        Toast.LENGTH_SHORT).show();
                }
                fileChooser.onReceiveValue(selected);
                fileChooser=null;
            }
            return;
        }
        if(req==SAVE_FILE){
            final File source=pendingSave;
            final String name=pendingName,mime=pendingMime;
            pendingSave=null;pendingName=null;pendingMime=null;
            if(source==null||!source.isFile()){
                showNextSave();return;
            }
            if(result!=RESULT_OK||data==null||data.getData()==null){
                // Cancellation is not a corrupted export, only a cancelled
                // folder selection. Ask before discarding the finished bytes.
                pendingSave=source;pendingName=name;pendingMime=mime;
                new AlertDialog.Builder(this)
                    .setTitle("Сохранение отменено")
                    .setMessage("Файл Phoenix ещё готов к экспорту. Повторить выбор папки?")
                    .setPositiveButton("Повторить",(d,w)->startDeviceSave())
                    .setNegativeButton("Удалить временную копию",(d,w)->dropPending())
                    .show();
                return;
            }
            final Uri uri=data.getData();
            fileCopyInProgress=true;
            new Thread(()->{
                try{
                    try(InputStream in=new FileInputStream(source);
                        OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                        if(out==null)throw new IOException("Выбранная папка недоступна");
                        byte[] buf=new byte[VERIFY_BUFFER_BYTES];
                        for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);
                        out.flush();
                    }
                    // A successful OutputStream.close() does NOT guarantee the
                    // Android document provider wrote the whole file. Read it
                    // back and verify the entire stream before deleting staging.
                    verifyDocumentCopy(source,uri);
                    source.delete();
                    runOnUiThread(()->{
                        fileCopyInProgress=false;
                        Toast.makeText(this,
                            "✅ Phoenix: файл сохранён и проверен",
                            Toast.LENGTH_LONG).show();
                        showNextSave();
                    });
                }catch(Exception error){
                    // Never delete a successfully prepared GLB/JSON when a
                    // document provider rejects a write. Offer retry.
                    runOnUiThread(()->{
                        fileCopyInProgress=false;
                        pendingSave=source;pendingName=name;pendingMime=mime;
                        new AlertDialog.Builder(this)
                            .setTitle("Ошибка копирования Phoenix")
                            .setMessage("Оригинальный файл сохранён временно. "+
                                "Выбери другую папку. Причина: "+error.getMessage())
                            .setPositiveButton("Повторить",(d,w)->startDeviceSave())
                            .setNegativeButton("Отмена",(d,w)->dropPending())
                            .show();
                    });
                }
            },"phoenix-saf-export").start();
        }
    }

    private void verifyDocumentCopy(File source,Uri destination) throws IOException {
        PhoenixFileVerifier.verify(source,
            getContentResolver().openInputStream(destination));
    }

    // The public API matches the original stable UV44 JS calls:
    // beginSave / appendSaveChunk / finishSave / saveProject /
    // saveManualProject / saveCleanProject / loadCleanProject.
    private final class PhoenixBridge {
        private FileOutputStream stream;
        private File staging;
        private long savedBytes;
        private String stagingName,stagingMime;
        private String lastError="";
        @JavascriptInterface public synchronized String getLastSaveError(){return lastError;}
        @JavascriptInterface public synchronized boolean abortSave(){
            if(stream!=null){try{stream.close();}catch(IOException ignored){}stream=null;}
            if(staging!=null){staging.delete();staging=null;}
            stagingName=null;stagingMime=null;savedBytes=0;
            return true;
        }
        @JavascriptInterface public synchronized boolean beginSave(String name,String type){
            // The previous JS write may have failed before finishSave().
            // Only the incomplete staging stream is discarded, never a
            // completed export already waiting in the Android file picker.
            if(stream!=null)abortSave();
            lastError="";
            String safe=name==null?"building.glb":
                name.replaceAll("[^\\p{L}\\p{N}._-]","_");
            if(safe.isEmpty()||safe.length()>110)safe="phoenix_export.glb";
            try {
                File dir=new File(getCacheDir(),"phoenix-export");
                if(!dir.isDirectory()&&!dir.mkdirs())return false;
                staging=File.createTempFile("phoenix-",".part",dir);
                stream=new FileOutputStream(staging);
                savedBytes=0;
                stagingName=safe;
                stagingMime=type==null||type.length()>100?
                    "application/octet-stream":type;
                return true;
            }catch(IOException error){
                lastError="Не удалось создать файл: "+error.getMessage();
                abortSave();return false;
            }
        }
        @JavascriptInterface public synchronized boolean appendSaveChunk(String base64){
            if(stream==null||base64==null||base64.length()>100000){
                lastError="Поток экспорта закрыт или некорректный фрагмент";
                abortSave();return false;
            }
            try{
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                if(savedBytes+bytes.length>MAX_EXPORT_BYTES){
                    lastError="Размер резервной копии превысил 1 ГБ";
                    abortSave();return false;
                }
                long available=staging.getUsableSpace();
                if(available>0&&available<bytes.length+DISK_RESERVE){
                    lastError="Недостаточно свободного места на планшете";
                    abortSave();return false;
                }
                stream.write(bytes);savedBytes+=bytes.length;
                return true;
            }catch(Exception error){
                lastError="Ошибка записи: "+error.getMessage();
                abortSave();return false;
            }
        }
        @JavascriptInterface public synchronized boolean finishSave(){
            if(stream==null)return false;
            try{stream.getFD().sync();stream.close();stream=null;}
            catch(IOException error){
                lastError="Не завершена запись: "+error.getMessage();
                abortSave();return false;
            }
            final ReadyFile ready=new ReadyFile(staging,stagingName,stagingMime);
            staging=null;stagingName=null;stagingMime=null;savedBytes=0;
            runOnUiThread(()->{
                pendingQueue.offer(ready);
                showNextSave();
            });
            return true;
        }
        private File projectFile(){
            return new File(getFilesDir(),"phoenix_uv44_project.json");
        }
        private File projectBackup(int slot){
            return new File(getFilesDir(),"phoenix_uv44_project.bak"+slot+".json");
        }
        private boolean validProjectJson(String json){
            if(json==null||json.length()<10)return false;
            try{
                JSONObject doc=new JSONObject(json);
                return doc.optInt("version",-1)==2 &&
                    doc.opt("objects") instanceof JSONArray;
            }catch(Exception error){return false;}
        }
        private String readValidProject(File file){
            try{
                if(!file.isFile()||file.length()<10||
                    file.length()>MAX_PROJECT_JSON_BYTES)return null;
                String content=new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
                return validProjectJson(content)?content:null;
            }catch(Exception ignored){return null;}
        }
        private void atomicWriteFile(File dest,byte[] bytes) throws IOException{
            File tmp=new File(dest.getParentFile(),dest.getName()+".new");
            try(FileOutputStream out=new FileOutputStream(tmp)){
                out.write(bytes);
                out.getFD().sync();
            }catch(IOException error){tmp.delete();throw error;}
            try{
                try{
                    Files.move(tmp.toPath(),dest.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }catch(AtomicMoveNotSupportedException unavailable){
                    Files.move(tmp.toPath(),dest.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }finally{tmp.delete();}
        }
        private void atomicCopyFile(File source,File dest) throws IOException{
            File tmp=new File(dest.getParentFile(),dest.getName()+".new");
            try{
                Files.copy(source.toPath(),tmp.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                try(FileOutputStream out=new FileOutputStream(tmp,true)){
                    out.getFD().sync();
                }
                try{
                    Files.move(tmp.toPath(),dest.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }catch(AtomicMoveNotSupportedException unsupported){
                    Files.move(tmp.toPath(),dest.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }finally{tmp.delete();}
        }
        // Versioned, on-disk and atomic snapshots. Every save preserves the
        // last known-good project. Never rotate in an invalid/truncated JSON.
        @JavascriptInterface public synchronized boolean saveCleanProject(String json){
            lastError="";
            if(json==null||json.length()>32*1024*1024||!validProjectJson(json)){
                lastError="JSON проекта повреждён или превышает лимит";
                return false;
            }
            File live=projectFile();
            try{
                // UV44 knows nothing about UV56 terrain. Preserve terrain
                // from the last known-good project when UV44 saves building
                // edits, so changing modes cannot silently delete the land.
                String previous=readValidProject(live);
                String merged=json;
                if(!landscapeMode && previous!=null){
                    JSONObject incoming=new JSONObject(json);
                    JSONObject old=new JSONObject(previous);
                    if(!incoming.has("terrain")&&old.has("terrain")){
                        incoming.put("terrain",old.get("terrain"));
                        merged=incoming.toString();
                    }
                }
                byte[] data=merged.getBytes(StandardCharsets.UTF_8);
                if(data.length>MAX_PROJECT_JSON_BYTES){
                    lastError="Общий JSON зданий и ландшафта превышает лимит";
                    return false;
                }
                // Phoenix can call two save aliases for one click. Do not
                // rotate away the same known-good snapshot twice.
                if(merged.equals(previous))return true;
                long available=live.getUsableSpace();
                if(available>0&&available<data.length*2L+DISK_RESERVE){
                    lastError="Недостаточно памяти для двух безопасных копий";
                    return false;
                }
                // Copies rotate oldest-first. A failure keeps the live file.
                for(int slot=SAVE_HISTORY;slot>=2;slot--){
                    File older=projectBackup(slot-1);
                    if(readValidProject(older)!=null)
                        atomicCopyFile(older,projectBackup(slot));
                }
                if(readValidProject(live)!=null)
                    atomicCopyFile(live,projectBackup(1));
                atomicWriteFile(live,data);
                return true;
            }catch(Exception error){
                lastError="Не удалось сохранить безопасную копию: "+
                    error.getMessage();
                return false;
            }
        }
        @JavascriptInterface public synchronized String loadCleanProject(){
            String live=readValidProject(projectFile());
            if(live!=null)return live;
            for(int slot=1;slot<=SAVE_HISTORY;slot++){
                String recovered=readValidProject(projectBackup(slot));
                if(recovered==null)continue;
                final int recoveredSlot=slot;
                runOnUiThread(()->new AlertDialog.Builder(PhoenixBuilderActivity.this)
                    .setTitle("🛟 Восстановлен проект Phoenix")
                    .setMessage("Основной JSON повреждён или отсутствует. "+
                        "Открыта сохранённая копия №"+recoveredSlot+
                        ". Перед продолжением сохрани проект в приложении "+
                        "и экспортируй портативную копию на устройство.")
                    .setPositiveButton("Понятно",null).show());
                return recovered;
            }
            return null;
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
