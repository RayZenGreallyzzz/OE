#!/usr/bin/env python3
"""Road texture source/renderer/project contract. Android APK compiles separately."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
road=(root/"android/src/main/cpp/roads/road_network.h").read_text()
model=(root/"android/src/main/cpp/roads/road_network.cpp").read_text()
editor=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
for needle in (
    'nativeRoadSetCustomTexture(', 'nativeRoadSetTileMetres(',
    'nativeRoadSelectedTileMetres(', 'bindPaint(material);',
    'const float uvScale=2.f/tile', 'roadId==0?x/tile:point.u*uvScale',
    'roadId==0?z/tile:point.v*uvScale', 'road.tileMetres=tiles[',
    'jdoubleArray roadTileMetres', 'validPaintKey(road.material)',
    'gRoadTileMetres=4.0', 'GL_REPEAT'
):
    assert needle in native,("C++ renderer",needle)
for needle in (
    'double tileMetres=4.0', '(!finite(road.tileMetres)||road.tileMetres<0.5||road.tileMetres>32.0)',
):
    assert needle in road+model,("C++ road",needle)
for needle in (
    'nativeRoadSetCustomTexture(String key)', 'nativeRoadSetTileMetres(double metres)',
    'private void chooseRoadTexture()', 'private void chooseRoadTextureSize()',
    'private void useRoadTexture(String key)', 'private void applyActiveRoadTexture()',
    'this::pickPaintTexture', 'this::chooseRoadTexture',
    'json.put("version",8)', 'sceneVersion>8',
    'roadTileMetres[i]=tiling', 'referencedPaint.add(material)',
    'roadIds,roadNames,roadMaterials,roadWidths,roadTileMetres,roadPaths',
):
    assert needle in editor,("Java editor",needle)
for needle in (
    'version>=1&&version<=8','JSONArray roads=document.optJSONArray("roads")',
    'if(custom)keys.add(material)', 'sceneVersion>=8',
    'version>=8&&(!json.has("lakes")',
):
    assert needle in archive,("ZIP export/import",needle)
print("OE_ROAD_TEXTURE_INTEGRATION_OK: JNI/UI, 2D images, UV, v8 ZIP, backwards scenes")
