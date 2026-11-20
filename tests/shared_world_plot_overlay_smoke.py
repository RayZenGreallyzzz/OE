#!/usr/bin/env python3
"""R5.8: choosing a site must add only a translucent guide on one UV56 world.
Source integration checks complement the portable C++ tests and Android APK QA.
"""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
panel=(root/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
core=(root/"android/src/main/cpp/terrain/uv56_landscape_core.h").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()

# UV56 area dropdown marks a frame: no call into the destructive terrain creator.
frame_handler=java.split("private void chooseUv56Territory(int area)",1)[1]
frame_handler=frame_handler.split("private void startBarrierPolyline()",1)[0]
assert "newEditorPlot((int)Math.round(Math.sqrt(area)))" in frame_handler
assert "nativeUv56ConfigureArea" not in frame_handler
assert "Создать / заменить землю" not in panel
assert "host.createPlot(AREAS[areas.getSelectedItemPosition()])" in panel
assert "Размер прозрачной границы участка" in panel
assert "sizeInput.setText(String.valueOf(previousSize))" in java
assert "nativePlotEdit(id,newName,cx,cz,newSize)" in java
assert 'p.size<16.f||p.size>static_cast<float>(world)' in native
assert "size<16||size>worldSize" in java
assert "size>=16&&size<=world*2" in archive

# Overlay never creates a physical floor, and is drawn with alpha blending.
guides=native.split("void drawEditorPlots(const Mat4&vp){",1)[1]
guides=guides.split("// Visual guides for authored physical barriers;",1)[0]
assert "glUniform1f(gShader.locOpacity,.38f)" in guides
assert "glEnable(GL_BLEND)" in guides and "glDepthMask(GL_FALSE)" in guides
assert "glDepthMask(GL_TRUE);glDisable(GL_BLEND)" in guides
assert "terrainHeightAt(x,z)+.42f" in guides

# One continuous editable world. Legacy plot-sized UV56 scenes enlarge
# without clearing heights; newly painted nodes are no longer plot-limited.
assert "static int gWorldSize=16384" in native
assert "bool expandToWorld(int worldMetres)" in core
assert "bool fitToWorld(int worldMetres)" in core
for signature in ("Java_game_oe_editor_EditorActivity_nativeUv56StrokeStart(",
                  "Java_game_oe_editor_EditorActivity_nativeUv56SculptAtScreen("):
    body=native.split(signature,1)[1].split("JNIEXPORT ",1)[0]
    assert "proposal.expandToWorld(gWorldSize)" in body
    assert "std::min(2000000" not in body

# Both local atomic saves and portable .oezip rely on the SAME snapshot.
assert "String json=snapshotWithGuide();" in java
assert 'json.put("version",10)' in java
assert 'json.put("plotPlans",plans)' in java
assert 'out<<"{\\\"id\\\":"<<p.id' in native
assert "areaSqM" in native
assert "validateEditorPlots(json,version)" in archive
assert "validateNativeUv56(json,version)" in archive
assert "stagedUv56.restore(uv56Area,uv56Nodes)" in native
assert "gEditorPlots=std::move(stagedPlots)" in native
assert "gUv56Terrain=std::move(stagedUv56)" in native
assert "nativeReplaceScene(" in java and "nativeSceneSnapshot()" in java
print("OE_SHARED_WORLD_PLOTS_OK: non-destructive transparent plots, UV56 full map, saved coords/heights")
