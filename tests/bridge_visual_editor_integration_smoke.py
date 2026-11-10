#!/usr/bin/env python3
"""R5.8a visual/collision bank seam renderer contract (static integration)."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
graph=(root/"android/src/main/cpp/roads/traversal_graph.cpp").read_text()
model=(root/"android/src/main/cpp/roads/bridge_approach_visual.cpp").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
roadmap=(root/"android/OE_DEVELOPMENT_ROADMAP.md").read_text()
for needle in [
    '#include "roads/bridge_approach_visual.h"',
    "std::uint64_t bridgeVisualStamp()",
    "gRoadGpuBridgeStamp==bridgeStamp",
    "gRoadGpuBridgeStamp=bridgeStamp;",
    "oe::roads::buildBankVisualSeams(traversal,visible)",
    "oe::roads::buildTraversalGraph(graph,bridges,height,traversal)",
    "part.mesh.triangles.begin(),part.mesh.triangles.end()",
    "batches[{source->material,source->tileMetres}]",
]:
    assert needle in native,needle
for needle in [
    "q.materialRoadId=sourceRoad",
    "closestSafeEnd(graph,roadEnds,walk.a,walk.b,walk",
]:
    assert needle in graph,needle
for needle in [
    "kVisualLift=.016",
    "if(!s.approach||!s.bridge||!full.count(s.ownerId))continue",
    "part.materialRoadId=s.materialRoadId",
]:
    assert needle in model,needle
assert "roads/bridge_approach_visual.cpp" in cmake
assert "R5.8a" in roadmap
assert "OE PLAY SOLIDS R5.9a TEST" in (root/"android/src/main/AndroidManifest.xml").read_text()
assert "versionCode 39" in (root/"android/build.gradle").read_text()
print("OE_BANK_VISUAL_EDITOR_OK: cache invalidation, material batching, safe surface authority")
