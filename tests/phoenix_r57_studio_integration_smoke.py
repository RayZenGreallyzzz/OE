#!/usr/bin/env python3
"""R5.7 city + intact Phoenix walker: static non-regression boundary.

These source checks do not replace on-tablet QA or the original private
UV44/UV56 APK comparison performed by stage_uv44_uv56_private.py.
"""
from pathlib import Path

repo=Path(__file__).resolve().parents[1]
java=(repo/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
studio=(repo/"android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
cmake=(repo/"android/src/main/cpp/CMakeLists.txt").read_text()
native=(repo/"android/src/main/cpp/native_engine.cpp").read_text()
archive=(repo/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
manifest=(repo/"android/src/main/AndroidManifest.xml").read_text()
stager=(repo/"tools/stage_uv44_uv56_private.py").read_text()
gradle=(repo/"android/build.gradle").read_text()
for name in (
    "nativeWalkPreviewStart", "nativeWalkPreviewStep",
    "nativeWalkPreviewStop", "startWalkCollisionPreview()",
    "finishWalkControls()", "walkTickPending",
    "nativeBridgeTraversalStats", "startLakeExcavation",
    "startWalkCollisionPreview", "walkJoystick",
    "openPhoenixStudio(String mode)", "PHOENIX_STUDIO=710",
    "PhoenixBuilderActivity.MODE_BUILD", "PhoenixBuilderActivity.MODE_LANDSCAPE",
    "finishWalkControls();", "snapshotWithGuide().getBytes(StandardCharsets.UTF_8)",
    "new AtomicFile(saveFile())", "PHOENIX_STUDIO){",
    'new File(getCacheDir(),"phoenix-export")',
    "getCanonicalFile()", "source.length()>GlbReader.MAX_BYTES",
):
    assert name in java, f"Missing preserved city or studio safety: {name}"
for name in (
    "roads/traversal_graph.h", "water/lake_surface.h",
    "terrainpaint/material_layers.h", "runtime/walk_collision.h",
    "nativeWalkPreviewStep", "nativeRoadBakeTopology", "gLakes",
):
    assert name in native, f"R5.7 city physics feature dropped: {name}"
for name in (
    "roads/traversal_graph.cpp", "water/lake_surface.cpp",
    "terrainpaint/material_layers.cpp",
):
    assert name in cmake, f"R5.7 native source missing from CMake: {name}"
assert 'version>=1&&version<=8' in archive, "v1-v8 archive backward compatibility lost"
assert 'new String[]{"Здания","Детали","Мосты","Фундаменты","Декор","Рельеф","Вода","Дороги","Текстуры","Проект"}' in java
assert 'private void selectTabInLiveScene(' in studio
assert 'class PhoenixBridge' in studio
assert 'private void saveCurrentProject(' in studio
assert 'landscapeMode=!MODE_ORIGINAL_UV44.equals(mode)' in studio
assert "transferTerrainToCity()" not in studio, (
    "Do NOT offer old v40 native height handoff: R5.7 format v1-v8 is different")
assert 'game.oe.editor.PhoenixBuilderActivity' in manifest
assert 'android:exported="false"' in manifest
assert "applicationIdSuffix '.r57studio'" in gradle
assert 'character-collision.js' in stager
assert 'touch-joystick.js' in stager
assert 'assets/js/world.js' in stager
assert 'assets/js/app.js' in stager
print("OK: R5.7 walker, medieval roads/bridges, lakes, v1-v8 saves intact; "
      "Phoenix original physics and landscape isolated in same OE app")
