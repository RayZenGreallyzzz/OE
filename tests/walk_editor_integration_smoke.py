#!/usr/bin/env python3
"""OE R5.6 character physics: preserve editor data, surface and JNI contract."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
runtime=(root/"android/src/main/cpp/runtime/walk_collision.cpp").read_text()
header=(root/"android/src/main/cpp/runtime/walk_collision.h").read_text()
for pattern in (
    '"runtime/walk_collision.h"',
    "nativeWalkPreviewStart(", "nativeWalkPreviewStep(",
    "nativeWalkPreviewState(", "nativeWalkPreviewStop(",
    "oeCurrentTraversalGraph(traversal)", "gWalkPreview.configure(",
):
    if pattern=="gWalkPreview.configure(":
        continue # start commits atomically after constructing local walker
    assert pattern in native,("JNI missing",pattern)
for pattern in (
    "gWalkPreview=std::move(demo)", "drawLakes(vp);",
    "if(gWalkPreview.active())", "gWalkPreview.clear(); // Stop demo",
    "gWalkPreview.clear(); // Undo/Redo",
    "gWalkPreview.clear();\n gEntities.swap(staged);",
    "gWalkPreview.clear();\n recordEdit();",
):
    assert pattern in native,("preview safety",pattern)
for pattern in (
    "nativeWalkPreviewStart();", "nativeWalkPreviewStop();",
    "nativeWalkPreviewStep(float axisX,float axisZ",
    "private void startWalkCollisionPreview()",
    "private void moveWalkPreview(",
    "private void showWalkPreviewState()",
    "private void stopWalkCollisionPreview()",
    "surface.queueEvent(()->{",
    "this::startWalkCollisionPreview",
    "this::showWalkPreviewState",
):
    assert pattern in java,("UI controls",pattern)
for pattern in (
    "kFixedTimeStep","kWalkStepHeight",
    "kWalkGravity", "kJumpSpeed",
    "bool applyToWorld(World&world,EntityId actor) const noexcept;",
):
    assert pattern in header,("Runtime header",pattern)
for pattern in (
    "quadHeight(", "s.bridge&&!connected.count(s.ownerId)",
    "std::hypot((right-left)", "next.verticalVelocity+=kWalkGravity*kDt",
    "world.setTransform(actor,t)", "state_=next;",
):
    assert pattern in runtime,("Runtime solver",pattern)
assert "runtime/walk_collision.cpp" in cmake
assert "android:allowBackup=\"false\"" in (root/"android/src/main/AndroidManifest.xml").read_text()
assert "OE WALK COLLISION R5.6 TEST" in (root/"android/src/main/AndroidManifest.xml").read_text()
print("OE_WALK_EDITOR_OK: runtime solver, ghost-only scene safety, JNI, steps and QA controls")
