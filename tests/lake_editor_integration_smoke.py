#!/usr/bin/env python3
"""R5.3 lake authoring/save/load wiring regression guard; stdlib only."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
editor=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
checks={
 "C++ API":['"water/lake_surface.h"','nativeLakePlaceAtScreen(', 'nativeLakeAdjust(', 
            'nativeLakeDelete(', 'nativeLakeSelect(', 'nativeLakeNames(',
            'buildLakeSurface(gLakes,worldHalf(),terrain,surface)'],
 "GL lifecycle":['gLakeGpu={};', 'drawLakes(vp);', 'glEnable(GL_BLEND)',
                 'gLakeGpu.terrainRevision=gTerrainRevision', 'glUniform1f(gShader.locOpacity,.77f)'],
 "undo":['gLakes=s.lakes;', 'gLakes.clear();gSelectedLake=-1;++gLakeRevision;',
         'gLakes.swap(stagedLakes);gSelectedLake=-1;++gLakeRevision;'],
 "scene export":['out<<"],\\\"lakes\\\":[";', 'jfloatArray lakeData',
                  'validLake(lake,half)'],
}
for name,patterns in checks.items():
 for p in patterns: assert p in native,(name,p)
for p in ['"Вода".equals(category)','nativeLakePlaceAtScreen(px,py,lakeRadius,lakeFillDepth)',
          'nativeLakeNames()','nativeLakeAdjust(level,radius)','nativeLakeDelete()',
          'json.put("version",7)','else json.remove("lakes")',
          'sceneVersion>7','savedLakes=document.optJSONArray("lakes")',
          'materialCoords,materialKeys,materialWeights,lakeData)']:
 assert p in editor,p
for p in ['version>=1&&version<=7','validateLakes(json,version)',
          'radius>=8&&radius<=160','level>=-24&&level<=160']:
 assert p in archive,p
assert "water/lake_surface.cpp" in cmake
assert "OE TERRAIN AND LAKES R5.3 TEST" in manifest
print("OE_WATER_EDITOR_INTEGRATION_OK: gesture, rendering, undo, v7 zip and atomic import")
