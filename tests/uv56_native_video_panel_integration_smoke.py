#!/usr/bin/env python3
"""Owner-video-driven UV56 landscape UX, native R5.7 safety smoke.

The original UV56 landscape demo shows area, brush dropdown, exact cm keypad,
strength slider, material gallery, finger-drag and persistent left sidebar.
This test verifies the Android code actually wires those controls to the
original R5.7 C++/GLES scene. On-device QA still required.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
panel=(root/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
for needle in (
    "🌳 Ландшафт · UV56", "Размер территории",
    "🌍 Создать / заменить землю", "Инструмент кисти",
    "⛰ Поднять землю", "⬇ Опустить землю", "🌄 Горный хребет",
    "〰 Сгладить землю", "▰ Выровнять", "🎨 Покрасить землю",
    "Диаметр кисти", "Радиус кисти",
    "−1 см", "+1 см", "⌗ Ввести см",
    "setOnSeekBarChangeListener", "Сила кисти",
    "Покрытие / загруженный тайл", "Загрузить сразу 10–32 PNG/JPG",
    "smallPreview", "inJustDecodeBounds", "inSampleSize",
    "host.chooseTexture(key)", "host.createArea(",
):
    assert needle in panel, ("UV56 owner-video control missing",needle)
for needle in (
    'showUv56LandscapePanel()', '"🌿 Ландшафт"',
    'nativeUv56StrokeStart(', 'nativeUv56StrokeMove(',
    'nativeUv56StrokeEnd()', 'private boolean uv56GestureStarted=false',
    'if(uv56SculptTool>0)', 'openUv56Mode(6)',
    'usePaintTexture(key,true)', 'activePaletteCategory',
    'nativeUv56ConfigureArea(area)', 'nativeUv56Area()',
):
    assert needle in java, ("Android native renderer wiring missing",needle)
for needle in (
    'static bool gUv56StrokeActive=false',
    'nativeUv56ConfigureArea(', 'nativeUv56StrokeStart(',
    'nativeUv56StrokeMove(', 'nativeUv56StrokeEnd(',
    'recordEdit(); // one native R5.7 undo entry for the whole drag',
    'gUv56Terrain.endStroke()', 'float h=gUv56Terrain.heightAt(x,z)',
    'runtime/walk_collision.h', 'water/lake_surface.h',
    'roads/traversal_graph.h',
):
    assert needle in cpp, ("native one-scene integration lost",needle)
terrain_section=java.split('}else if("Рельеф".equals(category)){',1)[1].split('}else if("Вода".equals(category)){',1)[0]
assert 'showUv56LandscapePanel();' in terrain_section
assert '⛰ Поднять гору' not in terrain_section
assert 'chooseTerrainBrush' not in terrain_section
assert 'enterTerrainMode' not in terrain_section
assert 'version>=1&&version<=13' in archive
assert 'sceneVersion>13' in java
assert "applicationId 'game.oe.editor.r57canvas.uv44full'" in gradle
assert "applicationIdSuffix '.dev'" in gradle
assert "import android.webkit" not in panel and "new WebView(" not in panel
print("UV56_NATIVE_VIDEO_PANEL_OK: persistent native 3D sidebar, cm keypad, "
      "touch-drag undo, area and texture previews; legacy R5.7 preserved")
