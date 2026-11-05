package game.oe.editor;

import android.app.Activity;
import android.util.AtomicFile;
import android.os.SystemClock;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class EditorActivity extends Activity {
    static { System.loadLibrary("oe_native"); }

    // Rendering and scene editing execute exclusively on the GL thread.
    private static native void nativeInit();
    private static native void nativeResize(int width, int height);
    private static native void nativeDraw();
    private static native String nativeRenderStats();
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
    private static native boolean nativeUndo();
    private static native boolean nativeRedo();
    private static native void nativeClearHistory();
    private static native int nativeDuplicateSelected();
    private static native boolean nativeRegisterAsset(String key, float[] vertices, float centerY, float radius);
    private static native String[] nativeLoadedAssetKeys();
    private static native boolean nativeCanRegisterMore(int newUniqueAssets);
    private static native void nativeDiscardUnusedAsset(String key);
    private static native boolean nativeReplaceScene(int size, boolean demoRoads,
            int[] kinds, float[] transforms, String[] modelKeys, float[] terrain);
    private static native void nativeChangeSelectedHeight(float dy);
    private static native void nativeScaleSelectedAxis(int axis,float factor);
    private static native void nativeSnapSelectedToGround();
    private static native int nativeApplyTerrainBrushAtScreen(float x,float y,float radius,float amount);
    private static native int nativeTerrainBrushCount();
    private static native int nativeAddImportedPlaced(String key, float x, float z, float yaw, float size);
    private static final int PICK_GLB = 702, PICK_PROJECT=703, SAVE_PROJECT=704;
    private static final int MAX_ASSETS = 12;

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
    private TextView renderStatsText;
    private volatile long statsUpdatedAt=0;
    private volatile boolean importing = false;
    private volatile int placingKind = -1;
    private volatile String placingGlb = null;
    private volatile int terrainMode=0; // -1 lower, +1 raise
    private volatile float terrainRadius=96f;
    private volatile float terrainStrength=20f;
    private volatile float moveStep=.25f,turnStep=5f,scaleStep=1.05f;
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
        if(importing) {message("Дождись завершения загрузки сцены");return;}
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
        title.setText("OE  /  ORVETRA ENGINE     •     NATIVE ANDROID 0.6.5 · ASSET PIPELINE TEST");
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
        addTool(tools, "+ Фонтан", () -> enterPlaceMode(4, null));
        addTool(tools, "+ Дерево", () -> enterPlaceMode(5, null));
        addTool(tools, "+ Камни", () -> enterPlaceMode(7, null));
        addTool(tools, "+ Куст", () -> enterPlaceMode(8, null));
        addTool(tools, "+ Лавка", () -> enterPlaceMode(9, null));
        addTool(tools, "▧ Стена", () -> enterPlaceMode(10, null));
        addTool(tools, "▤ Пол", () -> enterPlaceMode(11, null));
        addTool(tools, "⌂ Крыша", () -> enterPlaceMode(12, null));
        addTool(tools, "▯ Дверная рама", () -> enterPlaceMode(13, null));
        addTool(tools, "▣ Окно", () -> enterPlaceMode(14, null));
        addTool(tools, "▥ Колонна", () -> enterPlaceMode(15, null));
        addTool(tools, "↗ Лестница", () -> enterPlaceMode(16, null));
        addTool(tools, "⛰ Гора +", () -> enterTerrainMode(1));
        addTool(tools, "⛰ Рельеф −", () -> enterTerrainMode(-1));
        addTool(tools, "Кисть рельефа", this::chooseTerrainBrush);
        addTool(tools, "Локации", this::showLocations);
        addTool(tools, "Размер карты", this::showWorldSize);
        addTool(tools, "Координаты", this::showCoordinateJump);
        addTool(tools, "Отмена", this::cancelPlace);
        addTool(tools, "Импорт GLB", this::pickGlb);
        addTool(tools, "Экспорт проекта", this::exportProject);
        addTool(tools, "Импорт проекта", this::pickProject);
        addTool(tools, "Объекты", this::showObjects);
        addTool(tools, "Сохранить", this::saveScene);
        addTool(tools, "Загрузить", this::loadScene);
        addTool(tools, "Новая сцена", this::askReset);
        addTool(tools, "↶ Отменить", this::undoAction);
        addTool(tools, "↷ Повторить", this::redoAction);
        addTool(tools, "⧉ Копировать", this::duplicateAction);
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
        addControl(moveRow, "X −", () -> edit(() -> nativeNudge(-moveStep, 0)));
        addControl(moveRow, "X +", () -> edit(() -> nativeNudge(moveStep, 0)));
        addControl(moveRow, "Z −", () -> edit(() -> nativeNudge(0, -moveStep)));
        addControl(moveRow, "Z +", () -> edit(() -> nativeNudge(0, moveStep)));
        inspector.addView(moveRow);

        LinearLayout otherRow = row();
        addControl(otherRow, "↺ Поворот", () -> edit(() -> nativeRotate(-turnStep)));
        addControl(otherRow, "Поворот ↻", () -> edit(() -> nativeRotate(turnStep)));
        addControl(otherRow, "− Размер", () -> edit(() -> nativeScale(1f/scaleStep)));
        addControl(otherRow, "+ Размер", () -> edit(() -> nativeScale(scaleStep)));
        inspector.addView(otherRow);
        LinearLayout detailRow=row();
        addControl(detailRow,"Y −",() -> edit(() -> nativeChangeSelectedHeight(-moveStep)));
        addControl(detailRow,"Y +",() -> edit(() -> nativeChangeSelectedHeight(moveStep)));
        addControl(detailRow,"К земле",() -> edit(EditorActivity::nativeSnapSelectedToGround));
        addControl(detailRow,"Точность",this::choosePrecision);
        inspector.addView(detailRow);
        LinearLayout axisRow=row();
        addControl(axisRow,"X− масштаб",() -> edit(() -> nativeScaleSelectedAxis(0,1f/scaleStep)));
        addControl(axisRow,"X+ масштаб",() -> edit(() -> nativeScaleSelectedAxis(0,scaleStep)));
        addControl(axisRow,"Y− масштаб",() -> edit(() -> nativeScaleSelectedAxis(1,1f/scaleStep)));
        addControl(axisRow,"Y+ масштаб",() -> edit(() -> nativeScaleSelectedAxis(1,scaleStep)));
        addControl(axisRow,"Z− масштаб",() -> edit(() -> nativeScaleSelectedAxis(2,1f/scaleStep)));
        addControl(axisRow,"Z+ масштаб",() -> edit(() -> nativeScaleSelectedAxis(2,scaleStep)));
        inspector.addView(axisRow);

        LinearLayout historyRow = row();
        addControl(historyRow, "↶ Отмена", this::undoAction);
        addControl(historyRow, "↷ Повтор", this::redoAction);
        addControl(historyRow, "⧉ Копия", this::duplicateAction);
        inspector.addView(historyRow);

        LinearLayout editRow = row();
        addControl(editRow, "🗑 Удалить выбранное", this::askDeleteSelected);
        addControl(editRow, "Локации карты", this::showLocations);
        inspector.addView(editRow);

        statusText = new TextView(this);
        statusText.setText("Размер мира: 1–16 км · 64-метровые участки. Для изменения открой «Размер карты», затем «Сохранить».");
        statusText.setTextColor(Color.rgb(167, 177, 189));
        statusText.setTextSize(10);
        inspector.addView(statusText);

        renderStatsText = new TextView(this);
        renderStatsText.setText("Рендер: данные появятся после первого кадра");
        renderStatsText.setTextColor(Color.rgb(125, 195, 151));
        renderStatsText.setTextSize(10);
        inspector.addView(renderStatsText);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(inspector, ip);

        setContentView(root);
    }
    private void enterPlaceMode(int kind, String asset) {
        if(importing) { message("Сначала дождись завершения импорта"); return; }
        terrainMode=0;
        placingKind = kind;
        placingGlb = asset;
        statusText.setText("РАЗМЕЩЕНИЕ: коснись свободной земли, чтобы поставить объект. Для отмены нажми «Отмена».");
        message("Коснись места на карте для размещения");
    }
    private void cancelPlace() {
        terrainMode=0;
        placingKind = -1;
        placingGlb = null;
        statusText.setText("Режим выбора · два пальца — перемещение; кнопки «Размер карты» и «Координаты» — большой мир.");
        message("Размещение отменено");
    }
    private void enterTerrainMode(int direction){
        if(importing){message("Дождись завершения операции");return;}
        placingKind=-1;placingGlb=null;
        terrainMode=direction;
        statusText.setText(direction>0?
            "ГОРА: коснись земли. Кисть радиус "+(int)terrainRadius+" м, высота +"+(int)terrainStrength+" м. Отмена ↶ работает.":
            "ВПАДИНА: коснись земли. Кисть радиус "+(int)terrainRadius+" м, глубина −"+(int)terrainStrength+" м.");
        message("Коснись земли для деформации · максимум 64 мазка на карту");
    }
    private void chooseTerrainBrush(){
        final String[] choices={"Небольшая · 32м / 8м","Средняя · 64м / 16м",
            "Гора · 96м / 24м","Большая · 160м / 40м","Очень большая · 256м / 60м"};
        final float[] radius={32,64,96,160,256},strength={8,16,24,40,60};
        new AlertDialog.Builder(this).setTitle("Размер и высота рельефа")
            .setItems(choices,(dialog,index)->{
                terrainRadius=radius[index];terrainStrength=strength[index];
                message("Кисть: радиус "+(int)terrainRadius+" м, высота "+(int)terrainStrength+" м");
            }).show();
    }
    private void choosePrecision(){
        final String[] choices={"Очень точно: 0.10 м / 5° / 5%",
            "Точно: 0.25 м / 5° / 5%",
            "Обычно: 0.5 м / 15° / 10%",
            "Крупный шаг: 1 м / 15° / 10%"};
        final float[] moves={.10f,.25f,.5f,1f};
        new AlertDialog.Builder(this).setTitle("Шаг редактирования деталей")
            .setItems(choices,(dialog,index)->{
                moveStep=moves[index];turnStep=index<=1?5f:15f;
                scaleStep=index<=1?1.05f:1.10f;
                message("Шаг: "+moveStep+" м");
            }).show();
    }
    private void showLocations() {
        final String[] locations = {
            "🏰 Город · центр", "🌲 Волшебный лес · восток",
            "🗿 Древние руины · запад", "🕯 Катакомбы · север", "🌾 Дикие земли · юг"
        };
        new AlertDialog.Builder(this).setTitle("Локации мира OE")
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
    private void undoAction() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
        edit(() -> {
            if(nativeUndo())message("Изменение отменено");
            else message("История отмены пуста");
        });
    }
    private void redoAction() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
        edit(() -> {
            if(nativeRedo())message("Изменение повторено");
            else message("История повтора пуста");
        });
    }
    private void duplicateAction() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
        if(placingKind!=-1)cancelPlace();
        edit(() -> {
            if(nativeDuplicateSelected()!=0)message("Копия создана рядом — можешь её двигать");
            else message("Выбери объект: возможно достигнут лимит или край карты");
        });
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
    // AtomicFile preserves the last valid scene if Android stops the app during a write.
    private void saveScene() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
        surface.queueEvent(() -> {
            String json=nativeSceneSnapshot();
            AtomicFile atomic=new AtomicFile(saveFile());
            FileOutputStream stream=null;
            try {
                stream=atomic.startWrite();
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                atomic.finishWrite(stream);
                message("Сцена сохранена безопасно");
            } catch(Exception e) {
                if(stream!=null)atomic.failWrite(stream);
                message("Ошибка сохранения: "+e.getMessage());
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
    private void exportProject(){
        if(importing){message("Дождись завершения операции");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"ORVETHRA-World.oezip");
        try{startActivityForResult(intent,SAVE_PROJECT);}
        catch(Exception e){message("Нельзя открыть файловый менеджер: "+e.getMessage());}
    }
    private void pickProject(){
        if(importing){message("Дождись завершения операции");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // .oezip, .zip and supported document providers.
        try{startActivityForResult(intent,PICK_PROJECT);}
        catch(Exception e){message("Нельзя открыть файловый менеджер: "+e.getMessage());}
    }
    private void exportToUri(Uri uri){
        if(importing)return;
        importing=true;
        surface.queueEvent(() -> {
            final String scene=nativeSceneSnapshot();
            new Thread(() -> {
                try{
                    try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
                        if(stream==null)throw new Exception("Нет доступа к файлу");
                        ProjectArchive.exportProject(scene,modelDir(),stream);
                    }
                    message("Проект экспортирован со всеми используемыми GLB");
                }catch(Exception e){
                    message("Ошибка экспорта: "+e.getMessage());
                }finally{importing=false;}
            },"oe-project-export").start();
        });
    }
    private void importFromUri(Uri uri){
        if(importing)return;
        importing=true;
        message("Проверяю архив проекта и модели…");
        new Thread(() -> {
            try{
                ProjectArchive.StagedProject staged;
                try(InputStream stream=getContentResolver().openInputStream(uri)){
                    if(stream==null)throw new Exception("Нет доступа к архиву");
                    staged=ProjectArchive.importProject(stream,getCacheDir());
                }
                surface.queueEvent(() -> {
                    final HashSet<String> cached=new HashSet<>(Arrays.asList(nativeLoadedAssetKeys()));
                    new Thread(() -> readAndPrepareScene(cached,staged.sceneFile,staged.modelDir,staged.root),
                        "oe-project-validate").start();
                });
            }catch(Exception e){
                importing=false;
                message("Проект НЕ импортирован: "+e.getMessage());
            }
        },"oe-project-import").start();
    }
    private void finishProjectLoad(File stagingRoot){
        importing=false;
        if(stagingRoot!=null)new Thread(() -> ProjectArchive.removeTree(stagingRoot),
                "oe-project-cleanup").start();
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
        if(requestCode==SAVE_PROJECT||requestCode==PICK_PROJECT){
            if(resultCode==RESULT_OK&&intent!=null&&intent.getData()!=null){
                if(requestCode==SAVE_PROJECT)exportToUri(intent.getData());
                else importFromUri(intent.getData());
            }
            return;
        }
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
                File destination=new File(modelDir(),key);
                if(!destination.exists())Files.write(destination.toPath(),bytes);
                // GPU asset lookup MUST run on the GL thread; do not decode a
                // large file again when the content-addressed resource exists.
                surface.queueEvent(() -> {
                    if(Arrays.asList(nativeLoadedAssetKeys()).contains(key)){
                        importing=false;
                        ui(() -> enterPlaceMode(6,key));
                        message("GLB уже загружена — использую модель из памяти");
                    }else{
                        new Thread(() -> {
                            try{
                                final GlbReader.Result model=GlbReader.decode(bytes);
                                surface.queueEvent(() -> {
                                    boolean success=nativeRegisterAsset(key,model.vertices,
                                            model.boundCenterY,model.boundRadius);
                                    importing=false;
                                    if(success){
                                        surface.requestRender();
                                        ui(() -> enterPlaceMode(6,key));
                                        if(model.previewOmitsRig||model.previewOmitsTextures){
                                            String notice="Статический предпросмотр GLB";
                                            if(model.previewOmitsRig)notice+=" · кости/анимации не воспроизводятся";
                                            if(model.previewOmitsTextures)notice+=" · текстуры пока не отображаются";
                                            final String alert=notice;
                                            ui(() -> statusText.setText(alert));
                                            message("GLB импортирован. "+notice);
                                        }else message("GLB прочитан: "+model.triangles+" треугольников. Коснись карты.");
                                    }else message("Не удалось загрузить GLB: память, лимит или GPU");
                                });
                            }catch(Exception e){
                                importing=false;
                                message("Ошибка GLB: "+e.getMessage());
                            }
                        },"oe-glb-decode").start();
                    }
                });
            }catch(Exception e){
                importing=false;
                message("Ошибка GLB: "+e.getMessage());
            }
        },"oe-glb-import").start();
    }
    private static final class SavedEntity {
        int type;
        float x,z,yaw,size,y,sx=1f,sy=1f,sz=1f;
        String asset;
    }
    private static boolean validAsset(String name) {
        return name!=null && name.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private void loadScene() {
        if(importing){message("Импорт или загрузка уже выполняется");return;}
        importing=true;
        // Take a consistent snapshot of the registered GPU assets. The reader
        // thread can skip expensive decode work for those immutable GLBs.
        surface.queueEvent(() -> {
            final HashSet<String> cached=new HashSet<>(Arrays.asList(nativeLoadedAssetKeys()));
            new Thread(() -> readAndPrepareScene(cached,null,null,null),"oe-scene-load").start();
        });
    }
    private void readAndPrepareScene(HashSet<String> cached,File sourceScene,
            File sourceModels,File stagingRoot) {
        try {
            File file=sourceScene==null?saveFile():sourceScene;
            ByteArrayOutputStream jsonBytes=new ByteArrayOutputStream();
            try(InputStream in=new AtomicFile(file).openRead()){
                byte[] buffer=new byte[4096];
                int count;
                while((count=in.read(buffer))!=-1){
                    if(jsonBytes.size()+count>200000)throw new Exception("Файл сцены слишком большой");
                    jsonBytes.write(buffer,0,count);
                }
            }catch(java.io.FileNotFoundException missing){
                throw new Exception("Сначала сохрани сцену");
            }
            JSONObject document=new JSONObject(new String(jsonBytes.toByteArray(),StandardCharsets.UTF_8));
            if(document.optInt("version",-1)!=1)throw new Exception("Неверная версия сцены");
            final boolean demoRoads=document.optBoolean("demoRoads",false);
            final int worldSize=document.optInt("worldSize",1024);
            if(worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&worldSize!=8192&&worldSize!=16384)
                throw new Exception("Неподдерживаемый размер карты");
            JSONArray arr=document.getJSONArray("items");
            if(arr.length()>300)throw new Exception("Слишком много объектов");
            SavedEntity[] items=new SavedEntity[arr.length()];
            HashSet<String> assets=new HashSet<>();
            int[] kinds=new int[arr.length()];
            float[] transforms=new float[arr.length()*8];
            JSONArray brushJson=document.optJSONArray("terrain");
            int brushCount=brushJson==null?0:brushJson.length();
            if(brushCount>64)throw new Exception("Слишком много изменений рельефа");
            float[] terrainBrushes=new float[brushCount*4];
            for(int i=0;i<brushCount;i++){
                JSONArray brush=brushJson.getJSONArray(i);
                if(brush.length()!=4)throw new Exception("Неверная кисть рельефа");
                for(int j=0;j<4;j++){
                    double v=brush.getDouble(j);
                    if(!Double.isFinite(v))throw new Exception("Неверные координаты рельефа");
                    terrainBrushes[i*4+j]=(float)v;
                }
                float bx=terrainBrushes[i*4],bz=terrainBrushes[i*4+1];
                float radius=terrainBrushes[i*4+2],power=terrainBrushes[i*4+3];
                if(Math.abs(bx)>worldSize/2f||Math.abs(bz)>worldSize/2f||
                   radius<16f||radius>256f||Math.abs(power)<.001f||Math.abs(power)>60f)
                    throw new Exception("Изменение рельефа за границами карты");
            }
            String[] modelKeys=new String[arr.length()];
            for(int i=0;i<items.length;i++){
                JSONObject object=arr.getJSONObject(i);
                SavedEntity e=new SavedEntity();
                e.type=object.getInt("type");
                double x=object.getDouble("x"),z=object.getDouble("z");
                double yaw=object.getDouble("yaw"),size=object.getDouble("size"),
                        y=object.optDouble("y",0.0);
                if(e.type<0||e.type>16||!Double.isFinite(x)||!Double.isFinite(z)||
                    !Double.isFinite(yaw)||!Double.isFinite(size)||!Double.isFinite(y)||
                    Math.abs(z)>worldSize/2.0||Math.abs(x)>worldSize/2.0||
                    Math.abs(yaw)>36000||size<.2||size>8||y<-24||y>160)
                    throw new Exception("Неверный объект "+i);
                double sx=object.optDouble("sx",1.0),sy=object.optDouble("sy",1.0),
                        sz=object.optDouble("sz",1.0);
                if(!Double.isFinite(sx)||!Double.isFinite(sy)||!Double.isFinite(sz)||
                   sx<.1||sx>8||sy<.1||sy>8||sz<.1||sz>8)
                    throw new Exception("Неверный масштаб детали "+i);
                e.x=(float)x;e.z=(float)z;e.yaw=(float)yaw;e.size=(float)size;e.y=(float)y;
                e.sx=(float)sx;e.sy=(float)sy;e.sz=(float)sz;
                if(e.type==6){
                    e.asset=object.getString("asset");
                    if(!validAsset(e.asset))throw new Exception("Неверная ссылка модели");
                    assets.add(e.asset);
                }
                items[i]=e;
                kinds[i]=e.type;
                transforms[i*8]=e.x;
                transforms[i*8+1]=e.z;
                transforms[i*8+2]=e.yaw;
                transforms[i*8+3]=e.size;
                transforms[i*8+4]=e.y;
                transforms[i*8+5]=e.sx;
                transforms[i*8+6]=e.sy;
                transforms[i*8+7]=e.sz;
                modelKeys[i]=e.asset;
            }
            if(assets.size()>MAX_ASSETS)throw new Exception("Слишком много разных GLB-моделей");
            Map<String,GlbReader.Result> missing=new LinkedHashMap<>();
            // Verify that each GLB still has a file on disk, but decode only
            // resources not already resident in the renderer.
            for(String asset:assets){
                File model=new File(sourceModels==null?modelDir():sourceModels,asset);
                if(!model.isFile()||model.length()<=0||
                   model.length()>GlbReader.MAX_BYTES)
                    throw new Exception("Модель отсутствует или слишком большая: "+asset);
                if(cached.contains(asset))continue;
                byte[] bytes=Files.readAllBytes(model.toPath());
                if(!GlbReader.key(bytes).equals(asset))
                    throw new Exception("Модель повреждена: "+asset);
                missing.put(asset,GlbReader.decode(bytes));
            }
            // Install verified model files from the staged archive without
            // deleting or overwriting the active project's files.
            if(sourceModels!=null){
                File targetDir=modelDir();
                for(String asset:assets){
                    File destination=new File(targetDir,asset);
                    if(destination.exists()){
                        if(!asset.equals(GlbReader.key(Files.readAllBytes(destination.toPath()))))
                            throw new Exception("Повреждена установленная модель: "+asset);
                    }else{
                        File source=new File(sourceModels,asset);
                        File partial=new File(targetDir,asset+".part");
                        Files.copy(source.toPath(),partial.toPath(),StandardCopyOption.REPLACE_EXISTING);
                        try{
                            Files.move(partial.toPath(),destination.toPath());
                        }finally{
                            Files.deleteIfExists(partial.toPath());
                        }
                    }
                }
            }
            surface.queueEvent(() -> {
                ArrayList<String> uploaded=new ArrayList<>();
                boolean committed=false;
                try {
                    if(!nativeCanRegisterMore(missing.size()))
                        throw new Exception("Недостаточно свободных слотов GLB (макс. 12)");
                    for(Map.Entry<String,GlbReader.Result> entry:missing.entrySet()){
                        GlbReader.Result mesh=entry.getValue();
                        if(!nativeRegisterAsset(entry.getKey(),mesh.vertices,
                                mesh.boundCenterY,mesh.boundRadius))
                            throw new Exception("Ошибка загрузки GPU: "+entry.getKey());
                        uploaded.add(entry.getKey());
                    }
                    if(!nativeReplaceScene(worldSize,demoRoads,kinds,transforms,modelKeys,terrainBrushes))
                        throw new Exception("Сцена не прошла полную проверку в движке");
                    committed=true;
                    surface.requestRender();
                    refreshSelected();
                    message("Сцена загружена · "+items.length+" объектов, GLB "+assets.size()+
                            " (из памяти "+(assets.size()-missing.size())+")");
                }catch(Exception e){
                    message("Сцена НЕ изменена: "+e.getMessage());
                }finally{
                    if(!committed){
                        // Roll back newly uploaded resources without touching
                        // assets referenced by the currently open scene.
                        for(String asset:uploaded)
                            if(!asset.equals(placingGlb))nativeDiscardUnusedAsset(asset);
                    }
                    finishProjectLoad(stagingRoot);
                }
            });
        }catch(Exception e){
            finishProjectLoad(stagingRoot);
            message("Сцена НЕ изменена: "+e.getMessage());
        }
    }
    private void askReset() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
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
        // The SurfaceView is full-size; only the underlying GLES buffer is reduced.
        private static final float RENDER_SCALE=.75f;
        private volatile int renderPixelWidth=1,renderPixelHeight=1;
        private long lastInteractionFrame=0; // Accessed only on GL thread.
        private void interactiveRender() {
            long now=SystemClock.uptimeMillis();
            if(now-lastInteractionFrame>=33){
                lastInteractionFrame=now;
                requestRender();
            }
        }
        private float surfaceX(float x){return x*renderPixelWidth/Math.max(1,getWidth());}
        private float surfaceY(float y){return y*renderPixelHeight/Math.max(1,getHeight());}
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
                    renderPixelWidth=w;
                    renderPixelHeight=h;
                    nativeResize(w, h);
                }
                @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
                    nativeDraw();
                    // Low-rate diagnostics only. No continuous rendering or FPS loop.
                    long now=SystemClock.uptimeMillis();
                    if(now-statsUpdatedAt>=900){
                        statsUpdatedAt=now;
                        final String stats=nativeRenderStats()+" · 3D 75%";
                        ui(() -> {if(renderStatsText!=null)renderStatsText.setText(stats);});
                    }
                }
            });
            // Critical for low-power tablets: zero unnecessary redraws while idle.
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            super.onSizeChanged(w,h,oldW,oldH);
            if(w>0&&h>0){
                // Android scales the lower-resolution EGL surface to the view.
                getHolder().setFixedSize(Math.max(1,Math.round(w*RENDER_SCALE)),
                                         Math.max(1,Math.round(h*RENDER_SCALE)));
            }
        }
        private float pinch(MotionEvent e) {
            return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(importing)return true; // Prevent input changes during scene replacement.
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
                        queueEvent(() -> {nativePan(dxMid,dyMid);nativeOrbit(0,0,zoom);interactiveRender();});
                    lastPinch=p;lastMidX=midX;lastMidY=midY;
                } else if(!multiTouch) {
                    float x=e.getX(),y=e.getY();
                    final float dx=x-lastX,dy=y-lastY;
                    if(dx!=0||dy!=0)queueEvent(() -> {nativeOrbit(dx,dy,1);interactiveRender();});
                    lastX=x;lastY=y;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP) {multiTouch=true;lastPinch=0;return true;}
            if(action==MotionEvent.ACTION_UP) {
                if(!multiTouch && Math.hypot(e.getX()-downX,e.getY()-downY)<dp(8)) {
                    final float x=surfaceX(e.getX()),y=surfaceY(e.getY());
                    final int kind=placingKind;
                    final String asset=placingGlb;
                    final int brush=terrainMode;
                    if(brush!=0){
                        queueEvent(() -> {
                            int count=nativeApplyTerrainBrushAtScreen(x,y,terrainRadius,brush*terrainStrength);
                            if(count>0)message("Рельеф изменён · "+count+"/64 · Сохранить проект");
                            else message("Не удалось изменить землю: точка вне карты или лимит 64 мазка");
                            requestRender();
                        });
                    }else if(kind>=0) {
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
                } else if(!multiTouch) {
                    // Always display the last movement even when redraws are capped.
                    final float dx=e.getX()-lastX,dy=e.getY()-lastY;
                    if(dx!=0||dy!=0)queueEvent(() -> {nativeOrbit(dx,dy,1);requestRender();});
                    else queueEvent(this::requestRender);
                } else {
                    queueEvent(this::requestRender);
                }
                multiTouch=false;
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL) {multiTouch=false;return true;}
            return true;
        }
    }
}
