package game.oe.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class EditorActivity extends Activity {
    static { System.loadLibrary("oe_native"); }

    // Rendering and scene editing execute exclusively on the GL thread.
    private static native void nativeInit();
    private static native void nativeResize(int width, int height);
    private static native void nativeDraw();
    private static native void nativeOrbit(float dx, float dy, float zoom);
    private static native void nativePan(float dx, float dy);
    private static native void nativeFocusLocation(int index);
    private static native int nativeGetWorldSize();
    private static native boolean nativeSetWorldSize(int size);
    private static native boolean nativeJumpTo(float x, float z);
    private static native int nativePick(float x, float y);
    private static native int nativeAdd(int kind);
    private static native int nativePlaceAtScreen(int kind, float x, float y);
    private static native int nativePlaceImportedAtScreen(String key, float x, float y);
    private static native int nativeAddPlaced(int kind, float x, float z, float yaw, float scale);
    private static native void nativeSelect(int id);
    private static native void nativeNudge(float dx, float dz);
    private static native void nativeRotate(float deg);
    private static native void nativeScale(float factor);
    private static native String nativeSelectedInfo();
    private static native String nativeSceneSnapshot();
    private static native String[] nativeLabels();
    private static native int[] nativeIds();
    private static native void nativeReset(boolean withDemo);
    private static native void nativeSetDemoRoads(boolean enabled);
    private static native boolean nativeDeleteSelected();
    private static native boolean nativeRegisterAsset(String key, float[] vertices);
    private static native int nativeAddImportedPlaced(String key, float x, float z, float yaw, float size);
    private static final int PICK_GLB = 702;
    private static final int MAX_ASSETS = 12;

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
    private volatile boolean importing = false;
    private volatile int placingKind = -1;
    private volatile String placingGlb = null;
    private final int gold = Color.rgb(240, 192, 113);
    private final int text = Color.rgb(234, 228, 218);

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void ui(Runnable r) { runOnUiThread(r); }
    private void message(String s) {
        ui(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
    private void edit(Runnable work) {
        surface.queueEvent(() -> {
            work.run();
            surface.requestRender();
            refreshSelected();
        });
    }
    private void refreshSelected() {
        String info = nativeSelectedInfo();
        ui(() -> selectionText.setText(info));
    }
    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextColor(text);
        b.setTextSize(12f);
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(52, 62, 77)));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void addControl(LinearLayout row, String title, Runnable task) {
        Button button = button(title, task);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(button, p);
    }
    private LinearLayout row() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        return line;
    }
    private void addTool(LinearLayout row, String title, Runnable task) {
        Button b = button(title, task);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(110), dp(44));
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(b, p);
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(14, 19, 27));
        getWindow().setNavigationBarColor(Color.rgb(14, 19, 27));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 19, 30));
        surface = new EditorSurface();
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(5), dp(5), dp(5), dp(5));
        top.setBackgroundColor(Color.argb(240, 17, 24, 34));
        top.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("OE  /  ORVETRA ENGINE     •     NATIVE ANDROID 0.5 · OPEN WORLD");
        title.setTextColor(gold);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(13);
        title.setPadding(dp(7), dp(2), 0, dp(4));
        top.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = row();
        addTool(tools, "+ Таверна", () -> enterPlaceMode(0, null));
        addTool(tools, "+ Кузница", () -> enterPlaceMode(1, null));
        addTool(tools, "+ Аукцион", () -> enterPlaceMode(2, null));
        addTool(tools, "+ Фонарь", () -> enterPlaceMode(3, null));
        addTool(tools, "Локации", this::showLocations);
        addTool(tools, "Размер карты", this::showWorldSize);
        addTool(tools, "Координаты", this::showCoordinateJump);
        addTool(tools, "Отмена", this::cancelPlace);
        addTool(tools, "Импорт GLB", this::pickGlb);
        addTool(tools, "Объекты", this::showObjects);
        addTool(tools, "Сохранить", this::saveScene);
        addTool(tools, "Загрузить", this::loadScene);
        addTool(tools, "Новая сцена", this::askReset);
        scroll.addView(tools);
        top.addView(scroll);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, tp);

        LinearLayout inspector = new LinearLayout(this);
        inspector.setOrientation(LinearLayout.VERTICAL);
        inspector.setBackgroundColor(Color.argb(238, 15, 22, 32));
        inspector.setPadding(dp(9), dp(7), dp(9), dp(7));

        selectionText = new TextView(this);
        selectionText.setText("Коснись здания, чтобы выбрать его");
        selectionText.setTextColor(gold);
        selectionText.setTextSize(13);
        inspector.addView(selectionText);

        LinearLayout moveRow = row();
        addControl(moveRow, "X −", () -> edit(() -> nativeNudge(-1f, 0)));
        addControl(moveRow, "X +", () -> edit(() -> nativeNudge(1f, 0)));
        addControl(moveRow, "Z −", () -> edit(() -> nativeNudge(0, -1f)));
        addControl(moveRow, "Z +", () -> edit(() -> nativeNudge(0, 1f)));
        inspector.addView(moveRow);

        LinearLayout otherRow = row();
        addControl(otherRow, "↺ 15°", () -> edit(() -> nativeRotate(-15f)));
        addControl(otherRow, "15° ↻", () -> edit(() -> nativeRotate(15f)));
        addControl(otherRow, "− Размер", () -> edit(() -> nativeScale(.90f)));
        addControl(otherRow, "+ Размер", () -> edit(() -> nativeScale(1.10f)));
        inspector.addView(otherRow);

        LinearLayout editRow = row();
        addControl(editRow, "🗑 Удалить выбранное", this::askDeleteSelected);
        addControl(editRow, "Локации карты", this::showLocations);
        inspector.addView(editRow);

        statusText = new TextView(this);
        statusText.setText("Размер мира: 1–16 км · 64-метровые участки. Для изменения открой «Размер карты», затем «Сохранить».");
        statusText.setTextColor(Color.rgb(167, 177, 189));
        statusText.setTextSize(10);
        inspector.addView(statusText);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(inspector, ip);

        setContentView(root);
    }
    private void enterPlaceMode(int kind, String asset) {
        if(importing) { message("Сначала дождись завершения импорта"); return; }
        placingKind = kind;
        placingGlb = asset;
        statusText.setText("РАЗМЕЩЕНИЕ: коснись свободной земли, чтобы поставить объект. Для отмены нажми «Отмена».");
        message("Коснись места на карте для размещения");
    }
    private void cancelPlace() {
        placingKind = -1;
        placingGlb = null;
        statusText.setText("Режим выбора · два пальца — перемещение; кнопки «Размер карты» и «Координаты» — большой мир.");
        message("Размещение отменено");
    }
    private void showLocations() {
        final String[] locations = {
            "🏰 Город · центр", "🌲 Волшебный лес · восток",
            "🗿 Древние руины · запад", "🕯 Катакомбы · север", "🌾 Дикие земли · юг"
        };
        new AlertDialog.Builder(this).setTitle("Локации карты · 1024×1024 м")
            .setItems(locations, (d, index) -> {
                cancelPlace();
                edit(() -> nativeFocusLocation(index));
                message("Переход: "+locations[index]);
            })
            .setNegativeButton("Закрыть", null).show();
    }
    private void showWorldSize() {
        final int[] sizes={1024,2048,4096,8192,16384};
        final String[] titles={"1 × 1 км  (1 км²)","2 × 2 км  (4 км²)",
                "4 × 4 км  (16 км²)","8 × 8 км  (64 км²)","16 × 16 км  (256 км²)"};
        surface.queueEvent(() -> {
            final int current=nativeGetWorldSize();
            int selected=0;
            for(int i=0;i<sizes.length;i++)if(sizes[i]==current)selected=i;
            final int checked=selected;
            ui(() -> new AlertDialog.Builder(this).setTitle("Размер мира OE")
                .setSingleChoiceItems(titles,checked,(dialog,which)->{
                    final int size=sizes[which];
                    edit(() -> {
                        if(nativeSetWorldSize(size)) {
                            message("Карта теперь "+(size/1024)+" × "+(size/1024)+" км. Нажми «Сохранить».");
                            ui(() -> statusText.setText("Мир "+(size/1024)+" × "+(size/1024)+" км · 64 м на участок · «Координаты» — переход по карте"));
                        } else message("Нельзя уменьшить карту: за новыми границами есть объекты.");
                    });
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена",null).show());
        });
    }
    private void showCoordinateJump() {
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(22),dp(8),dp(22),dp(4));
        EditText x=new EditText(this), z=new EditText(this);
        int mode=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;
        x.setInputType(mode);z.setInputType(mode);
        x.setSingleLine(true);z.setSingleLine(true);
        x.setHint("X (метры, например 2000)");z.setHint("Z (метры, например -1500)");
        fields.addView(x);fields.addView(z);
        new AlertDialog.Builder(this).setTitle("Перейти к координатам")
            .setMessage("Центр карты: X=0, Z=0. Координаты должны быть внутри выбранного мира.")
            .setView(fields)
            .setPositiveButton("Перейти",(d,which)->{
                try {
                    final float px=Float.parseFloat(x.getText().toString().trim());
                    final float pz=Float.parseFloat(z.getText().toString().trim());
                    edit(() -> {
                        if(nativeJumpTo(px,pz))message("Камера перемещена: X "+px+", Z "+pz);
                        else message("Точка за границами карты. Увеличь мир или введи другие координаты.");
                    });
                }catch(Exception error){message("Укажи числовые значения X и Z.");}
            })
            .setNegativeButton("Отмена",null).show();
    }
    private void askDeleteSelected() {
        if(placingKind != -1)cancelPlace();
        new AlertDialog.Builder(this).setTitle("Удалить объект?")
            .setMessage("Будет удалён только выбранный объект. Изменение можно сохранить после удаления.")
            .setPositiveButton("Удалить", (d, which) ->
                edit(() -> {
                    if(nativeDeleteSelected())message("Объект удалён");
                    else message("Сначала выбери объект касанием или через «Объекты»");
                }))
            .setNegativeButton("Отмена", null).show();
    }
    private void showObjects() {
        surface.queueEvent(() -> {
            String[] names = nativeLabels();
            int[] ids = nativeIds();
            ui(() -> new AlertDialog.Builder(this).setTitle("Сцена OE")
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < ids.length) edit(() -> nativeSelect(ids[which]));
                }).setNegativeButton("Закрыть", null).show());
        });
    }
    private File saveFile() { return new File(getFilesDir(), "oe_scene_v1.json"); }
    private void saveScene() {
        surface.queueEvent(() -> {
            String json = nativeSceneSnapshot();
            try (FileOutputStream stream = new FileOutputStream(saveFile())) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                message("Сцена сохранена в памяти планшета");
            } catch (Exception e) {
                message("Ошибка сохранения: " + e.getMessage());
            }
        });
    }

    private File modelDir() throws Exception {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Нет доступа к папке моделей");
        return dir;
    }
    private byte[] limitedRead(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n=stream.read(buf))!=-1) {
                if (out.size() + n > GlbReader.MAX_BYTES) throw new Exception("GLB больше 24 МБ");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    private void pickGlb() {
        if(importing) { message("Импорт или загрузка уже выполняется"); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try { startActivityForResult(intent, PICK_GLB); }
        catch(Exception e) { message("Файловый менеджер недоступен: "+e.getMessage()); }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent intent) {
        super.onActivityResult(requestCode,resultCode,intent);
        if(requestCode!=PICK_GLB || resultCode!=RESULT_OK || intent==null || intent.getData()==null)return;
        final Uri uri=intent.getData();
        if(importing)return;
        importing=true;
        message("Читаю модель GLB…");
        new Thread(() -> {
            try {
                InputStream stream=getContentResolver().openInputStream(uri);
                if(stream==null)throw new Exception("Не удалось открыть файл");
                byte[] bytes=limitedRead(stream);
                final String key=GlbReader.key(bytes);
                final GlbReader.Result model=GlbReader.decode(bytes);
                File destination=new File(modelDir(),key);
                if(!destination.exists())Files.write(destination.toPath(),bytes);
                surface.queueEvent(() -> {
                    boolean success=nativeRegisterAsset(key,model.vertices);
                    importing=false;
                    if(success) {
                        surface.requestRender();
                        ui(() -> enterPlaceMode(6, key));
                        message("GLB прочитан: "+model.triangles+" треугольников. Теперь коснись карты.");
                    } else message("Не удалось загрузить GLB: лимит уникальных моделей");
                });
            }catch(Exception e){ importing=false; message("Ошибка GLB: "+e.getMessage()); }
        },"oe-glb-import").start();
    }
    private static final class SavedEntity {
        int type;
        float x,z,yaw,size;
        String asset;
    }
    private static boolean validAsset(String name) {
        return name!=null && name.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private void loadScene() {
        if(importing){message("Импорт или загрузка уже выполняется");return;}
        importing=true;
        new Thread(() -> {
            try {
                File file=saveFile();
                if(!file.exists())throw new Exception("Сначала сохрани сцену");
                if(file.length()>200000L)throw new Exception("Файл сцены слишком большой");
                JSONObject document=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
                if(document.optInt("version",-1)!=1)throw new Exception("Неверная версия сцены");
                final boolean demoRoads=document.optBoolean("demoRoads", false);
                final int worldSize=document.optInt("worldSize",1024);
                if(worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&worldSize!=8192&&worldSize!=16384)
                    throw new Exception("Неподдерживаемый размер карты");
                JSONArray arr=document.getJSONArray("items");
                if(arr.length()>300)throw new Exception("Слишком много объектов");
                SavedEntity[] items=new SavedEntity[arr.length()];
                HashSet<String> assets=new HashSet<>();
                for(int i=0;i<items.length;i++){
                    JSONObject object=arr.getJSONObject(i);
                    SavedEntity e=new SavedEntity();
                    e.type=object.getInt("type");
                    double x=object.getDouble("x"),z=object.getDouble("z");
                    double yaw=object.getDouble("yaw"),size=object.getDouble("size");
                    if(e.type<0||e.type>6||!Double.isFinite(x)||!Double.isFinite(z)||
                       !Double.isFinite(yaw)||!Double.isFinite(size)||Math.abs(x)>300||
                       Math.abs(z)>worldSize/2.0||Math.abs(x)>worldSize/2.0||Math.abs(yaw)>36000||size<.2||size>8)
                       throw new Exception("Неверный объект "+i);
                    e.x=(float)x;e.z=(float)z;e.yaw=(float)yaw;e.size=(float)size;
                    if(e.type==6){
                        e.asset=object.getString("asset");
                        if(!validAsset(e.asset))throw new Exception("Неверная ссылка модели");
                        assets.add(e.asset);
                    }
                    items[i]=e;
                }
                if(assets.size()>MAX_ASSETS)throw new Exception("Слишком много разных GLB-моделей");
                Map<String,GlbReader.Result> meshes=new HashMap<>();
                for(String asset:assets){
                    File model=new File(modelDir(),asset);
                    if(!model.isFile()||model.length()>GlbReader.MAX_BYTES)throw new Exception("Модель отсутствует: "+asset);
                    byte[] bytes=Files.readAllBytes(model.toPath());
                    if(!GlbReader.key(bytes).equals(asset))throw new Exception("Модель повреждена: "+asset);
                    meshes.put(asset,GlbReader.decode(bytes));
                }
                surface.queueEvent(() -> {
                    nativeReset(false);
                    if(!nativeSetWorldSize(worldSize)) {
                        message("Не удалось восстановить размер карты");
                        importing=false;
                        return;
                    }
                    nativeSetDemoRoads(demoRoads);
                    for(Map.Entry<String,GlbReader.Result> entry:meshes.entrySet()){
                        if(!nativeRegisterAsset(entry.getKey(),entry.getValue().vertices)){
                            message("Не удалось загрузить GLB: "+entry.getKey());
                            importing=false;
                            return;
                        }
                    }
                    for(SavedEntity e:items){
                        if(e.type==6)nativeAddImportedPlaced(e.asset,e.x,e.z,e.yaw,e.size);
                        else nativeAddPlaced(e.type,e.x,e.z,e.yaw,e.size);
                    }
                    surface.requestRender();
                    refreshSelected();
                    message("Сцена загружена · "+items.length+" объектов, GLB "+assets.size());
                    importing=false;
                });
            }catch(Exception e){importing=false;message("Ошибка загрузки: "+e.getMessage());}
        },"oe-scene-load").start();
    }
    private void askReset() {
        String[] choices={"Пустая карта — удалить ВСЕ объекты", "Восстановить демонстрационный город"};
        new AlertDialog.Builder(this).setTitle("Новая сцена OE")
            .setItems(choices,(dialog,option)->{
                boolean demo=(option==1);
                new AlertDialog.Builder(this).setTitle(demo?"Загрузить демо?":"Очистить карту?")
                    .setMessage("Все текущие несохранённые объекты исчезнут. Ранее сохранённый файл останется доступен через «Загрузить».")
                    .setPositiveButton(demo?"Загрузить":"Очистить",(confirm,which)->{
                        cancelPlace();
                        edit(()->nativeReset(demo));
                        message(demo?"Демонстрационный квартал восстановлен":"Пустая карта: все объекты удалены");
                    })
                    .setNegativeButton("Отмена",null).show();
            })
            .setNegativeButton("Отмена",null).show();
    }
    @Override protected void onResume() { super.onResume(); if (surface != null) surface.onResume(); }
    @Override protected void onPause() { if (surface != null) surface.onPause(); super.onPause(); }

    private final class EditorSurface extends GLSurfaceView {
        private float lastX, lastY, downX, downY, lastPinch, lastMidX, lastMidY;
        private boolean multiTouch = false;
        EditorSurface() {
            super(EditorActivity.this);
            setEGLContextClientVersion(3);
            setEGLConfigChooser(8, 8, 8, 8, 24, 0);
            setRenderer(new Renderer() {
                @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                                        javax.microedition.khronos.egl.EGLConfig config) {
                    nativeInit();
                    refreshSelected();
                }
                @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) {
                    nativeResize(w, h);
                }
                @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
                    nativeDraw();
                }
            });
            // Critical for low-power tablets: zero unnecessary redraws while idle.
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        private float pinch(MotionEvent e) {
            return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN) {
                downX=lastX=e.getX();downY=lastY=e.getY();multiTouch=false;lastPinch=0;
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                multiTouch=true;
                if(e.getPointerCount()>=2){
                    lastPinch=pinch(e);
                    lastMidX=(e.getX(0)+e.getX(1))*.5f;
                    lastMidY=(e.getY(0)+e.getY(1))*.5f;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE) {
                if(e.getPointerCount()>=2) {
                    float p=pinch(e);
                    final float midX=(e.getX(0)+e.getX(1))*.5f;
                    final float midY=(e.getY(0)+e.getY(1))*.5f;
                    final float dxMid=midX-lastMidX,dyMid=midY-lastMidY;
                    final float zoom=(lastPinch>1 && p>1)?Math.max(.7f,Math.min(1.3f,lastPinch/p)):1f;
                    if(zoom!=1f||dxMid!=0||dyMid!=0)
                        queueEvent(() -> {nativePan(dxMid,dyMid);nativeOrbit(0,0,zoom);requestRender();});
                    lastPinch=p;lastMidX=midX;lastMidY=midY;
                } else if(!multiTouch) {
                    float x=e.getX(),y=e.getY();
                    final float dx=x-lastX,dy=y-lastY;
                    if(dx!=0||dy!=0)queueEvent(() -> {nativeOrbit(dx,dy,1);requestRender();});
                    lastX=x;lastY=y;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP) {multiTouch=true;lastPinch=0;return true;}
            if(action==MotionEvent.ACTION_UP) {
                if(!multiTouch && Math.hypot(e.getX()-downX,e.getY()-downY)<dp(8)) {
                    final float x=e.getX(),y=e.getY();
                    final int kind=placingKind;
                    final String asset=placingGlb;
                    if(kind>=0) {
                        queueEvent(() -> {
                            int id=kind==6?nativePlaceImportedAtScreen(asset,x,y):nativePlaceAtScreen(kind,x,y);
                            if(id>0){
                                placingKind=-1;placingGlb=null;
                                ui(() -> statusText.setText("Объект размещён · для следующего используй + в панели."));
                                message("Объект добавлен на карту");
                            } else message("Место занято или вне карты — коснись свободного участка");
                            requestRender();refreshSelected();
                        });
                    } else {
                        queueEvent(() -> {nativeSelect(nativePick(x,y));requestRender();refreshSelected();});
                    }
                }
                multiTouch=false;
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL) {multiTouch=false;return true;}
            return true;
        }
    }
}
