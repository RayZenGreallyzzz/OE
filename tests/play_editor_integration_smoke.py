#!/usr/bin/env python3
"""R5.8b Play/Stop integration: independent GL-thread scene, UI safety, JNI."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
runtime=(root/"android/src/main/cpp/runtime/play_session.cpp").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
for needle in (
    "private static native boolean nativePlayStart();",
    "private static native boolean nativePlayStep(float axisX,float axisZ,",
    "private static native void nativePlayOrbit(float dx,float dy);",
    "private static native void nativePlayStop();",
    "private static native double[] nativePlayState();",
    "private void startPlayMode(){",
    "private void finishPlayMode(){",
    "private void setPlayEditorLocked(boolean locked){",
    "if(playModeActive||playStartPending){",
    "if(walkControlsActive||playModeActive)walkJumpQueued=true;",
    "if(!walkControlsActive&&!playModeActive)return false;",
    "final boolean playing=playModeActive;",
    "playing?nativePlayStep(dx,dz,jump,2)",
    "final double[] state=ok?",
    "if(playing)finishPlayMode();",
    "if(playModeActive||playStartPending){finishPlayMode();return;}",
    "finishPlayMode();\n        finishWalkControls();",
    "if(playModeActive||playStartPending){\n                final int action=e.getActionMasked();",
    "queueEvent(()->{nativePlayOrbit(dx,dy);requestRender();});",
    "walkActivityPaused=true;",
    'editorPlayButton=button("▶ Play",this::startPlayMode);',
    'walkStopButton.setText("■ Stop");',
):
    assert needle in java,needle
for needle in (
    '"runtime/play_session.h"',
    "static std::unique_ptr<oe::runtime::PlaySession> gPlaySession;",
    "static std::vector<Entity> gPlaySceneObjects;",
    "if(gPlaySession&&gPlaySession->active()){",
    "const auto&renderObjects=(gPlaySession&&gPlaySession->active())?",
    "gPlaySceneObjects:gEntities",
    "oePlayTerrainCopy()",
    "const auto terrainBrushes=gBrushes;",
    "std::make_unique<oe::runtime::PlaySession>(512)",
    "std::vector<Entity> copied=gEntities;",
    "gPlaySceneObjects.swap(copied);",
    "Java_game_oe_editor_EditorActivity_nativePlayStart(",
    "Java_game_oe_editor_EditorActivity_nativePlayStep(",
    "Java_game_oe_editor_EditorActivity_nativePlayStop(",
    "Java_game_oe_editor_EditorActivity_nativePlayState(",
    "Java_game_oe_editor_EditorActivity_nativePlayOrbit(",
):
    assert needle in native,needle
assert "world_.loadStaticScene(scene)" in runtime
assert "walker_.applyToWorld(world_,playerId_)" in runtime
assert "world_.stepTicks(fixedTicks)" in runtime
assert "runtime/play_session.cpp" in cmake
assert "OE PLAY STOP R5.8b TEST" in manifest
assert "versionCode 38" in (root/"android/build.gradle").read_text()
assert "version>=1&&version<=8" in (root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
print("OE_PLAY_EDITOR_OK: separate Runtime ECS snapshot, GL-only Play/Stop, blocked authoring and camera")
