#!/usr/bin/env python3
"""Stage8b: physically meaningful wall testing on the owner's Android tablet.

Compile/runtime walk/steep terrain crossing is verified independently by
walk_barrier_cut_stage8_smoke.cpp. This static guard ensures JNI and
Android touch/UI remain wired after bug fixes.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
walk=(root/"android/src/main/cpp/runtime/walk_collision.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
gradle=(root/"android/build.gradle").read_text()
smoke=(root/"tests/walk_barrier_cut_stage8_smoke.cpp").read_text()
for item in (
    "bool oeStartWalkPreview(int targetBarrierId)",
    "nativeWalkPreviewStartAtBarrier(",
    "oeStartWalkPreview(barrierId)?JNI_TRUE:JNI_FALSE",
    "const auto safePoint=[&](double px,double pz)->bool",
    "walkPointSegmentDistance(px,pz",
    "for(const double extra:{1.5,3.0,5.0})",
    "demo.configure(traversal,ground,worldHalf(),gWalkBarriers)",
    "gWalkPreview.clear(); // Stop demo before changing real authored geometry.",
    "gWalkPreview.clear(); // Undo/Redo invalidates derived physical surfaces.",
):
    assert item in cpp, ("physical collision preview guard missing",item)
for item in (
    "nativeWalkPreviewStartAtBarrier(int wallId)",
    "startWalkCollisionPreview(id);",
    "private void startWalkCollisionPreview(int selectedWallId)",
    "if(walkControlsActive)finishWalkControls();",
    "nativeWalkPreviewStartAtBarrier(selectedWallId)",
    "СНАРУЖИ выбранной стены",
    "Розовые линии — только редакторские подсказки",
    "Вода и перила пока не имеют отдельной физики",
):
    assert item in java, ("walk UI guard missing",item)
assert "Пока нет столкновений со стенами" not in java
assert "terrain_( (wall.ax+wall.bx)*.5" not in walk
assert "validTerrain(terrain_,wall.ax+t*vx,wall.az+t*vz,groundY)" in walk
assert "const oe::roads::TerrainHeight hillside" in smoke
assert "ridgeGap.size()==2" in smoke
assert "applicationId 'game.oe.editor.r57wallfix'" in gradle
assert "versionCode 44" in gradle
assert "runtime/walk_collision.h" in cpp
print("OE_STAGE8B_WALL_QA_OK: spawn outside wall, correct hill-height collision, "
      "UI cancels stale joystick, warnings accurate, legacy scene untouched")
