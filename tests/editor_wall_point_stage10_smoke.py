#!/usr/bin/env python3
"""Stage10 regression: tapping/selecting existing physical wall and relocating
its individual endpoints without introducing a second physics/world engine.
Android source guards complement actual compiled WalkController tests.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
arch=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
walker=(root/"tests/walk_barrier_cut_stage8_smoke.cpp").read_text()
for q in (
    "static int gSelectedBarrierId=0",
    "nativeBarrierPickAtScreen(",
    "walkPointSegmentDistance(x,z,",
    "const double tolerance=std::clamp(",
    "nativeBarrierSelect(",
    "nativeBarrierMoveAtScreen(",
    "if(mode==0){change.ax=x;change.az=z;}",
    "else if(mode==1){change.bx=x;change.bz=z;}",
    "change.ax+=dx;change.az+=dz;change.bx+=dx;change.bz+=dz;",
    "validWalkBarrier(change,worldHalf())",
    "recordEdit();gWalkBarriers.swap(staged);gSelectedBarrierId=id;",
    "chosen?.81f:.18f",
    "endpoint(ax,az,.16f,.96f,.34f)",
    "endpoint(bx,bz,.08f,.76f,1.f)",
):
    assert q in cpp, ("native physical wall selection/move missing",q)
for q in (
    "nativeBarrierPickAtScreen(float px,float py)",
    "nativeBarrierMoveAtScreen(int id,int mode,float px,float py)",
    "private void startBarrierPick()",
    "private void startBarrierMove(int id,int mode)",
    'addPaletteOption("👉 Выбрать отрезок касанием земли"',
    '"🟢 Перенести общий узел начала ("',
    '"🔵 Перенести общий узел конца ("',
    '"↔ Перенести отрезок со связанными узлами"',
    '"🗑 Удалить только этот отрезок"',
    "startBarrierMove(id,k-2);",
    "int id=nativeBarrierPickAtScreen(px,py);",
    "if(generation!=barrierDrawGeneration)return;",
    "final int affected=nativeBarrierMoveAtScreen(",
    "(barrierDrawMode||barrierPickMode||barrierMoveId>0)",
    "barrierPickMode=false;barrierMoveId=0",
    "e.getPointerCount()<2)return true;",
):
    assert q in java, ("Android single-pointer wall editing missing",q)
assert "WalkBarrier edited=wall" in walker
assert "WalkBarrier reshaped=wall" in walker
assert "validWalkBarrier(reshaped,512.)" in walker
assert "validWalkBarrier(edited,512.)" in walker
assert "version>=1&&version<=13" in arch
assert 'json.put("version",12)' in java
assert "collisionWalls" in cpp
assert "applicationId 'game.oe.editor.r58canvas.paintcapacity'" in gradle
assert "versionCode 58" in gradle
assert "new WebView(" not in java
print("OE_STAGE10_WALL_POINT_OK: one-tap nearest wall, selected endpoints, native "
      "physical transform/validation, undo and v12 persistence")
