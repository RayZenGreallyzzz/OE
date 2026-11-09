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
    private static native String nativeRuntimeCoreReport();
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
    private static native boolean nativeReplaceScene(int size, boolean demoRoads,
            int[] kinds, float[] transforms, String[] modelKeys, float[] terrain,
            String[] objectPaintKeys, float[] painting, String[] terrainPaintKeys,
            long[] roadIds, String[] roadNames, String[] roadMaterials,
            double[] roadWidths, double[][] roadPoints,int[] tileCoords,byte[][] tileData,
            int[] layerCoords,String[] layerKeys,byte[][] layerMasks);
    private static native boolean nativeRegisterPaintTexture(String key, byte[] rgba,int width,int height);
    private static native boolean nativePaintSelected(String key);
    private static native int nativePaintTerrainAtScreen(float x,float y,float radius,String key);
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
    private static native void nativeRoadBegin();
    private static native void nativeRoadFinish();
    private static native boolean nativeRoadContinue();
    private static native int nativeRoadTap(float x,float y);
    private static native String[] nativeRoadLabels();
    private static native boolean nativeRoadSelect(int index);
    private static native boolean nativeRoadSetWidth(double width);
    private static native double nativeRoadSelectedWidth();
    private static native boolean nativeRoadSetMaterial(int choice);
    private static native boolean nativeRoadRemoveLastPoint();
    private static native boolean nativeRoadDeleteSelected();
    private static native double[] nativeRoadPointCoords();
    private static native boolean nativeRoadPointSelect(int index);
    private static native boolean nativeRoadPointMove(double x,double z);
    private static native boolean nativeRoadPointTapMove(float x,float y);
    private static native boolean nativeRoadSmooth(double radiusMetres);
    private static native int nativeRoadIntersectionCount();
    private static native int[] nativePaintTileCoords();
    private static native byte[] nativePaintTileData(int index);
    private static native boolean nativePaintCursor(float x,float y,float radius);
    private static native boolean nativePaintStrokeBegin(float x,float y,float radius,float opacity,
            String texture,boolean erase);
    private static native boolean nativePaintStrokeMove(float x,float y);
    private static native void nativePaintStrokeEnd();
    private static native int[] nativeLayerCoords();
    private static native String[] nativeLayerKeys();
    private static native byte[] nativeLayerMask(int index);
    private static native boolean nativeLayerStrokeStart(float x,float y,float radius,
            float opacity,String material,boolean erase);
    private static native boolean nativeLayerStrokeMove(float x,float y);
    private static native void nativeLayerStrokeEnd();
    private static native double[] nativeMapTarget();
    private static native boolean nativeMapStage(byte[] rgba,int width,int height);
    private static native boolean nativeMapCommit(double x,double z,double width,float opacity,boolean visible);
    private static native boolean nativeMapConfigure(double x,double z,double width,float opacity,boolean visible);
    private static native void nativeMapAbort();
    private static native void nativeMapClear();
    private static native void nativeMapTopView();
    private static native void nativeMapPerspective();
    private static final int PICK_GLB = 702, PICK_PROJECT=703, SAVE_PROJECT=704, PICK_PAINT=705, PICK_MAP=706;
    private static final int MAX_ASSETS = 12;

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
    private TextView renderStatsText;
    private LinearLayout toolDrawer, paletteItems, inspectorPanel, advancedInspector;
    private boolean drawerOpen=false;
    private volatile int gizmoMode=0;
    private volatile String activePaintKey="";
    private volatile boolean paintTerrainMode=false;
    private volatile boolean precisionPaintMode=false;
    private volatile boolean materialPaintMode=false;
    private volatile boolean precisionPaintErase=false;
    private volatile float precisionPaintRadius=2f,precisionPaintOpacity=.75f;
    private volatile boolean roadMode=false;
    private volatile boolean roadMoveMode=false;
    private volatile boolean mapTopViewActive=false;
    private volatile MapGuide mapGuide=null;
    private volatile double selectedRoadWidth=6.0;
    private volatile float paintRadius=16f;
    private volatile long statsUpdatedAt=0;
    private volatile boolean importing = false;
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
        title.setText("OE ROAD JUNCTIONS R5 / TEST");
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
        for(String category:new String[]{"Здания","Детали","Декор","Рельеф","Дороги","Текстуры","Проект"}){
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
            if(placingKind>=0||terrainMode!=0||paintTerrainMode||roadMode||precisionPaintMode)toggleDrawer(false);
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
            addPaletteOption("Импорт 3D: GLB / glTF / VOX",this::pickGlb);
        }else if("Рельеф".equals(category)){
            addPaletteOption("⛰ Поднять гору",()->enterTerrainMode(1));
            addPaletteOption("⛰ Опустить землю",()->enterTerrainMode(-1));
            addPaletteOption("📐 Радиус и сила кисти",this::chooseTerrainBrush);
            addPaletteOption("✕ Завершить изменение",this::cancelPlace);
        }else if("Дороги".equals(category)){
            addPaletteOption("🗺 Загрузить PNG-план города",this::pickMapReference);
            addPaletteOption("📍 Положение и масштаб плана",this::configureMapReference);
            addPaletteOption("👁 Показать / скрыть план",this::toggleMapReference);
            addPaletteOption("⬇ Вид сверху на карту",this::showMapTopView);
            addPaletteOption("🎥 Вернуть вид 3D",this::showMapPerspective);
            addPaletteOption("🗑 Убрать PNG-подложку",this::askRemoveMapReference);
            addPaletteOption("🛣 Новая дорога · точки",this::beginNewRoad);
            addPaletteOption("✔ Завершить дорогу",this::finishRoad);
            addPaletteOption("📋 Выбрать дорогу",this::chooseExistingRoad);
            addPaletteOption("➕ Продолжить выбранную",this::continueExistingRoad);
            addPaletteOption("📍 Переместить точку касанием",()->chooseRoadPoint(true));
            addPaletteOption("⌨ Точные координаты точки",()->chooseRoadPoint(false));
            addPaletteOption("〰 Сгладить повороты",this::chooseRoadSmoothing);
            addPaletteOption("✣ Перекрёстки · информация",this::showJunctionStatus);
            addPaletteOption("↶ Удалить последнюю точку",this::removeRoadPoint);
            addPaletteOption("↔ Ширина дороги",this::chooseRoadWidth);
            addPaletteOption("🎨 Покрытие дороги",this::chooseRoadMaterial);
            addPaletteOption("🗑 Удалить выбранную дорогу",this::askDeleteRoad);
            addPaletteOption("ℹ Помощь",()->new AlertDialog.Builder(this)
                .setTitle("Дороги OE · R3")
                .setMessage("Выбери «Новая дорога», затем коснись земли для первой точки, "
                    +"ещё раз — для второй. Следующие касания продолжают улицу. "
                    +"Двумя пальцами перемещай карту. Дороги сохраняются вместе с проектом. "
                    +"PNG-план загружается через этот каталог и сохраняется в .oezip. Покрытие пока показано цветом; текстурная кисть будет позже.")
                .setPositiveButton("Понятно",null).show());
        }else if("Текстуры".equals(category)){
            addPaletteOption("🖼 Загрузить свой PNG / JPG",this::pickPaintTexture);
            addPaletteOption("🎨 Мои текстуры",this::choosePaintTexture);
            addPaletteOption("🧱 Нанести на выбранную деталь",this::applyPaintToSelected);
            addPaletteOption("🧽 Очистить текстуру детали",()->edit(()->nativePaintSelected("")));
            addPaletteOption("🧩 Слои R4.1 · рисовать",()->activateMaterialPaint(false));
            addPaletteOption("🧽 Слои R4.1 · стереть материал",()->activateMaterialPaint(true));
            addPaletteOption("🖌 Старый композит R4 · рисовать",()->activatePrecisionPaint(false));
            addPaletteOption("🧽 Старый композит R4 · стереть",()->activatePrecisionPaint(true));
            addPaletteOption("📏 Радиус и сила кисти",this::choosePrecisionPaint);
            addPaletteOption("✕ Завершить точную кисть",this::cancelPlace);
            addPaletteOption("○ Старые пятна · совместимость",this::activateTerrainPainting);
            addPaletteOption("📏 Радиус кисти земли",this::choosePaintRadius);
            addPaletteOption("✕ Завершить покраску",this::cancelPlace);
            addPaletteOption("Импорт модели: GLB / glTF / VOX",this::pickGlb);
            addPaletteOption("Качество GLB 1024 / 2048 px",this::chooseTextureLimit);
            addPaletteOption("Форматы и ограничения",()->new AlertDialog.Builder(this)
                .setTitle("Текстуры OE")
                .setMessage("Можно загружать PNG/JPG до 1024px и наносить на объекты или землю. "
                    +"В GLB читаются встроенные изображения и готовые KTX2 ETC2/RGBA8. "
                    +"Draco пока не поддерживается. KTX2 BasisU ETC1S/UASTC LDR -> ETC2 подключён (тест).")
                .setPositiveButton("Понятно",null).show());
        }else if("Проект".equals(category)){
            addPaletteOption("🧠 Ядро игры · проверить",this::showRuntimeCoreStatus);
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
    private void changeGizmoMode(int mode){
        gizmoMode=mode;
        surface.queueEvent(()->{nativeSetGizmoMode(mode);surface.requestRender();});
        statusText.setText(mode==0?"3D-оси: тяни X (красная), Y (зелёная), Z (синяя)":
            mode==1?"3D-ось Y: тяни зелёный маркер для поворота вокруг вертикали":
                     "3D-оси: тяни цветной маркер для изменения масштаба по выбранной оси");
    }
    private void enterPlaceMode(int kind, String asset) {
        if(importing) { message("Сначала дождись завершения импорта"); return; }
        terrainMode=0;paintTerrainMode=false;roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;
        materialPaintMode=false;
         surface.queueEvent(()->{nativePaintStrokeEnd();nativeLayerStrokeEnd();});
        surface.queueEvent(EditorActivity::nativeRoadFinish);
        placingKind = kind;
        placingGlb = asset;
        statusText.setText("РАЗМЕЩЕНИЕ: коснись свободной земли, чтобы поставить объект. Для отмены нажми «Отмена».");
        message("Коснись места на карте для размещения");
    }
    private void cancelPlace() {
        paintTerrainMode=false;roadMode=false;roadMoveMode=false;precisionPaintMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();nativeLayerStrokeEnd();});
        terrainMode=0;
        placingKind = -1;
        placingGlb = null;
        statusText.setText("Режим выбора · два пальца — перемещение; кнопки «Размер карты» и «Координаты» — большой мир.");
        message("Размещение отменено");
    }
    private void enterTerrainMode(int direction){
        if(importing){message("Дождись завершения операции");return;}
        placingKind=-1;placingGlb=null;paintTerrainMode=false;roadMode=false;roadMoveMode=false;
        precisionPaintMode=false;materialPaintMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();});
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

    private void beginNewRoad(){
        if(importing){message("Дождись завершения импорта");return;}
        placingKind=-1;placingGlb=null;terrainMode=0;paintTerrainMode=false;precisionPaintMode=false;materialPaintMode=false;roadMoveMode=false;roadMode=true;
        surface.queueEvent(()->{nativeRoadBegin();surface.requestRender();});
        statusText.setText("ДОРОГА: коснись земли для 1-й точки, затем для 2-й. "
            +"Следующие касания продолжают дорогу. Два пальца — камера.");
        message("Создание новой дороги · две точки задают первый участок");
    }
    private void finishRoad(){
        roadMode=false;roadMoveMode=false;
        surface.queueEvent(()->{nativeRoadFinish();surface.requestRender();});
        statusText.setText("Дорога завершена · выбери другую или отредактируй ширину.");
    }
    private void continueExistingRoad(){
        if(importing)return;
        surface.queueEvent(()->{
            if(nativeRoadContinue()){
                roadMoveMode=false;roadMode=true;placingKind=-1;terrainMode=0;paintTerrainMode=false;
                ui(()->statusText.setText("Продолжение дороги: касайся земли для добавления точек."));
                surface.requestRender();
            }else message("Сначала выбери дорогу через «Дороги → Выбрать»");
        });
    }
    private void chooseExistingRoad(){
        if(importing)return;
        surface.queueEvent(()->{
            final String[] list=nativeRoadLabels();
            ui(()->{
                if(list==null||list.length==0){message("Пока нет дорог");return;}
                new AlertDialog.Builder(this).setTitle("Выбери дорогу")
                    .setItems(list,(dialog,index)->{
                        roadMode=false;roadMoveMode=false;
                        edit(()->{
                            nativeRoadFinish();
                            nativeRoadSelect(index);
                            selectedRoadWidth=nativeRoadSelectedWidth();
                            message("Дорога выделена: можно менять ширину, покрытие и точки");
                        });
                    }).setNegativeButton("Отмена",null).show();
            });
        });
    }

    private void chooseRoadPoint(boolean byTap){
        if(importing)return;
        surface.queueEvent(()->{
            final double[] coords=nativeRoadPointCoords();
            ui(()->{
                if(coords==null||coords.length<4){
                    message("Сначала выбери дорогу в разделе «Дороги»");return;
                }
                final int count=coords.length/2;
                final String[] choices=new String[Math.min(120,count)];
                for(int i=0;i<choices.length;i++)
                    choices[i]=String.format(Locale.US,"Точка %d · X %.2f / Z %.2f",
                                              i+1,coords[2*i],coords[2*i+1]);
                final AlertDialog.Builder builder=new AlertDialog.Builder(this)
                    .setTitle(byTap?"Выбрать точку для переноса":"Точные координаты точки")
                    .setItems(choices,(dialog,index)->onRoadPointChosen(index,
                                                coords[2*index],coords[2*index+1],byTap))
                    .setNegativeButton("Отмена",null);
                if(count>120){
                    builder.setMessage("Показаны первые 120 точек из "+count+
                        ". Для дальних точек выбери «По номеру».")
                        .setNeutralButton("По номеру",(dialog,which)->{
                            final EditText number=new EditText(this);
                            number.setSingleLine(true);
                            number.setInputType(InputType.TYPE_CLASS_NUMBER);
                            number.setHint("От 1 до "+count);
                            new AlertDialog.Builder(this)
                                .setTitle("Номер контрольной точки").setView(number)
                                .setPositiveButton("Выбрать",(d,k)->{
                                    try{
                                        int idx=Integer.parseInt(number.getText().toString().trim())-1;
                                        if(idx<0||idx>=count)throw new NumberFormatException();
                                        onRoadPointChosen(idx,coords[2*idx],coords[2*idx+1],byTap);
                                    }catch(Exception ignored){message("Номер точки вне диапазона");}
                                }).setNegativeButton("Отмена",null).show();
                        });
                }
                builder.show();
            });
        });
    }
    private void onRoadPointChosen(int index,double oldX,double oldZ,boolean byTap){
        if(byTap){
            roadMode=false;roadMoveMode=true;
            placingKind=-1;placingGlb=null;paintTerrainMode=false;
            precisionPaintMode=false;materialPaintMode=false;terrainMode=0;
            surface.queueEvent(()->{
                nativeRoadFinish();
                if(nativeRoadPointSelect(index)){
                    surface.requestRender();
                    ui(()->statusText.setText("ТОЧКА "+(index+1)+
                        ": коснись нового места на земле. Привязка к концам дорог — 2 м."));
                    message("Выбрана точка "+(index+1)+" · коснись места переноса");
                }
            });
            return;
        }
        roadMoveMode=false;
        LinearLayout fields=new LinearLayout(this);
        fields.setPadding(dp(20),dp(8),dp(20),dp(5));
        fields.setOrientation(LinearLayout.VERTICAL);
        EditText x=new EditText(this),z=new EditText(this);
        int input=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|
                  InputType.TYPE_NUMBER_FLAG_SIGNED;
        x.setSingleLine(true);z.setSingleLine(true);
        x.setInputType(input);z.setInputType(input);
        x.setHint("X, метры");z.setHint("Z, метры");
        x.setText(String.format(Locale.US,"%.3f",oldX));
        z.setText(String.format(Locale.US,"%.3f",oldZ));
        fields.addView(x);fields.addView(z);
        new AlertDialog.Builder(this).setTitle("Точка "+(index+1)+" · координаты")
            .setView(fields).setPositiveButton("Применить",(dialog,which)->{
                try{
                    double newX=Double.parseDouble(x.getText().toString().trim().replace(',','.'));
                    double newZ=Double.parseDouble(z.getText().toString().trim().replace(',','.'));
                    if(!Double.isFinite(newX)||!Double.isFinite(newZ))
                        throw new NumberFormatException();
                    edit(()->{
                        if(nativeRoadPointSelect(index)&&nativeRoadPointMove(newX,newZ))
                            message("Точка "+(index+1)+" обновлена. Доступна отмена ↶.");
                        else message("Точка недоступна, слишком близко к соседней или вне мира");
                    });
                }catch(Exception invalid){message("Введите числовые X и Z в метрах");}
            }).setNegativeButton("Отмена",null).show();
    }
    private void chooseRoadSmoothing(){
        if(importing)return;
        final double[] radii={1,2,4,8};
        final String[] names={"1 м — лёгкое","2 м — обычное",
                              "4 м — плавное","8 м — широкие повороты"};
        new AlertDialog.Builder(this).setTitle("Сгладить повороты дороги")
            .setMessage("Заменяет резкие углы дополнительными редактируемыми точками. "
                +"Возможна отмена ↶. Для точной прокладки по PNG используй маленький радиус.")
            .setItems(names,(dialog,index)->edit(()->{
                roadMoveMode=false;roadMode=false;
                if(nativeRoadSmooth(radii[index]))
                    message("Повороты сглажены · радиус "+radii[index]+" м");
                else message("Не получилось сгладить: выбери дорогу или уменьшай число точек");
            })).setNegativeButton("Отмена",null).show();
    }
    private void showJunctionStatus(){
        if(importing)return;
        surface.queueEvent(()->{
            final int count=nativeRoadIntersectionCount();
            ui(()->new AlertDialog.Builder(this).setTitle("Перекрёстки OE · R5")
                .setMessage(count<0?
                    "Дорожная сеть превышает ограничение предварительного поиска (1800 отрезков). "
                    +"Сами дороги остаются доступны, но предпросмотр перекрёстков отключён.":
                    "Найдено пересечений: "+count+". Центры пересечений показаны поверх дорог. "
                    +"Это визуальные площадки, не окончательная геометрия тротуаров или навигации. "
                    +"При подведении конца дороги к другой конечной точке действует привязка 2 м.")
                .setPositiveButton("Понятно",null).show());
        });
    }

    private void chooseRoadWidth(){
        if(importing)return;
        final EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setText(String.format(Locale.US,"%.2f",selectedRoadWidth));
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this).setTitle("Ширина дороги, метры · 0,25–32")
            .setView(field)
            .setPositiveButton("Применить",(dialog,which)->{
                try{
                    final double value=Double.parseDouble(
                        field.getText().toString().trim().replace(',','.'));
                    if(!Double.isFinite(value)||value<.25||value>32)
                        throw new IllegalArgumentException("range");
                    edit(()->{
                        if(nativeRoadSetWidth(value)){
                            selectedRoadWidth=value;
                            message("Ширина дороги: "+value+" м");
                        }else message("Ширина не подходит: граница карты или лимит геометрии");
                    });
                }catch(Exception error){message("Введите ширину от 0,25 до 32 метров");}
            }).setNegativeButton("Отмена",null).show();
    }

    // Editor-only visual reference: no entity, collision or standalone-game dependency.
    private static final class MapGuide {
        final String key;
        final int widthPx,heightPx;
        final double centerX,centerZ,widthMetres;
        final float opacity;
        final boolean visible;
        MapGuide(String key,int w,int h,double x,double z,double width,float alpha,boolean show){
            this.key=key;this.widthPx=w;this.heightPx=h;
            this.centerX=x;this.centerZ=z;this.widthMetres=width;
            this.opacity=alpha;this.visible=show;
        }
        JSONObject toJson() throws Exception{
            JSONObject result=new JSONObject();
            result.put("key",key);
            result.put("centerX",centerX);result.put("centerZ",centerZ);
            result.put("widthMetres",widthMetres);
            result.put("opacity",opacity);result.put("visible",visible);
            return result;
        }
        MapGuide aligned(double x,double z,double width,float alpha,boolean show){
            return new MapGuide(key,widthPx,heightPx,x,z,width,alpha,show);
        }
    }

    private static final int PAINT_TILE_BYTES=128*128*4;
    private static String compressedTile(byte[] raw) throws Exception{
        if(raw==null||raw.length!=PAINT_TILE_BYTES)throw new Exception("Неполная кисть R4");
        java.util.zip.Deflater compressor=new java.util.zip.Deflater(3);
        try{
            compressor.setInput(raw);compressor.finish();
            ByteArrayOutputStream out=new ByteArrayOutputStream(2048);
            byte[] buffer=new byte[8192];
            while(!compressor.finished()){
                int n=compressor.deflate(buffer);
                if(n<=0)throw new Exception("Не удалось сжать слой земли");
                if(out.size()+n>75000)throw new Exception("Слишком сложный текстурный слой");
                out.write(buffer,0,n);
            }
            return android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
        }finally{compressor.end();}
    }
    private static byte[] restoredTile(String encoded) throws Exception{
        if(encoded==null||encoded.length()<4||encoded.length()>100000||
            !encoded.matches("[A-Za-z0-9+/=]+"))
            throw new Exception("Повреждённые данные кисти R4");
        byte[] zipped=android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP);
        if(zipped.length==0||zipped.length>75000)throw new Exception("Размер слоя неверен");
        java.util.zip.Inflater compressor=new java.util.zip.Inflater();
        try{
            compressor.setInput(zipped);
            byte[] out=new byte[PAINT_TILE_BYTES];
            int pos=0;
            while(!compressor.finished()&&pos<out.length){
                int n=compressor.inflate(out,pos,out.length-pos);
                if(n<=0)throw new Exception("Повреждённый сжатый слой земли");
                pos+=n;
            }
            if(!compressor.finished()||pos!=out.length||compressor.getRemaining()!=0)
                throw new Exception("Неверный объём текстурного слоя");
            return out;
        }finally{compressor.end();}
    }

    private static final int MATERIAL_WEIGHT_BYTES=128*128;
    private static String compressedMaterialMask(byte[] bytes) throws Exception{
        if(bytes==null||bytes.length!=MATERIAL_WEIGHT_BYTES)
            throw new Exception("Повреждённая маска материала");
        java.util.zip.Deflater stream=new java.util.zip.Deflater(3);
        try{
            stream.setInput(bytes);stream.finish();
            ByteArrayOutputStream result=new ByteArrayOutputStream(1024);
            byte[] buffer=new byte[8192];
            while(!stream.finished()){
                int n=stream.deflate(buffer);
                if(n<=0||result.size()+n>20000)
                    throw new Exception("Слишком большая маска текстуры");
                result.write(buffer,0,n);
            }
            return android.util.Base64.encodeToString(result.toByteArray(),
                android.util.Base64.NO_WRAP);
        }finally{stream.end();}
    }
    private static byte[] restoredMaterialMask(String text) throws Exception{
        if(text==null||text.length()<4||text.length()>28000||
           !text.matches("[A-Za-z0-9+/=]+"))
            throw new Exception("Неверная маска материала в сцене");
        byte[] compressed=android.util.Base64.decode(text,android.util.Base64.NO_WRAP);
        if(compressed.length<=0||compressed.length>20000)
            throw new Exception("Маска материала превышает лимит");
        java.util.zip.Inflater stream=new java.util.zip.Inflater();
        try{
            stream.setInput(compressed);
            byte[] restored=new byte[MATERIAL_WEIGHT_BYTES];
            int offset=0;
            while(!stream.finished()&&offset<restored.length){
                int n=stream.inflate(restored,offset,restored.length-offset);
                if(n<=0)throw new Exception("Повреждены данные маски");
                offset+=n;
            }
            if(offset!=restored.length||!stream.finished()||stream.getRemaining()!=0)
                throw new Exception("Размер маски материала не совпадает");
            return restored;
        }finally{stream.end();}
    }
    private String snapshotWithGuide() throws Exception{
        final JSONObject json=new JSONObject(nativeSceneSnapshot());
        final MapGuide guide=mapGuide;
        if(guide!=null){
            if(!new File(referenceDir(),guide.key).isFile())
                throw new Exception("PNG-подложка отсутствует в памяти проекта");
            json.put("version",3);
            json.put("mapReference",guide.toJson());
        }
        int[] locations=nativePaintTileCoords();
        if(locations==null||locations.length%2!=0||locations.length>64)
            throw new Exception("Слишком много участков кисти");
        if(locations.length!=0){
            JSONArray saved=new JSONArray();
            for(int i=0;i<locations.length/2;i++){
                JSONArray tile=new JSONArray();
                tile.put(locations[2*i]);
                tile.put(locations[2*i+1]);
                tile.put(compressedTile(nativePaintTileData(i)));
                saved.put(tile);
            }
            json.put("paintTiles",saved);
            json.put("version",4);
        }
        int[] layerLocations=nativeLayerCoords();
        String[] layerMaterials=nativeLayerKeys();
        if(layerLocations==null||layerMaterials==null||
           layerLocations.length%2!=0||layerLocations.length>256||
           layerLocations.length/2!=layerMaterials.length)
            throw new Exception("Превышены границы R4.1");
        if(layerMaterials.length!=0){
            JSONArray serialized=new JSONArray();
            HashSet<String> tileLocations=new HashSet<>();
            for(int i=0;i<layerMaterials.length;i++){
                if(!validTexture(layerMaterials[i]))
                    throw new Exception("Неизвестный материал слоя");
                tileLocations.add(layerLocations[2*i]+":"+layerLocations[2*i+1]);
                JSONArray layer=new JSONArray();
                layer.put(layerLocations[2*i]);layer.put(layerLocations[2*i+1]);
                layer.put(layerMaterials[i]);
                layer.put(compressedMaterialMask(nativeLayerMask(i)));
                serialized.put(layer);
            }
            if(tileLocations.size()>32)throw new Exception("Превышено число участков");
            json.put("paintLayers",serialized);
            json.put("version",5);
        }
        String result=json.toString();
        if(result.getBytes(StandardCharsets.UTF_8).length>ProjectArchive.MAX_SCENE_BYTES)
            throw new Exception("Рисунок R4 превысил безопасный размер проекта");
        return result;
    }
    private File referenceDir() throws Exception{
        File dir=new File(getFilesDir(),"references");
        if(!dir.isDirectory()&&!dir.mkdirs())
            throw new Exception("Нельзя создать каталог PNG-планов");
        return dir;
    }
    private static String mapKey(byte[] bytes) throws Exception{
        return "ref_"+textureKey(bytes,"image/png").substring(3);
    }
    private void pickMapReference(){
        if(importing){message("Дождись завершения операции");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        try{startActivityForResult(intent,PICK_MAP);}
        catch(Exception error){message("Не открыть PNG: "+error.getMessage());}
    }
    private void importMapReference(Uri uri){
        if(importing)return;
        importing=true;
        surface.queueEvent(()->{
            final double[] target=nativeMapTarget();
            final int world=nativeGetWorldSize();
            new Thread(()->{
                try{
                    byte[] original=limitedTextureRead(getContentResolver().openInputStream(uri));
                    if(!"image/png".equals(textureType(original)))
                        throw new Exception("Карта города должна быть PNG");
                    String key=mapKey(original);
                    TextureReader.Image image=TextureReader.decode(original,"image/png",2048);
                    double defaultWidth=Math.min(512.0,world*.75);
                    defaultWidth=Math.min(defaultWidth,world*.75*image.width/image.height);
                    double defaultHeight=defaultWidth*image.height/image.width;
                    if(defaultWidth<8.0||defaultHeight<8.0)
                        throw new Exception("Необычные пропорции PNG-карты: нужен обычный план города");
                    File saved=new File(referenceDir(),key);
                    if(saved.exists()){
                        if(!key.equals(mapKey(Files.readAllBytes(saved.toPath()))))
                            throw new Exception("Повреждён ранее сохранённый PNG");
                    }else{
                        File partial=new File(referenceDir(),key+".part");
                        try{
                            Files.write(partial.toPath(),original);
                            Files.move(partial.toPath(),saved.toPath(),StandardCopyOption.REPLACE_EXISTING);
                        }finally{Files.deleteIfExists(partial.toPath());}
                    }
                    final double centerX=target==null?0.0:target[0];
                    final double centerZ=target==null?0.0:target[1];
                    final double metres=defaultWidth;
                    surface.queueEvent(()->{
                        try{
                            if(!nativeMapStage(image.bytes,image.width,image.height)||
                               !nativeMapCommit(centerX,centerZ,metres,.5f,true))
                                throw new Exception("GPU не смог загрузить PNG-карту");
                            mapGuide=new MapGuide(key,image.width,image.height,
                                                  centerX,centerZ,metres,.5f,true);
                            nativeMapTopView();mapTopViewActive=true;
                            surface.requestRender();
                            message("PNG-карта поверх земли · вид сверху · отрегулируй размер");
                        }catch(Exception error){
                            nativeMapAbort();
                            message("Ошибка PNG-карты: "+error.getMessage());
                        }finally{importing=false;}
                    });
                }catch(Exception error){
                    importing=false;
                    message("Карта не загружена: "+error.getMessage());
                }
            },"oe-map-reference-import").start();
        });
    }
    private void configureMapReference(){
        MapGuide current=mapGuide;
        if(importing||current==null){message("Сначала загрузи PNG-карту");return;}
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20),dp(6),dp(20),dp(4));
        EditText[] inputs=new EditText[4];
        String[] hints={"Центр X, метры","Центр Z, метры",
                        "Ширина карты, метры","Непрозрачность, %"};
        double[] values={current.centerX,current.centerZ,current.widthMetres,current.opacity*100};
        for(int i=0;i<4;i++){
            final EditText input=new EditText(this);
            input.setSingleLine(true);
            input.setHint(hints[i]);
            input.setText(String.format(Locale.US,"%.2f",values[i]));
            input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|
                InputType.TYPE_NUMBER_FLAG_SIGNED);
            fields.addView(input);inputs[i]=input;
        }
        new AlertDialog.Builder(this).setTitle("PNG-подложка · привязка к карте")
            .setMessage("X/Z — центр плана. Ширина в метрах; высота сохраняет пропорции PNG.")
            .setView(fields)
            .setPositiveButton("Применить",(dialog,which)->{
                try{
                    double x=Double.parseDouble(inputs[0].getText().toString().trim().replace(',','.'));
                    double z=Double.parseDouble(inputs[1].getText().toString().trim().replace(',','.'));
                    double width=Double.parseDouble(inputs[2].getText().toString().trim().replace(',','.'));
                    double alpha=Double.parseDouble(inputs[3].getText().toString().trim().replace(',','.'));
                    double height=width*current.heightPx/current.widthPx;
                    if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(width)||
                       !Double.isFinite(alpha)||width<8||width>16384||height<8||
                       height>16384||alpha<5||alpha>95||Math.abs(x)>8192||Math.abs(z)>8192)
                        throw new IllegalArgumentException("range");
                    final float opacity=(float)(alpha/100.0);
                    edit(()->{
                        if(nativeMapConfigure(x,z,width,opacity,current.visible)){
                            mapGuide=current.aligned(x,z,width,opacity,current.visible);
                            message("Положение и масштаб карты обновлены");
                        }else message("PNG-карта не помещается в размер игрового мира");
                    });
                }catch(Exception invalid){message("Проверь X/Z, ширину и прозрачность от 5 до 95%");}
            }).setNegativeButton("Отмена",null).show();
    }
    private void toggleMapReference(){
        MapGuide current=mapGuide;
        if(importing||current==null){message("PNG-карта ещё не загружена");return;}
        edit(()->{
            if(nativeMapConfigure(current.centerX,current.centerZ,current.widthMetres,
                                  current.opacity,!current.visible)){
                mapGuide=current.aligned(current.centerX,current.centerZ,current.widthMetres,
                                         current.opacity,!current.visible);
                message(current.visible?"Карта-подложка скрыта":"Карта-подложка показана");
            }
        });
    }
    private void showMapTopView(){
        mapTopViewActive=true;
        edit(EditorActivity::nativeMapTopView);
        message("Вид сверху: одним пальцем перемещай карту, касанием ставь точки дороги");
    }
    private void showMapPerspective(){
        mapTopViewActive=false;
        edit(EditorActivity::nativeMapPerspective);
    }
    private void askRemoveMapReference(){
        if(importing||mapGuide==null){message("Нет PNG-карты");return;}
        new AlertDialog.Builder(this).setTitle("Убрать PNG-карту из проекта?")
            .setMessage("Созданные дороги сохранятся. Исходный PNG останется в памяти приложения.")
            .setPositiveButton("Убрать",(dialog,which)->edit(()->{
                nativeMapClear();
                mapGuide=null;mapTopViewActive=false;
                message("Подложка убрана; дороги не изменены");
            })).setNegativeButton("Отмена",null).show();
    }

    private void chooseRoadMaterial(){
        if(importing)return;
        final String[] options={"Камень (цветовой просмотр)","Грунт","Песок","Трава"};
        new AlertDialog.Builder(this).setTitle("Покрытие дороги · предпросмотр")
            .setItems(options,(dialog,index)->edit(()->{
                if(nativeRoadSetMaterial(index))message("Покрытие: "+options[index]);
            })).setNegativeButton("Отмена",null).show();
    }
    private void removeRoadPoint(){
        if(importing)return;
        edit(()->{
            if(nativeRoadRemoveLastPoint())message("Последняя точка убрана · можно отменить ↶");
            else message("Сначала выбери дорогу");
        });
    }
    private void askDeleteRoad(){
        if(importing)return;
        new AlertDialog.Builder(this).setTitle("Удалить выделенную дорогу?")
            .setMessage("Удаление можно отменить кнопкой ↶.")
            .setPositiveButton("Удалить",(dialog,index)->edit(()->{
                if(nativeRoadDeleteSelected())message("Дорога удалена");
                else message("Нет выбранной дороги");
            })).setNegativeButton("Отмена",null).show();
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
    private void showRuntimeCoreStatus(){
        if(importing){message("Сначала закончи загрузку проекта");return;}
        if(surface==null)return;
        // The GLES/editor scene is read only on its owning thread.
        // The new game core receives a separate COPY and cannot modify a project.
        surface.queueEvent(()->{
            final String report=nativeRuntimeCoreReport();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("OE Game Runtime Core · первый этап")
                .setMessage(report)
                .setPositiveButton("Готово",null)
                .show());
        });
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
            AtomicFile atomic=new AtomicFile(saveFile());
            FileOutputStream stream=null;
            try {
                String json=snapshotWithGuide();
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
        terrainMode=0;placingKind=-1;placingGlb=null;roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();});
        paintTerrainMode=true;
        statusText.setText("КИСТЬ ТЕКСТУР: касайся земли · радиус "+(int)paintRadius+
            "м · до 48 мазков · ↶ отменить");
    }

    private void activatePrecisionPaint(boolean erase){
        if(importing){message("Дождись окончания загрузки");return;}
        if(!erase&&activePaintKey.isEmpty()){
            message("Сначала загрузи и выбери текстуру PNG/JPG");return;
        }
        paintTerrainMode=false;terrainMode=0;placingKind=-1;placingGlb=null;roadMode=false;
        precisionPaintErase=erase;precisionPaintMode=true;materialPaintMode=false;roadMoveMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();});
        statusText.setText(erase?"ЛАСТИК R4: веди пальцем · курсор на 70 dp выше пальца":
             "КИСТЬ R4: веди пальцем непрерывно · круг на 70 dp выше пальца. "
             +"Размер "+precisionPaintRadius+" м, двухпальцевый зум.");
        message(erase?"Режим стирания покрытия":"Непрерывная кисть · одно касание рисует линию");
    }

    private void activateMaterialPaint(boolean erase){
        if(importing){message("Дождись окончания импорта");return;}
        if(activePaintKey.isEmpty()){
            message("Сначала выбери материал PNG/JPG в библиотеке текстур");return;
        }
        paintTerrainMode=false;terrainMode=0;placingKind=-1;placingGlb=null;roadMode=false;
        precisionPaintErase=erase;precisionPaintMode=true;materialPaintMode=true;roadMoveMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();nativeLayerStrokeEnd();});
        statusText.setText(erase?
            "ЛАСТИК СЛОЯ: стирает ТОЛЬКО выбранную текстуру. Курсор выше пальца.":
            "СЛОИ R4.1: рисуй поверх плана. До 4 материалов на один участок 64м.");
        message(erase?"Стирать выбранный материал, остальные слои не изменятся":
                       "Слои R4.1 · нарисованную текстуру можно править отдельно");
    }
    private void choosePrecisionPaint(){
        final String[] sizes={"0,5 м — точная","1 м — узкая","2 м — дорога",
            "4 м — широкая","8 м — площадь","16 м — большой участок"};
        final float[] values={.5f,1f,2f,4f,8f,16f};
        new AlertDialog.Builder(this).setTitle("Радиус кисти R4")
            .setItems(sizes,(dialog,index)->{
                precisionPaintRadius=values[index];
                final String[] strengths={"30% — прозрачнее","60%","85%","100% — плотное"};
                final float[] alpha={.3f,.6f,.85f,1f};
                new AlertDialog.Builder(this).setTitle("Сила нанесения")
                    .setItems(strengths,(d,k)->{
                        precisionPaintOpacity=alpha[k];
                        message("Кисть R4: радиус "+precisionPaintRadius+
                            " м · сила "+Math.round(100*precisionPaintOpacity)+"%");
                    }).setNegativeButton("Отмена",null).show();
            }).setNegativeButton("Отмена",null).show();
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
            final String scene;
            try{scene=snapshotWithGuide();}
            catch(Exception error){
                importing=false;message("Нельзя экспортировать сцену: "+error.getMessage());return;
            }
            new Thread(() -> {
                try{
                    try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
                        if(stream==null)throw new Exception("Нет доступа к файлу");
                        ProjectArchive.exportProject(scene,modelDir(),textureDir(),originalDir(),referenceDir(),stream);
                    }
                    message("Проект экспортирован · дороги и PNG-подложка сохранены");
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
                    staged.textureDir,staged.sourceDir,staged.referenceDir,staged.root),
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
        if(requestCode==PICK_MAP){
            if(resultCode==RESULT_OK&&intent!=null&&intent.getData()!=null)
                importMapReference(intent.getData());
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
        float x,z,yaw,size,y,sx=1f,sy=1f,sz=1f;
        String asset,paint="";
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
            new Thread(() -> readAndPrepareScene(cached,null,null,null,null,null,null),"oe-scene-load").start();
        });
    }
    private void readAndPrepareScene(HashSet<String> cached,File sourceScene,
            File sourceModels,File sourceTextures,File sourceOriginals,
            File sourceReferences,File stagingRoot) {
        try {
            File file=sourceScene==null?saveFile():sourceScene;
            ByteArrayOutputStream jsonBytes=new ByteArrayOutputStream();
            try(InputStream in=new AtomicFile(file).openRead()){
                byte[] buffer=new byte[4096];
                int count;
                while((count=in.read(buffer))!=-1){
                    if(jsonBytes.size()+count>ProjectArchive.MAX_SCENE_BYTES)throw new Exception("Файл сцены слишком большой");
                    jsonBytes.write(buffer,0,count);
                }
            }catch(java.io.FileNotFoundException missing){
                throw new Exception("Сначала сохрани сцену");
            }
            JSONObject document=new JSONObject(new String(jsonBytes.toByteArray(),StandardCharsets.UTF_8));
            final int sceneVersion=document.optInt("version",-1);
            if(sceneVersion<1||sceneVersion>5)
                throw new Exception("Неверная версия сцены (нужна 1–5)");
            final boolean demoRoads=document.optBoolean("demoRoads",false);
            final int worldSize=document.optInt("worldSize",1024);
            if(worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&worldSize!=8192&&worldSize!=16384)
                throw new Exception("Неподдерживаемый размер карты");

            JSONObject mapObject=document.optJSONObject("mapReference");
            if((sceneVersion==3&&mapObject==null)||
               (sceneVersion<3&&document.has("mapReference"))||
               (document.has("mapReference")&&mapObject==null))
                throw new Exception("Некорректная PNG-карта в версии сцены");
            final MapGuide parsedGuide;
            final TextureReader.Image parsedMapImage;
            if(mapObject!=null){
                String key=mapObject.getString("key");
                if(!key.matches("ref_[0-9a-f]{24}\\.png"))
                    throw new Exception("Неверное имя PNG-подложки");
                double x=mapObject.getDouble("centerX");
                double z=mapObject.getDouble("centerZ");
                double width=mapObject.getDouble("widthMetres");
                double opacity=mapObject.getDouble("opacity");
                boolean visible=mapObject.getBoolean("visible");
                File png=new File(sourceReferences==null?referenceDir():sourceReferences,key);
                if(!png.isFile()||png.length()<=0||
                   png.length()>ProjectArchive.MAX_REFERENCE_BYTES)
                    throw new Exception("PNG-подложка отсутствует");
                byte[] original=Files.readAllBytes(png.toPath());
                if(!"image/png".equals(textureType(original))||!key.equals(mapKey(original)))
                    throw new Exception("Повреждена исходная PNG-подложка");
                parsedMapImage=TextureReader.decode(original,"image/png",2048);
                double height=width*parsedMapImage.height/parsedMapImage.width;
                if(!Double.isFinite(x)||!Double.isFinite(z)||
                   !Double.isFinite(width)||!Double.isFinite(opacity)||
                   Math.abs(x)>worldSize/2.0||Math.abs(z)>worldSize/2.0||
                   width<8||width>worldSize||height<8||height>worldSize||
                   opacity<.05||opacity>.95)
                    throw new Exception("PNG-подложка не соответствует размеру мира");
                parsedGuide=new MapGuide(key,parsedMapImage.width,parsedMapImage.height,
                                         x,z,width,(float)opacity,visible);
            }else{
                parsedGuide=null;
                parsedMapImage=null;
            }
            // R4 tiles are independently compressed; reject corruption before GLES swap.
            JSONArray paintTilesJson=document.optJSONArray("paintTiles");
            if((sceneVersion==4&&paintTilesJson==null)||
               (sceneVersion<4&&document.has("paintTiles"))||
               (document.has("paintTiles")&&paintTilesJson==null))
                throw new Exception("Неверная версия данных кисти R4");
            final int tileCount=paintTilesJson==null?0:paintTilesJson.length();
            if(tileCount>32)throw new Exception("Слишком много текстурных участков");
            int[] preciseTileCoords=new int[tileCount*2];
            byte[][] preciseTileBytes=new byte[tileCount][];
            HashSet<String> tilePairs=new HashSet<>();
            for(int i=0;i<tileCount;i++){
                JSONArray tile=paintTilesJson.getJSONArray(i);
                if(tile.length()!=3)throw new Exception("Неверный участок текстуры R4");
                int x=tile.getInt(0),z=tile.getInt(1);
                if(x*64>=worldSize/2||x*64+64<=-worldSize/2||
                   z*64>=worldSize/2||z*64+64<=-worldSize/2||
                   !tilePairs.add(x+":"+z))throw new Exception("Повтор или неверный участок");
                preciseTileCoords[2*i]=x;preciseTileCoords[2*i+1]=z;
                preciseTileBytes[i]=restoredTile(tile.getString(2));
            }

            JSONArray savedLayers=document.optJSONArray("paintLayers");
            if((sceneVersion==5&&savedLayers==null)||
               (sceneVersion<5&&document.has("paintLayers"))||
               (document.has("paintLayers")&&savedLayers==null))
                throw new Exception("Неверная версия независимых слоёв");
            final int materialCount=savedLayers==null?0:savedLayers.length();
            if(materialCount>128)throw new Exception("Слишком много слоёв материала");
            int[] materialCoords=new int[materialCount*2];
            String[] materialKeys=new String[materialCount];
            byte[][] materialWeights=new byte[materialCount][];
            HashSet<String> uniqueMaterialTile=new HashSet<>();
            HashSet<String> touchedTileKeys=new HashSet<>();
            HashSet<String> requiredMaterials=new HashSet<>();
            java.util.Map<String,Integer> materialCounts=new java.util.HashMap<>();
            for(int i=0;i<materialCount;i++){
                JSONArray layer=savedLayers.getJSONArray(i);
                if(layer.length()!=4)throw new Exception("Неверная запись R4.1");
                int tx=layer.getInt(0),tz=layer.getInt(1);
                String key=layer.getString(2);
                String pair=tx+":"+tz;
                if(!validTexture(key)||!uniqueMaterialTile.add(pair+":"+key)||
                   tx*64>=worldSize/2||tx*64+64<=-worldSize/2||
                   tz*64>=worldSize/2||tz*64+64<=-worldSize/2)
                    throw new Exception("Повтор или неверный материал R4.1");
                int count=materialCounts.containsKey(pair)?materialCounts.get(pair)+1:1;
                if(count>4)throw new Exception("Не больше четырёх материалов на участок");
                materialCounts.put(pair,count);touchedTileKeys.add(pair);
                materialCoords[2*i]=tx;materialCoords[2*i+1]=tz;
                materialKeys[i]=key;
                materialWeights[i]=restoredMaterialMask(layer.getString(3));
                requiredMaterials.add(key);
            }
            if(touchedTileKeys.size()>32)throw new Exception("Превышен лимит участков R4.1");
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
            String[] objectPaintKeys=new String[arr.length()];
            HashSet<String> referencedPaint=new HashSet<>(requiredMaterials);
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
            // Version 1 archives have no roads. Version 2 stores authoring centerlines,
            // not generated rendering triangles. Validate before native atomic scene swap.
            JSONArray savedRoads=document.optJSONArray("roads");
            if((sceneVersion==2 && savedRoads==null)||
               (sceneVersion==1 && document.has("roads"))||
               (document.has("roads")&&savedRoads==null))
                throw new Exception("Некорректная версия или раздел дорог в сцене");
            final int roadCount=savedRoads==null?0:savedRoads.length();
            if(roadCount>256)throw new Exception("Слишком много дорог");
            long[] roadIds=new long[roadCount];
            String[] roadNames=new String[roadCount],roadMaterials=new String[roadCount];
            double[] roadWidths=new double[roadCount];
            double[][] roadPaths=new double[roadCount][];
            HashSet<Long> usedRoadIds=new HashSet<>();
            int totalPoints=0,roadVertices=0;
            for(int i=0;i<roadCount;i++){
                JSONObject road=savedRoads.getJSONObject(i);
                String rawId=road.getString("id");
                if(!rawId.matches("[1-9][0-9]{0,18}"))
                    throw new Exception("Неверный идентификатор дороги");
                long id=Long.parseLong(rawId);
                if(!usedRoadIds.add(id))throw new Exception("Повторяется ID дороги");
                String name=road.getString("name");
                if(!name.matches("[A-Za-z0-9 _-]{1,64}"))
                    throw new Exception("Неверное имя дороги");
                String material=road.getString("material");
                if(!material.equals("builtin:cobblestone")&&
                   !material.equals("builtin:dirt")&&
                   !material.equals("builtin:sand")&&
                   !material.equals("builtin:grass"))
                    throw new Exception("Неизвестное покрытие дороги");
                double width=road.getDouble("widthMetres");
                if(!Double.isFinite(width)||width<.25||width>32)
                    throw new Exception("Неверная ширина дороги");
                JSONArray points=road.getJSONArray("controlPoints");
                if(points.length()<2||points.length()>4096||totalPoints+points.length()>4096)
                    throw new Exception("Слишком много точек дороги");
                totalPoints+=points.length();
                double[] xy=new double[points.length()*2];
                double distance=0;
                for(int j=0;j<points.length();j++){
                    JSONArray point=points.getJSONArray(j);
                    if(point.length()!=2)throw new Exception("Неверная точка дороги");
                    double x=point.getDouble(0),z=point.getDouble(1);
                    if(!Double.isFinite(x)||!Double.isFinite(z)||
                        Math.abs(x)>worldSize/2.0-width*2||
                        Math.abs(z)>worldSize/2.0-width*2)
                        throw new Exception("Дорога выходит за карту");
                    xy[j*2]=x;xy[j*2+1]=z;
                    if(j>0){
                        double len=Math.hypot(x-xy[(j-1)*2],z-xy[(j-1)*2+1]);
                        if(len<.01||!Double.isFinite(len))
                            throw new Exception("Слишком близкие точки дороги");
                        distance+=len;
                        roadVertices+=6*(int)Math.ceil(len/2.0);
                        if(roadVertices>120000||distance>25000)
                            throw new Exception("Дорожная сеть слишком велика");
                    }
                }
                roadIds[i]=id;roadNames[i]=name;roadMaterials[i]=material;
                roadWidths[i]=width;roadPaths[i]=xy;
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
            if(sourceReferences!=null&&parsedGuide!=null){
                File destination=new File(referenceDir(),parsedGuide.key);
                if(destination.exists()){
                    if(!parsedGuide.key.equals(mapKey(Files.readAllBytes(destination.toPath()))))
                        throw new Exception("Повреждён локальный PNG-план города");
                }else{
                    File partial=new File(referenceDir(),parsedGuide.key+".part");
                    try{
                        Files.copy(new File(sourceReferences,parsedGuide.key).toPath(),
                            partial.toPath(),StandardCopyOption.REPLACE_EXISTING);
                        Files.move(partial.toPath(),destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    }finally{Files.deleteIfExists(partial.toPath());}
                }
            }
            surface.queueEvent(() -> {
                ArrayList<String> uploaded=new ArrayList<>();
                boolean committed=false;
                try {
                    if(parsedGuide!=null&&!nativeMapStage(parsedMapImage.bytes,
                            parsedMapImage.width,parsedMapImage.height))
                        throw new Exception("Нельзя загрузить PNG-подложку в видеопамять");
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
                            roadIds,roadNames,roadMaterials,roadWidths,roadPaths,
                            preciseTileCoords,preciseTileBytes,
                            materialCoords,materialKeys,materialWeights))
                        throw new Exception("Сцена не прошла полную проверку в движке");
                    if(parsedGuide!=null){
                        if(!nativeMapCommit(parsedGuide.centerX,parsedGuide.centerZ,
                                parsedGuide.widthMetres,parsedGuide.opacity,parsedGuide.visible))
                            throw new Exception("PNG-подложка не подошла к новому размеру мира");
                    }else nativeMapClear();
                    mapGuide=parsedGuide;
                    mapTopViewActive=false;
                    committed=true;
                    roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;
                    surface.requestRender();
                    refreshSelected();
                    message("Сцена загружена · "+items.length+" объектов, текстур "+referencedPaint.size()+", GLB "+assets.size()+
                            " (из памяти "+(assets.size()-missing.size())+")");
                }catch(Exception e){
                    message("Сцена НЕ изменена: "+e.getMessage());
                }finally{
                    if(!committed){
                        nativeMapAbort();
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
                        edit(()->{
                            nativeReset(demo);nativeMapClear();
                            mapGuide=null;mapTopViewActive=false;
                        });
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
        private volatile boolean paintStrokeFailed=false;
        private float precisionScreenY(float y){
            return surfaceY(Math.max(0.f,y-dp(70))); // visible cursor ABOVE the finger
        }
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
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(importing)return true; // Prevent input changes during scene replacement.
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN) {
                downX=lastX=e.getX();downY=lastY=e.getY();multiTouch=false;lastPinch=0;
                touchedGizmoAxis=-1;
                final float px=surfaceX(downX),py=surfaceY(downY);
                if(precisionPaintMode){
                    paintStrokeFailed=false;
                    final float cy=precisionScreenY(downY);
                    final String paintKey=activePaintKey;
                    final boolean eraser=precisionPaintErase;
                    final float rad=precisionPaintRadius, strength=precisionPaintOpacity;
                    queueEvent(()->{
                        nativePaintCursor(px,cy,rad);
                        final boolean ok=materialPaintMode?
                            nativeLayerStrokeStart(px,cy,rad,strength,paintKey,eraser):
                            nativePaintStrokeBegin(px,cy,rad,strength,paintKey,eraser);
                        if(!ok)paintStrokeFailed=true;
                        requestRender();
                    });
                    return true;
                }
                queueEvent(() -> {
                    nativeGizmoEnd();gizmoEditStarted=false;
                    if(placingKind==-1&&terrainMode==0&&!paintTerrainMode&&!roadMode&&!roadMoveMode)
                        touchedGizmoAxis=nativeGizmoHit(px,py);
                });
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                multiTouch=true;
                touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;
                    nativePaintStrokeEnd();nativeLayerStrokeEnd();requestRender();});
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
                    if(precisionPaintMode){
                        final float px=surfaceX(x),py=precisionScreenY(y);
                        queueEvent(()->{
                            if(!(materialPaintMode?nativeLayerStrokeMove(px,py):
                                  nativePaintStrokeMove(px,py)))
                                paintStrokeFailed=true;
                            interactiveRender();
                        });
                        lastX=x;lastY=y;
                        return true;
                    }
                    final float dx=x-lastX,dy=y-lastY;
                    if(dx!=0||dy!=0){
                        final float sx=surfaceX(dx),sy=surfaceY(dy);
                        final boolean movedEnough=Math.hypot(x-downX,y-downY)>dp(5);
                        // All input decisions happen on the GLES event queue:
                        // axis hit-test on DOWN is guaranteed to run before MOVE.
                        queueEvent(()->{
                            final int axis=touchedGizmoAxis;
                            if(axis>=0&&placingKind==-1&&terrainMode==0&&!paintTerrainMode&&!roadMode&&!roadMoveMode){
                                if(!gizmoEditStarted&&movedEnough)
                                    gizmoEditStarted=nativeGizmoBegin(axis);
                                if(gizmoEditStarted)nativeGizmoDrag(axis,sx,sy);
                            }else if(mapTopViewActive)nativePan(dx,dy);
                            else nativeOrbit(dx,dy,1);
                            interactiveRender();
                        });
                    }
                    lastX=x;lastY=y;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP) {multiTouch=true;lastPinch=0;return true;}
            if(action==MotionEvent.ACTION_UP) {
                if(precisionPaintMode){
                    final float px=surfaceX(e.getX()),py=precisionScreenY(e.getY());
                    final boolean wasMultiTouch=multiTouch;
                    queueEvent(()->{
                        if(!wasMultiTouch&&!(materialPaintMode?nativeLayerStrokeMove(px,py):
                                  nativePaintStrokeMove(px,py)))paintStrokeFailed=true;
                        nativePaintStrokeEnd();nativeLayerStrokeEnd();requestRender();
                        if(paintStrokeFailed)message("Рисование остановлено: лимит участков/материалов, граница или текстура");
                    });
                    multiTouch=false;
                    return true;
                }
                if(!multiTouch){
                    // Decide after the queued DOWN hit-test; don't accidentally
                    // pick another entity when tapping a gizmo handle.
                    final float upX=e.getX(),upY=e.getY();
                    final float px=surfaceX(upX),py=surfaceY(upY);
                    final boolean tap=Math.hypot(upX-downX,upY-downY)<dp(8);
                    final int kind=placingKind,brush=terrainMode;
                    final boolean paint=paintTerrainMode;
                    final boolean roadDraw=roadMode;
                    final boolean roadPointMove=roadMoveMode;
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
                            if(roadPointMove){
                                if(nativeRoadPointTapMove(px,py)){
                                    roadMoveMode=false;
                                    message("Контрольная точка перенесена · ↶ отменить");
                                    ui(()->statusText.setText(
                                        "Точка перенесена · можно выбрать следующую."));
                                }else message("Перенос невозможен: край карты или соседняя точка");
                            }else if(roadDraw){
                                final int result=nativeRoadTap(px,py);
                                if(result==1)message("Первая точка · коснись земли ещё раз");
                                else if(result==2)message("Дорога создана · добавляй точки");
                                else if(result==3)message("Точка дороги добавлена");
                                else if(result==0)message("Не попали на землю: попробуй другой ракурс");
                                else message("Точка слишком близко, за краем карты или превышен лимит дороги");
                            }else if(paint){
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
                            if(dx!=0||dy!=0){if(mapTopViewActive)nativePan(dx,dy);else nativeOrbit(dx,dy,1);}
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
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;
                    nativePaintStrokeEnd();nativeLayerStrokeEnd();requestRender();});
                return true;
            }
            return true;
        }
    }
}
