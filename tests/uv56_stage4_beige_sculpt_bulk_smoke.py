#!/usr/bin/env python3
"""Owner tablet validation gate, Stage 4: beige edit canvas + working brush.

This catches regressions in source wiring. It is NOT a claim that an Android
tablet has rendered or raised a visible hill; owner QA remains mandatory.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
editor=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
panel=(root/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
arch=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
checks=[
 ("one uniform beige base", "const Color c{.83f,.77f,.66f};" in cpp),
 ("no biome-colored terrain", "dxForest*dxForest+dzForest*dzForest" not in cpp),
 ("no checkerboard color", "if((tx+tz)%3==0)" not in cpp),
 ("UV56 hill normals", "terrainTriangle(vertices,x0,z0,x1,z0,x1,z1,c,detail||uv56Edited);" in cpp),
 ("terrain from UV56", "float h=gUv56Terrain.heightAt(x,z);" in cpp),
 ("neutral editor-only JNI", "nativeSetNeutralLandPreview(" in cpp),
 ("neutral holds paint data", "if(!gNeutralLandPreview){" in cpp),
 ("native cache invalidation", "++gTerrainRevision;" in cpp),
 ("raise by default", "uv56LandscapeMode=1,uv56BrushCm=400;" in editor),
 ("arm tool on palette open", "enterUv56Sculpt(uv56LandscapeMode);" in editor),
 ("neutral preview loaded on GL thread", "nativeSetNeutralLandPreview(uv56NeutralCanvas);" in editor),
 ("neutral or texture toggle UI", "void neutralPreview(boolean neutral);" in panel),
 ("quick batch import on screen", "Загрузить сразу 10–32 PNG/JPG" in panel),
 ("bulk picker", "Intent.EXTRA_ALLOW_MULTIPLE,true" in editor),
 ("ClipData import", "intent.getClipData().getItemCount()" in editor),
 ("up to 32 tiles", "MAX_TILE_LIBRARY=32" in editor and
                       "MAX_TEXTURES=32" in arch),
 ("native height diagnostic", "nativeUv56LastTouchHeightCm()" in editor and
                              "nativeUv56LastTouchHeightCm(" in cpp),
 ("R5.7 walker still intact", "runtime/walk_collision.h" in cpp),
 ("roads still intact", "roads/traversal_graph.h" in cpp),
 ("lakes intact", "water/lake_surface.h" in cpp),
 ("v1-v9 scene compatibility", "version>=1&&version<=10" in arch),
 ("isolated debug identity", "applicationId 'game.oe.editor.r57plots'" in gradle),
]
for name,valid in checks:
    assert valid, name
assert "new WebView(" not in editor
assert "new WebView(" not in panel
print("OE_STAGE4_OK: UV56 brush active, hill normals, beige non-checkered canvas, "
      "reversible texture visibility and up to 32 PNG/JPG selected together")
