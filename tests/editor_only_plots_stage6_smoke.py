#!/usr/bin/env python3
"""Stage 6: authoring-only village/city frames in the original R5.7 city.

Source-level integration gate. Android device and GLES still need owner QA.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
for marker in [
  "struct EditorPlot", "std::vector<EditorPlot> gEditorPlots",
  "static constexpr size_t MAX_EDITOR_PLOTS=32",
  "void drawEditorPlots(const Mat4&vp)",
  "drawEditorPlots(vp); // authoring overlay, never a game collision",
  "terrainHeightAt(x,z)+.42f",
  "nativePlotAdd(", "nativePlotEdit(", "nativePlotDelete(",
  "nativePlotList(", "nativePlotFocus(", "nativePlotShow(",
  "std::vector<EditorPlot> stagedPlots",
  "gEditorPlots=std::move(stagedPlots)",
  "gEditorPlots=s.editorPlots",
  "gEditorPlots.clear();gNextEditorPlotId=1",
  "gEditorPlotsVisible=true",
  "out<<\"],\\\"editorPlots\\\":[\";",
  "(p.size!=500.f&&p.size!=1000.f)",
  "for(const auto&p:gEditorPlots)if(std::abs(p.x)+p.size*.5f>half",
]:
    assert marker in cpp, ("missing native editor-only plot operation",marker)
for marker in [
    '"Участки".equals(category)',
    'newEditorPlot(500)', 'newEditorPlot(1000)',
    'private void chooseEditorPlot()', 'private void editEditorPlot(String record)',
    'nativePlotFocus(id)',
    'nativePlotShow(editorPlotsVisible)',
    'json.put("version",10)',
    'int[] plotIds,String[] plotNames,float[] plotValues',
    'plotIds,plotNames,plotValues,wallRecords))',
    'sceneVersion>12',
    'validEditorPlotName(label)',
    'if(((sceneVersion==10||sceneVersion==11)&&(plotsJson==null||plotsJson.length()==0))',
]:
    assert marker in java, ("missing Android plots UI/scene import",marker)
for marker in [
    "version>=1&&version<=12",
    "validateEditorPlots(json,version)",
    "validateNativeUv56(json,version)",
    "validateLakes(json,version)",
    "((version==10||version==11)&&plots!=null&&plots.length()>0)",
]:
    assert marker in archive, ("missing legacy compatible v10 archive gate",marker)
assert "applicationId 'game.oe.editor.r57uv44'" in gradle
assert "MAX_TEXTURES=32" in archive
# Crucial: no plot geometry is added to gameplay walker/runtime.
walk=cpp.split('Java_game_oe_editor_EditorActivity_nativeRuntimeCoreReport(',1)[1]
walk=walk.split('JNIEXPORT ',1)[0]
assert "gEditorPlots" not in walk
assert "new WebView(" not in java
print("OE_EDITOR_PLOTS_STAGE6_OK: 500m/1000m frames + v10 save/load + "
      "GL overlay-only + unchanged R5.7 game runtime/terrain/collisions")
