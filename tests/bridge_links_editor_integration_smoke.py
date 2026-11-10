#!/usr/bin/env python3
"""R5.4 road ↔ bridge native/Java wiring. Complements strict host geometry tests."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
road=(root/"android/src/main/cpp/roads/bridge_links.cpp").read_text()
assert '#include "roads/bridge_links.h"' in cpp
assert "roads/bridge_links.cpp" in cmake
assert "nativeBridgeRoadStats(" in cpp
assert "nativeBridgeAlignSelected(" in cpp
assert "proposeBridgeRoadFit(graph,original,height,fitted)" in cpp
assert "buildBridgeNetwork(graph,bridges,height,network)" in cpp
assert "recordEdit();" in cpp
assert "if(!oe::roads::proposeBridgeRoadFit" in cpp
assert "private static native int[] nativeBridgeRoadStats();" in java
assert "private static native boolean nativeBridgeAlignSelected();" in java
assert 'this::alignSelectedBridge' in java
assert 'this::showBridgeRoadStats' in java
assert "nativeBridgeAlignSelected()" in java
assert "nativeBridgeRoadStats()" in java
assert '"Мосты".equals(category)' in java
assert "OE BRIDGE ROAD LINKS R5.4 TEST" in manifest
assert "kBridgeMaxHorizontalGap=2.0" in (root/"android/src/main/cpp/roads/bridge_links.h").read_text()
assert "kBridgeMaxVerticalGap=.75" in (root/"android/src/main/cpp/roads/bridge_links.h").read_text()
assert "usedNodes.insert" in road
assert "if(!appendDeck(deck,result.collision))return false;" in road
assert "result.roadCollisionTriangles=result.collision.size()" in road
print("OE_BRIDGE_EDITOR_INTEGRATION_OK: select, auto-fit, undo, stats and collision adapter")
