#!/usr/bin/env python3
"""Stage14 paint touch regression contract.

Static integration checks; device EGL touch, GL texture display and FPS
still require a real Android test. Never mistake CI for on-device proof.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = (root / "android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
panel = (root / "android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
cpp = (root / "android/src/main/cpp/native_engine.cpp").read_text()

assert "paintDragCamera" not in java, "paint must never become camera orbit"
assert "return surfaceY(y); // paint at the exact finger contact" in java
assert "uv56NeutralCanvas=false;" in java, "match native visible-material default"

# The same pointer drives ground hit, start, move and end, in GLES pixels.
touch = java.split("@Override public boolean onTouchEvent(MotionEvent e) {", 1)[1]
down = touch.split("if(action==MotionEvent.ACTION_DOWN) {", 1)[1].split(
    "if(action==MotionEvent.ACTION_POINTER_DOWN)", 1
)[0]
move = touch.split("if(action==MotionEvent.ACTION_MOVE)", 1)[1].split(
    "if(action==MotionEvent.ACTION_POINTER_UP)", 1
)[0]
up = touch.split("if(action==MotionEvent.ACTION_UP)", 1)[1].split(
    "if(action==MotionEvent.ACTION_CANCEL)", 1
)[0]
paint_move = move.split("if(precisionPaintMode){", 1)[1].split(
    "if(uv56SculptTool>0)", 1
)[0]

assert "final float cy=precisionScreenY(downY);" in down
assert "if(!nativeGroundHitAtScreen(px,cy))" in down
assert "paintFailureReason=1;" in down
assert "nativeLayerStrokeStart(px,cy,rad,strength,paintKey,eraser)" in down
assert "nativePaintStrokeBegin(px,cy,rad,strength,paintKey,eraser)" in down
assert "nativeOrbit" not in down
assert "nativeOrbit" not in paint_move
assert "if(!paintStrokeFailed &&" in paint_move
assert "nativeLayerStrokeMove(px,py)" in paint_move
assert "nativePaintStrokeMove(px,py)" in paint_move
assert "paintFailureReason==1?" in up
assert "nativePaintStrokeEnd();nativeLayerStrokeEnd();" in up
assert "final boolean wasMultiTouch=multiTouch;" in up

# Paint cannot silently run with hidden materials or a non-existent texture.
assert "if(neutral&&(precisionPaintMode||paintTerrainMode))" in java
assert "if(uv56NeutralCanvas)setUv56NeutralPreview(false);" in java
assert "uv56LandscapeMode=0;" in java
assert "Кисть не включена: выбери или загрузи PNG/JPG" in java
assert 'if("Рельеф".equals(activePaletteCategory)){' in java
assert "if(drawerOpen)showPalette(\"Рельеф\");" in java
assert "Кнопки выше только показывают/скрывают покрытие." in panel
assert "Для рисования выбери" in panel
assert "Покрасить землю" in panel

# Native painter remains inside the same GLES world, keeps actual masks.
for symbol in ("nativeGroundHitAtScreen", "nativeLayerStrokeStart",
               "nativePaintStrokeBegin", "drawMaterialLayers(vp);",
               "gMaterialCanvas.stroke(", "drawTerrainPaint(vp);"):
    assert symbol in cpp, symbol
assert "if(!gNeutralLandPreview){" in cpp
assert 'json.put("version",13)' in java, "do not break existing .oezip"

print("OE_STAGE14_PAINT_TOUCH_OK: direct-touch strokes, no camera fallback, "
      "visible materials and honest missing-texture error (static only)")
