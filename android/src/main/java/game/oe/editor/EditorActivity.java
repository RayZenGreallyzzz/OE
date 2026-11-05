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
    private static native boolean nativeRegisterAsset(String key, float[] vertices, float centerY,
            float radius, int[] draws, byte[][] images, int[] imageInfo);
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
    private static native void nativeSetGizmoMode(int mode);
    private static native int nativeGizmoHit(float x,float y);
    private static native boolean nativeGizmoBegin(int axis);
    private static native void nativeGizmoDrag(int axis,float dx,float dy);
    private static native void nativeGizmoEnd();
    private static native int nativeAddImportedPlaced(String key, float x, float z, float yaw, float size);
    private static final int PICK_GLB = 702, PICK_PROJECT=703, SAVE_PROJECT=704;
    private static final int MAX_ASSETS = 12;

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
    private TextView renderStatsText;
    private LinearLayout toolDrawer, paletteItems, inspectorPanel, advancedInspector;
    private boolean drawerOpen=false;
    private volatile int gizmoMode=0;
    private volatile long statsUpdatedAt=0;
    private volatile boolean importing = false;
    private volatile int placingKind = -1;
    private volatile String placingGlb = null;
    private volatile int terrainMode=0; // -1 lower, +1 raise
    private volatile float terrainRadius=96f;
    private volatile float terrainStrength=20f;
    private volatile float moveStep=.25f,turnStep=5f,scaleStep=1.05f;
    private volatile int textureLimit=1024; // Low-power default; applies to newly imported GLB.
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
        final boolean hasSelection=!info.startsWith("Выбери");
        ui(() -> {
            if(selectionText!=null)selectionText.setText(info);
            if(inspectorPanel!=null)inspectorPanel.setVisibility(hasSelection?View.VISIBLE:View.GONE);
        });
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
        // Keep navigation gestures usable while giving the scene the entire display.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 19, 30));
        surface = new EditorSurface();
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        // All controls are OVERLAYS. The full-screen 3D surface is never
        // resized/covered by a permanent bottom editor panel.
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(5),dp(2),dp(5),dp(2));
        top.setBackgroundColor(Color.argb(220,14,21,32));
        TextView title=new TextView(this);
        title.setText("OE 0.6.7  /  ORVETRA ENGINE");
        title.setTextColor(gold);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8),0,dp(5),0);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(45),1f));
        Button menu=button("☰ Каталог",()->toggleDrawer(!drawerOpen));
        top.addView(menu,new LinearLayout.LayoutParams(dp(110),dp(43)));
        Button save=button("💾 Сохранить",this::saveScene);
        top.addView(save,new LinearLayout.LayoutParams(dp(112),dp(43)));
        Button undo=button("↶",this::undoAction);
        top.addView(undo,new LinearLayout.LayoutParams(dp(45),dp(43)));
        Button redo=button("↷",this::redoAction);
        top.addView(redo,new LinearLayout.LayoutParams(dp(45),dp(43)));
        root.addView(top,new FrameLayout.LayoutParams(-1,dp(49),Gravity.TOP));

        // Left drawer opens on demand and is vertically scrollable.
        toolDrawer=new LinearLayout(this);
        toolDrawer.setOrientation(LinearLayout.VERTICAL);
        toolDrawer.setPadding(dp(7),dp(8),dp(7),dp(8));
        toolDrawer.setBackgroundColor(Color.argb(249,18,26,38));
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryRow=row();
        for(String category:new String[]{"Здания","Детали","Декор","Рельеф","Текстуры","Проект"}){
            Button tab=button(category,()->showPalette(category));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(98),dp(44));
            p.setMargins(dp(2),0,dp(2),0);categoryRow.addView(tab,p);
        }
        categoryScroll.addView(categoryRow);
        toolDrawer.addView(categoryScroll,new LinearLayout.LayoutParams(-1,dp(47)));
        android.widget.ScrollView paletteScroll=new android.widget.ScrollView(this);
        paletteScroll.setFillViewport(false);
        paletteItems=new LinearLayout(this);
        paletteItems.setOrientation(LinearLayout.VERTICAL);
        paletteScroll.addView(paletteItems);
        toolDrawer.addView(paletteScroll,new LinearLayout.LayoutParams(-1,0,1f));
        Button closePalette=button("✕ Закрыть каталог",()->toggleDrawer(false));
        toolDrawer.addView(closePalette,new LinearLayout.LayoutParams(-1,dp(42)));
        FrameLayout.LayoutParams drawerParams=new FrameLayout.LayoutParams(dp(355),dp(405),
            Gravity.TOP|Gravity.LEFT);
        drawerParams.topMargin=dp(50);
        root.addView(toolDrawer,drawerParams);
        toolDrawer.setVisibility(View.GONE);
        showPalette("Здания");

        // Tiny, context-sensitive inspector. No selection => no inspector at all.
        inspectorPanel=new LinearLayout(this);
        inspectorPanel.setOrientation(LinearLayout.VERTICAL);
        inspectorPanel.setPadding(dp(7),dp(6),dp(7),dp(6));
        inspectorPanel.setBackgroundColor(Color.argb(235,17,25,36));
        selectionText=new TextView(this);
        selectionText.setText("Выбери объект касанием");
        selectionText.setTextColor(gold);
        selectionText.setTextSize(11);
        selectionText.setMaxLines(2);
        inspectorPanel.addView(selectionText);
        LinearLayout modes=row();
        addControl(modes,"↔ Двигать",()->changeGizmoMode(0));
        addControl(modes,"⟳ Повернуть",()->changeGizmoMode(1));
        addControl(modes,"⤢ Масштаб",()->changeGizmoMode(2));
        inspectorPanel.addView(modes);
        LinearLayout quick=row();
        addControl(quick,"⚙ Точно",()->{
            boolean open=advancedInspector.getVisibility()!=View.VISIBLE;
            advancedInspector.setVisibility(open?View.VISIBLE:View.GONE);
        });
        addControl(quick,"К земле",()->edit(EditorActivity::nativeSnapSelectedToGround));
        addControl(quick,"⧉ Копия",this::duplicateAction);
        addControl(quick,"🗑",this::askDeleteSelected);
        inspectorPanel.addView(quick);

        advancedInspector=new LinearLayout(this);
        advancedInspector.setOrientation(LinearLayout.VERTICAL);
        advancedInspector.setVisibility(View.GONE);
        LinearLayout moveRow=row();
        addControl(moveRow,"X −",()->edit(()->nativeNudge(-moveStep,0)));
        addControl(moveRow,"X +",()->edit(()->nativeNudge(moveStep,0)));
        addControl(moveRow,"Z −",()->edit(()->nativeNudge(0,-moveStep)));
        addControl(moveRow,"Z +",()->edit(()->nativeNudge(0,moveStep)));
        advancedInspector.addView(moveRow);
        LinearLayout heightRow=row();
        addControl(heightRow,"Y −",()->edit(()->nativeChangeSelectedHeight(-moveStep)));
        addControl(heightRow,"Y +",()->edit(()->nativeChangeSelectedHeight(moveStep)));
        addControl(heightRow,"↺",()->edit(()->nativeRotate(-turnStep)));
        addControl(heightRow,"↻",()->edit(()->nativeRotate(turnStep)));
        advancedInspector.addView(heightRow);
        LinearLayout scaleRow=row();
        addControl(scaleRow,"− Размер",()->edit(()->nativeScale(1f/scaleStep)));
        addControl(scaleRow,"+ Размер",()->edit(()->nativeScale(scaleStep)));
        addControl(scaleRow,"Точность",this::choosePrecision);
        advancedInspector.addView(scaleRow);
        for(int axis=0;axis<3;axis++){
            final int targetAxis=axis;
            LinearLayout axisRow=row();
            String name=axis==0?"X":axis==1?"Y":"Z";
            addControl(axisRow,name+" − масштаб",()->edit(()->nativeScaleSelectedAxis(targetAxis,1f/scaleStep)));
            addControl(axisRow,name+" + масштаб",()->edit(()->nativeScaleSelectedAxis(targetAxis,scaleStep)));
            advancedInspector.addView(axisRow);
        }
        inspectorPanel.addView(advancedInspector);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(360),-2,Gravity.BOTTOM|Gravity.RIGHT);
        ip.setMargins(0,0,dp(8),dp(12));
        root.addView(inspectorPanel,ip);
        inspectorPanel.setVisibility(View.GONE);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(7),dp(3),dp(7),dp(3));
        info.setBackgroundColor(Color.argb(135,12,18,29));
        statusText=new TextView(this);
        statusText.setText("1 палец — камера · 2 пальца — панорама/зум · выбери объект для 3D-осей");
        statusText.setTextColor(Color.rgb(193,205,217));
        statusText.setTextSize(10);
        statusText.setMaxLines(2);
        info.addView(statusText);
        renderStatsText=new TextView(this);
        renderStatsText.setText("Рендер: по запросу · 3D 75%");
        renderStatsText.setTextColor(Color.rgb(127,192,154));
        renderStatsText.setTextSize(9);
        renderStatsText.setMaxLines(1);
        info.addView(renderStatsText);
        FrameLayout.LayoutParams infoParams=new FrameLayout.LayoutParams(dp(390),-2,
            Gravity.LEFT|Gravity.BOTTOM);
        infoParams.leftMargin=dp(6);
        infoParams.bottomMargin=dp(12);
        root.addView(info,infoParams);
        setContentView(root);
    }
    private void toggleDrawer(boolean open){
        drawerOpen=open;
        toolDrawer.setVisibility(open?View.VISIBLE:View.GONE);
    }
    private void addPaletteOption(String label,Runnable action){
        Button b=button(label,()->{
            action.run();
            // Start placing directly on the exposed 3D world.
            if(placingKind>=0||terrainMode!=0)toggleDrawer(false);
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));
        p.setMargins(dp(4),dp(2),dp(4),dp(2));
        paletteItems.addView(b,p);
    }
    private void showPalette(String category){
        paletteItems.removeAllViews();
        if("Здания".equals(category)){
            addPaletteOption("+ Таверна",()->enterPlaceMode(0,null));
            addPaletteOption("+ Кузница",()->enterPlaceMode(1,null));
            addPaletteOption("+ Аукционный дом",()->enterPlaceMode(2,null));
        }else if("Детали".equals(category)){
            addPaletteOption("▧ Стена",()->enterPlaceMode(10,null));
            addPaletteOption("▤ Пол",()->enterPlaceMode(11,null));
            addPaletteOption("⌂ Крыша",()->enterPlaceMode(12,null));
            addPaletteOption("▯ Дверная рама",()->enterPlaceMode(13,null));
            addPaletteOption("▣ Окно",()->enterPlaceMode(14,null));
            addPaletteOption("▥ Колонна",()->enterPlaceMode(15,null));
            addPaletteOption("↗ Лестница",()->enterPlaceMode(16,null));
        }else if("Декор".equals(category)){
            addPaletteOption("+ Фонарь",()->enterPlaceMode(3,null));
            addPaletteOption("+ Фонтан",()->enterPlaceMode(4,null));
            addPaletteOption("+ Дерево",()->enterPlaceMode(5,null));
            addPaletteOption("+ Камни",()->enterPlaceMode(7,null));
            addPaletteOption("+ Куст",()->enterPlaceMode(8,null));
            addPaletteOption("+ Лавка",()->enterPlaceMode(9,null));
            addPaletteOption("Импорт GLB модели",this::pickGlb);
        }else if("Рельеф".equals(category)){
            addPaletteOption("⛰ Поднять гору",()->enterTerrainMode(1));
            addPaletteOption("⛰ Опустить землю",()->enterTerrainMode(-1));
            addPaletteOption("📐 Радиус и сила кисти",this::chooseTerrainBrush);
            addPaletteOption("✕ Завершить изменение",this::cancelPlace);
        }else if("Текстуры".equals(category)){
            addPaletteOption("Импорт GLB с текстурами",this::pickGlb);
            addPaletteOption("Качество 1024 / 2048 px",this::chooseTextureLimit);
            addPaletteOption("Форматы и ограничения",()->new AlertDialog.Builder(this)
                .setTitle("Текстуры OE")
                .setMessage("Сейчас работают встроенные в GLB PNG/JPG и GPU-готовые ETC2/сырой RGBA8 KTX2. "
                    +"Нанесение отдельного PNG на стену и покраска земли кистью пока не готовы. "
                    +"BasisU и Draco ещё не подключены.")
                .setPositiveButton("Понятно",null).show());
        }else if("Проект".equals(category)){
            addPaletteOption("💾 Сохранить локально",this::saveScene);
            addPaletteOption("📂 Загрузить локально",this::loadScene);
            addPaletteOption("📦 Экспорт .oezip",this::exportProject);
            addPaletteOption("📥 Импорт .oezip",this::pickProject);
            addPaletteOption("Объекты сцены",this::showObjects);
            addPaletteOption("Локации карты",this::showLocations);
            addPaletteOption("Размер карты",this::showWorldSize);
            addPaletteOption("Перейти к координатам",this::showCoordinateJump);
            addPaletteOption("Новая / пустая сцена",this::askReset);
            addPaletteOption("↶ Отмена",this::undoAction);
            addPaletteOption("↷ Повтор",this::redoAction);
        }
    }
    private void changeGizmoMode(int mode){
        gizmoMode=mode;
        surface.queueEvent(()->{nativeSetGizmoMode(mode);surface.requestRender();});
        statusText.setText(mode==0?"3D-оси: тяни X (красная), Y (зелёная), Z (синяя)":
            mode==1?"3D-ось Y: тяни зелёный маркер для поворота вокруг вертикали":
                     "3D-оси: тяни цветной маркер для изменения масштаба по выбранной оси");
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
    private void chooseTextureLimit(){
        final String[] choices={
            "1024 px · экономия памяти и энергии (рекомендуется)",
            "2048 px · более чёткие детали, больше расход видеопамяти"};
        new AlertDialog.Builder(this).setTitle("Лимит текстур для новых GLB")
            .setSingleChoiceItems(choices,textureLimit==1024?0:1,(dialog,which)->{
                textureLimit=which==0?1024:2048;
                message("Лимит: "+textureLimit+" px · уже загруженные модели не изменяются");
                dialog.dismiss();
            }).setNegativeButton("Отмена",null).show();
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
                                final GlbReader.Result model=GlbReader.decode(bytes,textureLimit);
                                surface.queueEvent(() -> {
                                    boolean success=nativeRegisterAsset(key,model.vertices,
                                            model.boundCenterY,model.boundRadius,
                                            model.draws,model.imageData,model.imageInfo);
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
                missing.put(asset,GlbReader.decode(bytes,textureLimit));
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
                                mesh.boundCenterY,mesh.boundRadius,
                                mesh.draws,mesh.imageData,mesh.imageInfo))
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
        private volatile int touchedGizmoAxis=-1;
        private boolean gizmoEditStarted=false; // GL thread only.
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
                touchedGizmoAxis=-1;
                final float px=surfaceX(downX),py=surfaceY(downY);
                queueEvent(() -> {
                    nativeGizmoEnd();gizmoEditStarted=false;
                    if(placingKind==-1&&terrainMode==0)
                        touchedGizmoAxis=nativeGizmoHit(px,py);
                });
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                multiTouch=true;
                touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;});
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
                    if(dx!=0||dy!=0){
                        final float sx=surfaceX(dx),sy=surfaceY(dy);
                        final boolean movedEnough=Math.hypot(x-downX,y-downY)>dp(5);
                        // All input decisions happen on the GLES event queue:
                        // axis hit-test on DOWN is guaranteed to run before MOVE.
                        queueEvent(()->{
                            final int axis=touchedGizmoAxis;
                            if(axis>=0&&placingKind==-1&&terrainMode==0){
                                if(!gizmoEditStarted&&movedEnough)
                                    gizmoEditStarted=nativeGizmoBegin(axis);
                                if(gizmoEditStarted)nativeGizmoDrag(axis,sx,sy);
                            }else nativeOrbit(dx,dy,1);
                            interactiveRender();
                        });
                    }
                    lastX=x;lastY=y;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP) {multiTouch=true;lastPinch=0;return true;}
            if(action==MotionEvent.ACTION_UP) {
                if(!multiTouch){
                    // Decide after the queued DOWN hit-test; don't accidentally
                    // pick another entity when tapping a gizmo handle.
                    final float upX=e.getX(),upY=e.getY();
                    final float px=surfaceX(upX),py=surfaceY(upY);
                    final boolean tap=Math.hypot(upX-downX,upY-downY)<dp(8);
                    final int kind=placingKind,brush=terrainMode;
                    final String asset=placingGlb;
                    queueEvent(()->{
                        if(touchedGizmoAxis>=0){
                            nativeGizmoEnd();gizmoEditStarted=false;
                            touchedGizmoAxis=-1;
                            requestRender();refreshSelected();
                            return;
                        }
                        if(tap){
                            if(brush!=0){
                                int count=nativeApplyTerrainBrushAtScreen(px,py,terrainRadius,
                                    brush*terrainStrength);
                                if(count>0)message("Рельеф изменён · "+count+"/64");
                                else message("Не удалось изменить землю: край карты или лимит 64 кисти");
                            }else if(kind>=0){
                                int id=kind==6?nativePlaceImportedAtScreen(asset,px,py):
                                    nativePlaceAtScreen(kind,px,py);
                                if(id>0){
                                    placingKind=-1;placingGlb=null;
                                    ui(()->statusText.setText("Объект размещён · выбери его для 3D-манипулятора"));
                                }else message("Место занято или вне карты");
                            }else nativeSelect(nativePick(px,py));
                            refreshSelected();
                        }else{
                            final float dx=upX-lastX,dy=upY-lastY;
                            if(dx!=0||dy!=0)nativeOrbit(dx,dy,1);
                        }
                        requestRender();touchedGizmoAxis=-1;
                    });
                    multiTouch=false;
                    return true;
                }
                // Two-finger gesture already processed during movement.
                queueEvent(this::requestRender);
                multiTouch=false;touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;});
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL) {
                multiTouch=false;touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;});
                return true;
            }
            return true;
        }
    }
}
