#!/usr/bin/env python3
"""Static cross-language authoring contract for new OE medieval module IDs.
Runs in addition to the native Android APK compile; no external dependencies.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
native = (root / "android/src/main/cpp/native_engine.cpp").read_text()
java = (root / "android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive = (root / "android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
manifest = (root / "android/src/main/AndroidManifest.xml").read_text()
cmake = (root / "android/src/main/cpp/CMakeLists.txt").read_text()

# Scene entity validators and both map-touch/add paths accept 17..28.
assert "gStaticMeshes[29]" in native
assert "for(int kind=0;kind<29;kind++)if(kind!=6)bakeStaticMesh(kind);" in native
assert "e.type>=29" in native
assert "type<0||type>28||type==6" in native
assert "type<0||type>16" not in native
assert "oe::medieval::moduleParts(type)" in native
assert "nativeFoundationUnderSelected(" in native
assert "fitsStockBuildingFoundation(foundationKind)" in native
assert "nativeFoundationUnderSelected(int foundationKind)" in java
assert '"Мосты".equals(category)' in java
assert '"Фундаменты".equals(category)' in java

# UI palette must truly offer the modules, not just names in an info panel.
for entry in (
    'enterPlaceMode(17,null)',
    'enterPlaceMode(18,null)',
    'enterPlaceMode(19,null)',
    'enterPlaceMode(20,null)',
    'enterPlaceMode(21,null)',
    'enterPlaceMode(22,null)',
    'enterPlaceMode(23,null)',
    'enterPlaceMode(24,null)',
    'enterPlaceMode(25,null)',
    'enterPlaceMode(26,null)',
    'enterPlaceMode(27,null)',
    'enterPlaceMode(28,null)',
    'this::foundationUnderSelected',
):
    assert entry in java, entry

# Only scenes containing new object types move to version six.
assert 'json.put("version",6)' in java
assert "kind>=17&&kind<=28" in java
assert "sceneVersion>7" in java
assert "(e.type>=17&&sceneVersion<6)" in java
assert "version>=1&&version<=7" in archive
assert "kind>=0&&kind<=28&&(kind<=16||version>=6)" in archive
assert "(version!=5||independent!=null)" in archive
assert "medieval/structures.cpp" in cmake
assert "OE BRIDGE ROAD LINKS R5.4 TEST" in manifest

print("OE_MEDIEVAL_EDITOR_INTEGRATION_OK: placement, palette, v6 archive, isolated build")
