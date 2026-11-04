package game.oe.editor;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.graphics.Color;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
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
    private static native int nativePick(float x, float y);
    private static native int nativeAdd(int kind);
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

    private EditorSurface surface;
    private TextView selectionText;
    private TextView statusText;
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
        title.setText("OE  /  ORVETRA ENGINE     •     NATIVE ANDROID 0.2");
        title.setTextColor(gold);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(13);
        title.setPadding(dp(7), dp(2), 0, dp(4));
        top.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = row();
        addTool(tools, "+ Таверна", () -> addEntity(0));
        addTool(tools, "+ Кузница", () -> addEntity(1));
        addTool(tools, "+ Аукцион", () -> addEntity(2));
        addTool(tools, "+ Фонарь", () -> addEntity(3));
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

        statusText = new TextView(this);
        statusText.setText("Камера: 1 палец — вращение; 2 пальца — масштаб. При простое кадры не перерисовываются.");
        statusText.setTextColor(Color.rgb(167, 177, 189));
        statusText.setTextSize(10);
        inspector.addView(statusText);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(inspector, ip);

        setContentView(root);
    }
    private void addEntity(int kind) {
        edit(() -> nativeAdd(kind));
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
    private void loadScene() {
        try {
            File file = saveFile();
            if (!file.exists()) { message("Сначала сохрани сцену"); return; }
            if (file.length() > 200000L) throw new IllegalArgumentException("Файл слишком большой");
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject document = new JSONObject(content);
            if (document.optInt("version", -1) != 1) throw new IllegalArgumentException("Неверная версия формата");
            JSONArray array = document.getJSONArray("items");
            if (array.length() > 300) throw new IllegalArgumentException("Превышен лимит объектов");
            final int count = array.length();
            final int[] types = new int[count];
            final float[] xs = new float[count], zs = new float[count], yaws = new float[count], sizes = new float[count];
            for (int i = 0; i < count; i++) {
                JSONObject obj = array.getJSONObject(i);
                int type = obj.getInt("type");
                double x = obj.getDouble("x"), z = obj.getDouble("z"), yaw = obj.getDouble("yaw"), size = obj.getDouble("size");
                if (type < 0 || type > 5 || !Double.isFinite(x) || !Double.isFinite(z) ||
                    !Double.isFinite(yaw) || !Double.isFinite(size) || Math.abs(x)>300 ||
                    Math.abs(z)>300 || Math.abs(yaw)>36000 || size<.2 || size>8) {
                    throw new IllegalArgumentException("Неверный объект №" + i);
                }
                types[i] = type; xs[i]=(float)x; zs[i]=(float)z; yaws[i]=(float)yaw; sizes[i]=(float)size;
            }
            edit(() -> {
                nativeReset(false);
                for (int i = 0; i < count; i++)
                    nativeAddPlaced(types[i],xs[i],zs[i],yaws[i],sizes[i]);
            });
            message("Сцена загружена · " + count + " объектов");
        } catch (Exception e) { message("Ошибка загрузки: " + e.getMessage()); }
    }
    private void askReset() {
        new AlertDialog.Builder(this).setTitle("Новая сцена")
            .setMessage("Вернуть первоначальный квартал? Сначала сохрани изменения.")
            .setPositiveButton("Сбросить", (d, w) -> edit(() -> nativeReset(true)))
            .setNegativeButton("Отмена", null).show();
    }
    @Override protected void onResume() { super.onResume(); if (surface != null) surface.onResume(); }
    @Override protected void onPause() { if (surface != null) surface.onPause(); super.onPause(); }

    private final class EditorSurface extends GLSurfaceView {
        private float lastX, lastY, downX, downY, lastPinch;
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
                if(e.getPointerCount()>=2)lastPinch=pinch(e);
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE) {
                if(e.getPointerCount()>=2) {
                    float p=pinch(e);
                    if(lastPinch>1 && p>1) {
                        final float zoom=Math.max(.7f,Math.min(1.3f,lastPinch/p));
                        queueEvent(() -> {nativeOrbit(0,0,zoom);requestRender();});
                    }
                    lastPinch=p;
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
                    queueEvent(() -> {nativeSelect(nativePick(x,y));requestRender();refreshSelected();});
                }
                multiTouch=false;
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL) {multiTouch=false;return true;}
            return true;
        }
    }
}
