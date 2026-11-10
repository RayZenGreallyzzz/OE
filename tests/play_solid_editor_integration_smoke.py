#!/usr/bin/env python3
"""R5.9a collision isolation contract: no authoring writes or format migration."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
physics=(root/"android/src/main/cpp/runtime/walk_collision.cpp").read_text()
solids=(root/"android/src/main/cpp/runtime/solid_collision.cpp").read_text()
play=(root/"android/src/main/cpp/runtime/play_session.cpp").read_text()
cmake=(root/"android/src/main/cpp/CMakeLists.txt").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
for needle in (
    "oePlaySolidFromEntity(const Entity&e",
    "case 0:case 1:case 2:",
    "case 10:",
    "case 15:",
    "default:return false;",
    "std::vector<oe::runtime::SolidBox> solids;",
    "std::make_unique<oe::runtime::PlaySession>(512)",
    "gYaw,solids))",
    "std::vector<Entity> copied=gEntities;",
):
    assert needle in cpp,needle
for needle in (
    "bool WalkController::setStaticSolids(",
    "solidOverlapsBody(solids_,{x,sample.hit.height,z})",
    "solidBlocksStep(solids_,from,{x,from.y,z})",
    "solids_.clear();",
):
    assert needle in physics,needle
for needle in (
    "segmentBox(",
    "std::cos(b.yawRadians)",
    "std::sin(b.yawRadians)",
    "verticalOverlap(",
):
    assert needle in solids,needle
assert "walker_.setStaticSolids(staticSolids)" in play
assert "runtime/solid_collision.cpp" in cmake
assert "OE PLAY SOLIDS R5.9a TEST" in manifest
assert "versionCode 39" in (root/"android/build.gradle").read_text()
assert "version>=1&&version<=8" in archive
print("OE_PLAY_SOLIDS_EDITOR_OK: safe static collider snapshots only in Play, no format changes")
