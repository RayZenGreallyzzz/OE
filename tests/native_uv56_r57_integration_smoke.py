#!/usr/bin/env python3
"""Static boundaries for the native OE R5.7 UV56 terrain milestone.
Must not be confused with Android/on-tablet validation.
"""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
native=(p/"android/src/main/cpp/native_engine.cpp").read_text()
java=(p/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive=(p/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
land=(p/"android/src/main/cpp/terrain/uv56_landscape_core.h").read_text()
for marker in (
    '#include "terrain/uv56_landscape_core.h"',
    'static oe::uv56::Terrain gUv56Terrain;',
    'float h=gUv56Terrain.heightAt(x,z);',
    'const int divisions=uv56Edited?(gDistance>250.f?8:64):(detail?8:1)',
    'gUv56Terrain=s.uv56;',
    'gUv56Terrain=std::move(stagedUv56)',
    'nativeUv56SculptAtScreen',
    'if(!groundPoint(px,py,x,z))return JNI_FALSE;',
    'if(!proposal.brushAt(x,z,static_cast<oe::uv56::Tool>(tool),radius,power))',
    'recordEdit();',
    'gWalkPreview.clear();',
    'road/traversal', # replaced separately, kept below
):
    if marker=="road/traversal":continue
    assert marker in native, ("native",marker)
for marker in (
    'roads/traversal_graph.h','runtime/walk_collision.h',
    'water/lake_surface.h','terrainpaint/material_layers.h'
):
    assert marker in native, ("r57 unchanged",marker)
for marker in (
    'nativeUv56SculptAtScreen(int tool,float x,float y,',
    'private void enterUv56Sculpt(int tool)',
    'private void chooseUv56SculptSize()',
    'final int kind=placingKind,brush=terrainMode,uv56Brush=uv56SculptTool;',
    'json.put("version",9)',
    'sceneVersion>13',
    'uv56Area,uv56Nodes,',
    'savedLakes=document.optJSONArray("lakes")',
    'snapshotWithGuide()'
):
    assert marker in java, ("java",marker)
for marker in (
    'version>=1&&version<=13',
    'validateNativeUv56(json,version)',
    'validateLakes(json,version)',
    'MAX_SCENE_BYTES=192*1024*1024'
):
    assert marker in archive, ("archive",marker)
assert "nativeReplaceScene(" in native and "nativeReplaceScene(" in java
assert "import android.webkit" not in java and "new WebView(" not in java
assert "new WebView(" not in native
assert "uv56Terrain" in native and "uv56Terrain" in java
assert 'static constexpr std::size_t kNodeLimit=32768;' in land
assert "bool affectsTile(int tx,int tz" in land
print("OE_UV56_NATIVE_R57_OK: one city GLES terrain; R5.7 walker/roads/water and archive v1-v8 retained; new v9 only for sculpted land")

# R5.8 paint budget invariants: same 64 MiB budgets in portable C++ and Android.
paint=(p/"android/src/main/cpp/terrainpaint/paint_tiles.h").read_text()
material=(p/"android/src/main/cpp/terrainpaint/material_layers.h").read_text()
paint_cpp=(p/"android/src/main/cpp/terrainpaint/paint_tiles.cpp").read_text()
material_cpp=(p/"android/src/main/cpp/terrainpaint/material_layers.cpp").read_text()
for check,source in (
    ('kMaxPaintTileBytes=64u*1024u*1024u',paint),
    ('kMaxMaterialMaskBytes=64u*1024u*1024u',material),
    ('MAX_PAINT_TILES_BY_MEMORY=(64*1024*1024)/(128*128*4)',archive),
    ('MAX_MATERIAL_MASKS_BY_MEMORY=(64*1024*1024)/(128*128)',archive),
    ('ProjectArchive.MAX_PAINT_TILES_BY_MEMORY',java),
    ('ProjectArchive.MAX_MATERIAL_MASKS_BY_MEMORY',java),
    ('kMaxPaintTilesByMemory*2',native),
    ('kMaxMaterialMasksByMemory*2',native),
): assert check in source,check
assert 'kMaxTiles=32' not in paint
assert 'kMaxMaterialTiles=32' not in material
assert 'kMaxMaterialsPerTile=4' not in material
assert 'tile->materials.size()>=kMaxMaterialsPerTile' not in material_cpp
assert 'tiles_.size()+needed.size()>kMaxTiles' not in paint_cpp
print("OE_R58_PAINT_BUDGETS_OK: C++ paint, JNI restore and Java archive agree")
