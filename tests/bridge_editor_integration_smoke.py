#!/usr/bin/env python3
"""R5.4 JNI/Android editor contract test. No external dependencies."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
for needle in (
    '"roads/traversal_graph.h"',
    "nativeBridgeTraversalStats(", "nativeBridgeAutoAlign(",
    "oe::roads::buildTraversalGraph(graph,bridges,terrain,result)",
    "oe::roads::suggestBridgeRoadAlignment(",
    "bridge.yaw=-static_cast<double>(e.yaw)*RAD",
    "const float nr=-static_cast<float>(proposal.yaw/RAD)",
    "recordEdit();",
):
    assert needle in native,needle
for needle in (
    "nativeBridgeTraversalStats();", "nativeBridgeAutoAlign();",
    'this::showBridgeTraversalStatus', 'this::alignSelectedBridge',
    'private void showBridgeTraversalStatus()', 'private void alignSelectedBridge()',
    "surface.queueEvent(()->{", "edit(()->{",
):
    assert needle in java,needle
assert "roads/traversal_graph.cpp" in cmake
assert "OE ROAD TEXTURES R5.5 TEST" in manifest
assert "android:allowBackup=\"false\"" in manifest
print("OE_BRIDGE_EDITOR_INTEGRATION_OK: compiler wiring, yaw units, UI and undo")
