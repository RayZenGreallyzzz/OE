#!/usr/bin/env python3
"""R5.7 Android touch preview: bounded ticking, pointer lifetimes, and JNI."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
road=(root/"android/src/main/cpp/roads/traversal_graph.cpp").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
for needle in [
    "private final class WalkJoystick extends View",
    "event.findPointerIndex(pointer)",
    "event.getPointerId(event.getActionIndex())==pointer",
    "pointer=-1;walkAxisX=0f;walkAxisZ=0f;invalidate()",
    "walkJoystick.setVisibility(View.GONE)",
    "walkJoystick.setVisibility(View.VISIBLE)",
    "walkControlsActive=true",
    "walkHandler.removeCallbacks(walkTick)",
    "walkHandler.postDelayed(this,moving||jump||walkAirborne?33:100)",
    "private final Handler walkHandler=new Handler(Looper.getMainLooper())",
    "nativeWalkPreviewStep(dx,dz,jump,2)",
    "if(!walkTickPending&&(moving||jump||walkAirborne))",
    "if(session!=walkSessionGeneration)return",
    "private void finishWalkControls()",
    "finishWalkControls(); // also invalidates a start still waiting on GL",
    "if(walkControlsActive||walkStartPending)finishWalkControls();",
    "walkJumpQueued=true",
    "statusText.setText(walkPreviewDescription(state))",
    "private void stopWalkCollisionPreview()",
]:
    assert needle in java,needle
for needle in ["q.bridge=true;q.approach=true","closestSafeEnd(graph,roadEnds,walk.a,walk.b,walk",
               "closestSafeEnd(graph,roadEnds,walk.b,walk.a,walk","if(status.endsConnected==2)"]:
    assert needle in road,needle
for needle in ["nativeWalkPreviewStart(", "nativeWalkPreviewStep(",
               "nativeWalkPreviewStop(", "nativeWalkPreviewState(",
               "gWalkPreview.clear(); // Stop demo before changing real authored geometry."]:
    assert needle in native,needle
# The UI and native preview must not resurrect an old session after pause.
for needle in [
    "private boolean walkStartPending=false,walkActivityPaused=false",
    "walkStartPending=true",
    "if(startSession!=walkSessionGeneration)return",
    "walkActivityPaused=true;",
    "walkActivityPaused=false;",
    "if(!walkControlsActive&&!walkStartPending)return",
    "finishWalkControls();\n        importing=true;",
    "info==null||info.length!=9||info[0]<0",
    "Проверенных переходов берег–мост:",
]:
    assert needle in java,needle
assert "jint stats[9]" in native
assert "traversal.approachSurfaces" in native
assert "validApproach(q)" in road
assert "version>=1&&version<=8" in archive
assert 'applicationIdSuffix' in (root/"android/build.gradle").read_text()
assert 'OE BANK SEAMS R5.8a TEST' in manifest
assert 'android:allowBackup="false"' in manifest
print("OE_TOUCH_WALK_UI_OK: bounded frame scheduling, pointer reset, lifecycle and safe approach ramps")
