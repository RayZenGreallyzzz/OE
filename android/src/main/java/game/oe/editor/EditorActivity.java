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
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
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
    private static native int nativeFoundationUnderSelected(int foundationKind);
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
    // Visual authoring guides only. Never become game physics or entities.
    private static native int nativePlotAdd(String name,float x,float z,int size);
    private static native boolean nativePlotEdit(int id,String name,float x,float z,int size);
    private static native boolean nativePlotDelete(int id);
    private static native boolean nativePlotFocus(int id);
    private static native String[] nativePlotList();
    private static native void nativePlotShow(boolean visible);
    private static native int nativeBarrierAdd(double ax,double az,double bx,
        double bz,double thickness,double height);
    private static native double[] nativeBarrierPointAtScreen(float px,float py);
    private static native void nativeBarrierDraftPoint(boolean visible,float x,float z);
    private static native boolean nativeBarrierEdit(int id,double ax,double az,
        double bx,double bz,double thickness,double height);
    private static native boolean nativeBarrierDelete(int id);
    private static native int nativeBarrierCutAtScreen(int id,float px,float py,
        float radius);
    private static native String[] nativeBarrierList();
    private static native void nativeBarrierShow(boolean visible);
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
            double[] roadWidths, double[] roadTileMetres,double[][] roadPoints,int[] tileCoords,byte[][] tileData,
            int[] layerCoords,String[] layerKeys,byte[][] layerMasks,float[] lakes,
            int uv56AreaSqM,int[] uv56Nodes,
            int[] plotIds,String[] plotNames,float[] plotValues,
            double[] wallRecords);
    private static native boolean nativeRegisterPaintTexture(String key, byte[] rgba,int width,int height);
    private static native boolean nativePaintSelected(String key);
    private static native int nativePaintTerrainAtScreen(float x,float y,float radius,String key);
    private static native void nativeChangeSelectedHeight(float dy);
    private static native void nativeScaleSelectedAxis(int axis,float factor);
    private static native void nativeSnapSelectedToGround();
    private static native int nativeApplyTerrainBrushAtScreen(float x,float y,float radius,float amount);
    private static native int nativeTerrainBrushCount();
    private static native boolean nativeUv56SculptAtScreen(int tool,float x,float y,
            float radius,float power); // stage 1 compatibility
    private static native boolean nativeGroundHitAtScreen(float x,float y);
    private static native void nativeSetNeutralLandPreview(boolean on);
    private static native int nativeUv56Area();
    private static native boolean nativeUv56ConfigureArea(int areaSqM);
    private static native int nativeUv56LastStrokeError();
    private static native int nativeUv56LastTouchHeightCm();
    private static native boolean nativeUv56StrokeStart(int tool,float x,float y,
            float radius,float power);
    private static native boolean nativeUv56StrokeMove(int tool,float x,float y,
            float radius,float power);
    private static native void nativeUv56StrokeEnd();
    private static native int nativeLakePlaceAtScreen(float x,float y,float radius,float fillDepth);
    private static native String[] nativeLakeNames();
    private static native boolean nativeLakeSelect(int index);
    private static native boolean nativeLakeAdjust(float deltaHeight,float deltaRadius);
    private static native boolean nativeLakeDelete();
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
    private static native boolean nativeRoadSetCustomTexture(String key);
    private static native boolean nativeRoadSetTileMetres(double metres);
    private static native double nativeRoadSelectedTileMetres();
    private static native boolean nativeRoadRemoveLastPoint();
    private static native boolean nativeRoadDeleteSelected();
    private static native double[] nativeRoadPointCoords();
    private static native boolean nativeRoadPointSelect(int index);
    private static native boolean nativeRoadPointMove(double x,double z);
    private static native boolean nativeRoadPointTapMove(float x,float y);
    private static native boolean nativeRoadSmooth(double radiusMetres);
    private static native int nativeRoadIntersectionCount();
    private static native int[] nativeRoadTopologyStats();
    private static native int[] nativeBridgeTraversalStats();
    private static native boolean nativeWalkPreviewStart();
    private static native boolean nativeWalkPreviewStartAtBarrier(int wallId);
    private static native boolean nativeWalkPreviewStep(float axisX,float axisZ,
            boolean jump,int fixedTicks);
    private static native void nativeWalkPreviewStop();
    private static native double[] nativeWalkPreviewState();
    private static native boolean nativeBridgeAutoAlign();
    private static native int[] nativeMedievalSurfaceStats();
    private static native boolean nativeRoadBakeTopology();
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
    private static final int PICK_GLB = 702, PICK_PROJECT=703, SAVE_PROJECT=704, PICK_PAINT=705, PICK_MAP=706, PICK_PLOT_PNG=707;
    private static final int MAX_ASSETS = 12;

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
    private TextView renderStatsText;
    private LinearLayout toolDrawer, paletteItems, inspectorPanel, advancedInspector;
    private WalkJoystick walkJoystick;
    private Button walkJumpButton,walkStopButton;
    private boolean walkControlsActive=false,walkTickPending=false;
    // Source: UV56 landscape-manager.js. No UV56 building/character code.
    private volatile int uv56SculptTool=0; // 1 raise,2 lower,3 ridge,4 smooth,5 flatten
    private volatile float uv56SculptRadius=4f,uv56SculptPower=2f;
    // First open must select a working brush, not the passive camera mode.
    private volatile int uv56LandscapeMode=1,uv56BrushCm=400;
    private volatile boolean uv56NeutralCanvas=true; // editor view, not saved game data
    private volatile boolean uv56ReverseOrbit=true; // owner can toggle direction
    private static final int MAX_TILE_LIBRARY=32; // imported files, not automatic GPU uploads
    private volatile String activePaletteCategory="Здания";
    private volatile boolean editorPlotsVisible=true;
    private volatile boolean barriersVisible=true;
    private volatile int barrierCutId=0;
    private volatile float barrierCutRadius=4f;
    private volatile boolean barrierDrawMode=false;
    private volatile int barrierDrawGeneration=0;
    private double[] barrierDraftStart=null; // GL thread only; never saved
    private NativeLandscapePanel activeLandscapePanel;
    private boolean walkJumpQueued=false,walkAirborne=false;
    private float walkAxisX=0f,walkAxisZ=0f;
    private volatile int walkSessionGeneration=0;
    private long lastWalkHudAt=0;
    private final Handler walkHandler=new Handler(Looper.getMainLooper());
    // At most one pending GL tick. 2 fixed 60Hz steps for each ~33ms frame;
    // pause/idle never queues hundreds of catch-up steps on a 4GiB tablet.
    private final Runnable walkTick=new Runnable(){
        @Override public void run(){
            if(!walkControlsActive||surface==null||importing)return;
            final boolean moving=Math.hypot(walkAxisX,walkAxisZ)>.05;
            final boolean jump=walkJumpQueued;
            if(!walkTickPending&&(moving||jump||walkAirborne)){
                final float dx=walkAxisX,dz=walkAxisZ;
                final int session=walkSessionGeneration;
                walkJumpQueued=false;walkTickPending=true;
                surface.queueEvent(()->{
                    if(session!=walkSessionGeneration)return;
                    boolean ok=nativeWalkPreviewStep(dx,dz,jump,2);
                    final double[] state=ok?nativeWalkPreviewState():null;
                    surface.requestRender();
                    ui(()->{
                        if(session!=walkSessionGeneration)return;
                        walkTickPending=false;
                        if(!ok||state==null){
                            finishWalkControls();
                            message("Путник остановлен: достигнут край карты либо изменена сцена");
                            return;
                        }
                        walkAirborne=state[3]<.5;
                        long now=SystemClock.uptimeMillis();
                        if(now-lastWalkHudAt>350){
                            lastWalkHudAt=now;
                            statusText.setText(walkPreviewDescription(state));
                        }
                    });
                });
            }
            walkHandler.postDelayed(this,moving||jump||walkAirborne?33:100);
        }
    };

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
    // Saved authoring plans are separate from the pre-existing global road PNG.
    // Only ONE plan is rendered through the native R5.7 guide at a time.
    private final java.util.concurrent.ConcurrentHashMap<Integer,MapGuide>
        plotPlans=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int activePlotPlanId=0,pendingPlotPngId=0;
    // Tracks frame origins so a PNG follows its site across undo and redo.
    private final java.util.concurrent.ConcurrentHashMap<Integer,double[]>
        plotPlanSiteCenters=new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_PLOT_PNGS=8;
    private volatile double selectedRoadWidth=6.0;
    private volatile float paintRadius=16f;
    private volatile long statsUpdatedAt=0;
    private volatile boolean importing = false;
    private volatile int placingKind = -1;
    private volatile String placingGlb = null;
    private volatile int terrainMode=0; // -1 lower, +1 raise
    private volatile boolean lakeMode=false; // tap to fill an excavated depression
    private volatile float lakeRadius=64f,lakeFillDepth=8f;
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
        if(walkControlsActive)finishWalkControls();
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
        title.setText("OE R5.7 · 🌳 Ландшафт UV56");
        title.setTextColor(gold);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8),0,dp(5),0);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(45),1f));
        Button menu=button("☰ Каталог",()->toggleDrawer(!drawerOpen));
        top.addView(menu,new LinearLayout.LayoutParams(dp(110),dp(43)));
        Button land=button("🌿 Ландшафт",()->{
            showPalette("Рельеф");
            toggleDrawer(true);
        });
        top.addView(land,new LinearLayout.LayoutParams(dp(112),dp(43)));
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
        for(String category:new String[]{"Здания","Детали","Мосты","Фундаменты","Декор","Рельеф","Участки","Коллизии","Вода","Дороги","Текстуры","Проект"}){
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
        // UV56 demonstration: persistent scrollable left landscape sidebar.
        // The scene remains full-screen, with its own touch surface on the right.
        FrameLayout.LayoutParams drawerParams=new FrameLayout.LayoutParams(dp(355),-1,
            Gravity.TOP|Gravity.LEFT);
        drawerParams.topMargin=dp(50);
        drawerParams.bottomMargin=dp(8);
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
        // Temporary analog controls never cover the main editor when
        // the preview is stopped. Bottom left = movement; right = jump/exit.
        walkJoystick=new WalkJoystick();
        FrameLayout.LayoutParams pad=new FrameLayout.LayoutParams(
            dp(154),dp(154),Gravity.LEFT|Gravity.BOTTOM);
        pad.leftMargin=dp(14);pad.bottomMargin=dp(82);
        root.addView(walkJoystick,pad);
        walkJoystick.setVisibility(View.GONE);
        walkJumpButton=button("🦘 Прыжок",()->{
            if(walkControlsActive)walkJumpQueued=true;
        });
        FrameLayout.LayoutParams jump=new FrameLayout.LayoutParams(
            dp(116),dp(54),Gravity.RIGHT|Gravity.BOTTOM);
        jump.rightMargin=dp(16);jump.bottomMargin=dp(96);
        root.addView(walkJumpButton,jump);
        walkJumpButton.setVisibility(View.GONE);
        walkStopButton=button("✕ Тест",this::stopWalkCollisionPreview);
        FrameLayout.LayoutParams exit=new FrameLayout.LayoutParams(
            dp(116),dp(48),Gravity.RIGHT|Gravity.BOTTOM);
        exit.rightMargin=dp(16);exit.bottomMargin=dp(159);
        root.addView(walkStopButton,exit);
        walkStopButton.setVisibility(View.GONE);
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
            if(placingKind>=0||terrainMode!=0||uv56SculptTool!=0||paintTerrainMode||roadMode||precisionPaintMode)toggleDrawer(false);
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));
        p.setMargins(dp(4),dp(2),dp(4),dp(2));
        paletteItems.addView(b,p);
    }
    private void showPalette(String category){
        // Entering another tool must never leave a hidden wall touch handler.
        if(!"Коллизии".equals(category)&&(barrierDrawMode||barrierCutId>0))
            cancelPlace();
        paletteItems.removeAllViews();
        activeLandscapePanel=null;
        activePaletteCategory=category;
        if("Здания".equals(category)){
            addPaletteOption("+ Таверна",()->enterPlaceMode(0,null));
            addPaletteOption("+ Кузница",()->enterPlaceMode(1,null));
            addPaletteOption("+ Аукционный дом",()->enterPlaceMode(2,null));
            addPaletteOption("🧱 Каменный фундамент · поставить",()->enterPlaceMode(19,null));
            addPaletteOption("⬇ Фундамент под выбранное здание",this::foundationUnderSelected);
        }else if("Детали".equals(category)){
            addPaletteOption("▧ Стена",()->enterPlaceMode(10,null));
            addPaletteOption("▤ Пол",()->enterPlaceMode(11,null));
            addPaletteOption("⌂ Крыша",()->enterPlaceMode(12,null));
            addPaletteOption("▯ Дверная рама",()->enterPlaceMode(13,null));
            addPaletteOption("▣ Окно",()->enterPlaceMode(14,null));
            addPaletteOption("▥ Колонна",()->enterPlaceMode(15,null));
            addPaletteOption("↗ Лестница",()->enterPlaceMode(16,null));
            addPaletteOption("🧱 Фундамент 7×6 м",()->enterPlaceMode(19,null));
        }else if("Мосты".equals(category)){
            addPaletteOption("🪨 Каменный мост · 8 м",()->enterPlaceMode(17,null));
            addPaletteOption("🪵 Деревянный мост · 8 м",()->enterPlaceMode(18,null));
            addPaletteOption("🌿 Узкий мостик · 6 м",()->enterPlaceMode(20,null));
            addPaletteOption("🪢 Верёвочный мост · 9 м",()->enterPlaceMode(21,null));
            addPaletteOption("🪨 Каменный брод · 8 м",()->enterPlaceMode(22,null));
            addPaletteOption("🐴 Широкий мост для телег · 10 м",()->enterPlaceMode(27,null));
            addPaletteOption("🌾 Настил через болото · 9 м",()->enterPlaceMode(28,null));
            addPaletteOption("🧲 Подогнать выбранный мост к дорогам",this::alignSelectedBridge);
            addPaletteOption("👣 Проверить проходы по мостам",this::showBridgeTraversalStatus);
            addPaletteOption("🧍 Проверить реальную ходьбу · R5.6",this::startWalkCollisionPreview);
            addPaletteOption("🔍 Статус путника",this::showWalkPreviewState);
            addPaletteOption("ℹ Регулировка высоты",()->new AlertDialog.Builder(this)
                .setTitle("Мосты и переправы OE")
                .setMessage("Поставь мост касанием земли. Затем выбери объект и подними " +
                    "по оси Y, чтобы настил совпал с берегами. Эти лёгкие " +
                    "декорации пока не создают воду или физические столкновения.")
                .setPositiveButton("Понятно",null).show());
        }else if("Фундаменты".equals(category)){
            addPaletteOption("🧱 Каменный фундамент · 7×6 м",()->enterPlaceMode(19,null));
            addPaletteOption("🪵 Свайное основание · 7×6 м",()->enterPlaceMode(23,null));
            addPaletteOption("🏚 Руины фундамента · 7×6 м",()->enterPlaceMode(24,null));
            addPaletteOption("🏡 Основание хижины · 4×4 м",()->enterPlaceMode(25,null));
            addPaletteOption("🏰 Основание башни · 7×7 м",()->enterPlaceMode(26,null));
            addPaletteOption("⬇ Под выбранное здание",this::foundationUnderSelected);
        }else if("Декор".equals(category)){
            addPaletteOption("+ Фонарь",()->enterPlaceMode(3,null));
            addPaletteOption("+ Фонтан",()->enterPlaceMode(4,null));
            addPaletteOption("+ Дерево",()->enterPlaceMode(5,null));
            addPaletteOption("+ Камни",()->enterPlaceMode(7,null));
            addPaletteOption("+ Куст",()->enterPlaceMode(8,null));
            addPaletteOption("+ Лавка",()->enterPlaceMode(9,null));
            addPaletteOption("Импорт 3D: GLB / glTF / VOX",this::pickGlb);
        }else if("Рельеф".equals(category)){
            // Stage 3 bug: the toolbar previously showed LOOK mode by default
            // while the owner was trying to raise ground. Arm Raise visibly.
            if(uv56LandscapeMode>=1&&uv56LandscapeMode<=5&&
               uv56SculptTool!=uv56LandscapeMode)
                enterUv56Sculpt(uv56LandscapeMode);
            surface.queueEvent(()->{
                nativeSetNeutralLandPreview(uv56NeutralCanvas);
                surface.requestRender();
            });
            showUv56LandscapePanel();
        }else if("Участки".equals(category)){
            addPaletteOption("🏘 Создать участок села 500×500 м",
                ()->newEditorPlot(500));
            addPaletteOption("🏙 Создать участок города 1000×1000 м",
                ()->newEditorPlot(1000));
            addPaletteOption("📋 Список / изменить / переместить",this::chooseEditorPlot);
            addPaletteOption("🗺 PNG-план для выбранного участка",this::choosePlotForPlan);
            addPaletteOption("🎥 Режим карты: план выбранного участка",
                this::choosePlotForPlan);
            addPaletteOption(editorPlotsVisible?"👁 Скрыть рамки":"👁 Показать рамки",
                this::toggleEditorPlotGuides);
            addPaletteOption("ℹ Рамки — только в редакторе",()->
                new AlertDialog.Builder(this).setTitle("Участки OE")
                    .setMessage("Границы не создают стен и коллизий, не "+
                        "мешают персонажу и не попадают в игровую сцену. "+
                        "Они помогают строить сёла и города на ОДНОЙ карте. "+
                        "PNG-планы участков добавим отдельным этапом.")
                    .setPositiveButton("Понятно",null).show());
        }else if("Коллизии".equals(category)){
            addPaletteOption("✏ Нарисовать границу по точкам",
                this::startBarrierPolyline);
            addPaletteOption("🧱 Поставить невидимую преграду 30 м",
                ()->createCollisionBarrier(30));
            addPaletteOption("🧱 Поставить преграду 100 м",
                ()->createCollisionBarrier(100));
            addPaletteOption("🧱 Список / изменить коллизии",this::chooseCollisionBarrier);
            addPaletteOption(barriersVisible?"👁 Скрыть розовую разметку":
                "👁 Показать розовую разметку",this::toggleCollisionBarriers);
            addPaletteOption("🧍 Проверить коллизии путником",this::startWalkCollisionPreview);
            addPaletteOption("✕ Завершить рисование / вырезание",this::cancelPlace);
            addPaletteOption("ℹ О коллизиях",()->
                new AlertDialog.Builder(this).setTitle("Физические преграды")
                    .setMessage("Розовая линия видна только в редакторе. "+
                        "Рисование: первая точка отмечается жёлтым, "+
                        "следующими касаниями создаются настоящие физические "+
                        "отрезки от 1 до 512 м. Два пальца перемещают камеру. "+
                        "В списке можно задать точные X/Z, толщину и высоту. "+
                        "Проходы вырезаются касанием выбранной стены. "+
                        "Рамки участков коллизий не создают.")
                    .setPositiveButton("Понятно",null).show());
        }else if("Вода".equals(category)){
            addPaletteOption("⛏ Вырыть чашу для озера",this::startLakeExcavation);
            addPaletteOption("🌊 Залить озеро касанием",this::beginLakePlacement);
            addPaletteOption("📏 Радиус и глубина заполнения",this::chooseLakeSettings);
            addPaletteOption("📋 Выбрать существующее озеро",this::chooseLake);
            addPaletteOption("⬆ Поднять воду на 1 м",()->adjustLake(1f,0f));
            addPaletteOption("⬇ Опустить воду на 1 м",()->adjustLake(-1f,0f));
            addPaletteOption("↔ Увеличить радиус на 8 м",()->adjustLake(0f,8f));
            addPaletteOption("↔ Уменьшить радиус на 8 м",()->adjustLake(0f,-8f));
            addPaletteOption("🗑 Удалить выбранное озеро",this::deleteLake);
            addPaletteOption("✕ Завершить работу с водой",this::cancelPlace);
        }else if("Дороги".equals(category)){
            addPaletteOption("🗺 Загрузить PNG-план города",this::pickMapReference);
            addPaletteOption("🗺 Активировать общий PNG-план",
                this::showGlobalMapReference);
            addPaletteOption("📍 Положение и масштаб плана",this::configureMapReference);
            addPaletteOption("👁 Показать / скрыть план",this::toggleMapReference);
            addPaletteOption("⬇ Вид сверху на карту",this::showMapTopView);
            addPaletteOption("🎥 Вернуть вид 3D",this::showMapPerspective);
            addPaletteOption("🗑 Убрать PNG-подложку",this::askRemoveMapReference);
            addPaletteOption("🪨 Каменный мост · 8 м",()->enterPlaceMode(17,null));
            addPaletteOption("🪵 Деревянный мост · 8 м",()->enterPlaceMode(18,null));
            addPaletteOption("🛣 Новая дорога · точки",this::beginNewRoad);
            addPaletteOption("✔ Завершить дорогу",this::finishRoad);
            addPaletteOption("📋 Выбрать дорогу",this::chooseExistingRoad);
            addPaletteOption("➕ Продолжить выбранную",this::continueExistingRoad);
            addPaletteOption("📍 Переместить точку касанием",()->chooseRoadPoint(true));
            addPaletteOption("⌨ Точные координаты точки",()->chooseRoadPoint(false));
            addPaletteOption("〰 Сгладить повороты",this::chooseRoadSmoothing);
            addPaletteOption("🔗 Соединить старые дороги",this::bakeExistingRoadTopology);
            addPaletteOption("✣ Узлы дорожной сети",this::showJunctionStatus);
            addPaletteOption("👣 Проходы через мосты · R5.4",this::showBridgeTraversalStatus);
            addPaletteOption("🧍 Физика R5.6: путник на дороге",this::startWalkCollisionPreview);
            addPaletteOption("⬆ Путник: север −Z",()->moveWalkPreview(0f,-1f,false));
            addPaletteOption("⬇ Путник: юг +Z",()->moveWalkPreview(0f,1f,false));
            addPaletteOption("⬅ Путник: запад −X",()->moveWalkPreview(-1f,0f,false));
            addPaletteOption("➡ Путник: восток +X",()->moveWalkPreview(1f,0f,false));
            addPaletteOption("🦘 Путник: прыжок",()->moveWalkPreview(0f,0f,true));
            addPaletteOption("🔍 Статус столкновения",this::showWalkPreviewState);
            addPaletteOption("✕ Завершить тест физики",this::stopWalkCollisionPreview);
            addPaletteOption("🧲 Подогнать выбранный мост",this::alignSelectedBridge);
            addPaletteOption("🪨 Проверить швы перекрёстков R5.2",this::showMedievalSurfaceStats);
            addPaletteOption("↶ Удалить последнюю точку",this::removeRoadPoint);
            addPaletteOption("↔ Ширина дороги",this::chooseRoadWidth);
            addPaletteOption("🎨 Покрытие дороги",this::chooseRoadMaterial);
            addPaletteOption("🖼 Загрузить PNG/JPG для дороги",this::pickPaintTexture);
            addPaletteOption("🖼 Выбрать PNG/JPG-текстуру дороги",this::chooseRoadTexture);
            addPaletteOption("🪨 Нанести активную текстуру",this::applyActiveRoadTexture);
            addPaletteOption("📏 Повторение камней по дороге",this::chooseRoadTextureSize);
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
    private void foundationUnderSelected(){
        if(importing){message("Дождись окончания загрузки проекта");return;}
        final int[] kinds={19,23,24,26};
        final String[] names={
            "🧱 Каменный фундамент",
            "🪵 Свайный настил",
            "🏚 Основание с руинами",
            "🏰 Каменное основание башни"
        };
        new AlertDialog.Builder(this)
            .setTitle("Какое основание поставить под зданием?")
            .setItems(names,(dialog,which)->edit(()->{
                final int id=nativeFoundationUnderSelected(kinds[which]);
                if(id>0)message("Основание установлено под зданием · ↶ отменить");
                else message("Выдели таверну, кузницу или аукционный дом, затем повтори");
            }))
            .setNegativeButton("Отмена",null).show();
    }
    private void enterPlaceMode(int kind, String asset) {
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing) { message("Сначала дождись завершения импорта"); return; }
        terrainMode=0;lakeMode=false;paintTerrainMode=false;roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;
        materialPaintMode=false;
         surface.queueEvent(()->{nativePaintStrokeEnd();nativeLayerStrokeEnd();});
        surface.queueEvent(EditorActivity::nativeRoadFinish);
        placingKind = kind;
        placingGlb = asset;
        statusText.setText("РАЗМЕЩЕНИЕ: коснись свободной земли, чтобы поставить объект. Для отмены нажми «Отмена».");
        message("Коснись места на карте для размещения");
    }
    private void cancelPlace() {
        barrierCutId=0;
        barrierDrawMode=false;
        ++barrierDrawGeneration;
        barrierDraftStart=null; // actual GL state is cleared on its queue below
        if(surface!=null)surface.queueEvent(()->
            nativeBarrierDraftPoint(false,0f,0f));
        uv56SculptTool=0;
        if(surface!=null)surface.queueEvent(EditorActivity::nativeUv56StrokeEnd);
        paintTerrainMode=false;roadMode=false;roadMoveMode=false;precisionPaintMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();nativeLayerStrokeEnd();});
        terrainMode=0;lakeMode=false;
        placingKind = -1;
        placingGlb = null;
        statusText.setText("Режим выбора · два пальца — перемещение; кнопки «Размер карты» и «Координаты» — большой мир.");
        message("Размещение отменено");
    }
    private void startLakeExcavation(){
        // Preserve R5.7 legacy brush data but use only native UV56 for new digs.
        uv56BrushCm=1600;
        uv56SculptRadius=16f;uv56SculptPower=5f;
        openUv56Mode(2);
        message("Нарисуй чашу инструментом UV56, затем залей озеро");
    }
    private void beginLakePlacement(){
        if(importing){message("Сначала дождись загрузки");return;}
        cancelPlace();
        lakeMode=true;
        statusText.setText("ОЗЕРО: коснись дна углубления · радиус "+
            (int)lakeRadius+" м, заполнение +"+(int)lakeFillDepth+" м.");
        message("Коснись дна будущего озера; уровень можно изменить после заливки");
    }
    private void chooseLakeSettings(){
        final String[] options={
            "Пруд · радиус 16 м / слой 2 м",
            "Маленькое озеро · 32 м / 4 м",
            "Озеро · 64 м / 8 м",
            "Большое озеро · 96 м / 12 м",
            "Долина · 128 м / 20 м",
            "Водохранилище · 160 м / 30 м"
        };
        final float[] radius={16,32,64,96,128,160};
        final float[] depth={2,4,8,12,20,30};
        new AlertDialog.Builder(this).setTitle("Радиус и глубина заполнения")
            .setItems(options,(dialog,index)->{
                lakeRadius=radius[index];lakeFillDepth=depth[index];
                message("Озеро: радиус "+(int)lakeRadius+
                        " м, уровень выше дна на "+(int)lakeFillDepth+" м");
            }).setNegativeButton("Отмена",null).show();
    }
    private void chooseLake(){
        if(importing)return;
        surface.queueEvent(()->{
            final String[] options=nativeLakeNames();
            ui(()->{
                if(options==null||options.length==0){
                    message("В проекте пока нет озёр");return;
                }
                new AlertDialog.Builder(this).setTitle("Выбери озеро для редактирования")
                    .setItems(options,(dialog,index)->{
                        surface.queueEvent(()->{
                            if(nativeLakeSelect(index))
                                message("Озеро выбрано · меняй уровень и радиус в разделе «Вода»");
                        });
                    }).setNegativeButton("Отмена",null).show();
            });
        });
    }
    private void adjustLake(float level,float radius){
        if(importing)return;
        edit(()->{
            if(nativeLakeAdjust(level,radius))
                message("Озеро изменено · ↶ можно отменить");
            else message("Сначала выбери озеро; проверь допустимые уровень и радиус");
        });
    }
    private void deleteLake(){
        if(importing)return;
        new AlertDialog.Builder(this).setTitle("Удалить озеро?")
            .setMessage("Исходный ландшафт останется без изменений. Можно отменить через ↶.")
            .setPositiveButton("Удалить",(d,w)->edit(()->{
                if(nativeLakeDelete())message("Вода убрана, рельеф остался · ↶ отменить");
                else message("Сначала выбери озеро в списке");
            })).setNegativeButton("Отмена",null).show();
    }
    private void enterUv56Sculpt(int tool){
        if(importing){message("Дождись окончания импорта");return;}
        // Preserve all R5.7 road and lake modes, only one editor tool active.
        cancelPlace();
        uv56SculptTool=tool;
        String[] names={"","Поднять","Опустить","Хребет","Сгладить","Выровнять"};
        statusText.setText("UV56: "+names[tool]+" · касайся земли. "+
            "Шаг узлов 1 метр, высота в сантиметрах. ↶ отменить.");
        message("Инструмент UV56 включён. Сцена города остаётся прежней");
    }
    private void openUv56Mode(int tool){
        uv56LandscapeMode=tool;
        if(tool==0){
            cancelPlace();
            statusText.setText("Осмотр карты · вращай камеру пальцем");
        }else if(tool<=5){
            enterUv56Sculpt(tool);
        }else if(activePaintKey.isEmpty()){
            cancelPlace();
            message("Для покраски выбери или загрузи PNG/JPG внизу панели");
        }else{
            // Painting must display the owner's actual material textures.
            setUv56NeutralPreview(false);
            // Reuse the existing R5.7 native material canvas, never a
            // second WebView renderer. Fine 1cm masks are a later milestone.
            activateMaterialPaint(tool==7);
            precisionPaintRadius=Math.max(.5f,uv56BrushCm/200.f);
            precisionPaintOpacity=Math.max(.05f,Math.min(1f,uv56SculptPower/2f));
        }
    }
    private void setUv56NeutralPreview(boolean neutral){
        uv56NeutralCanvas=neutral;
        if(surface==null)return;
        surface.queueEvent(()->{
            nativeSetNeutralLandPreview(neutral);
            surface.requestRender();
        });
        message(neutral?
            "Бежевый холст · текстуры скрыты только в редакторе (не удалены)":
            "Отображаются твои материалы · высоты и коллизии неизменны");
    }
    private void chooseUv56Territory(int area){
        if(importing)return;
        surface.queueEvent(()->{
            final int old=nativeUv56Area();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("🌍 Территория UV56 в городе OE")
                .setMessage((old>0?"ВНИМАНИЕ: прежний слой высот UV56 будет заменён. "+
                    "Дома, дороги, старые кисти OE и озёра останутся. "+
                    "Это действие можно отменить кнопкой ↶. ":"")+
                    "Новая территория: "+area+" м². Она должна помещаться "+
                    "в размеры города OE. Продолжить?")
                .setPositiveButton("Создать землю",(dialog,which)->
                    surface.queueEvent(()->{
                        boolean ok=nativeUv56ConfigureArea(area);
                        surface.requestRender();
                        if(ok){
                            message("Территория "+area+" м² создана · ↶ отменить");
                            ui(()->{
                                if("Рельеф".equals(activePaletteCategory)&&drawerOpen)
                                    showPalette("Рельеф");
                            });
                        }else message("Не помещается в карту OE. Выбери "+
                            "меньшую территорию или увеличь размер мира.");
                    }))
                .setNegativeButton("Не менять землю",null).show());
        });
    }
    private void startBarrierPolyline(){
        if(importing)return;
        if(walkControlsActive)finishWalkControls();
        cancelPlace();
        barrierDrawMode=true;
        barrierDrawGeneration++;
        barrierDraftStart=null;
        if(!barriersVisible){
            barriersVisible=true;
            surface.queueEvent(()->nativeBarrierShow(true));
        }
        statusText.setText("🧱 СТЕНА: касайся земли по точкам · "+
            "два пальца — камера · ✕ завершить");
        message("Поставь первую точку коллизии. Далее каждое касание "+
            "продолжает стену. Границы не влияют на грунт.");
        toggleDrawer(false);
    }
    private void editCollisionBarrier(String row){
        if(importing)return;
        final String[] parts=row.split("\\|",7);
        if(parts.length!=7){message("Повреждённые данные стены");return;}
        final int id;
        try{id=Integer.parseInt(parts[0]);}
        catch(NumberFormatException ex){message("Неверный номер стены");return;}
        final String[] labels={"Начало X, м","Начало Z, м",
            "Конец X, м","Конец Z, м","Толщина, м","Высота, м"};
        LinearLayout fields=new LinearLayout(this);
        fields.setPadding(dp(14),dp(5),dp(14),dp(5));
        fields.setOrientation(LinearLayout.VERTICAL);
        final android.widget.EditText[] inputs=new android.widget.EditText[6];
        for(int i=0;i<6;i++){
            android.widget.EditText input=new android.widget.EditText(this);
            input.setSingleLine(true);
            input.setHint(labels[i]);
            input.setText(parts[i+1]);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            fields.addView(input);inputs[i]=input;
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.addView(fields);
        new AlertDialog.Builder(this)
            .setTitle("✏ Изменить физическую стену #"+id)
            .setMessage("Отрезок 1–512 м · толщина 0.3–4 м · "+
                "высота 1–30 м. Все значения в метрах.")
            .setView(scroll)
            .setPositiveButton("Применить",(dialog,which)->{
                final double[] values=new double[6];
                try{
                    for(int i=0;i<6;i++){
                        values[i]=Double.parseDouble(inputs[i].getText()
                            .toString().trim().replace(',','.'));
                        if(!Double.isFinite(values[i]))
                            throw new NumberFormatException();
                    }
                }catch(NumberFormatException ex){
                    message("Ошибка: введи числовые значения для стены");
                    return;
                }
                surface.queueEvent(()->{
                    boolean ok=nativeBarrierEdit(id,values[0],values[1],
                        values[2],values[3],values[4],values[5]);
                    if(ok){
                        surface.requestRender();
                        message("✓ Физическая стена изменена · ↶ отменить");
                    }else message("Недопустимая длина, толщина, высота "+
                        "или точка за границей карты");
                });
            })
            .setNegativeButton("Отмена",null)
            .show();
    }
    private void toggleCollisionBarriers(){
        barriersVisible=!barriersVisible;
        surface.queueEvent(()->{
            nativeBarrierShow(barriersVisible);
            surface.requestRender();
        });
        showPalette("Коллизии");
        message(barriersVisible?"Коллизии видны розовым":
            "Розовые линии скрыты, ФИЗИКА остаётся активной");
    }
    private void createCollisionBarrier(int length){
        if(importing)return;
        if(walkControlsActive)finishWalkControls();
        cancelPlace();
        surface.queueEvent(()->{
            double[] at=nativeMapTarget();
            if(at==null)return;
            final double cx=at[0],cz=at[1];
            ui(()->{
                final android.widget.EditText thickness=new android.widget.EditText(this);
                thickness.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                thickness.setSingleLine(true);
                thickness.setText("0.8");
                LinearLayout content=new LinearLayout(this);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(dp(14),dp(8),dp(14),dp(8));
                content.addView(new TextView(this){{
                    setText("Расположение — по центру камеры. "+
                        "Физическая высота 4 м; видна только розовая линия.");}});
                content.addView(thickness);
                new AlertDialog.Builder(this)
                    .setTitle("🧱 Преграда "+length+" метров")
                    .setView(content)
                    .setPositiveButton("Создать",(d,w)->{
                        try{
                            final double thick=Double.parseDouble(thickness
                                .getText().toString().trim().replace(',','.'));
                            surface.queueEvent(()->{
                                int id=nativeBarrierAdd(cx-length*.5,cz,
                                    cx+length*.5,cz,thick,4.);
                                if(id>0){
                                    surface.requestRender();
                                    message("✓ Коллизия #"+id+" создана · ↶ отменить");
                                }else message("Не хватает места или достигнут лимит 64 стен");
                            });
                        }catch(NumberFormatException e){message("Толщина: число 0.3–4 м");}
                    }).setNegativeButton("Отмена",null).show();
            });
        });
    }
    private void chooseCollisionBarrier(){
        if(importing)return;
        if(barrierDrawMode)cancelPlace();
        if(walkControlsActive)finishWalkControls();
        surface.queueEvent(()->{
            String[] raw=nativeBarrierList();
            ui(()->{
                if(raw==null||raw.length==0){
                    message("Сначала добавь коллизионную преграду");
                    return;
                }
                String[] labels=new String[raw.length];
                for(int i=0;i<raw.length;i++){
                    String[] p=raw[i].split("\\|",7);
                    labels[i]=p.length==7?"Преграда #"+p[0]+
                        " · X "+p[1]+"…"+p[3]+" / Z "+p[2]+"…"+p[4]:
                        "Преграда "+(i+1);
                }
                new AlertDialog.Builder(this).setTitle("Настоящие коллизии OE")
                    .setItems(labels,(d,k)->collisionBarrierActions(raw[k]))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void collisionBarrierActions(String row){
        String[] parts=row.split("\\|",7);
        if(parts.length!=7)return;
        final int id;
        try{id=Integer.parseInt(parts[0]);}
        catch(NumberFormatException e){return;}
        final String[] options={
            "✂ Вырезать проход 8 м касанием",
            "✂ Вырезать проход 16 м касанием",
            "✏ Изменить X/Z, толщину и высоту",
            "🗑 Удалить всю преграду",
            "🧍 Пройти сквозь стену путником"
        };
        new AlertDialog.Builder(this).setTitle("Коллизия #"+id)
            .setItems(options,(d,k)->{
                if(k==0||k==1){
                    cancelPlace();
                    barrierCutId=id;
                    barrierCutRadius=k==0?4f:8f;
                    statusText.setText("✂ Коснись розовой линии: проход "+
                        (k==0?8:16)+" м; ↶ вернуть стену.");
                    message("Коснись нужного места на розовой линии "+
                        "одним пальцем. Камера пока зафиксирована.");
                    toggleDrawer(false);
                }else if(k==2){
                    editCollisionBarrier(row);
                }else if(k==3){
                    new AlertDialog.Builder(this)
                        .setTitle("Удалить всю физическую преграду?")
                        .setMessage("Дороги, горы и здания сохраняются.")
                        .setPositiveButton("Удалить",(d2,k2)->
                            surface.queueEvent(()->{
                                if(nativeBarrierDelete(id)){
                                    surface.requestRender();
                                    message("Преграда удалена · ↶ восстановить");
                                }
                            }))
                        .setNegativeButton("Отмена",null).show();
                }else startWalkCollisionPreview(id);
            }).setNegativeButton("Отмена",null).show();
    }

    private void toggleEditorPlotGuides(){
        editorPlotsVisible=!editorPlotsVisible;
        surface.queueEvent(()->{
            nativePlotShow(editorPlotsVisible);
            surface.requestRender();
        });
        showPalette("Участки");
        message(editorPlotsVisible?"Рамки участков показаны":"Рамки временно скрыты");
    }
    private void newEditorPlot(int size){
        if(importing)return;
        cancelPlace();
        surface.queueEvent(()->{
            final double[] target=nativeMapTarget();
            final int count=nativePlotList().length;
            ui(()->{
                final android.widget.EditText input=new android.widget.EditText(this);
                input.setSingleLine(true);
                input.setText((size==500?"Село ":"Город ")+(count+1));
                new AlertDialog.Builder(this)
                    .setTitle(size==500?"🏘 Новый участок 500×500 м":
                        "🏙 Новый участок 1000×1000 м")
                    .setMessage("Рамка появится около центра камеры. "+
                        "Затем можно задать точные X/Z в списке участков. "+
                        "Земля и объекты НЕ будут изменены.")
                    .setView(input)
                    .setPositiveButton("Создать",(d,w)->{
                        final String name=input.getText().toString().trim();
                        if(!validEditorPlotName(name)){
                            message("Название: 1–80 символов, без знака |");
                            return;
                        }
                        final float x=target==null?0f:(float)target[0];
                        final float z=target==null?0f:(float)target[1];
                        surface.queueEvent(()->{
                            final int id=nativePlotAdd(name,x,z,size);
                            if(id>0){
                                nativePlotFocus(id);
                                surface.requestRender();
                                message("✓ Участок "+name+" создан · ↶ отменить");
                            }else message("Участок не помещается в мир. "+
                                "Измени размер карты или положение камеры.");
                        });
                    }).setNegativeButton("Отмена",null).show();
            });
        });
    }
    private static boolean validEditorPlotName(String s){
        if(s==null||s.isEmpty()||s.length()>80||
            s.getBytes(StandardCharsets.UTF_8).length>100)return false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isISOControl(c)||c=='|')return false;
        }
        return true;
    }
    private void chooseEditorPlot(){
        if(importing)return;
        surface.queueEvent(()->{
            final String[] records=nativePlotList();
            ui(()->{
                if(records==null||records.length==0){
                    message("Участков пока нет. Создай село или город.");
                    return;
                }
                final String[] names=new String[records.length];
                for(int i=0;i<records.length;i++){
                    String[] p=records[i].split("\\|",5);
                    names[i]=p.length==5?
                        p[4]+" · "+p[1]+"×"+p[1]+" м · X "+p[2]+" / Z "+p[3]:
                        "Участок "+(i+1);
                }
                new AlertDialog.Builder(this).setTitle("Участки единого мира OE")
                    .setItems(names,(dialog,which)->editEditorPlot(records[which]))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void editEditorPlot(String record){
        final String[] rec=record.split("\\|",5);
        if(rec.length!=5){message("Некорректная запись участка");return;}
        final int id;
        final int previousSize;
        try{id=Integer.parseInt(rec[0]);previousSize=Integer.parseInt(rec[1]);}
        catch(NumberFormatException ex){message("Некорректный номер участка");return;}
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15),dp(6),dp(15),dp(6));
        android.widget.EditText name=new android.widget.EditText(this),
            x=new android.widget.EditText(this),z=new android.widget.EditText(this);
        name.setSingleLine(true);name.setText(rec[4]);name.setHint("Название");
        x.setSingleLine(true);x.setText(rec[2]);x.setHint("Центр X, м");
        z.setSingleLine(true);z.setText(rec[3]);z.setHint("Центр Z, м");
        int decimal=android.text.InputType.TYPE_CLASS_NUMBER|
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        x.setInputType(decimal);z.setInputType(decimal);
        body.addView(name);body.addView(x);body.addView(z);
        new AlertDialog.Builder(this).setTitle("Изменить участок "+id)
            .setView(body)
            .setNeutralButton("🔎 Найти", (dialog,which)->
                surface.queueEvent(()->{
                    if(nativePlotFocus(id)){surface.requestRender();
                        message("Камера наведена на участок");}
                }))
            .setPositiveButton("💾 Применить",(dialog,which)->{
                final String newName=name.getText().toString().trim();
                if(!validEditorPlotName(newName)){message("Неверное название");return;}
                try{
                    final float cx=Float.parseFloat(x.getText().toString()
                        .trim().replace(',','.'));
                    final float cz=Float.parseFloat(z.getText().toString()
                        .trim().replace(',','.'));
                    surface.queueEvent(()->{
                        boolean ok=nativePlotEdit(id,newName,cx,cz,previousSize);
                        if(ok){syncPlotPlanCentres();surface.requestRender();
                            message("Участок обновлён · PNG следует за рамкой · ↶ отменить");}
                        else message("Границы участка выходят за пределы мира");
                    });
                }catch(NumberFormatException ex){message("Введи числа для X/Z");}
            })
            .setNegativeButton("🗑 Удалить",(dialog,which)->
                new AlertDialog.Builder(this).setTitle("Удалить только рамку?")
                    .setMessage("Земля, дома, дороги и коллизии останутся без изменений.")
                    .setPositiveButton("Удалить рамку",(d,w)->
                        surface.queueEvent(()->{
                            if(nativePlotDelete(id)){
                                if(activePlotPlanId==id){
                                    nativeMapClear();activePlotPlanId=0;
                                }
                                surface.requestRender();
                                message("Рамка удалена · PNG сохранён для ↶");
                            }
                        }))
                    .setNegativeButton("Отмена",null).show()).show();
    }

    // One R5.7 native PNG overlay is visible at a time to protect tablet VRAM.
    // Each plot's PNG remains independently saved and selectable in .oezip.
    // Called only on the GLES thread. Site frames and PNG drawings move
    // together; undo/redo of the frame repositions its editor-only plan.
    private void syncPlotPlanCentres(){
        String[] records=nativePlotList();
        for(String row:records){
            String[] rec=row.split("\\|",5);
            if(rec.length!=5)continue;
            try{
                int id=Integer.parseInt(rec[0]);
                MapGuide plan=plotPlans.get(id);
                if(plan==null)continue;
                double cx=Double.parseDouble(rec[2]),cz=Double.parseDouble(rec[3]);
                double[] old=plotPlanSiteCenters.get(id);
                if(old==null){plotPlanSiteCenters.put(id,new double[]{cx,cz});continue;}
                if(Math.abs(cx-old[0])<.0001&&Math.abs(cz-old[1])<.0001)continue;
                MapGuide moved=plan.aligned(plan.centerX+(cx-old[0]),
                    plan.centerZ+(cz-old[1]),plan.widthMetres,plan.opacity,plan.visible);
                if(activePlotPlanId==id&&
                   !nativeMapConfigure(moved.centerX,moved.centerZ,
                       moved.widthMetres,moved.opacity,moved.visible))
                    continue;
                plotPlans.put(id,moved);
                plotPlanSiteCenters.put(id,new double[]{cx,cz});
            }catch(NumberFormatException ignored){}
        }
    }

    private void choosePlotForPlan(){
        if(importing)return;
        surface.queueEvent(()->{
            final String[] records=nativePlotList();
            ui(()->{
                if(records==null||records.length==0){
                    message("Сначала создай участок села или города");
                    return;
                }
                String[] labels=new String[records.length];
                for(int i=0;i<records.length;i++){
                    String[] p=records[i].split("\\|",5);
                    if(p.length!=5){labels[i]="Участок "+(i+1);continue;}
                    int id;
                    try{id=Integer.parseInt(p[0]);}
                    catch(NumberFormatException ex){labels[i]="Участок "+(i+1);continue;}
                    labels[i]=p[4]+" · "+p[1]+"×"+p[1]+" м "+
                        (plotPlans.containsKey(id)?"📍 PNG прикреплён":"Без PNG");
                }
                new AlertDialog.Builder(this)
                    .setTitle("🗺 Чертежи для участков")
                    .setMessage("У каждого участка свой PNG. Одновременно "+
                        "отображается один выбранный план, остальные сохранены.")
                    .setItems(labels,(dialog,index)->showPlotPlanActions(records[index]))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void showPlotPlanActions(String record){
        final String[] rec=record.split("\\|",5);
        if(rec.length!=5){message("Некорректный участок");return;}
        final int id;
        try{id=Integer.parseInt(rec[0]);}
        catch(NumberFormatException ex){message("Некорректный номер участка");return;}
        final MapGuide current=plotPlans.get(id);
        final String[] choices=current==null?
            new String[]{"📂 Загрузить прозрачный PNG","🔎 Перейти к участку"}:
            new String[]{"👁 Показать PNG на земле","📂 Заменить PNG",
                "📏 Положение / масштаб / прозрачность",
                current.visible?"🙈 Скрыть PNG":"👁 Сделать PNG видимым",
                "🗑 Отвязать PNG, не трогая постройки",
                "🔎 Перейти к участку"};
        new AlertDialog.Builder(this)
            .setTitle("🗺 "+rec[4])
            .setItems(choices,(d,index)->{
                if(current==null){
                    if(index==0)pickPlotPng(id);
                    else focusPlotId(id);
                }else{
                    switch(index){
                        case 0: showPlotPlan(id);break;
                        case 1: pickPlotPng(id);break;
                        case 2: configurePlotPlan(record);break;
                        case 3: togglePlotPlanVisibility(id);break;
                        case 4: removePlotPlan(id);break;
                        default:focusPlotId(id);
                    }
                }
            }).setNegativeButton("Закрыть",null).show();
    }
    private void focusPlotId(int id){
        surface.queueEvent(()->{
            if(nativePlotFocus(id)){
                surface.requestRender();
                message("Камера наведена на участок · PNG не меняет землю");
            }else message("Участок не найден");
        });
    }
    private void pickPlotPng(int id){
        if(importing)return;
        if(!plotPlans.containsKey(id)&&plotPlans.size()>=MAX_PLOT_PNGS){
            message("Этап 7: до 8 PNG-планов одновременно в одном проекте");
            return;
        }
        pendingPlotPngId=id;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        try{startActivityForResult(intent,PICK_PLOT_PNG);}
        catch(Exception e){pendingPlotPngId=0;message("Не открыть PNG: "+e.getMessage());}
    }
    private void importPlotPlan(int id,Uri uri){
        if(importing||id<1)return;
        importing=true;
        surface.queueEvent(()->{
            String[] records=nativePlotList();
            String[] chosen=null;
            for(String record:records){
                String[] p=record.split("\\|",5);
                if(p.length==5&&p[0].equals(String.valueOf(id))){
                    chosen=p;break;
                }
            }
            if(chosen==null){importing=false;message("Участок удалён");return;}
            final String[] site=chosen;
            new Thread(()->{
                try{
                    byte[] source=limitedTextureRead(
                        getContentResolver().openInputStream(uri));
                    if(!"image/png".equals(textureType(source)))
                        throw new Exception("Нужен настоящий PNG с прозрачностью");
                    final String key=mapKey(source);
                    // At most 1024 px GPU resolution for tablet VRAM.
                    final TextureReader.Image img=TextureReader.decode(source,"image/png",1024);
                    final double size=Double.parseDouble(site[1]);
                    final double cx=Double.parseDouble(site[2]);
                    final double cz=Double.parseDouble(site[3]);
                    double width=Math.min(size*.90,
                        size*.90*img.width/Math.max(1,img.height));
                    if(width<8||width*img.height/img.width>size)
                        throw new Exception("PNG слишком вытянут для выбранного участка");
                    File saved=new File(referenceDir(),key);
                    if(saved.isFile()){
                        if(!key.equals(mapKey(Files.readAllBytes(saved.toPath()))))
                            throw new Exception("Ранее сохранённый PNG повреждён");
                    }else{
                        File part=new File(referenceDir(),key+".part");
                        try{
                            Files.write(part.toPath(),source);
                            Files.move(part.toPath(),saved.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }finally{Files.deleteIfExists(part.toPath());}
                    }
                    final MapGuide plan=new MapGuide(key,img.width,img.height,
                        cx,cz,width,.55f,true);
                    surface.queueEvent(()->{
                        try{
                            if(!nativeMapStage(img.bytes,img.width,img.height)||
                               !nativeMapCommit(plan.centerX,plan.centerZ,
                                   plan.widthMetres,plan.opacity,true))
                                throw new Exception("Нельзя показать PNG поверх участка");
                            plotPlans.put(id,plan);
                            plotPlanSiteCenters.put(id,new double[]{cx,cz});
                            activePlotPlanId=id;
                            nativePlotFocus(id);
                            surface.requestRender();
                            message("✓ PNG привязан к участку · настрой размер и прозрачность");
                        }catch(Exception e){
                            nativeMapAbort();
                            message("Ошибка плана: "+e.getMessage());
                        }finally{importing=false;}
                    });
                }catch(Exception e){
                    importing=false;
                    message("PNG не загружен: "+e.getMessage());
                }
            },"oe-site-png-import").start();
        });
    }
    private void showPlotPlan(int id){
        MapGuide plan=plotPlans.get(id);
        if(plan==null||importing){message("У участка нет PNG-плана");return;}
        displayEditorGuide(plan,id);
    }
    private void showGlobalMapReference(){
        if(importing)return;
        MapGuide plan=mapGuide;
        if(plan==null){
            surface.queueEvent(()->{
                nativeMapClear();
                activePlotPlanId=0;
                surface.requestRender();
                message("У общей карты нет PNG. Планы участков сохранены.");
            });
        }else displayEditorGuide(plan,0);
    }
    private void displayEditorGuide(MapGuide plan,int plotId){
        if(importing)return;
        importing=true;
        new Thread(()->{
            try{
                File file=new File(referenceDir(),plan.key);
                byte[] png=limitedTextureRead(new java.io.FileInputStream(file));
                if(!"image/png".equals(textureType(png))||
                   !plan.key.equals(mapKey(png)))
                    throw new Exception("PNG повреждён или отсутствует");
                TextureReader.Image img=TextureReader.decode(png,"image/png",1024);
                surface.queueEvent(()->{
                    try{
                        if(!nativeMapStage(img.bytes,img.width,img.height)||
                           !nativeMapCommit(plan.centerX,plan.centerZ,
                               plan.widthMetres,plan.opacity,plan.visible))
                            throw new Exception("PNG не помещается в общую карту");
                        activePlotPlanId=plotId;
                        if(plotId>0)nativePlotFocus(plotId);
                        surface.requestRender();
                        message(plotId>0?
                            "План участка открыт · один PNG в редакторе":
                            "Общий PNG-план дорог активирован");
                    }catch(Exception ex){
                        nativeMapAbort();
                        message("Ошибка отображения PNG: "+ex.getMessage());
                    }finally{importing=false;}
                });
            }catch(Exception ex){
                importing=false;
                message("Не открыть PNG: "+ex.getMessage());
            }
        },"oe-show-plot-guide").start();
    }
    private void configurePlotPlan(String record){
        String[] rec=record.split("\\|",5);
        if(rec.length!=5)return;
        final int id=Integer.parseInt(rec[0]);
        MapGuide plan=plotPlans.get(id);
        if(plan==null){message("Сначала загрузи PNG для участка");return;}
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18),dp(6),dp(18),dp(6));
        EditText[] inputs=new EditText[4];
        String[] hints={"Центр PNG: X, метры","Центр PNG: Z, метры",
                        "Ширина PNG, метры","Непрозрачность, %"};
        double[] values={plan.centerX,plan.centerZ,plan.widthMetres,plan.opacity*100.};
        for(int i=0;i<4;i++){
            EditText field=new EditText(this);
            field.setSingleLine(true);
            field.setHint(hints[i]);
            field.setText(String.format(Locale.US,"%.2f",values[i]));
            field.setInputType(InputType.TYPE_CLASS_NUMBER|
                InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
            fields.addView(field);inputs[i]=field;
        }
        new AlertDialog.Builder(this).setTitle("Привязка PNG · "+rec[4])
            .setMessage("Размер в метрах, исходные пропорции сохраняются. "+
                "План должен целиком помещаться внутри рамки участка.")
            .setView(fields)
            .setPositiveButton("Применить",(dialog,which)->{
                try{
                    double x=Double.parseDouble(inputs[0].getText().toString().trim().replace(',','.'));
                    double z=Double.parseDouble(inputs[1].getText().toString().trim().replace(',','.'));
                    double width=Double.parseDouble(inputs[2].getText().toString().trim().replace(',','.'));
                    double percent=Double.parseDouble(inputs[3].getText().toString().trim().replace(',','.'));
                    double size=Double.parseDouble(rec[1]);
                    double siteX=Double.parseDouble(rec[2]),siteZ=Double.parseDouble(rec[3]);
                    double height=width*plan.heightPx/plan.widthPx;
                    if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(width)||
                       !Double.isFinite(percent)||width<8||height<8||percent<5||percent>95||
                       Math.abs(x-siteX)+width*.5>size*.5||
                       Math.abs(z-siteZ)+height*.5>size*.5)
                        throw new Exception("План выходит за границу участка");
                    final MapGuide adjusted=plan.aligned(x,z,width,(float)(percent/100),plan.visible);
                    surface.queueEvent(()->{
                        if(activePlotPlanId!=id||
                           nativeMapConfigure(x,z,width,adjusted.opacity,adjusted.visible)){
                            plotPlans.put(id,adjusted);
                            surface.requestRender();
                            message("План участка обновлён · PNG и земля не изменены");
                        }else message("Не удалось изменить положение PNG");
                    });
                }catch(Exception ex){
                    message("Проверь координаты, ширину и прозрачность от 5 до 95%");
                }
            }).setNegativeButton("Отмена",null).show();
    }
    private void togglePlotPlanVisibility(int id){
        MapGuide plan=plotPlans.get(id);
        if(plan==null)return;
        MapGuide changed=plan.aligned(plan.centerX,plan.centerZ,
                                      plan.widthMetres,plan.opacity,!plan.visible);
        surface.queueEvent(()->{
            if(activePlotPlanId==id&&
               !nativeMapConfigure(changed.centerX,changed.centerZ,
                                   changed.widthMetres,changed.opacity,changed.visible)){
                message("Не удалось переключить отображение PNG");
                return;
            }
            plotPlans.put(id,changed);
            surface.requestRender();
            message(changed.visible?"PNG-план показан":"PNG-план скрыт в редакторе");
        });
    }
    private void removePlotPlan(int id){
        if(!plotPlans.containsKey(id))return;
        new AlertDialog.Builder(this).setTitle("Отвязать PNG-план?")
            .setMessage("Рамка, дороги, дома и рельеф останутся без изменений. "+
                "Сам PNG-файл останется в библиотеке проекта.")
            .setPositiveButton("Отвязать",(dialog,which)->
                surface.queueEvent(()->{
                    plotPlans.remove(id);
                    if(activePlotPlanId==id){
                        nativeMapClear();
                        activePlotPlanId=0;
                    }
                    surface.requestRender();
                    message("PNG отвязан · участок не изменён");
                }))
            .setNegativeButton("Отмена",null).show();
    }
    private void showUv56LandscapePanel(){
        final File[] textures;
        try{
            File[] files=textureDir().listFiles((dir,name)->validTexture(name));
            if(files==null)files=new File[0];
            Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
            textures=files;
        }catch(Exception ex){
            message("Нет доступа к библиотеке текстур: "+ex.getMessage());
            return;
        }
        Map<String,String> titles=new HashMap<>();
        for(File file:textures){
            String title=getPreferences(MODE_PRIVATE)
                .getString("tile_title_"+file.getName(),null);
            if(title!=null)titles.put(file.getName(),title);
        }
        NativeLandscapePanel panel=new NativeLandscapePanel(this,
            new NativeLandscapePanel.Host(){
                @Override public void tool(int tool){openUv56Mode(tool);}
                @Override public void brushCentimeters(int cm){
                    uv56BrushCm=cm;
                    uv56SculptRadius=cm/100.f;
                    precisionPaintRadius=Math.max(.5f,cm/200.f);
                }
                @Override public void brushPower(float power){
                    uv56SculptPower=power;
                    precisionPaintOpacity=Math.max(.05f,Math.min(1f,power/2.f));
                }
                @Override public void createArea(int areaSqM){
                    chooseUv56Territory(areaSqM);
                }
                @Override public void neutralPreview(boolean neutral){
                    setUv56NeutralPreview(neutral);
                }
                @Override public void reverseCamera(boolean reverse){
                    uv56ReverseOrbit=reverse;
                    message(reverse?"Камера: обратное направление пальца":
                        "Камера: стандартное направление пальца");
                    if("Рельеф".equals(activePaletteCategory)&&drawerOpen)
                        showPalette("Рельеф");
                }
                @Override public void uploadTexture(){pickPaintTexture();}
                @Override public void chooseTexture(String key){
                    uv56LandscapeMode=6;
                    usePaintTexture(key,true);
                }
                @Override public void close(){
                    openUv56Mode(0);
                    setUv56NeutralPreview(false);
                    toggleDrawer(false);
                }
            },uv56LandscapeMode,uv56BrushCm,uv56SculptPower,textures,
               activePaintKey,titles,uv56NeutralCanvas,uv56ReverseOrbit);
        activeLandscapePanel=panel;
        paletteItems.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        surface.queueEvent(()->{
            final int area=nativeUv56Area();
            ui(()->{if(panel.getParent()!=null)panel.setAreaStatus(area);});
        });
    }
    private void chooseUv56SculptSize(){
        final String[] options={"Точно: 1 м / сила 1",
            "Мало: 2,75 м / сила 1","Средне: 4 м / сила 2",
            "Широко: 8 м / сила 3","Очень широко: 16 м / сила 5"};
        final float[] radii={1f,2.75f,4f,8f,16f};
        final float[] powers={1f,1f,2f,3f,5f};
        new AlertDialog.Builder(this).setTitle("UV56: радиус и сила кисти")
            .setItems(options,(dialog,index)->{
                uv56SculptRadius=radii[index];uv56SculptPower=powers[index];
                message("Кисть UV56: "+options[index]);
            }).show();
    }
    private void enterTerrainMode(int direction){
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing){message("Дождись завершения операции");return;}
        placingKind=-1;placingGlb=null;paintTerrainMode=false;roadMode=false;roadMoveMode=false;lakeMode=false;
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
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing){message("Дождись завершения импорта");return;}
        placingKind=-1;placingGlb=null;terrainMode=0;lakeMode=false;paintTerrainMode=false;precisionPaintMode=false;materialPaintMode=false;roadMoveMode=false;roadMode=true;
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
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing)return;
        surface.queueEvent(()->{
            if(nativeRoadContinue()){
                roadMoveMode=false;roadMode=true;placingKind=-1;terrainMode=0;lakeMode=false;paintTerrainMode=false;
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
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(byTap){
            roadMode=false;roadMoveMode=true;
            placingKind=-1;placingGlb=null;paintTerrainMode=false;
            precisionPaintMode=false;materialPaintMode=false;terrainMode=0;lakeMode=false;
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
    // The preview character is a temporary runtime collision actor,
    // never an object, never saved in .oezip or applied to editor transforms.
    // Pointer-scoped virtual joystick: losing the finger resets movement.
    private final class WalkJoystick extends View{
        private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pointer=-1;
        WalkJoystick(){super(EditorActivity.this);}
        @Override protected void onDraw(Canvas canvas){
            float x=getWidth()*.5f,y=getHeight()*.5f;
            float outer=Math.min(x,y)*.88f,inner=outer*.42f;
            brush.setStyle(Paint.Style.FILL);
            brush.setColor(Color.argb(169,13,29,45));
            canvas.drawCircle(x,y,outer,brush);
            brush.setColor(Color.argb(218,226,182,107));
            canvas.drawCircle(x+walkAxisX*outer*.62f,
                              y+walkAxisZ*outer*.62f,inner,brush);
            brush.setStyle(Paint.Style.STROKE);
            brush.setStrokeWidth(dp(2));
            brush.setColor(Color.argb(175,242,219,175));
            canvas.drawCircle(x,y,outer,brush);
            brush.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(!walkControlsActive)return false;
            final int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                pointer=event.getPointerId(event.getActionIndex());
            }else if(action==MotionEvent.ACTION_POINTER_UP||
                     action==MotionEvent.ACTION_UP||
                     action==MotionEvent.ACTION_CANCEL){
                if(action==MotionEvent.ACTION_CANCEL||
                   event.getPointerId(event.getActionIndex())==pointer){
                    pointer=-1;walkAxisX=0f;walkAxisZ=0f;invalidate();
                }
                return true;
            }
            int index=event.findPointerIndex(pointer);
            if(index>=0){
                float x=getWidth()*.5f,y=getHeight()*.5f;
                float radius=Math.max(1f,Math.min(x,y)*.63f);
                float dx=(event.getX(index)-x)/radius;
                float dz=(event.getY(index)-y)/radius;
                float mag=(float)Math.hypot(dx,dz);
                if(mag>1f){dx/=mag;dz/=mag;}
                if(mag<.12f){dx=0f;dz=0f;}
                walkAxisX=dx;walkAxisZ=dz;
                invalidate();
            }
            return true;
        }
    }
    private void finishWalkControls(){
        if(!walkControlsActive)return;
        walkControlsActive=false;
        ++walkSessionGeneration;
        walkHandler.removeCallbacks(walkTick);
        walkAxisX=0f;walkAxisZ=0f;walkJumpQueued=false;
        walkAirborne=false;walkTickPending=false;
        if(walkJoystick!=null){
            walkJoystick.setVisibility(View.GONE);
            walkJoystick.invalidate();
        }
        if(walkJumpButton!=null)walkJumpButton.setVisibility(View.GONE);
        if(walkStopButton!=null)walkStopButton.setVisibility(View.GONE);
        if(surface!=null)surface.queueEvent(()->{
            nativeWalkPreviewStop();
            surface.requestRender();
        });
    }
    private void startWalkCollisionPreview(){
        startWalkCollisionPreview(0);
    }
    private void startWalkCollisionPreview(int selectedWallId){
        if(importing)return;
        if(walkControlsActive)finishWalkControls();
        cancelPlace(); // close all editor paint/road modes before walking
        surface.queueEvent(()->{
            boolean ok=selectedWallId>0?
                nativeWalkPreviewStartAtBarrier(selectedWallId):
                nativeWalkPreviewStart();
            double[] state=ok?nativeWalkPreviewState():null;
            ui(()->{
                if(!ok||state==null){
                    message(selectedWallId>0?
                        "Нет безопасной точки рядом с преградой: "+
                        "проверь уклон, край карты или соседние стены.":
                        "Не удалось поставить путника: проверь уклон земли, "+
                        "положение камеры и границу мира.");
                    return;
                }
                statusText.setText(walkPreviewDescription(state));
                walkControlsActive=true;walkTickPending=false;
                walkJumpQueued=false;walkAirborne=false;
                walkAxisX=0f;walkAxisZ=0f;++walkSessionGeneration;
                walkJoystick.setVisibility(View.VISIBLE);
                walkJumpButton.setVisibility(View.VISIBLE);
                walkStopButton.setVisibility(View.VISIBLE);
                toggleDrawer(false);
                walkHandler.removeCallbacks(walkTick);
                walkHandler.post(walkTick);
                message(selectedWallId>0?
                    "Путник поставлен СНАРУЖИ выбранной стены. Двигайся "+
                    "джойстиком К розовой линии — коллизия должна остановить. "+
                    "После вырезания прохода проверим снова.":
                    "Круг слева — непрерывное движение. Прыжок и ✕ Тест справа.");
            });
            surface.requestRender();
        });
    }
    private static String walkPreviewDescription(double[] st){
        if(st==null||st.length!=7)return "Путник не запущен";
        String ground=st[4]==3.?"мост":st[4]==2.?"дорога":
                       st[4]==1.?"земля":"в воздухе";
        return String.format(java.util.Locale.ROOT,
            "Физика R5.7 · X %.1f / Y %.1f / Z %.1f · %s · %s · %d тиков",
            st[0],st[1],st[2],st[3]>.5?"стоит":"падает/прыгает",
            ground,(int)st[6]);
    }
    private void moveWalkPreview(float dx,float dz,boolean jump){
        if(importing)return;
        surface.queueEvent(()->{
            // Each press advances exactly 60 physics frames (1 second).
            // Jump is triggered ONCE; idle frames complete the ballistic arc.
            boolean ok=nativeWalkPreviewStep(dx,dz,jump,60);
            double[] state=ok?nativeWalkPreviewState():null;
            ui(()->{
                if(ok&&state!=null)statusText.setText(walkPreviewDescription(state));
                else message("Путник не запущен либо достиг края карты: " +
                    "нажми «Физика R5.6: путник на дороге»");
            });
            surface.requestRender();
        });
    }
    private void showWalkPreviewState(){
        if(importing)return;
        surface.queueEvent(()->{
            double[] state=nativeWalkPreviewState();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("Опора под ногами · R5.7")
                .setMessage(walkPreviewDescription(state)+"\n\n"+
                    "Зелёный — стоит на земле/дороге, золотой — на мосту, " +
                    "красный — находится в прыжке или падает.\n"+
                    "Путник не сохраняется в проекте. Сенсорный круг слева " +
                    "двигает непрерывно, прыжок — справа. Физика 60 Гц.\n"+
                    "Стены в разделе «Коллизии» останавливают путника. " +
                    "Розовые линии — только редакторские подсказки. " +
                    "Вода и перила пока не имеют отдельной физики.")
                .setPositiveButton("Понятно",null).show());
        });
    }
    private void stopWalkCollisionPreview(){
        finishWalkControls();
        statusText.setText("Тест физики завершён. Город не изменён.");
    }
    private void showBridgeTraversalStatus(){
        if(importing)return;
        surface.queueEvent(()->{
            final int[] info=nativeBridgeTraversalStats();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("Проходимость дорог и мостов · R5.4")
                .setMessage(info==null||info.length!=8||info[0]<0?
                    "Не удалось собрать сеть проходов. Проверь дорожные " +
                    "контрольные точки, наложенные улицы и допустимые масштабы мостов.":
                    "Участков дорожной сети: "+info[0]+"\n"+
                    "Мостов и переправ: "+info[1]+"\n"+
                    "Связаны с двумя берегами: "+info[2]+"\n"+
                    "Связаны с одним берегом: "+info[3]+"\n"+
                    "Пока без соединений: "+info[4]+"\n"+
                    "Четырёхугольников дороги: "+info[5]+"\n"+
                    "Настилов мостов: "+info[6]+"\n"+
                    "Рёбер для будущей навигации: "+info[7]+"\n\n"+
                    "Для соединения нужна настоящая КОНЦЕВАЯ точка дороги " +
                    "у каждого входа на мост (до 2,5 м), подход по направлению " +
                    "и перепад уровня до 0,65 м.\n"+
                    "Каменный брод — отдельные камни, не непрерывный настил.\n\n"+
                    "Это данные для будущей физики и маршрутов NPC, " +
                    "а не готовое столкновение персонажа.")
                .setPositiveButton("Понятно",null).show());
        });
    }
    private void alignSelectedBridge(){
        if(importing)return;
        new AlertDialog.Builder(this)
            .setTitle("Подогнать мост к концам дорог?")
            .setMessage("Выдели уже установленный мост и подведи к нему две " +
                "заканчивающиеся дороги с разных сторон. OE может " +
                "подогнать положение, поворот и высоту настила без " +
                "изменения дорог. Подходит для каменных, деревянных " +
                "и настильных мостов; не для брода из отдельных камней. " +
                "Можно отменить через ↶.")
            .setPositiveButton("Подогнать",(dialog,which)->edit(()->{
                if(nativeBridgeAutoAlign())
                    message("Мост подогнан к двум дорогам · ↶ отменить");
                else message("Не найдено двух подходящих концов дорог " +
                    "или мост уже правильно установлен. Проверь расстояния и уклон.");
            }))
            .setNegativeButton("Отмена",null).show();
    }
    private void showJunctionStatus(){
        if(importing)return;
        surface.queueEvent(()->{
            final int[] graph=nativeRoadTopologyStats();
            ui(()->new AlertDialog.Builder(this).setTitle("Дорожная сеть OE · R5.1")
                .setMessage(graph==null||graph.length!=4||graph[0]<0?
                    "Дорожная сеть содержит наложенные параллельные дороги либо превышает " +
                    "лимит 1200 сегментов / 96 соединений. Сами дороги остаются доступными. " +
                    "Уменьши сеть или исправь дублирующие улицы.":
                    "Общих узлов: "+graph[0]+"\n" +
                    "Точек сети: "+graph[1]+"\n" +
                    "Отрезков: "+graph[2]+"\n\n" +
                    "Новым улицам автоматически добавляются общие контрольные точки " +
                    "в местах пересечений X/T/Y. Концы улиц привязываются к середине " +
                    "соседней дороги в радиусе 2 м.\n\n" +
                    "Это связная схема дорог, а не готовые полосы транспорта/физика. " +
                    "Для прежних проектов нажми «Соединить старые дороги».")
                .setPositiveButton("Понятно",null).show());
        });
    }
    private void showMedievalSurfaceStats(){
        if(importing)return;
        surface.queueEvent(()->{
            final int[] stats=nativeMedievalSurfaceStats();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("Средневековые улицы · R5.2")
                .setMessage(stats==null||stats.length!=3||stats[0]!=1?
                    "Для этой сложной геометрии используется безопасный старый режим " +
                    "R5: без бесшовных центров перекрёстков. Попробуй увеличить " +
                    "расстояния между соединениями, изменить острый угол или убрать " +
                    "накладывающиеся параллельные улицы.\n\n" +
                    "Обычные перекрёстки X/T/Y строятся без модерновых бордюров.":
                    "Бесшовная геометрия: включена.\n" +
                    "Соединённых перекрёстков: "+stats[1]+"\n" +
                    "Треугольников дорог: "+stats[2]+"\n\n" +
                    "Общее покрытие — булыжник, песок, земля и травяные края; " +
                    "не городские магистрали. Подлинные колеи и канавы будут " +
                    "отдельным этапом по результатам проверки на планшете.")
                .setPositiveButton("Понятно",null).show());
        });
    }
    private void bakeExistingRoadTopology(){
        if(importing)return;
        new AlertDialog.Builder(this).setTitle("Соединить улицы по плану")
            .setMessage("OE найдёт Т/X/Y-перекрёстки и добавит к существующим дорогам " +
                "общие контрольные точки. Это изменит геометрию проекта, но сохранит " +
                "ID дорог, материалы и имена. Действие можно отменить через ↶. " +
                "Перед обработкой большого города сохрани копию .oezip.")
            .setPositiveButton("Соединить",(dialog,which)->edit(()->{
                if(nativeRoadBakeTopology())
                    message("Дорожные узлы соединены · сохрани .oezip · ↶ отменить");
                else message("Не удалось: проверь пересечения, наложенные улицы или лимит сети");
            })).setNegativeButton("Отмена",null).show();
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
        syncPlotPlanCentres();
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
        // New object kinds 17–19 require version 6. Older projects with only
        // original kinds keep their previous version/format.
        JSONArray objects=json.getJSONArray("items");
        for(int i=0;i<objects.length();i++){
            int kind=objects.getJSONObject(i).getInt("type");
            if(kind>=17&&kind<=28){json.put("version",6);break;}
        }
        // v7 water is stored as terrain-independent level/radius records;
        // empty native lake array must not upgrade a v2-v6 legacy project.
        JSONArray water=json.optJSONArray("lakes");
        if(water!=null&&water.length()>0)json.put("version",7);
        else json.remove("lakes");
        // R5.5 scene v8: authored JPEG/PNG road material and tiling size.
        // Earlier cities keep their old file format until the first custom
        // road is painted or its tiling size is changed.
        JSONArray savedRoads=json.optJSONArray("roads");
        if(savedRoads!=null)for(int i=0;i<savedRoads.length();i++){
            JSONObject road=savedRoads.getJSONObject(i);
            String key=road.optString("material","");
            if(validTexture(key)||road.has("tileMetres")){
                json.put("version",8);break;
            }
        }
        // New native terrain is an explicit v9 extension. Old v1–v8 city
        // projects retain their historical version and complete feature sets.
        if(json.has("uv56Terrain")){
            if(json.optJSONObject("uv56Terrain")==null)
                throw new Exception("Повреждены высоты UV56");
            json.put("version",9);
        }
        // Plots are optional editor-only v10 metadata. This is NOT game data.
        if(json.has("editorPlots")){
            JSONArray plots=json.optJSONArray("editorPlots");
            if(plots==null||plots.length()<1||plots.length()>32)
                throw new Exception("Повреждены границы участков");
            json.put("version",10);
            JSONArray plans=new JSONArray();
            for(int i=0;i<plots.length();i++){
                int id=plots.getJSONObject(i).getInt("id");
                MapGuide plan=plotPlans.get(id);
                if(plan==null)continue;
                if(!new File(referenceDir(),plan.key).isFile())
                    throw new Exception("PNG-план участка отсутствует: "+id);
                JSONObject entry=plan.toJson();
                entry.put("plotId",id);
                plans.put(entry);
            }
            if(plans.length()>0){
                if(plans.length()>MAX_PLOT_PNGS)
                    throw new Exception("В этом этапе максимум 8 PNG-планов");
                json.put("plotPlans",plans);
                json.put("version",11);
            }
        }
        if(json.has("collisionWalls")){
            JSONArray walls=json.optJSONArray("collisionWalls");
            if(walls==null||walls.length()==0||walls.length()>64)
                throw new Exception("Сцена содержит неверные физические преграды");
            json.put("version",12);
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
                            activePlotPlanId=0;
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
        if(activePlotPlanId!=0){
            message("Сначала нажми «Активировать общий PNG-план» в разделе «Дороги»");
            return;
        }
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
        if(activePlotPlanId!=0){
            message("Сначала активируй общий PNG-план в разделе «Дороги»");
            return;
        }
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
        if(activePlotPlanId!=0){
            message("Сначала активируй общий PNG-план в разделе «Дороги»");
            return;
        }
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
        final String[] options={"Камень · цвет","Грунт · цвет",
            "Песок · цвет","Трава · цвет","🎨 Моя PNG/JPG-текстура"};
        new AlertDialog.Builder(this).setTitle("Настоящее покрытие дороги · R5.5")
            .setItems(options,(dialog,index)->{
                if(index==4){chooseRoadTexture();return;}
                edit(()->{
                    if(nativeRoadSetMaterial(index))
                        message("Покрытие: "+options[index]);
                });
            }).setNegativeButton("Отмена",null).show();
    }
    private void applyActiveRoadTexture(){
        if(importing)return;
        final String key=activePaintKey;
        if(!validTexture(key)){
            message("Сначала загрузи PNG/JPG через «Текстуры» или «Выбрать PNG/JPG»");
            return;
        }
        edit(()->{
            if(nativeRoadSetCustomTexture(key))
                message("Текстура нанесена на дорогу / выбрана для новых улиц · ↶ отменить");
            else message("Текстура ещё не загружена в GPU. Выбери её из библиотеки дорог.");
        });
    }
    private void chooseRoadTexture(){
        if(importing)return;
        try{
            File[] files=textureDir().listFiles((dir,name)->validTexture(name));
            if(files==null||files.length==0){
                message("Импортируй PNG/JPG: Каталог → Текстуры → Загрузить");
                return;
            }
            java.util.Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
            String[] names=new String[files.length];
            for(int i=0;i<files.length;i++)
                names[i]="🪨 "+files[i].getName().substring(3,15);
            new AlertDialog.Builder(this)
                .setTitle("Текстура булыжника, грунта или песка")
                .setItems(names,(dialog,index)->useRoadTexture(files[index].getName()))
                .setNegativeButton("Отмена",null).show();
        }catch(Exception error){
            message("Библиотека дорожных текстур: "+error.getMessage());
        }
    }
    private void useRoadTexture(String key){
        if(importing)return;
        importing=true;
        new Thread(()->{
            try{
                if(!validTexture(key))throw new Exception("Неверная PNG/JPG-текстура");
                File file=new File(textureDir(),key);
                if(!file.isFile()||file.length()<=0||
                   file.length()>ProjectArchive.MAX_TEXTURE_BYTES)
                    throw new Exception("Нет файла текстуры");
                byte[] source=Files.readAllBytes(file.toPath());
                String mime=textureType(source);
                if(!key.equals(textureKey(source,mime)))
                    throw new Exception("Неверная контрольная сумма текстуры");
                TextureReader.Image decoded=TextureReader.decode(source,mime,1024);
                surface.queueEvent(()->{
                    boolean loaded=nativeRegisterPaintTexture(key,decoded.bytes,
                        decoded.width,decoded.height);
                    boolean applied=loaded&&nativeRoadSetCustomTexture(key);
                    importing=false;
                    if(applied){
                        activePaintKey=key;
                        message("Дорога текстурирована · выбери повторение рисунка");
                    }else message("Не удалось применить: лимит текстур или GPU");
                    surface.requestRender();
                    refreshSelected();
                });
            }catch(Exception error){
                importing=false;
                message("Не удалось выбрать дорожную текстуру: "+error.getMessage());
            }
        },"oe-road-texture").start();
    }
    private void chooseRoadTextureSize(){
        if(importing)return;
        final double[] sizes={.5,1.,2.,4.,8.,16.,32.};
        final String[] labels={
            "0,5 м · очень мелкий булыжник",
            "1 м · мелкие камни",
            "2 м · обычная брусчатка",
            "4 м · средние плиты",
            "8 м · крупные камни",
            "16 м · большие узоры",
            "32 м · масштабный рисунок"
        };
        surface.queueEvent(()->{
            final double current=nativeRoadSelectedTileMetres();
            ui(()->new AlertDialog.Builder(this)
                .setTitle("Повторение PNG/JPG на дороге · сейчас "+current+" м")
                .setItems(labels,(dialog,index)->edit(()->{
                    if(nativeRoadSetTileMetres(sizes[index]))
                        message("Рисунок повторяется каждые "+sizes[index]+" м · ↶ отменить");
                })).setNegativeButton("Отмена",null).show());
        });
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
        if(barrierDrawMode)cancelPlace();
        edit(() -> {
            if(nativeUndo()){syncPlotPlanCentres();message("Изменение отменено");}
            else message("История отмены пуста");
        });
    }
    private void redoAction() {
        if(importing){message("Дождись завершения загрузки сцены");return;}
        if(barrierDrawMode)cancelPlace();
        edit(() -> {
            if(nativeRedo()){syncPlotPlanCentres();message("Изменение повторено");}
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
        // Android's standard Files picker can choose 10/20/32 tiles at once.
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{startActivityForResult(intent,PICK_PAINT);}
        catch(Exception e){message("Не открыть галерею: "+e.getMessage());}
    }
    private String safeTileLabel(Uri uri){
        String raw=uri.getLastPathSegment();
        try(android.database.Cursor cursor=getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))
                raw=cursor.getString(0);
        }catch(Exception ignored){}
        if(raw==null)return "Мой тайл";
        raw=raw.replaceAll("[\\p{Cntrl}\\r\\n]"," ").trim();
        return raw.length()>34?raw.substring(0,34):raw;
    }
    private void importPaintTextures(ArrayList<Uri> batch){
        if(importing){message("Сначала дождись завершения загрузки");return;}
        if(batch==null||batch.isEmpty())return;
        if(batch.size()>MAX_TILE_LIBRARY){
            message("Выбери до "+MAX_TILE_LIBRARY+" тайлов за один раз");
            return;
        }
        importing=true;
        new Thread(()->{
            int added=0,existing=0,errors=0;
            String firstKey=null;
            try{
                File dir=textureDir();
                File[] all=dir.listFiles((folder,name)->validTexture(name));
                int count=all==null?0:all.length;
                for(Uri uri:batch){
                    try{
                        byte[] original=limitedTextureRead(
                            getContentResolver().openInputStream(uri));
                        String mime=textureType(original);
                        String key=textureKey(original,mime);
                        File destination=new File(dir,key);
                        // Validate that these bytes really decode as a tile,
                        // without keeping all selected images in tablet RAM.
                        TextureReader.Image decoded=TextureReader.decode(original,mime,1024);
                        if(decoded.width<=0||decoded.height<=0)
                            throw new Exception("Не открыть PNG/JPG");
                        if(destination.exists()){
                            byte[] previous=Files.readAllBytes(destination.toPath());
                            if(!key.equals(textureKey(previous,textureType(previous))))
                                throw new Exception("Существующий тайл повреждён");
                            ++existing;
                        }else{
                            if(count>=MAX_TILE_LIBRARY)
                                throw new Exception("Лимит библиотеки: "+
                                    MAX_TILE_LIBRARY+" тайла");
                            File partial=new File(dir,key+".part");
                            try{
                                Files.write(partial.toPath(),original);
                                Files.move(partial.toPath(),destination.toPath(),
                                    StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING);
                            }finally{Files.deleteIfExists(partial.toPath());}
                            ++count;++added;
                        }
                        getPreferences(MODE_PRIVATE).edit()
                            .putString("tile_title_"+key,safeTileLabel(uri)).apply();
                        if(firstKey==null)firstKey=key;
                    }catch(Exception bad){
                        ++errors;
                        android.util.Log.w("OE-Tiles","Tile import skipped",bad);
                    }
                }
            }catch(Exception ex){
                ++errors;
                message("Тайлы: "+ex.getMessage());
            }finally{
                importing=false;
                final String selected=firstKey;
                final int good=added,duplicates=existing,failed=errors;
                ui(()->{
                    message("Тайлы: добавлено "+good+", уже были "+
                        duplicates+", ошибок "+failed);
                    if(drawerOpen&&"Рельеф".equals(activePaletteCategory))
                        showPalette("Рельеф");
                    if(selected!=null)usePaintTexture(selected,true);
                });
            }
        },"oe-bulk-tiles").start();
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
                        ui(()->{
                            if("Рельеф".equals(activePaletteCategory)&&drawerOpen){
                                uv56LandscapeMode=6;
                                openUv56Mode(6);
                                showPalette("Рельеф");
                            }
                        });
                    }else message("Лимит видеопамяти. Можно хранить до 32 тайлов, GPU загружается по запросу.");
                });
            }catch(Exception err){
                importing=false;message("Текстура не добавлена: "+err.getMessage());
            }
        },"oe-paint-image-import").start();
    }
    private void usePaintTexture(String key){usePaintTexture(key,false);}
    private void usePaintTexture(String key,boolean armLandscapeAfterLoad){
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
                    if(ok){
                        activePaintKey=key;
                        message("Выбрана текстура: "+key);
                        if(armLandscapeAfterLoad){
                            ui(()->{
                                openUv56Mode(6);
                                if(drawerOpen&&"Рельеф".equals(activePaletteCategory))
                                    showPalette("Рельеф");
                            });
                        }
                    }
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
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(activePaintKey.isEmpty()){
            message("Сначала загрузи и выбери PNG/JPG");return;
        }
        terrainMode=0;lakeMode=false;placingKind=-1;placingGlb=null;roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();});
        paintTerrainMode=true;
        statusText.setText("КИСТЬ ТЕКСТУР: касайся земли · радиус "+(int)paintRadius+
            "м · до 48 мазков · ↶ отменить");
    }

    private void activatePrecisionPaint(boolean erase){
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing){message("Дождись окончания загрузки");return;}
        if(!erase&&activePaintKey.isEmpty()){
            message("Сначала загрузи и выбери текстуру PNG/JPG");return;
        }
        paintTerrainMode=false;terrainMode=0;lakeMode=false;placingKind=-1;placingGlb=null;roadMode=false;
        precisionPaintErase=erase;precisionPaintMode=true;materialPaintMode=false;roadMoveMode=false;
        surface.queueEvent(()->{nativeRoadFinish();nativePaintStrokeEnd();});
        statusText.setText(erase?"ЛАСТИК R4: веди пальцем · курсор на 70 dp выше пальца":
             "КИСТЬ R4: веди пальцем непрерывно · круг на 70 dp выше пальца. "
             +"Размер "+precisionPaintRadius+" м, двухпальцевый зум.");
        message(erase?"Режим стирания покрытия":"Непрерывная кисть · одно касание рисует линию");
    }

    private void activateMaterialPaint(boolean erase){
        uv56SculptTool=0; // exclusive tools; preserve R5.7 inputs
        if(importing){message("Дождись окончания импорта");return;}
        if(activePaintKey.isEmpty()){
            message("Сначала выбери материал PNG/JPG в библиотеке текстур");return;
        }
        paintTerrainMode=false;terrainMode=0;lakeMode=false;placingKind=-1;placingGlb=null;roadMode=false;
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
        if(requestCode==PICK_PLOT_PNG){
            int plotId=pendingPlotPngId;
            pendingPlotPngId=0;
            if(resultCode==RESULT_OK&&intent!=null&&intent.getData()!=null)
                importPlotPlan(plotId,intent.getData());
            return;
        }
        if(requestCode==PICK_PAINT){
            if(resultCode==RESULT_OK&&intent!=null){
                java.util.LinkedHashSet<Uri> selected=new java.util.LinkedHashSet<>();
                if(intent.getClipData()!=null){
                    for(int i=0;i<intent.getClipData().getItemCount();i++){
                        Uri uri=intent.getClipData().getItemAt(i).getUri();
                        if(uri!=null)selected.add(uri);
                    }
                }
                if(intent.getData()!=null)selected.add(intent.getData());
                importPaintTextures(new ArrayList<>(selected));
            }
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
            if(sceneVersion<1||sceneVersion>12)
                throw new Exception("Неверная версия сцены (нужна 1–12)");
            final boolean demoRoads=document.optBoolean("demoRoads",false);
            final int worldSize=document.optInt("worldSize",1024);
            if(worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&worldSize!=8192&&worldSize!=16384)
                throw new Exception("Неподдерживаемый размер карты");
            final JSONObject uv56Json=document.optJSONObject("uv56Terrain");
            if((sceneVersion==9&&uv56Json==null)||
               (sceneVersion<9&&document.has("uv56Terrain"))||
               (document.has("uv56Terrain")&&uv56Json==null))
                throw new Exception("Слой UV56 требует корректный формат сцены v9");
            final int uv56Area=uv56Json==null?0:uv56Json.getInt("areaSqM");
            final int[] uv56Nodes;
            if(uv56Json!=null){
                if(uv56Json.optInt("version",-1)!=1||
                   !"m".equals(uv56Json.optString("unit",""))||
                   uv56Json.optInt("step",-1)!=1||
                   uv56Area<1000||uv56Area>2000000||
                   Math.sqrt(uv56Area)>worldSize)
                    throw new Exception("Неверный размер или формат UV56");
                JSONArray raw=uv56Json.getJSONArray("nodes");
                if(raw.length()<1||raw.length()>32768)
                    throw new Exception("Превышено количество узлов UV56");
                uv56Nodes=new int[raw.length()*4];
                HashSet<String> unique=new HashSet<>();
                for(int i=0;i<raw.length();i++){
                    JSONArray rec=raw.getJSONArray(i);
                    if(rec.length()!=4)throw new Exception("Повреждён узел UV56");
                    for(int j=0;j<4;j++){
                        double v=rec.getDouble(j);
                        if(!Double.isFinite(v)||v!=Math.rint(v)||
                           v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)
                            throw new Exception("Некорректная координата UV56");
                        uv56Nodes[i*4+j]=(int)v;
                    }
                    int x=uv56Nodes[4*i],z=uv56Nodes[4*i+1],
                        height=uv56Nodes[4*i+2],paint=uv56Nodes[4*i+3];
                    if(Math.abs(x)>Math.sqrt(uv56Area)/2+1||
                       Math.abs(z)>Math.sqrt(uv56Area)/2+1||
                       height< -60000||height>60000||paint<0||paint>5||
                       !unique.add(x+":"+z))
                        throw new Exception("Некорректный узел UV56");
                }
            }else uv56Nodes=new int[0];

            // Stage v10 editor guides alongside the scene, then hand all
            // validated coordinates to the native transactional replacement.
            JSONArray plotsJson=document.optJSONArray("editorPlots");
            if(((sceneVersion==10||sceneVersion==11)&&(plotsJson==null||plotsJson.length()==0))||
               (sceneVersion<10&&document.has("editorPlots"))||
               (document.has("editorPlots")&&plotsJson==null))
                throw new Exception("Сцена v10 требует границы участков");
            final int plotCount=plotsJson==null?0:plotsJson.length();
            if(plotCount>32)throw new Exception("Максимум 32 участка");
            final int[] plotIds=new int[plotCount];
            final String[] plotNames=new String[plotCount];
            final float[] plotValues=new float[plotCount*3];
            HashSet<Integer> plotUniqueIds=new HashSet<>();
            for(int p=0;p<plotCount;p++){
                JSONObject plot=plotsJson.getJSONObject(p);
                int id=plot.getInt("id"),size=plot.getInt("size");
                String label=plot.getString("name");
                double x=plot.getDouble("x"),z=plot.getDouble("z");
                if(id<1||id>1000000||!plotUniqueIds.add(id)||
                   !validEditorPlotName(label)||
                   (size!=500&&size!=1000)||
                   !Double.isFinite(x)||!Double.isFinite(z)||
                   Math.abs(x)+size/2.0>worldSize/2.0||
                   Math.abs(z)+size/2.0>worldSize/2.0)
                    throw new Exception("Неверные границы участка "+(p+1));
                plotIds[p]=id;plotNames[p]=label;
                plotValues[3*p]=(float)x;plotValues[3*p+1]=(float)z;
                plotValues[3*p+2]=size;
            }

            // Transactional physical wall preflight (a corrupt wall NEVER
            // wipes the already opened city, terrain or editor blueprints).
            JSONArray wallsJson=document.optJSONArray("collisionWalls");
            if((sceneVersion<12&&document.has("collisionWalls"))||
               (document.has("collisionWalls")&&wallsJson==null))
                throw new Exception("Коллизии требуют сцену версии v12");
            final int barrierCount=wallsJson==null?0:wallsJson.length();
            if(barrierCount>64)throw new Exception("Максимум 64 коллизии");
            final double[] wallRecords=new double[barrierCount*7];
            HashSet<Integer> wallIds=new HashSet<>();
            for(int i=0;i<barrierCount;i++){
                JSONArray row=wallsJson.getJSONArray(i);
                if(row.length()!=7)throw new Exception("Неверные данные коллизии");
                for(int j=0;j<7;j++){
                    double value=row.getDouble(j);
                    if(!Double.isFinite(value))
                        throw new Exception("Некорректные координаты стены");
                    wallRecords[i*7+j]=value;
                }
                double idRaw=wallRecords[i*7];
                int id=(int)idRaw;
                if(id<1||id>1000000||idRaw!=id||!wallIds.add(id)||
                   Math.abs(wallRecords[i*7+1])>worldSize*.5-1||
                   Math.abs(wallRecords[i*7+2])>worldSize*.5-1||
                   Math.abs(wallRecords[i*7+3])>worldSize*.5-1||
                   Math.abs(wallRecords[i*7+4])>worldSize*.5-1||
                   Math.hypot(wallRecords[i*7+3]-wallRecords[i*7+1],
                              wallRecords[i*7+4]-wallRecords[i*7+2])<1.||
                   Math.hypot(wallRecords[i*7+3]-wallRecords[i*7+1],
                              wallRecords[i*7+4]-wallRecords[i*7+2])>512.||
                   wallRecords[i*7+5]<.3||wallRecords[i*7+5]>4.||
                   wallRecords[i*7+6]<1.||wallRecords[i*7+6]>30.)
                    throw new Exception("Коллизия за пределами карты");
            }

            JSONArray savedLakes=document.optJSONArray("lakes");
            if((sceneVersion==7&&(savedLakes==null||savedLakes.length()==0))||
               (sceneVersion<7&&document.has("lakes"))||
               (document.has("lakes")&&savedLakes==null))
                throw new Exception("Неверная версия водоёмов: нужна сцена v7");
            final int lakeCount=savedLakes==null?0:savedLakes.length();
            if(lakeCount>8)throw new Exception("Максимум 8 озёр");
            final float[] lakeData=new float[lakeCount*4];
            for(int i=0;i<lakeCount;++i){
                JSONArray lake=savedLakes.getJSONArray(i);
                if(lake.length()!=4)throw new Exception("Неверная запись озера");
                for(int j=0;j<4;++j){
                    double value=lake.getDouble(j);
                    if(!Double.isFinite(value)||Math.abs(value)>10000.0)
                        throw new Exception("Некорректные параметры воды");
                    lakeData[i*4+j]=(float)value;
                }
                double x=lake.getDouble(0),z=lake.getDouble(1);
                double r=lake.getDouble(2),level=lake.getDouble(3);
                if(r<8||r>160||level< -24||level>160||
                   Math.abs(x)+r>worldSize/2.0||
                   Math.abs(z)+r>worldSize/2.0)
                    throw new Exception("Озеро выходит за границы карты");
            }

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
            // Stage 7 v11 plot references. Validate ALL PNG files and fit
            // before changing any part of the existing native R5.7 city.
            final java.util.Map<Integer,MapGuide> parsedPlotPlans=new java.util.HashMap<>();
            final java.util.Map<Integer,TextureReader.Image> parsedPlotImages=
                new java.util.HashMap<>();
            JSONArray plotPlanRecords=document.optJSONArray("plotPlans");
            if((sceneVersion==11&&(plotPlanRecords==null||plotPlanRecords.length()==0))||
               (sceneVersion<11&&document.has("plotPlans"))||
               (document.has("plotPlans")&&plotPlanRecords==null))
                throw new Exception("Сцена v11 требует PNG-планы участков");
            final int plotPlanCount=plotPlanRecords==null?0:plotPlanRecords.length();
            if(plotPlanCount>MAX_PLOT_PNGS)throw new Exception("Максимум 8 PNG-планов");
            for(int i=0;i<plotPlanCount;i++){
                JSONObject jsonPlan=plotPlanRecords.getJSONObject(i);
                int id=jsonPlan.getInt("plotId");
                int siteIndex=-1;
                for(int k=0;k<plotIds.length;k++)if(plotIds[k]==id)siteIndex=k;
                if(siteIndex<0||parsedPlotPlans.containsKey(id))
                    throw new Exception("PNG относится к отсутствующему участку");
                String key=jsonPlan.getString("key");
                if(!key.matches("ref_[0-9a-f]{24}\\.png"))
                    throw new Exception("Неверное имя PNG-плана");
                double px=jsonPlan.getDouble("centerX"),
                       pz=jsonPlan.getDouble("centerZ"),
                       width=jsonPlan.getDouble("widthMetres");
                float opacity=(float)jsonPlan.getDouble("opacity");
                boolean visible=jsonPlan.getBoolean("visible");
                File png=new File(sourceReferences==null?referenceDir():sourceReferences,key);
                if(!png.isFile()||png.length()<1||
                    png.length()>ProjectArchive.MAX_REFERENCE_BYTES)
                    throw new Exception("PNG-план не найден: "+key);
                byte[] original=Files.readAllBytes(png.toPath());
                if(!"image/png".equals(textureType(original))||
                   !key.equals(mapKey(original)))
                    throw new Exception("Повреждён PNG-план участка "+id);
                TextureReader.Image image=TextureReader.decode(original,"image/png",1024);
                double h=width*image.height/image.width;
                double siteX=plotValues[siteIndex*3],
                       siteZ=plotValues[siteIndex*3+1],
                       siteSize=plotValues[siteIndex*3+2];
                if(!Double.isFinite(px)||!Double.isFinite(pz)||
                   !Double.isFinite(width)||!Float.isFinite(opacity)||
                   width<8||h<8||opacity<.05f||opacity>.95f||
                   Math.abs(px-siteX)+width/2>siteSize/2||
                   Math.abs(pz-siteZ)+h/2>siteSize/2)
                    throw new Exception("PNG-план выходит за участок "+id);
                parsedPlotPlans.put(id,new MapGuide(key,image.width,image.height,
                    px,pz,width,opacity,visible));
                parsedPlotImages.put(id,image);
            }
            // First visible plan is shown; all others are persisted but
            // unloaded from GLES, not copied into multiple 16 MB textures.
            int selectedPlanId=0;
            for(int id:plotIds){
                MapGuide plan=parsedPlotPlans.get(id);
                if(plan!=null&&plan.visible){selectedPlanId=id;break;}
            }
            final int firstPlotPlanId=selectedPlanId;
            final MapGuide previewGuide=selectedPlanId==0?
                parsedGuide:parsedPlotPlans.get(selectedPlanId);
            final TextureReader.Image previewImage=selectedPlanId==0?
                parsedMapImage:parsedPlotImages.get(selectedPlanId);

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
                if(e.type<0||e.type>28||(e.type>=17&&sceneVersion<6)||
                   !Double.isFinite(x)||!Double.isFinite(z)||
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
            double[] roadWidths=new double[roadCount],roadTileMetres=new double[roadCount];
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
                   !material.equals("builtin:grass")&&
                   !(sceneVersion>=8&&validTexture(material)))
                    throw new Exception("Неизвестное покрытие дороги");
                if(validTexture(material))referencedPaint.add(material);
                if(sceneVersion<8&&road.has("tileMetres"))
                    throw new Exception("Настройка текстуры дороги требует v8");
                double tiling=road.optDouble("tileMetres",4.0);
                if(!Double.isFinite(tiling)||tiling<.5||tiling>32)
                    throw new Exception("Размер плитки дороги: 0.5–32 метра");
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
                roadWidths[i]=width;roadTileMetres[i]=tiling;roadPaths[i]=xy;
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
            if(sourceReferences!=null){
                HashSet<String> requiredPlans=new HashSet<>();
                if(parsedGuide!=null)requiredPlans.add(parsedGuide.key);
                for(MapGuide plan:parsedPlotPlans.values())requiredPlans.add(plan.key);
                for(String key:requiredPlans){
                    File destination=new File(referenceDir(),key);
                    if(destination.exists()){
                        if(!key.equals(mapKey(Files.readAllBytes(destination.toPath()))))
                            throw new Exception("Повреждён локальный PNG-план: "+key);
                    }else{
                        File partial=new File(referenceDir(),key+".part");
                        try{
                            Files.copy(new File(sourceReferences,key).toPath(),
                                partial.toPath(),StandardCopyOption.REPLACE_EXISTING);
                            Files.move(partial.toPath(),destination.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }finally{Files.deleteIfExists(partial.toPath());}
                    }
                }
            }
            surface.queueEvent(() -> {
                ArrayList<String> uploaded=new ArrayList<>();
                boolean committed=false;
                try {
                    if(previewGuide!=null&&!nativeMapStage(previewImage.bytes,
                            previewImage.width,previewImage.height))
                        throw new Exception("Нельзя загрузить PNG-план в видеопамять");
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
                            roadIds,roadNames,roadMaterials,roadWidths,roadTileMetres,roadPaths,
                            preciseTileCoords,preciseTileBytes,
                            materialCoords,materialKeys,materialWeights,lakeData,
                            uv56Area,uv56Nodes,
                            plotIds,plotNames,plotValues,wallRecords))
                        throw new Exception("Сцена не прошла полную проверку в движке");
                    if(previewGuide!=null){
                        if(!nativeMapCommit(previewGuide.centerX,previewGuide.centerZ,
                                previewGuide.widthMetres,previewGuide.opacity,previewGuide.visible))
                            throw new Exception("PNG не подошёл к размерам мира");
                    }else nativeMapClear();
                    mapGuide=parsedGuide;
                    plotPlans.clear();plotPlans.putAll(parsedPlotPlans);
                    plotPlanSiteCenters.clear();
                    for(int k=0;k<plotIds.length;k++)
                        if(parsedPlotPlans.containsKey(plotIds[k]))
                            plotPlanSiteCenters.put(plotIds[k],new double[]{
                                plotValues[3*k],plotValues[3*k+1]});
                    activePlotPlanId=firstPlotPlanId;
                    mapTopViewActive=false;
                    editorPlotsVisible=true;
                    barriersVisible=true;barrierCutId=0;
                    barrierDrawMode=false;++barrierDrawGeneration;
                    barrierDraftStart=null;
                    committed=true;
                    roadMode=false;roadMoveMode=false;precisionPaintMode=false;materialPaintMode=false;lakeMode=false;
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
                            plotPlans.clear();plotPlanSiteCenters.clear();
                            activePlotPlanId=0;
                        });
                        message(demo?"Демонстрационный квартал восстановлен":"Пустая карта: все объекты удалены");
                    })
                    .setNegativeButton("Отмена",null).show();
            })
            .setNegativeButton("Отмена",null).show();
    }
    @Override protected void onResume() { super.onResume(); if (surface != null) surface.onResume(); }
    @Override protected void onPause() {
        finishWalkControls();
        barrierDrawMode=false;++barrierDrawGeneration;barrierCutId=0;
        barrierDraftStart=null;
        if(surface!=null){
            surface.queueEvent(()->nativeBarrierDraftPoint(false,0f,0f));
            surface.onPause();
        }
        super.onPause();
    }

    private final class EditorSurface extends GLSurfaceView {
        private float lastX, lastY, downX, downY, lastPinch, lastMidX, lastMidY;
        private boolean multiTouch = false;
        private volatile int touchedGizmoAxis=-1;
        private boolean gizmoEditStarted=false; // GL thread only.
        private volatile boolean paintStrokeFailed=false;
        private boolean uv56GestureStarted=false; // GL thread only
        private boolean paintDragCamera=false; // GL thread only: sky pan never paints
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
                if(barrierCutId>0||barrierDrawMode){
                    downX=lastX=e.getX();downY=lastY=e.getY();
                    multiTouch=false;lastPinch=0;return true;
                }
                downX=lastX=e.getX();downY=lastY=e.getY();multiTouch=false;lastPinch=0;
                touchedGizmoAxis=-1;
                final float px=surfaceX(downX),py=surfaceY(downY);
                if(uv56SculptTool>0){
                    final int tool=uv56SculptTool;
                    final float radius=uv56SculptRadius,power=uv56SculptPower;
                    queueEvent(()->{
                        uv56GestureStarted=nativeUv56StrokeStart(tool,px,py,radius,power);
                        if(!uv56GestureStarted){
                            final int reason=nativeUv56LastStrokeError();
                            if(reason==2)message("Кисть не попала на землю. "+
                                "Попробуй ближе к центру экрана или ниже горизонта");
                            else if(reason==3)message("Точка за границей территории. "+
                                "Выбери большую площадь или вернись к её центру");
                            else if(reason==4)message("Рельеф не изменился: "+
                                "проверь силу кисти или лимит узлов");
                            else message("Кисть не запущена · попробуй ещё раз");
                        }else requestRender();
                    });
                    return true;
                }
                if(precisionPaintMode){
                    paintStrokeFailed=false;
                    final float cy=precisionScreenY(downY);
                    final String paintKey=activePaintKey;
                    final boolean eraser=precisionPaintErase;
                    final float rad=precisionPaintRadius, strength=precisionPaintOpacity;
                    queueEvent(()->{
                        paintDragCamera=!nativeGroundHitAtScreen(px,cy);
                        if(paintDragCamera){
                            // Start in dark background = navigate, never start a brush.
                            nativePaintStrokeEnd();nativeLayerStrokeEnd();
                        }else{
                            nativePaintCursor(px,cy,rad);
                            final boolean ok=materialPaintMode?
                                nativeLayerStrokeStart(px,cy,rad,strength,paintKey,eraser):
                                nativePaintStrokeBegin(px,cy,rad,strength,paintKey,eraser);
                            if(!ok)paintStrokeFailed=true;
                        }
                        requestRender();
                    });
                    return true;
                }
                queueEvent(() -> {
                    nativeGizmoEnd();gizmoEditStarted=false;
                    if(placingKind==-1&&terrainMode==0&&uv56SculptTool==0&&!lakeMode&&!paintTerrainMode&&!roadMode&&!roadMoveMode)
                        touchedGizmoAxis=nativeGizmoHit(px,py);
                });
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                multiTouch=true;
                touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;
                    if(uv56GestureStarted)nativeUv56StrokeEnd();
                    uv56GestureStarted=false;
                    nativePaintStrokeEnd();nativeLayerStrokeEnd();requestRender();});
                if(e.getPointerCount()>=2){
                    lastPinch=pinch(e);
                    lastMidX=(e.getX(0)+e.getX(1))*.5f;
                    lastMidY=(e.getY(0)+e.getY(1))*.5f;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE) {
                if(barrierCutId>0)return true;
                // One finger places vertices, two fingers remain camera controls.
                if(barrierDrawMode&&e.getPointerCount()<2)return true;
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
                        final float ox=surfaceX(x-lastX),oy=surfaceY(y-lastY);
                        final boolean reverse=uv56ReverseOrbit;
                        queueEvent(()->{
                            if(paintDragCamera){
                                // A gesture that started outside visible land is
                                // camera-only for its entire lifetime.
                                nativeOrbit(reverse?-ox:ox,reverse?-oy:oy,1f);
                            }else if(!(materialPaintMode?nativeLayerStrokeMove(px,py):
                                  nativePaintStrokeMove(px,py))){
                                paintStrokeFailed=true;
                            }
                            interactiveRender();
                        });
                        lastX=x;lastY=y;
                        return true;
                    }
                    if(uv56SculptTool>0){
                        final int tool=uv56SculptTool;
                        final float px=surfaceX(x),py=surfaceY(y);
                        final float radius=uv56SculptRadius,power=uv56SculptPower;
                        queueEvent(()->{
                            if(uv56GestureStarted&&
                               nativeUv56StrokeMove(tool,px,py,radius,power))
                                interactiveRender();
                        });
                        lastX=x;lastY=y;
                        return true;
                    }
                    final float dx=x-lastX,dy=y-lastY;
                    if(dx!=0||dy!=0){
                        final float sx=surfaceX(dx),sy=surfaceY(dy);
                        final boolean reverse=uv56ReverseOrbit;
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
                            else nativeOrbit(reverse?-sx:sx,reverse?-sy:sy,1);
                            interactiveRender();
                        });
                    }
                    lastX=x;lastY=y;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP) {multiTouch=true;lastPinch=0;return true;}
            if(action==MotionEvent.ACTION_UP) {
                if(barrierDrawMode){
                    final boolean tap=!multiTouch&&
                        Math.hypot(e.getX()-downX,e.getY()-downY)<dp(18);
                    final float px=surfaceX(e.getX()),py=surfaceY(e.getY());
                    final int version=barrierDrawGeneration;
                    if(tap)queueEvent(()->{
                        if(!barrierDrawMode||version!=barrierDrawGeneration)return;
                        double[] at=nativeBarrierPointAtScreen(px,py);
                        if(at==null){
                            message("Коснись земли внутри карты (не тёмного фона)");
                            return;
                        }
                        if(barrierDraftStart==null){
                            barrierDraftStart=at;
                            nativeBarrierDraftPoint(true,(float)at[0],(float)at[1]);
                            requestRender();
                            message("Начало стены установлено · касайся следующей "+
                                "точки или нажми ✕ для отмены");
                        }else{
                            int id=nativeBarrierAdd(barrierDraftStart[0],
                                barrierDraftStart[1],at[0],at[1],.8,4.);
                            if(id>0){
                                barrierDraftStart=at; // continue same polygonal chain
                                nativeBarrierDraftPoint(true,(float)at[0],(float)at[1]);
                                requestRender();
                                message("✓ Отрезок #"+id+" создан · следующая "+
                                    "точка продолжит линию · ↶ отменить");
                            }else{
                                message("Отрезок должен быть от 1 до 512 м "+
                                    "в пределах карты (лимит 64). Первая точка сохранена.");
                            }
                        }
                    });
                    multiTouch=false;
                    return true;
                }
                if(barrierCutId>0){
                    final int id=barrierCutId;
                    final float radius=barrierCutRadius;
                    final float sx=surfaceX(e.getX()),sy=surfaceY(e.getY());
                    final boolean tap=!multiTouch&&
                        Math.hypot(e.getX()-downX,e.getY()-downY)<dp(18);
                    barrierCutId=0;
                    if(tap)queueEvent(()->{
                        int result=nativeBarrierCutAtScreen(id,sx,sy,radius);
                        if(result>0){
                            requestRender();
                            message("✓ Проход вырезан · ↶ Undo вернёт коллизию");
                        }else message("Не попал в преграду · выбери стену и повтори");
                    });
                    return true;
                }
                if(uv56SculptTool>0){
                    final float px=surfaceX(e.getX()),py=surfaceY(e.getY());
                    final int tool=uv56SculptTool;
                    final float radius=uv56SculptRadius,power=uv56SculptPower;
                    final boolean hadMultiple=multiTouch;
                    queueEvent(()->{
                        if(uv56GestureStarted){
                            if(!hadMultiple)nativeUv56StrokeMove(tool,px,py,radius,power);
                            nativeUv56StrokeEnd();
                            uv56GestureStarted=false;
                            final int area=nativeUv56Area();
                            final int heightCm=nativeUv56LastTouchHeightCm();
                            ui(()->{
                                if(activeLandscapePanel!=null)
                                    activeLandscapePanel.setAreaStatus(area);
                                statusText.setText("✓ Рельеф изменён · высота в точке "+
                                    heightCm+" см · ↶ отменить / ↷ повторить");
                            });
                        }
                        requestRender();
                    });
                    multiTouch=false;return true;
                }
                if(precisionPaintMode){
                    final float px=surfaceX(e.getX()),py=precisionScreenY(e.getY());
                    final boolean wasMultiTouch=multiTouch;
                    queueEvent(()->{
                        if(!paintDragCamera&&!wasMultiTouch&&
                           !(materialPaintMode?nativeLayerStrokeMove(px,py):
                             nativePaintStrokeMove(px,py)))
                            paintStrokeFailed=true;
                        nativePaintStrokeEnd();nativeLayerStrokeEnd();
                        final boolean cameraOnly=paintDragCamera;
                        paintDragCamera=false;
                        requestRender();
                        if(!cameraOnly&&paintStrokeFailed)
                            message("Рисование остановлено: лимит участков/материалов, граница или текстура");
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
                    final int kind=placingKind,brush=terrainMode,uv56Brush=uv56SculptTool;
                    final float uv56Radius=uv56SculptRadius,uv56Power=uv56SculptPower;
                    final boolean paint=paintTerrainMode;
                    final boolean roadDraw=roadMode;
                    final boolean lakePlace=lakeMode;
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
                                else if(result==2)message("Дорога создана · перекрёстки соединены · добавляй точки");
                                else if(result==3)message("Точка добавлена · дорожные узлы обновлены");
                                else if(result==0)message("Не попали на землю: попробуй другой ракурс");
                                else message("Нельзя соединить: слишком близко, наложение улиц или лимит сети");
                            }else if(paint){
                                int strokes=nativePaintTerrainAtScreen(px,py,paintRadius,paintingKey);
                                if(strokes>0)message("Земля окрашена · мазок "+strokes+"/48");
                                else message("Не получилось покрасить: нет текстуры или лимит 48 кистей");
                            }else if(lakePlace){
                                final int count=nativeLakePlaceAtScreen(px,py,lakeRadius,lakeFillDepth);
                                if(count>0){
                                    lakeMode=false;
                                    message("Озеро залито · "+count+"/8 · ↶ отменить");
                                    ui(()->statusText.setText(
                                        "Озеро создано · выбери его в разделе «Вода» для изменения уровня"));
                                }else message("Невозможно залить: лимит 8 озёр, край карты или неверная высота");
                            }else if(uv56Brush!=0){
                                if(nativeUv56SculptAtScreen(uv56Brush,px,py,uv56Radius,uv56Power))
                                    message("Высоты UV56 изменены · ↶ отменить · 💾 сохранить");
                                else message("UV56: не удалось изменить землю (край карты или лимит)");
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
                barrierCutId=0;
                barrierDrawMode=false;
                ++barrierDrawGeneration;
                barrierDraftStart=null;
                queueEvent(()->nativeBarrierDraftPoint(false,0f,0f));
                multiTouch=false;touchedGizmoAxis=-1;
                queueEvent(()->{nativeGizmoEnd();gizmoEditStarted=false;
                    if(uv56GestureStarted)nativeUv56StrokeEnd();
                    uv56GestureStarted=false;
                    paintDragCamera=false;
                    nativePaintStrokeEnd();nativeLayerStrokeEnd();requestRender();});
                return true;
            }
            return true;
        }
    }
}
