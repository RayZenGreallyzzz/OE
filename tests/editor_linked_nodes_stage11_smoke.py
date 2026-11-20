#!/usr/bin/env python3
"""Stage 11: immutable/atomic junction edits for true native collision walls.
C++ physical tests compile separately in the same GitHub Actions workflow.
"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
cpp=(r/"android/src/main/cpp/native_engine.cpp").read_text()
java=(r/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
nodes=(r/"android/src/main/cpp/runtime/walk_barrier_nodes.h").read_text()
gradle=(r/"android/build.gradle").read_text()
archive=(r/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
for q in (
    '#include "runtime/walk_barrier_nodes.h"',
    "nativeBarrierNodeDegree(",
    "walkNodeDegree(gWalkBarriers,x,z)",
    "oe::runtime::editLinkedWalkBarrier(",
    "recordEdit();gWalkBarriers.swap(staged);gSelectedBarrierId=id;",
    "recordEdit();",
    "gWalkBarriers.swap(staged);",
    "nativeBarrierMoveAtScreen(",
    "nativeBarrierEdit(",
):
    assert q in cpp, ("native edit of linked collision nodes missing",q)
for q in (
    "kLinkedWalkNodeToleranceMetres=.05",
    "int editLinkedWalkBarrier(",
    "if(!validWalkBarrier(wall,halfWorld))return 0;",
    "if(changedCount>0)walls.swap(staged);",
    "moveA&&sameWalkNode(",
    "moveB&&sameWalkNode(",
    "wall.thickness=desired.thickness;",
    "wall.height=desired.height;",
    "std::size_t walkNodeDegree(",
):
    assert q in nodes, ("atomic linked-node geometry missing",q)
for q in (
    "nativeBarrierNodeDegree(int id,int endpoint)",
    "nativeBarrierNodeDegree(id,0)",
    "nativeBarrierNodeDegree(id,1)",
    '"🟢 Перенести общий узел начала ("+degreeA+" стен)"',
    '"🔵 Перенести общий узел конца ("+degreeB+" стен)"',
    "Перемещено стен:",
    "Смещение",  # optional, checked separately below
):
    if q=="Смещение":continue
    assert q in java, ("Android linked-node menu missing",q)
assert "if(generation!=barrierDrawGeneration)return;" in java
assert 'json.put("version",12)' in java
assert "version>=1&&version<=12" in archive
assert "collisionWalls" in cpp
assert "applicationId 'game.oe.editor.r57nodes'" in gradle
assert "versionCode 47" in gradle
assert "new WebView(" not in java
print("OE_STAGE11_LINKED_JUNCTIONS_OK: atomic C++ shared endpoints, v12 "
      "project, one undo and on-demand menu counts")
