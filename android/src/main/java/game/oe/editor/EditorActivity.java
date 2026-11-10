package game.oe.editor;

import android.app.Activity;
import android.app.ActivityManager;
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
import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

public class EditorActivity extends Activity {
    static { System.loadLibrary("oe_native"); }

    // Rendering and scene editing execute exclusively on the GL thread.
    private static native void nativeInit();
    private static native void nativeResize(int width, int height);
    private static native void nativeDraw();
    private static native String nativeRenderStats();
    private static native int nativeRecommendQuality(int ramMiB);
    private static native boolean nativeSetQualityProfile(int tier);
    private static native float nativeQualityScale(int tier);
    private static native String[] nativeGetGpuCapabilities();
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
    private static native double[] nativeSelectedCoordinates();
    private static native boolean nativeSetSelectedCoordinates(double x,double y,double z);
    private static native String nativeSceneSnapshot();
    private static native void nativeRoadMode(int mode);
    private static native int nativeRoadBegin(float width,boolean markings,int material);
    private static native int nativeRoadAppend(float pixelX,float pixelY,float minSpacing);
    private static native boolean nativeRoadFinish();
    private static native boolean nativeRoadPickHandle(float pixelX,float pixelY);
    private static native boolean nativeRoadDragHandle(float pixelX,float pixelY);
    private static native void nativeRoadEndDrag();
    private static native boolean nativeRoadSetWidth(float width);
    private static native boolean nativeRoadSetMarkings(boolean markings);
    private static native boolean nativeRoadSetMaterial(int material);
    private static native void nativeRoadMedievalPreset();
    private static native int nativeRoadTogglePedestrian(int feature);
    private static native boolean nativeRoadDelete();
    private static native void nativeRoadSelect(int roadId);
    private static native int[] nativeRoadIds();
    private static native String[] nativeRoadLabels();
    private static native String nativeRoadInfo();
    private static native String nativeRoadNetworkReport();
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
            float radius, int[] draws, byte[][] images, int[] imageInfo,
            boolean preserveRig);
    private static native String[] nativeLoadedAssetKeys();
    private static native boolean nativeCanRegisterMore(int newUniqueAssets);
    private static native void nativeDiscardUnusedAsset(String key);
    private static native int nativeTrimUnusedAssets();
    private static native boolean nativeReplaceScene(int size, boolean demoRoads,
            int[] kinds, float[] transforms, String[] modelKeys, float[] terrain,
            String[] objectPaintKeys, float[] painting, String[] terrainPaintKeys,
            int[] roadIds, float[] roadWidths, boolean[] roadMarkings,
            int[] roadCounts, int[] roadMaterials, float[] roadCoordinates,
            boolean roadSidewalks, boolean roadCrosswalks);
    private static native boolean nativeRegisterPaintTexture(String key, byte[] rgba,int width,int height);
    private static native boolean nativePaintSelected(String key);
    private static native int nativePaintTerrainAtScreen(float x,float y,float radius,String key);
    private static native void nativeChangeSelectedHeight(float dy);
    private static native void nativeScaleSelectedAxis(int axis,float factor);
    private static native void nativeSnapSelectedToGround();
    private static native int nativeApplyTerrainBrushAtScreen(float x,float y,float radius,float amount);
    private static native int nativeTerrainBrushCount();
    private static native void nativeSetGizmoMode(int mode);
    private static native void nativeSetRotationSnap(boolean enabled);
    private static native int nativeGizmoHit(float x,float y);
    private static native boolean nativeGizmoBegin(int axis,float x,float y);
    private static native void nativeGizmoDrag(int axis,float dx,float dy,float x,float y);
    private static native void nativeGizmoEnd();
    private static native void nativeRotateSelectedAxis(int axis,float degrees);
    private static native void nativeResetSelectedRotation();
    private static native double[] nativeSelectedRotationEuler();
    private static native boolean nativeSetSelectedRotationEuler(double x,double y,double z);
    private static native int nativeAddImportedPlaced(String key, float x, float z, float yaw, float size);
    private static final int PICK_GLB = 702, PICK_PROJECT=703, SAVE_PROJECT=704, PICK_PAINT=705,
            PICK_LIBRARY=AssetLibrary.PICK_LIBRARY;
    private static final int MAX_ASSETS = 32;

    private EditorSurface surface;
    private Button roadDoneButton;
    private volatile int roadMode=0; // 0=objects, 1=draw roads, 2=drag road points
    private volatile float roadWidth=6f;
    private volatile boolean roadMarkings=false;
    private volatile int roadMaterial=1; // medieval cobblestone; 0 means legacy asphalt
    private TextView selectionText;
    private TextView statusText;
    private TextView renderStatsText;
    private LinearLayout toolDrawer, paletteItems, inspectorPanel, advancedInspector;
    private boolean drawerOpen=false;
    private volatile int gizmoMode=0;
    private volatile boolean snapRotation15=false;
    private volatile String activePaintKey="";
    private volatile boolean paintTerrainMode=false;
    private volatile float paintRadius=16f;
    private volatile long statsUpdatedAt=0;
    private volatile boolean importing = false;
    private volatile String activeLibraryTab="Деревья";
    private volatile int placingKind = -1;
    private volatile String placingGlb = null;
    private volatile int terrainMode=0; // -1 lower, +1 raise
    private volatile float terrainRadius=96f;
    private volatile float terrainStrength=20f;
    private volatile float moveStep=.001f,turnStep=5f,scaleStep=1.05f;
    private volatile int textureLimit=1024; // Applies to newly imported GLB only.
    private volatile int deviceRamMiB=4096;
    private volatile int qualityChoice=-1; // -1=conservative automatic recommendation
    private volatile int qualityActive=1;
    private volatile String[] gpuCapabilities=null;
    private static final String[] QUALITY_MODES={
        "Авто — с учётом RAM/GPU",
        "Экономия — 55% рендер",
        "Низкое — 70% рендер",
        "Баланс — 85% рендер",
        "Высокое — 100% рендер"
    };
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
        qualityChoice=getPreferences(MODE_PRIVATE).getInt("oe_quality_tier",-1);
        if(qualityChoice<-1||qualityChoice>3)qualityChoice=-1;
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(manager!=null){
            ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(mem);
            deviceRamMiB=(int)Math.max(256,Math.min(131072,mem.totalMem/(1024L*1024L)));
        }
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
        title.setText("OE 0.7.12 / XYZ 360° GIZMO TEST");
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
        roadDoneButton=button("✓ Завершить дорогу",this::finishRoadMode);
        FrameLayout.LayoutParams doneParams=new FrameLayout.LayoutParams(dp(175),dp(46),
                Gravity.TOP|Gravity.RIGHT);
        doneParams.topMargin=dp(52);
        doneParams.rightMargin=dp(8);
        root.addView(roadDoneButton,doneParams);
        roadDoneButton.setVisibility(View.GONE);

        // Left drawer opens on demand and is vertically scrollable.
        toolDrawer=new LinearLayout(this);
        toolDrawer.setOrientation(LinearLayout.VERTICAL);
        toolDrawer.setPadding(dp(7),dp(8),dp(7),dp(8));
        toolDrawer.setBackgroundColor(Color.argb(249,18,26,38));
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryRow=row();
        for(String category:new String[]{"Дома","Деревья","Крепость","Декорации","Окружение",
                "Детали","Декор","Рельеф","Дороги","Текстуры","Проект"}){
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
        addControl(modes,"⟳ XYZ 360°",()->changeGizmoMode(1));
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
        LinearLayout rotateRowX=row();
        addControl(rotateRowX,"🔴 X −90°",()->edit(()->nativeRotateSelectedAxis(0,-90)));
        addControl(rotateRowX,"🔴 X +90°",()->edit(()->nativeRotateSelectedAxis(0,90)));
        advancedInspector.addView(rotateRowX);
        LinearLayout rotateRowZ=row();
        addControl(rotateRowZ,"🔵 Z −90°",()->edit(()->nativeRotateSelectedAxis(2,-90)));
        addControl(rotateRowZ,"🔵 Z +90°",()->edit(()->nativeRotateSelectedAxis(2,90)));
        advancedInspector.addView(rotateRowZ);
        LinearLayout rotateTools=row();
        addControl(rotateTools,"15°: вкл/выкл",this::toggleRotationSnap);
        addControl(rotateTools,"XYZ точно",this::editSelectedRotation);
        addControl(rotateTools,"⟲ Сброс",()->edit(EditorActivity::nativeResetSelectedRotation));
        advancedInspector.addView(rotateTools);
        LinearLayout uprightRow=row();
        addControl(uprightRow,"↥ Поставить на землю",()->
            edit(EditorActivity::nativeSnapSelectedToGround));
        advancedInspector.addView(uprightRow);
        LinearLayout scaleRow=row();
        addControl(scaleRow,"− Размер",()->edit(()->nativeScale(1f/scaleStep)));
        addControl(scaleRow,"+ Размер",()->edit(()->nativeScale(scaleStep)));
        addControl(scaleRow,"Точность",this::choosePrecision);
        advancedInspector.addView(scaleRow);
        LinearLayout millimeterRow=row();
        addControl(millimeterRow,"📍 XYZ вручную",this::editSelectedCoordinates);
        addControl(millimeterRow,"Шаг 1 мм",()->{
            moveStep=.001f;
            message("Точный шаг: 0,001 м (1 мм) · сетка выключена");
        });
        advancedInspector.addView(millimeterRow);
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
            if(placingKind>=0||terrainMode!=0||paintTerrainMode||roadMode!=0)toggleDrawer(false);
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));
        p.setMargins(dp(4),dp(2),dp(4),dp(2));
        paletteItems.addView(b,p);
    }
    private void showPalette(String category){
        paletteItems.removeAllViews();
        if("Дома".equals(category)||"Здания".equals(category)){
            addPaletteOption("+ Таверна",()->enterPlaceMode(0,null));
            addPaletteOption("+ Кузница",()->enterPlaceMode(1,null));
            addPaletteOption("+ Аукционный дом",()->enterPlaceMode(2,null));
            addLibraryButtons(AssetLibrary.HOUSES);
        }else if("Деревья".equals(category)){
            addPaletteOption("🌳 Дерево OE",()->enterPlaceMode(5,null));
            addPaletteOption("🌿 Куст OE",()->enterPlaceMode(8,null));
            addLibraryButtons(AssetLibrary.TREES);
        }else if("Крепость".equals(category)){
            addPaletteOption("▥ Каменная стена OE",()->enterPlaceMode(10,null));
            addPaletteOption("▥ Колонна OE",()->enterPlaceMode(15,null));
            addLibraryButtons(AssetLibrary.FORTRESS);
        }else if("Декорации".equals(category)){
            addPaletteOption("🔥 Фонарь OE",()->enterPlaceMode(3,null));
            addPaletteOption("🪨 Камни OE",()->enterPlaceMode(7,null));
            addPaletteOption("🧺 Лавка OE",()->enterPlaceMode(9,null));
            addLibraryButtons(AssetLibrary.DECOR);
        }else if("Окружение".equals(category)){
            addLibraryButtons(AssetLibrary.ENVIRONMENT);
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
            addPaletteOption("Импорт 3D: GLB / glTF / VOX",this::pickGlb);
        }else if("Рельеф".equals(category)){
            addPaletteOption("⛰ Поднять гору",()->enterTerrainMode(1));
            addPaletteOption("⛰ Опустить землю",()->enterTerrainMode(-1));
            addPaletteOption("📐 Радиус и сила кисти",this::chooseTerrainBrush);
            addPaletteOption("✕ Завершить изменение",this::cancelPlace);
        }else if("Дороги".equals(category)){
            addPaletteOption("🏰 Средневековый пресет улиц",this::applyMedievalRoadPreset);
            addPaletteOption("🪨 Покрытие дороги · брусчатка/грунт/гравий",this::chooseRoadMaterial);
            addPaletteOption("🛣 Новая дорога · рисовать пальцем",this::startRoadDrawing);
            addPaletteOption("✏ Редактировать узлы дорог",this::startRoadEditing);
            addPaletteOption("📏 Ширина дороги",this::chooseRoadWidth);
            addPaletteOption("〰 Разметка · включить/выключить",this::toggleRoadMarkings);
            addPaletteOption("📋 Список дорог и выбор",this::showRoadList);
            addPaletteOption("🔗 Соединения и перекрёстки",this::showRoadConnections);
            addPaletteOption("🚶 Тротуары и бордюры · включить/выключить",
                ()->toggleRoadPedestrian(0));
            addPaletteOption("🚸 Пешеходные переходы · включить/выключить",
                ()->toggleRoadPedestrian(1));
            addPaletteOption("🗑 Удалить выбранную дорогу",this::deleteRoad);
            addPaletteOption("✓ Завершить / выйти",this::finishRoadMode);
        }else if("Текстуры".equals(category)){
            addPaletteOption("🖼 Загрузить свой PNG / JPG",this::pickPaintTexture);
            addPaletteOption("🎨 Мои текстуры",this::choosePaintTexture);
            addPaletteOption("🧱 Нанести на выбранную деталь",this::applyPaintToSelected);
            addPaletteOption("🧽 Очистить текстуру детали",()->edit(()->nativePaintSelected("")));
            addPaletteOption("🖌 Покрасить землю",this::activateTerrainPainting);
            addPaletteOption("📏 Радиус кисти земли",this::choosePaintRadius);
            addPaletteOption("✕ Завершить покраску",this::cancelPlace);
            addPaletteOption("Импорт модели: GLB / glTF / VOX",this::pickGlb);
            addPaletteOption("Качество GLB 1024 / 2048 px",this::chooseTextureLimit);
            addPaletteOption("Форматы и ограничения",()->new AlertDialog.Builder(this)
                .setTitle("Текстуры OE")
                .setMessage("Можно загружать PNG/JPG до 1024px и наносить на объекты или землю. "
                    +"В GLB читаются встроенные изображения и готовые KTX2 ETC2/RGBA8. "
                    +"Draco пока не поддерживается. KTX2 BasisU ETC1S/UASTC LDR -> ETC2 включён в тестовом режиме.")
                .setPositiveButton("Понятно",null).show());
        }else if("Проект".equals(category)){
            addPaletteOption("⚡ Графика и качество",this::chooseDeviceProfile);
            addPaletteOption("🖥 Информация о GPU",this::showDeviceCapabilities);
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
    private void applyMedievalRoadPreset(){
        if(importing){message("Дождись загрузки сцены");return;}
        // Changes only future road defaults and scene-wide modern pavement UI.
        // Existing road geometry and per-road materials remain untouched.
        roadWidth=4f;
        roadMaterial=1; // cobblestone
        roadMarkings=false;
        surface.queueEvent(()->{
            nativeRoadMedievalPreset();
            surface.requestRender();
        });
        message("🏰 Средневековые улицы: брусчатка 4 м, без современной разметки, тротуаров и зебр. Старые дороги не изменены.");
    }
    private void chooseRoadMaterial(){
        if(importing){message("Дождись загрузки сцены");return;}
        final String[] names={
            "🛣 Асфальт · современная дорога",
            "🪨 Брусчатка · жилые улочки и рынок",
            "🐎 Грунт · повозки и деревенские тропы",
            "⛰ Гравий · тракт и окраины",
            "🌙 Тёмные древние плиты · фэнтези квартал"
        };
        new AlertDialog.Builder(this).setTitle("Покрытие улицы")
            .setSingleChoiceItems(names,roadMaterial,(dialog,index)->{
                roadMaterial=index;
                if(index!=0)roadMarkings=false;
                final int chosen=index;
                surface.queueEvent(()->{
                    nativeRoadSetMaterial(chosen);
                    surface.requestRender();
                });
                message("Покрытие новых и выбранной дороги: "+names[index]);
                dialog.dismiss();
            }).setNegativeButton("Отмена",null).show();
    }
    private void startRoadDrawing(){
        if(importing){message("Заверши загрузку сцены");return;}
        if(roadMode==1)finishRoadMode();
        placingKind=-1;placingGlb=null;terrainMode=0;paintTerrainMode=false;
        roadMode=1;
        roadDoneButton.setVisibility(View.VISIBLE);
        roadDoneButton.setText("✓ Завершить дорогу");
        statusText.setText("ДОРОГА: веди пальцем. Концы соединятся с соседней дорогой до 3 м. Готово — ✓.");
        surface.queueEvent(()->{
            int roadId=nativeRoadBegin(roadWidth,roadMarkings,roadMaterial);
            if(roadId==0){
                ui(()->{
                    roadMode=0;roadDoneButton.setVisibility(View.GONE);
                    message("Достигнут лимит 24 дорог");
                });
            }
            surface.requestRender();
        });
    }
    private void startRoadEditing(){
        if(importing){message("Заверши загрузку сцены");return;}
        if(roadMode==1)finishRoadMode();
        placingKind=-1;placingGlb=null;terrainMode=0;paintTerrainMode=false;
        roadMode=2;
        roadDoneButton.setVisibility(View.VISIBLE);
        roadDoneButton.setText("✓ Закрыть дороги");
        statusText.setText("ДОРОГИ: тяни оранжевые узлы пальцем. Выбирай дороги через список.");
        surface.queueEvent(()->{nativeRoadMode(2);surface.requestRender();});
    }
    private void finishRoadMode(){
        final int previous=roadMode;
        if(previous==0)return;
        roadMode=0;roadDoneButton.setVisibility(View.GONE);
        roadDoneButton.setText("✓ Завершить дорогу");
        surface.queueEvent(()->{
            if(previous==1&&!nativeRoadFinish())
                message("Для дороги нужны хотя бы две разные точки; заготовка удалена");
            nativeRoadMode(0);nativeRoadEndDrag();
            surface.requestRender();
        });
        statusText.setText("Режим объектов · каталог → Дороги для редактирования");
    }
    private void chooseRoadWidth(){
        final String[] labels={"2 м · тропинка","4 м · узкая улица","6 м · обычная дорога",
                "8 м · широкая дорога","12 м · проспект","18 м · большая трасса"};
        final float[] widths={2,4,6,8,12,18};
        new AlertDialog.Builder(this).setTitle("Ширина дороги")
            .setSingleChoiceItems(labels,-1,(dialog,index)->{
                roadWidth=widths[index];
                final float w=roadWidth;
                surface.queueEvent(()->{
                    nativeRoadSetWidth(w);
                    surface.requestRender();
                });
                message("Ширина новых дорог: "+(int)roadWidth+" м");
                dialog.dismiss();
            }).setNegativeButton("Отмена",null).show();
    }
    private void toggleRoadMarkings(){
        if(importing){message("Дождись загрузки сцены");return;}
        final boolean enabled=!roadMarkings;
        surface.queueEvent(()->{
            final boolean updated=nativeRoadSetMarkings(enabled);
            surface.requestRender();
            ui(()->{
                if(updated){
                    roadMarkings=enabled;
                    message(enabled?"Разметка включена":"Разметка выключена");
                }else{
                    // Modern paint stripes are intentionally absent on medieval surfaces.
                    message("Современная разметка доступна только на асфальтовой дороге. Выбери её в списке.");
                }
            });
        });
    }
    private void toggleRoadPedestrian(int feature){
        if(importing||surface==null){message("Дождись загрузки сцены");return;}
        surface.queueEvent(()->{
            final int enabled=nativeRoadTogglePedestrian(feature);
            surface.requestRender();
            if(enabled>=0)
                message((feature==0?"Тротуары и бордюры":"Пешеходные переходы")+
                        (enabled==1?" включены":" выключены")+" · сохранится с проектом");
        });
    }
    private void showRoadConnections(){
        if(importing||surface==null)return;
        surface.queueEvent(()->{
            final String report=nativeRoadNetworkReport();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("OE · Автоматические перекрёстки")
                .setMessage(report+"\n\nПересекающиеся дороги соединяются автоматически. "+
                    "При завершении дороги её концы притягиваются к существующей "+
                    "трассе на расстоянии до 3 метров.")
                .setPositiveButton("Готово",null).show());
        });
    }
    private void showRoadList(){
        if(importing)return;
        surface.queueEvent(()->{
            final String[] labels=nativeRoadLabels();
            final int[] ids=nativeRoadIds();
            ui(()->{
                if(labels==null||ids==null||ids.length!=labels.length||ids.length==0){
                    message("Дорог пока нет. Начни с «Новая дорога»");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Дороги сцены")
                    .setItems(labels,(d,i)->{
                        startRoadEditing();
                        surface.queueEvent(()->{
                            nativeRoadSelect(ids[i]);
                            final String info=nativeRoadInfo();
                            surface.requestRender();
                            message(info.replace("\n"," · "));
                        });
                    }).setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void deleteRoad(){
        if(importing){message("Дождись загрузки сцены");return;}
        new AlertDialog.Builder(this).setTitle("Удалить выбранную дорогу?")
            .setMessage("Удаляется только выбранная дорога. Действие можно отменить.")
            .setPositiveButton("Удалить",(d,which)->edit(()->{
                if(nativeRoadDelete())message("Дорога удалена");
                else message("Сначала выбери дорогу через список");
            }))
            .setNegativeButton("Отмена",null).show();
    }
    private void changeGizmoMode(int mode){
        gizmoMode=mode;
        surface.queueEvent(()->{nativeSetGizmoMode(mode);surface.requestRender();});
        statusText.setText(mode==0?"3D-оси: тяни X (красная), Y (зелёная), Z (синяя)":
            mode==1?"Вращение XYZ 360°: тяни красное (X), зелёное (Y), синее (Z) кольцо. В «Точно» есть шаг 15° и ±90°.":
                     "3D-оси: тяни цветной маркер для изменения масштаба по выбранной оси");
    }
    private void enterPlaceMode(int kind, String asset) {
        if(roadMode!=0)finishRoadMode();
        if(importing) { message("Сначала дождись завершения импорта"); return; }
        terrainMode=0;paintTerrainMode=false;
        placingKind = kind;
        placingGlb = asset;
        statusText.setText("РАЗМЕЩЕНИЕ: коснись свободной земли, чтобы поставить объект. Для отмены нажми «Отмена».");
        message("Коснись места на карте для размещения");
    }
    private void cancelPlace() {
        if(roadMode!=0)finishRoadMode();
        paintTerrainMode=false;
        terrainMode=0;
        placingKind = -1;
        placingGlb = null;
        statusText.setText("Режим выбора · два пальца — перемещение; кнопки «Размер карты» и «Координаты» — большой мир.");
        message("Размещение отменено");
    }
    private void enterTerrainMode(int direction){
        if(roadMode!=0)finishRoadMode();
        if(importing){message("Дождись завершения операции");return;}
        placingKind=-1;placingGlb=null;paintTerrainMode=false;
        terrainMode=direction;
        statusText.setText(direction>0?
            "ГОРА: коснись земли. Кисть радиус "+(int)terrainRadius+" м, высота +"+(int)terrainStrength+" м. Отмена ↶ работает.":
            "ВПАДИНА: коснись земли. Кисть радиус "+(int)terrainRadius+" м, глубина −"+(int)terrainStrength+" м.");
        message("Коснись земли для деформации · максимум 64 мазка на карту");
    }
    private void selectDeviceQuality(int selected){
        if(selected< -1||selected>3)return;
        qualityChoice=selected;
        getPreferences(MODE_PRIVATE).edit().putInt("oe_quality_tier",selected).apply();
        if(surface==null)return;
        surface.queueEvent(()->{
            final int tier=selected<0?nativeRecommendQuality(deviceRamMiB):selected;
            if(!nativeSetQualityProfile(tier))return;
            qualityActive=tier;
            textureLimit=tier==3?2048:1024;
            final float scale=nativeQualityScale(tier);
            ui(()->{
                surface.applyRenderScale(scale);
                if(statusText!=null)statusText.setText("Графика: "+
                    QUALITY_MODES[tier+1]+(selected<0?" · автоподбор":" · вручную"));
            });
            surface.requestRender();
        });
    }
    private void chooseDeviceProfile(){
        if(importing){message("Заверши импорт проекта");return;}
        new AlertDialog.Builder(this)
            .setTitle("Качество графики OE")
            .setSingleChoiceItems(QUALITY_MODES,qualityChoice+1,
                (dialog,which)->{selectDeviceQuality(which-1);dialog.dismiss();})
            .setNeutralButton("Видеочип",(dialog,which)->showDeviceCapabilities())
            .setNegativeButton("Отмена",null).show();
    }
    private void showDeviceCapabilities(){
        if(surface==null)return;
        surface.queueEvent(()->{
            final String[] caps=nativeGetGpuCapabilities();
            gpuCapabilities=caps;
            if(caps==null||caps.length<6){message("Данные GPU пока недоступны");return;}
            final int recommended=nativeRecommendQuality(deviceRamMiB);
            final String report="GPU: "+caps[1]+"\n"+
                "Производитель: "+caps[0]+"\n"+
                "OpenGL ES: "+caps[2]+"\n"+
                "Макс. текстура: "+caps[3]+" px\n"+
                "ASTC: "+("yes".equals(caps[4])?"поддерживается":"не обнаружен")+"\n"+
                "Texture units: "+caps[5]+"\n"+
                "ОЗУ: "+deviceRamMiB+" МиБ\n"+
                "Рекомендация: "+QUALITY_MODES[recommended+1]+"\n\n"+
                "Это проверка возможностей GPU, а не тест FPS. "+
                "Реальную скорость и нагрев нужно измерять на устройстве.";
            ui(()->new AlertDialog.Builder(this)
                .setTitle("Графика и оборудование")
                .setMessage(report).setPositiveButton("Готово",null)
                .setNeutralButton("Выбрать профиль",(dialog,which)->chooseDeviceProfile())
                .show());
        });
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
        final String[] choices={
            "1 мм · 0,001 м",
            "5 мм · 0,005 м",
            "1 см · 0,010 м",
            "5 см · 0,050 м",
            "10 см · 0,100 м",
            "25 см · 0,250 м",
            "50 см · 0,500 м",
            "1 м · 1,000 м"};
        final float[] moves={.001f,.005f,.01f,.05f,.10f,.25f,.5f,1f};
        new AlertDialog.Builder(this).setTitle("Точность без сетки")
            .setItems(choices,(dialog,index)->{
                moveStep=moves[index];
                turnStep=index<=3?5f:15f;
                scaleStep=index<=3?1.05f:1.10f;
                message("Шаг перемещения: "+choices[index]+" · без привязки к сетке");
            }).show();
    }
    private void toggleRotationSnap(){
        snapRotation15=!snapRotation15;
        final boolean enabled=snapRotation15;
        surface.queueEvent(()->{
            nativeSetRotationSnap(enabled);
            surface.requestRender();
        });
        statusText.setText(enabled?
            "XYZ 360° · угловая привязка каждые 15° ВКЛ":
            "XYZ 360° · свободное вращение, без привязки");
        message(enabled?"Вращение с шагом 15°":"Свободное вращение 360°");
    }
    private void editSelectedRotation(){
        if(importing){message("Заверши импорт перед настройкой поворота");return;}
        surface.queueEvent(()->{
            final double[] xyz=nativeSelectedRotationEuler();
            if(xyz==null){message("Сначала выбери дом или 3D-модель");return;}
            ui(()->{
                LinearLayout form=new LinearLayout(this);
                form.setOrientation(LinearLayout.VERTICAL);
                form.setPadding(dp(22),dp(8),dp(22),dp(4));
                final EditText[] fields=new EditText[3];
                final String[] names={"X — наклон", "Y — поворот вокруг вертикали",
                    "Z — боковой крен"};
                final int mode=InputType.TYPE_CLASS_NUMBER|
                    InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;
                for(int i=0;i<3;i++){
                    EditText field=new EditText(this);
                    field.setInputType(mode);field.setSingleLine(true);
                    field.setText(String.format(Locale.US,"%.2f",xyz[i]));
                    field.setHint(names[i]+" (градусы)");
                    field.setSelectAllOnFocus(true);
                    form.addView(field);fields[i]=field;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Профессиональный поворот X / Y / Z")
                    .setMessage("Углы в градусах: ±90° ставит лежащий дом вертикально. "
                        +"360° — полный оборот. Точный поворот сохраняется в проекте.")
                    .setView(form)
                    .setPositiveButton("Применить",(dialog,which)->{
                        try{
                            double x=Double.parseDouble(fields[0].getText().toString()
                                .trim().replace(',','.'));
                            double y=Double.parseDouble(fields[1].getText().toString()
                                .trim().replace(',','.'));
                            double z=Double.parseDouble(fields[2].getText().toString()
                                .trim().replace(',','.'));
                            if(!Double.isFinite(x)||!Double.isFinite(y)||
                               !Double.isFinite(z)||Math.abs(x)>36000||
                               Math.abs(y)>36000||Math.abs(z)>36000)
                                throw new NumberFormatException();
                            edit(()->{
                                if(!nativeSetSelectedRotationEuler(x,y,z))
                                    message("Не удалось выставить углы XYZ");
                            });
                        }catch(NumberFormatException ex){
                            message("Введи числовые углы X, Y, Z в градусах");
                        }
                    }).setNegativeButton("Отмена",null).show();
            });
        });
    }
    private void editSelectedCoordinates(){
        if(importing){message("Заверши импорт перед редактированием");return;}
        surface.queueEvent(()->{
            final double[] xyz=nativeSelectedCoordinates();
            if(xyz==null){
                message("Сначала выбери деталь, здание или GLB");
                return;
            }
            ui(()->{
                LinearLayout form=new LinearLayout(this);
                form.setOrientation(LinearLayout.VERTICAL);
                form.setPadding(dp(22),dp(8),dp(22),dp(4));
                final EditText[] fields=new EditText[3];
                final String[] names={"X — вправо / влево","Y — вверх / вниз",
                    "Z — вперёд / назад"};
                final int mode=InputType.TYPE_CLASS_NUMBER|
                    InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;
                for(int i=0;i<3;i++){
                    EditText field=new EditText(this);
                    field.setInputType(mode);
                    field.setSingleLine(true);
                    field.setText(String.format(Locale.US,"%.3f",xyz[i]));
                    field.setSelectAllOnFocus(true);
                    field.setHint(names[i]+" (метры)");
                    form.addView(field);
                    fields[i]=field;
                }
                new AlertDialog.Builder(this).setTitle("Точные координаты X / Y / Z")
                    .setMessage("В метрах: 0,001 = 1 мм. Свободное размещение без сетки. "
                        +"Предел X/Z — размер мира, Y от −24 до 160 м.")
                    .setView(form)
                    .setPositiveButton("Применить",(dialog,which)->{
                        try{
                            final double x=Double.parseDouble(fields[0].getText()
                                .toString().trim().replace(',','.'));
                            final double y=Double.parseDouble(fields[1].getText()
                                .toString().trim().replace(',','.'));
                            final double z=Double.parseDouble(fields[2].getText()
                                .toString().trim().replace(',','.'));
                            if(!Double.isFinite(x)||!Double.isFinite(y)||
                                !Double.isFinite(z))throw new NumberFormatException();
                            edit(()->{
                                if(nativeSetSelectedCoordinates(x,y,z))
                                    message("Координаты обновлены · точность 0,001 м");
                                else message("Координаты вне мира или объект не выбран");
                            });
                        }catch(NumberFormatException invalid){
                            message("Введите корректные координаты X, Y и Z");
                        }
                    })
                    .setNegativeButton("Отмена",null)
                    .show();
            });
        });
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
    private File originalDir() throws Exception {
        File dir=new File(getFilesDir(),"originals");
        if(!dir.exists()&&!dir.mkdirs())
            throw new Exception("Не создать каталог оригиналов 3D");
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
    private File textureDir() throws Exception {
        File folder=new File(getFilesDir(),"textures");
        if(!folder.exists()&&!folder.mkdirs())throw new Exception("Не создать папку текстур");
        return folder;
    }
    private static String textureType(byte[] data) throws Exception {
        if(data.length>=8&&(data[0]&255)==137&&data[1]==80&&data[2]==78&&data[3]==71)
            return "image/png";
        if(data.length>=4&&(data[0]&255)==255&&(data[1]&255)==216)
            return "image/jpeg";
        throw new Exception("Выбери настоящую PNG/JPG текстуру");
    }
    private static String textureKey(byte[] bytes,String mime) throws Exception {
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder("tx_");
        final char[] alphabet="0123456789abcdef".toCharArray();
        for(int i=0;i<12;i++){
            out.append(alphabet[(sha[i]>>>4)&15]);
            out.append(alphabet[sha[i]&15]);
        }
        return out.append("image/png".equals(mime)?".png":".jpg").toString();
    }
    private static boolean validTexture(String key){
        return key!=null&&key.matches("tx_[0-9a-f]{24}\\.(png|jpg)");
    }
    private byte[] limitedTextureRead(InputStream stream) throws Exception {
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new Exception("Нельзя открыть изображение");
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>ProjectArchive.MAX_TEXTURE_BYTES)
                    throw new Exception("Максимум 8 МБ на текстуру");
                out.write(buffer,0,n);
            }
            return out.toByteArray();
        }
    }
    private void pickPaintTexture(){
        if(importing){message("Дождись загрузки");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try{startActivityForResult(intent,PICK_PAINT);}
        catch(Exception e){message("Не открыть галерею: "+e.getMessage());}
    }
    private void importPaintTexture(Uri uri){
        if(importing)return;
        importing=true;
        new Thread(()->{
            try{
                byte[] original=limitedTextureRead(getContentResolver().openInputStream(uri));
                String mime=textureType(original);
                String key=textureKey(original,mime);
                TextureReader.Image image=TextureReader.decode(original,mime,1024);
                File destination=new File(textureDir(),key);
                if(!destination.exists()){
                    File partial=new File(textureDir(),key+".part");
                    try{
                        Files.write(partial.toPath(),original);
                        Files.move(partial.toPath(),destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    }finally{Files.deleteIfExists(partial.toPath());}
                }else{
                    byte[] previous=Files.readAllBytes(destination.toPath());
                    if(!textureKey(previous,textureType(previous)).equals(key))
                        throw new Exception("Файл текстуры повреждён: "+key);
                }
                surface.queueEvent(()->{
                    boolean ok=nativeRegisterPaintTexture(key,image.bytes,image.width,image.height);
                    importing=false;
                    if(ok){
                        activePaintKey=key;
                        message("Текстура добавлена · теперь нанеси её на деталь или землю");
                        surface.requestRender();
                    }else message("Лимит текстур/памяти GPU. Максимум 8 изображений.");
                });
            }catch(Exception err){
                importing=false;message("Текстура не добавлена: "+err.getMessage());
            }
        },"oe-paint-image-import").start();
    }
    private void usePaintTexture(String key){
        if(importing)return;
        importing=true;
        new Thread(()->{
            try{
                if(!validTexture(key))throw new Exception("Недопустимое имя");
                File file=new File(textureDir(),key);
                if(!file.isFile()||file.length()>ProjectArchive.MAX_TEXTURE_BYTES)
                    throw new Exception("Текстура не найдена");
                byte[] source=Files.readAllBytes(file.toPath());
                String mime=textureType(source);
                if(!key.equals(textureKey(source,mime)))
                    throw new Exception("Контрольная сумма не совпадает");
                TextureReader.Image image=TextureReader.decode(source,mime,1024);
                surface.queueEvent(()->{
                    boolean ok=nativeRegisterPaintTexture(key,image.bytes,image.width,image.height);
                    importing=false;
                    if(ok){activePaintKey=key;message("Выбрана текстура: "+key);}
                    else message("Не удалось загрузить текстуру в GPU");
                });
            }catch(Exception e){importing=false;message("Не открыть текстуру: "+e.getMessage());}
        },"oe-load-paint").start();
    }
    private void choosePaintTexture(){
        try{
            File[] files=textureDir().listFiles((dir,name)->validTexture(name));
            if(files==null||files.length==0){
                message("Сначала импортируй PNG/JPG");return;
            }
            java.util.Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
            String[] names=new String[files.length];
            for(int i=0;i<files.length;i++)
                names[i]="🎨 "+files[i].getName().substring(3,15);
            new AlertDialog.Builder(this).setTitle("Мои текстуры")
                .setItems(names,(dialog,which)->usePaintTexture(files[which].getName()))
                .setNegativeButton("Отмена",null).show();
        }catch(Exception e){message("Библиотека текстур: "+e.getMessage());}
    }
    private void applyPaintToSelected(){
        String key=activePaintKey;
        if(key.isEmpty()){message("Сначала выбери PNG/JPG в каталоге");return;}
        edit(()->{
            if(nativePaintSelected(key))message("Текстура нанесена на выбранную деталь");
            else message("Выбери объект или загрузить текстуру повторно");
        });
    }
    private void activateTerrainPainting(){
        if(activePaintKey.isEmpty()){
            message("Сначала загрузи и выбери PNG/JPG");return;
        }
        terrainMode=0;placingKind=-1;placingGlb=null;
        paintTerrainMode=true;
        statusText.setText("КИСТЬ ТЕКСТУР: касайся земли · радиус "+(int)paintRadius+
            "м · до 48 мазков · ↶ отменить");
    }
    private void choosePaintRadius(){
        final String[] labels={"Маленькая · 4м","8м","16м","32м","64м"};
        final float[] values={4,8,16,32,64};
        new AlertDialog.Builder(this).setTitle("Размер кисти текстуры")
            .setItems(labels,(dialog,n)->{
                paintRadius=values[n];
                message("Радиус кисти: "+(int)paintRadius+"м");
            }).show();
    }
    private void exportProject(){
        if(importing){message("Дождись завершения операции");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Some Android document providers label ZIPs as octet-stream;
        // validate the actual archive content after selection.
        intent.setType("*/*");
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
                        ProjectArchive.exportProject(scene,modelDir(),textureDir(),originalDir(),stream);
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
                    new Thread(() -> readAndPrepareScene(cached,staged.sceneFile,staged.modelDir,
                    staged.textureDir,staged.sourceDir,staged.root),
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
    private void addLibraryButtons(String category){
        addPaletteOption("📚 Встроенные модели + свои ZIP",()->
            showLibraryObjects(category));
        addPaletteOption("📦 Добавить набор моделей ZIP",()->
            pickLibrary(category));
    }
    private void pickLibrary(String category){
        if(importing){message("Импорт ещё выполняется");return;}
        activeLibraryTab=category;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_LIBRARY);}
        catch(Exception e){message("Файловый менеджер недоступен: "+e.getMessage());}
    }
    private void installLibrary(Uri uri){
        if(importing)return;
        importing=true;
        message("📦 Добавляю библиотеку; сами модели пока не загружаю в GPU");
        new Thread(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new Exception("ZIP не найден");
                final int count=AssetLibrary.install(input,getFilesDir());
                importing=false;
                message("✅ Библиотека добавлена: "+count+
                        " отдельных моделей. Выбери раздел каталога.");
                ui(()->showPalette(activeLibraryTab));
            }catch(Exception e){
                importing=false;
                message("Не удалось добавить ZIP: "+e.getMessage());
            }
        },"oe-library-install").start();
    }
    private void showLibraryObjects(String category){
        if(importing){message("Дождись завершения загрузки");return;}
        activeLibraryTab=category;
        new Thread(()->{
            try{
                // Seed all 105 verified Kenney objects from APK once.
                // Runs in the library worker, never on the UI or GLES thread.
                AssetLibrary.ensureBundled(getAssets(),getFilesDir());
                AssetLibrary.ensureMedievalAddon(getAssets(),getFilesDir());
                final java.util.List<AssetLibrary.Entry> items=
                        AssetLibrary.list(getFilesDir(),category);
                ui(()->{
                    if(items.isEmpty()){
                        message("В этой категории пока нет моделей. Попробуй другую или добавь отдельный ZIP.");
                        return;
                    }
                    String[] labels=new String[items.size()];
                    for(int i=0;i<items.size();++i)
                        labels[i]=items.get(i).label();
                    new AlertDialog.Builder(this)
                       .setTitle(category+" · "+items.size()+" отдельных объектов")
                       .setItems(labels,(dialog,index)->selectLibraryObject(items.get(index)))
                       .setNegativeButton("Назад",null).show();
                });
            }catch(Exception e){message("Каталог: "+e.getMessage());}
        },"oe-library-list").start();
    }
    private void selectLibraryObject(AssetLibrary.Entry entry){
        if(importing){message("Подожди: предыдущая модель загружается");return;}
        importing=true;
        message("Загружаю отдельно: "+entry.label());
        new Thread(()->{
            try{
                final byte[] bytes=AssetLibrary.load(entry);
                final String key=GlbReader.key(bytes);
                final File target=new File(modelDir(),key);
                // A selected model becomes part of the normal content-addressed
                // OE model store; project .oezip includes it unchanged.
                if(!target.exists()){
                    final File part=new File(modelDir(),key+".part");
                    try{
                        Files.write(part.toPath(),bytes);
                        Files.move(part.toPath(),target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    }finally{Files.deleteIfExists(part.toPath());}
                }
                surface.queueEvent(()->{
                    if(Arrays.asList(nativeLoadedAssetKeys()).contains(key)){
                        importing=false;
                        ui(()->enterPlaceMode(6,key));
                        message("Выбрана: "+entry.label());
                        return;
                    }
                    // A small GPU cache is separate from the ZIP catalog. Only
                    // models not used by the scene or undo/redo may be evicted.
                    nativeTrimUnusedAssets();
                    if(!nativeCanRegisterMore(1)){
                        importing=false;
                        message("Достигнут лимит "+MAX_ASSETS+
                                " уникальных моделей в сцене. Копии уже размещённых объектов не расходуют слот.");
                        return;
                    }
                    new Thread(()->{
                        try{
                            final GlbReader.Result mesh=GlbReader.decode(bytes,textureLimit);
                            surface.queueEvent(()->{
                                final boolean ok=nativeRegisterAsset(key,mesh.vertices,
                                        mesh.boundCenterY,mesh.boundRadius,mesh.draws,
                                        mesh.imageData,mesh.imageInfo,mesh.previewOmitsRig);
                                importing=false;
                                if(ok){
                                    ui(()->enterPlaceMode(6,key));
                                    surface.requestRender();
                                    message("✅ "+entry.label()+" готов. Коснись земли.");
                                }else message("GPU отклонил модель: лимит памяти или геометрии.");
                            });
                        }catch(Exception e){
                            importing=false;
                            message("Не загрузить "+entry.label()+": "+e.getMessage());
                        }
                    },"oe-library-decode").start();
                });
            }catch(Exception e){
                importing=false;
                message("Ошибка чтения модели: "+e.getMessage());
            }
        },"oe-library-load").start();
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
        if(requestCode==PICK_LIBRARY){
            if(resultCode==RESULT_OK&&intent!=null&&intent.getData()!=null)
                installLibrary(intent.getData());
            return;
        }
        if(requestCode==PICK_PAINT){
            if(resultCode==RESULT_OK&&intent!=null&&intent.getData()!=null)
                importPaintTexture(intent.getData());
            return;
        }
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
        message("Распознаю GLB / glTF / VOX…");
        new Thread(() -> {
            try {
                InputStream stream=getContentResolver().openInputStream(uri);
                if(stream==null)throw new Exception("Не удалось открыть файл");
                byte[] original=limitedRead(stream);
                UniversalModelReader.Converted converted=UniversalModelReader.convert(original);
                byte[] bytes=converted.glb;
                final String key=GlbReader.key(bytes);
                // Canonical GLB is the renderer/project asset. The exact original
                // VOX/glTF bytes are stored separately and exported in .oezip.
                File destination=new File(modelDir(),key);
                if(!destination.exists()){
                    File partial=new File(modelDir(),key+".part");
                    try{
                        Files.write(partial.toPath(),bytes);
                        Files.move(partial.toPath(),destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    }finally{Files.deleteIfExists(partial.toPath());}
                }
                if(!"glb".equals(converted.originalType)){
                    String originalName=key.substring(0,key.length()-4)+"."+converted.originalType;
                    File savedOriginal=new File(originalDir(),originalName);
                    if(!savedOriginal.exists()){
                        File partial=new File(originalDir(),originalName+".part");
                        try{
                            Files.write(partial.toPath(),original);
                            Files.move(partial.toPath(),savedOriginal.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }finally{Files.deleteIfExists(partial.toPath());}
                    }
                }
                // GPU asset lookup MUST run on the GL thread; do not decode a
                // large file again when the content-addressed resource exists.
                surface.queueEvent(() -> {
                    if(Arrays.asList(nativeLoadedAssetKeys()).contains(key)){
                        importing=false;
                        ui(() -> enterPlaceMode(6,key));
                        message("Модель уже загружена — использую меш из памяти");
                    }else{
                        new Thread(() -> {
                            try{
                                final GlbReader.Result model=GlbReader.decode(bytes,textureLimit);
                                surface.queueEvent(() -> {
                                    boolean success=nativeRegisterAsset(key,model.vertices,
                                            model.boundCenterY,model.boundRadius,
                                            model.draws,model.imageData,model.imageInfo,model.previewOmitsRig);
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
                                            message("Модель импортирована. "+notice);
                                        }else message("Модель готова: "+model.triangles+" треугольников. Коснись карты.");
                                    }else message("Не удалось загрузить модель: память, лимит или GPU");
                                });
                            }catch(Exception e){
                                importing=false;
                                message("Ошибка импорта 3D: "+e.getMessage());
                            }
                        },"oe-glb-decode").start();
                    }
                });
            }catch(Exception e){
                importing=false;
                message("Ошибка импорта 3D: "+e.getMessage());
            }
        },"oe-glb-import").start();
    }
    private static final class SavedEntity {
        int type;
        float x,z,yaw,size,y,sx=1f,sy=1f,sz=1f,qx=0f,qy=0f,qz=0f,qw=1f;
        String asset,paint="";
    }
    private static boolean validAsset(String name) {
        return name!=null && name.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private void loadScene() {
        if(importing){message("Импорт или загрузка уже выполняется");return;}
        if(roadMode!=0)finishRoadMode();
        importing=true;
        // Take a consistent snapshot of the registered GPU assets. The reader
        // thread can skip expensive decode work for those immutable GLBs.
        surface.queueEvent(() -> {
            final HashSet<String> cached=new HashSet<>(Arrays.asList(nativeLoadedAssetKeys()));
            new Thread(() -> readAndPrepareScene(cached,null,null,null,null,null),"oe-scene-load").start();
        });
    }
    private void readAndPrepareScene(HashSet<String> cached,File sourceScene,
            File sourceModels,File sourceTextures,File sourceOriginals,
            File stagingRoot) {
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
            // Strict validation; old OE v1 scenes omit these settings and default to on.
            if((document.has("roadSidewalks")&&
                !(document.get("roadSidewalks") instanceof Boolean))||
               (document.has("roadCrosswalks")&&
                !(document.get("roadCrosswalks") instanceof Boolean)))
                throw new Exception("Неверные параметры тротуаров или переходов");
            final boolean roadSidewalks=document.optBoolean("roadSidewalks",true);
            final boolean roadCrosswalks=document.optBoolean("roadCrosswalks",true);
            final int worldSize=document.optInt("worldSize",1024);
            if(worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&worldSize!=8192&&worldSize!=16384)
                throw new Exception("Неподдерживаемый размер карты");
            JSONArray arr=document.getJSONArray("items");
            if(arr.length()>300)throw new Exception("Слишком много объектов");
            // Backward-compatible version-1 scenes can omit the roads field.
            JSONArray roadsJson=document.optJSONArray("roads");
            if(document.has("roads")&&roadsJson==null)
                throw new Exception("Неверный формат дорог");
            final int roadTotal=roadsJson==null?0:roadsJson.length();
            if(roadTotal>24)throw new Exception("Слишком много дорог (макс. 24)");
            int[] roadIds=new int[roadTotal],roadCounts=new int[roadTotal],
                  roadMaterials=new int[roadTotal];
            float[] roadWidths=new float[roadTotal];
            boolean[] roadMarkings=new boolean[roadTotal];
            float[] roadsRaw=new float[24*48*2];
            int roadRawCount=0;
            HashSet<Integer> seenRoads=new HashSet<>();
            for(int ri=0;ri<roadTotal;ri++){
                JSONArray road=roadsJson.getJSONArray(ri);
                if(road.length()!=4&&road.length()!=5)
                    throw new Exception("Неверная структура дороги "+ri);
                final int material=road.length()==5?road.getInt(4):0;
                if(material<0||material>4)
                    throw new Exception("Неизвестное покрытие дороги "+ri);
                roadMaterials[ri]=material;
                final int id=road.getInt(0),flag=road.getInt(2);
                final double width=road.getDouble(1);
                JSONArray points=road.getJSONArray(3);
                if(id<=0||id==Integer.MAX_VALUE||!seenRoads.add(id)||
                   !Double.isFinite(width)||width<2||width>18||
                   (flag!=0&&flag!=1)||points.length()<2||points.length()>48)
                    throw new Exception("Неверные параметры дороги "+ri);
                roadIds[ri]=id;roadWidths[ri]=(float)width;
                roadMarkings[ri]=(flag==1);roadCounts[ri]=points.length();
                double prevX=0,prevZ=0;
                for(int j=0;j<points.length();j++){
                    JSONArray point=points.getJSONArray(j);
                    if(point.length()!=2)throw new Exception("Неверный узел дороги");
                    double x=point.getDouble(0),z=point.getDouble(1);
                    if(!Double.isFinite(x)||!Double.isFinite(z)||
                       Math.abs(x)>worldSize/2.0||Math.abs(z)>worldSize/2.0)
                        throw new Exception("Точка дороги за границей карты");
                    if(j>0&&(x-prevX)*(x-prevX)+(z-prevZ)*(z-prevZ)<.25*.25)
                        throw new Exception("Слишком близкие узлы дороги");
                    roadsRaw[roadRawCount++]=(float)x;
                    roadsRaw[roadRawCount++]=(float)z;
                    prevX=x;prevZ=z;
                }
            }
            final float[] roadCoordinates=Arrays.copyOf(roadsRaw,roadRawCount);
            SavedEntity[] items=new SavedEntity[arr.length()];
            HashSet<String> assets=new HashSet<>();
            int[] kinds=new int[arr.length()];
            float[] transforms=new float[arr.length()*12];
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
            String[] objectPaintKeys=new String[arr.length()];
            HashSet<String> referencedPaint=new HashSet<>();
            JSONArray paintJson=document.optJSONArray("terrainPaint");
            int paintCount=paintJson==null?0:paintJson.length();
            if(paintCount>48)throw new Exception("Максимум 48 мазков по земле");
            float[] paintBrushes=new float[paintCount*3];
            String[] paintBrushKeys=new String[paintCount];
            for(int i=0;i<paintCount;i++){
                JSONArray brush=paintJson.getJSONArray(i);
                if(brush.length()!=4)throw new Exception("Неверная кисть текстуры");
                for(int j=0;j<3;j++){
                    double value=brush.getDouble(j);
                    if(!Double.isFinite(value))throw new Exception("Неверная координата покраски");
                    paintBrushes[3*i+j]=(float)value;
                }
                float x=paintBrushes[3*i],z=paintBrushes[3*i+1],r=paintBrushes[3*i+2];
                if(Math.abs(x)>worldSize/2f||Math.abs(z)>worldSize/2f||
                    r<4f||r>64f)throw new Exception("Кисть текстуры за пределами карты");
                String key=brush.getString(3);
                if(!validTexture(key))throw new Exception("Неверная текстура кисти");
                paintBrushKeys[i]=key;referencedPaint.add(key);
            }
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
                double qx=object.optDouble("qx",0.0),qy=object.optDouble("qy",0.0),
                       qz=object.optDouble("qz",0.0),qw=object.optDouble("qw",1.0);
                double qlen=qx*qx+qy*qy+qz*qz+qw*qw;
                if(!Double.isFinite(qlen)||qlen<.49||qlen>1.51||
                   Math.abs(qx)>1.01||Math.abs(qy)>1.01||
                   Math.abs(qz)>1.01||Math.abs(qw)>1.01)
                    throw new Exception("Некорректный поворот модели "+i);
                double inv=1.0/Math.sqrt(qlen);
                e.qx=(float)(qx*inv);e.qy=(float)(qy*inv);
                e.qz=(float)(qz*inv);e.qw=(float)(qw*inv);
                e.paint=object.optString("paint","");
                if(!e.paint.isEmpty()){
                    if(!validTexture(e.paint))throw new Exception("Неверная текстура объекта "+i);
                    referencedPaint.add(e.paint);
                }
                objectPaintKeys[i]=e.paint;
                if(e.type==6){
                    e.asset=object.getString("asset");
                    if(!validAsset(e.asset))throw new Exception("Неверная ссылка модели");
                    assets.add(e.asset);
                }
                items[i]=e;
                kinds[i]=e.type;
                transforms[i*12]=e.x;
                transforms[i*12+1]=e.z;
                transforms[i*12+2]=e.yaw;
                transforms[i*12+3]=e.size;
                transforms[i*12+4]=e.y;
                transforms[i*12+5]=e.sx;
                transforms[i*12+6]=e.sy;
                transforms[i*12+7]=e.sz;
                transforms[i*12+8]=e.qx;
                transforms[i*12+9]=e.qy;
                transforms[i*12+10]=e.qz;
                transforms[i*12+11]=e.qw;
                modelKeys[i]=e.asset;
            }
            if(assets.size()>MAX_ASSETS)throw new Exception("Слишком много разных GLB-моделей");
            if(referencedPaint.size()>8)throw new Exception("Слишком много текстур (макс. 8)");
            Map<String,TextureReader.Image> paintImages=new LinkedHashMap<>();
            for(String key:referencedPaint){
                File texture=new File(sourceTextures==null?textureDir():sourceTextures,key);
                if(!texture.isFile()||texture.length()<=0||
                   texture.length()>ProjectArchive.MAX_TEXTURE_BYTES)
                    throw new Exception("Файл текстуры не найден: "+key);
                byte[] bytes=Files.readAllBytes(texture.toPath());
                String mime=textureType(bytes);
                if(!key.equals(textureKey(bytes,mime)))
                    throw new Exception("Повреждена текстура: "+key);
                paintImages.put(key,TextureReader.decode(bytes,mime,1024));
            }
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
            if(sourceTextures!=null){
                File targetFolder=textureDir();
                for(String key:referencedPaint){
                    File target=new File(targetFolder,key);
                    if(target.exists()){
                        byte[] current=Files.readAllBytes(target.toPath());
                        if(!key.equals(textureKey(current,textureType(current))))
                            throw new Exception("Повреждена существующая текстура: "+key);
                    }else{
                        File partial=new File(targetFolder,key+".part");
                        try{
                            Files.copy(new File(sourceTextures,key).toPath(),partial.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                            Files.move(partial.toPath(),target.toPath());
                        }finally{Files.deleteIfExists(partial.toPath());}
                    }
                }
            }
            if(sourceOriginals!=null){
                // Archive import has already checked that each unmodified source
                // corresponds to the exact content-addressed GLB in this scene.
                File[] originals=sourceOriginals.listFiles();
                if(originals==null)throw new Exception("Нет каталога исходных моделей");
                File target=originalDir();
                for(File original:originals){
                    if(!original.isFile())throw new Exception("Неверный источник модели");
                    File installed=new File(target,original.getName());
                    if(installed.isFile()){
                        if(!Arrays.equals(Files.readAllBytes(original.toPath()),
                                Files.readAllBytes(installed.toPath())))
                            throw new Exception("Различаются исходные модели с одним именем");
                    }else{
                        File partial=new File(target,original.getName()+".part");
                        try{
                            Files.copy(original.toPath(),partial.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                            Files.move(partial.toPath(),installed.toPath());
                        }finally{Files.deleteIfExists(partial.toPath());}
                    }
                }
            }
            surface.queueEvent(() -> {
                ArrayList<String> uploaded=new ArrayList<>();
                boolean committed=false;
                try {
                    for(Map.Entry<String,TextureReader.Image> entry:paintImages.entrySet()){
                        TextureReader.Image img=entry.getValue();
                        if(!nativeRegisterPaintTexture(entry.getKey(),img.bytes,img.width,img.height))
                            throw new Exception("Лимит памяти пользовательских текстур: "+entry.getKey());
                    }
                    if(!nativeCanRegisterMore(missing.size()))
                        throw new Exception("Недостаточно свободных слотов GLB (макс. 12)");
                    for(Map.Entry<String,GlbReader.Result> entry:missing.entrySet()){
                        GlbReader.Result mesh=entry.getValue();
                        if(!nativeRegisterAsset(entry.getKey(),mesh.vertices,
                                mesh.boundCenterY,mesh.boundRadius,
                                mesh.draws,mesh.imageData,mesh.imageInfo,mesh.previewOmitsRig))
                            throw new Exception("Ошибка загрузки GPU: "+entry.getKey());
                        uploaded.add(entry.getKey());
                    }
                    if(!nativeReplaceScene(worldSize,demoRoads,kinds,transforms,modelKeys,
                            terrainBrushes,objectPaintKeys,paintBrushes,paintBrushKeys,
                            roadIds,roadWidths,roadMarkings,roadCounts,roadMaterials,roadCoordinates,
                            roadSidewalks,roadCrosswalks))
                        throw new Exception("Сцена не прошла полную проверку в движке");
                    committed=true;
                    ui(()->{
                        roadMode=0;
                        roadDoneButton.setVisibility(View.GONE);
                    });
                    surface.requestRender();
                    refreshSelected();
                    message("Сцена загружена · "+items.length+" объектов, текстур "+referencedPaint.size()+", GLB "+assets.size()+
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
        // Render into a scaled GLES buffer, keep touch/viewport full screen.
        private volatile float renderScale=.70f;
        void applyRenderScale(float newScale){
            if(!Float.isFinite(newScale)||newScale<.5f||newScale>1.0f)return;
            renderScale=newScale;
            int w=getWidth(),h=getHeight();
            if(w>0&&h>0)
                getHolder().setFixedSize(Math.max(1,Math.round(w*renderScale)),
                    Math.max(1,Math.round(h*renderScale)));
            requestRender();
        }
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
                    gpuCapabilities=nativeGetGpuCapabilities();
                    final int tier=qualityChoice<0?nativeRecommendQuality(deviceRamMiB):qualityChoice;
                    if(nativeSetQualityProfile(tier)){
                        qualityActive=tier;
                        textureLimit=tier==3?2048:1024;
                        final float scale=nativeQualityScale(tier);
                        ui(()->applyRenderScale(scale));
                    }
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
                        final String stats=nativeRenderStats()+" · 3D "+
                            Math.round(renderScale*100.f)+"%";
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
                getHolder().setFixedSize(Math.max(1,Math.round(w*renderScale)),
                                         Math.max(1,Math.round(h*renderScale)));
            }
        }
        private float pinch(MotionEvent e) {
            return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));
        }
        private boolean handleRoadTouch(MotionEvent e){
            final int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                multiTouch=false;
                lastX=downX=e.getX();lastY=downY=e.getY();
                final float px=surfaceX(lastX),py=surfaceY(lastY);
                if(roadMode==1)queueEvent(()->{
                    nativeRoadAppend(px,py,.5f);interactiveRender();
                });
                else queueEvent(()->{
                    nativeRoadPickHandle(px,py);interactiveRender();
                });
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN){
                multiTouch=true;
                queueEvent(EditorActivity::nativeRoadEndDrag);
                if(e.getPointerCount()>=2){
                    lastPinch=pinch(e);
                    lastMidX=(e.getX(0)+e.getX(1))*.5f;
                    lastMidY=(e.getY(0)+e.getY(1))*.5f;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                if(e.getPointerCount()>=2){
                    final float pinchValue=pinch(e);
                    final float midX=(e.getX(0)+e.getX(1))*.5f;
                    final float midY=(e.getY(0)+e.getY(1))*.5f;
                    final float zoom=lastPinch>1.f&&pinchValue>1.f?
                        Math.max(.7f,Math.min(1.3f,lastPinch/pinchValue)):1.f;
                    final float dx=midX-lastMidX,dy=midY-lastMidY;
                    queueEvent(()->{
                        nativePan(dx,dy);nativeOrbit(0,0,zoom);interactiveRender();
                    });
                    lastPinch=pinchValue;lastMidX=midX;lastMidY=midY;
                }else if(!multiTouch){
                    final float px=surfaceX(e.getX()),py=surfaceY(e.getY());
                    if(roadMode==1)queueEvent(()->{
                        nativeRoadAppend(px,py,2.0f);interactiveRender();
                    });
                    else queueEvent(()->{
                        nativeRoadDragHandle(px,py);interactiveRender();
                    });
                }
                lastX=e.getX();lastY=e.getY();
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP){
                multiTouch=true;lastPinch=0.f;return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(action==MotionEvent.ACTION_UP&&!multiTouch&&roadMode==1){
                    final float px=surfaceX(e.getX()),py=surfaceY(e.getY());
                    queueEvent(()->{
                        nativeRoadAppend(px,py,.5f);requestRender();
                    });
                }else queueEvent(()->{nativeRoadEndDrag();requestRender();});
                multiTouch=false;return true;
            }
            return true;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(importing)return true; // Prevent input changes during scene replacement.
            if(roadMode!=0)return handleRoadTouch(e);
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN) {
                downX=lastX=e.getX();downY=lastY=e.getY();multiTouch=false;lastPinch=0;
                touchedGizmoAxis=-1;
                final float px=surfaceX(downX),py=surfaceY(downY);
                queueEvent(() -> {
                    nativeGizmoEnd();gizmoEditStarted=false;
                    if(placingKind==-1&&terrainMode==0&&!paintTerrainMode)
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
                            if(axis>=0&&placingKind==-1&&terrainMode==0&&!paintTerrainMode){
                                if(!gizmoEditStarted&&movedEnough)
                                    gizmoEditStarted=nativeGizmoBegin(axis,
                                        surfaceX(downX),surfaceY(downY));
                                if(gizmoEditStarted)nativeGizmoDrag(axis,sx,sy,
                                    surfaceX(x),surfaceY(y));
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
                    final boolean paint=paintTerrainMode;
                    final String paintingKey=activePaintKey;
                    final String asset=placingGlb;
                    queueEvent(()->{
                        if(touchedGizmoAxis>=0){
                            nativeGizmoEnd();gizmoEditStarted=false;
                            touchedGizmoAxis=-1;
                            requestRender();refreshSelected();
                            return;
                        }
                        if(tap){
                            if(paint){
                                int strokes=nativePaintTerrainAtScreen(px,py,paintRadius,paintingKey);
                                if(strokes>0)message("Земля окрашена · мазок "+strokes+"/48");
                                else message("Не получилось покрасить: нет текстуры или лимит 48 кистей");
                            }else if(brush!=0){
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
