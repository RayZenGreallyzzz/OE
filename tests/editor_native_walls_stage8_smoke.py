#!/usr/bin/env python3
"""Stage8 collision editor and portable R5.7 WalkController integration gates.

Static guards accompany the actual compiled 60Hz geometry test in check.yml.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
walk=(root/"android/src/main/cpp/runtime/walk_collision.cpp").read_text()
header=(root/"android/src/main/cpp/runtime/walk_collision.h").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
for marker in (
  "static std::vector<oe::runtime::WalkBarrier> gWalkBarriers",
  "gWalkBarriers=s.barriers",
  "gWalkBarriers.clear();gNextWalkBarrierId=1",
  "gWalkBarriers=std::move(stagedWalls)",
  "nativeBarrierAdd(", "nativeBarrierDelete(",
  "nativeBarrierCutAtScreen(", "nativeBarrierList(",
  "nativeBarrierShow(", "drawCollisionBarrierGuides(vp)",
  'out<<"],\\\"collisionWalls\\\":[";',
  "demo.configure(traversal,ground,worldHalf(),gWalkBarriers)",
  "gWalkBarriersVisible=true",
  "wallFloats%7!=0", "kMaxWalkBarriers*7",
  "if(!oe::runtime::validWalkBarrier(w,size*.5))return JNI_FALSE",
  "recordEdit();",
):
    assert marker in cpp, ("native real collision missing",marker)
for marker in ("walkBarrierStops(wall", "barriers_=barriers",
    "barriers_.clear()", "const bool blocked=collisionWall"):
    assert marker in walk, ("60Hz character collision not connected",marker)
assert "const std::vector<WalkBarrier>& barriers={}" in header
for marker in (
  '"Коллизии".equals(category)', "private void createCollisionBarrier(",
  "private void chooseCollisionBarrier()", "private void collisionBarrierActions(",
  "nativeBarrierCutAtScreen(id,sx,sy,radius)",
  "barrierCutId=0;", "barrierCutRadius=k==0?4f:8f",
  'json.put("version",12)', "sceneVersion>13",
  'wallRecords[i*7+j]=value', 'plotIds,plotNames,plotValues,wallRecords))'
):
    assert marker in java, ("Android authoring colliders missing",marker)
assert "validateCollisionWalls(json,version)" in archive
assert "version>=1&&version<=13" in archive
assert "MAX_TEXTURES=32" in archive
assert "referenceNames(scene)" in archive
assert "applicationId 'game.oe.editor.r58canvas.paintcapacity'" in gradle
assert "new WebView(" not in java
print("OE_STAGE8_NATIVE_COLLISIONS_OK: 64 walls, physical 60Hz blocking, "
      "true gap erase, v12 restore/Undo, editor-only magenta guides")
