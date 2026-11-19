#!/usr/bin/env python3
"""Owner video regression: snow material may go black after display/recording
context change; Camera mode and off-canvas touches must never paint.
Static gates supplement (do not replace) a physical Android GLES test.
"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
cpp=(r/"android/src/main/cpp/native_engine.cpp").read_text()
java=(r/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
panel=(r/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
arch=(r/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
setup=cpp.split("void setupGL(){",1)[1].split("const char*vert=",1)[0]
for q in (
    "gMaterialGpu.clear()", # do not reuse GL names belonging to old context
    "gPaintTileGpu.clear()",
):
    assert q in setup, ("EGL recreation must reset both overlay caches",q)
for q in (
    "glIsTexture(material->id)!=GL_TRUE",
    "glIsTexture(gpu.texture)!=GL_TRUE",
    "nativeGroundHitAtScreen(",
    "quality().terrainTileRadius",
    "glUniform1f(gShader.locMaterialLayer,0.f)",
    "glUniform1f(gShader.locGuide,0.f)",
):
    assert q in cpp, ("GLES material/land hit recovery",q)
draw=cpp.split('JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeDraw(',1)[1].split('JNIEXPORT void JNICALL',1)[0]
assert "if(!gNeutralLandPreview){" in draw
assert "drawPrecisionPaint(vp);" in draw.split("if(!gNeutralLandPreview){",1)[1].split("}",1)[0]
assert "drawMaterialLayers(vp);" in draw.split("if(!gNeutralLandPreview){",1)[1].split("}",1)[0]
for q in (
    "nativeGroundHitAtScreen(px,cy)",
    "paintDragCamera=!nativeGroundHitAtScreen(px,cy)",
    "if(paintDragCamera){",
    "if(!paintDragCamera&&!wasMultiTouch",
    "if(!cameraOnly&&paintStrokeFailed)",
    "void reverseCamera(boolean reverse)",
):
    if q=="void reverseCamera(boolean reverse)":
        assert q in panel
    else:
        assert q in java, ("touch gesture must not paint sky",q)
assert "uv56ReverseOrbit=true;" in java
assert '"Рельеф".equals(activePaletteCategory)&&drawerOpen' in java
assert "MAX_TEXTURES=32" in arch
assert "version>=1&&version<=10" in arch
print("OE_VIDEO_BLACK_SNOW_FIX_OK: EGL layer cache recovery, blank sky look-only, "
      "camera direction toggle, user paint tiles preserved, R5.7 scenes compatible")
