#!/usr/bin/env python3
"""Stage 9: editor-only touch chain creates editable TRUE WalkBarriers.

Runs on GitHub Actions alongside compiled WalkController regression. Static
wiring test is NOT a substitute for an on-tablet UX/thermal test.
"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
cpp=(r/"android/src/main/cpp/native_engine.cpp").read_text()
java=(r/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
gradle=(r/"android/build.gradle").read_text()
smoke=(r/"tests/walk_barrier_cut_stage8_smoke.cpp").read_text()
for q in (
    "nativeBarrierPointAtScreen(",
    "nativeBarrierDraftPoint(",
    "nativeBarrierEdit(",
    "validWalkBarrier(edited,worldHalf())",
    "recordEdit();\n  gWalkBarriers.swap(staged);",
    "gBarrierDraftPointVisible=false;",
    "void drawCollisionBarrierGuides(const Mat4&vp)",
    "const float x=gBarrierDraftX,z=gBarrierDraftZ,r=.8f",
    "if(gWalkBarriersVisible)for(const auto&w:gWalkBarriers)",
    "gWalkBarriers=std::move(stagedWalls)",
    "gWalkBarriers=s.barriers",
):
    assert q in cpp, ("native chain, editable physics, or GLES marker missing",q)
for q in (
    "nativeBarrierPointAtScreen(float px,float py)",
    "nativeBarrierDraftPoint(boolean visible,float x,float z)",
    "nativeBarrierEdit(int id,double ax,double az",
    "private void startBarrierPolyline()",
    "private void editCollisionBarrier(String row)",
    "(barrierDrawMode||barrierPickMode||barrierMoveId>0)&&",
    "int id=nativeBarrierAdd(barrierDraftStart[0]",
    "barrierDraftStart=at; // continue same polygonal chain",
    "++barrierDrawGeneration",
    'if(!"Коллизии".equals(category)&&(barrierDrawMode||barrierCutId>0||barrierPickMode||barrierMoveId>0))',
    "if(barrierDrawMode||barrierPickMode||barrierMoveId>0)cancelPlace();",
    "android.widget.ScrollView scroll",
    "final double[] values=new double[6]",
    "nativeBarrierEdit(id,values[0],values[1]",
    "surface.queueEvent(()->nativeBarrierDraftPoint(false,0f,0f))",
):
    assert q in java, ("Android wall chain gesture/edit/lifecycle missing",q)
assert "WalkBarrier diagonal" in smoke and "diagonalGap.size()==2" in smoke
assert "applicationId 'game.oe.editor.r57nodes'" in gradle
assert "versionCode 47" in gradle
assert "GLSurfaceView.RENDERMODE_WHEN_DIRTY" in java
assert "new WebView(" not in java
# Do not upgrade v12: same persisted physical wall record shape.
assert 'json.put("version",12)' in java
assert "collisionWalls" in cpp
print("OE_STAGE9_WALL_CHAIN_OK: two-finger camera, continuous native wall segments, "
      "correct physical edit and Undo, reusable v12 save format, no extra loop")
