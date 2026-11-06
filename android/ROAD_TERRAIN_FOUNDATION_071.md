# OE Road & Terrain Foundation — experimental milestone R1

Branch: `test/road-terrain-foundation-071`, forked from `test/game-runtime-core-070`.
Scope: **foundation / geometry only**. This is **not** yet a paintable road editor or a new APK feature. OE `main` and the ORVETHRA game must not be modified.

## Architectural decision
A road is a durable, editable **world object**, not a sequence of GPU decals. A reference PNG map is editor-only guidance and is **not** the road data model. Road geometry, gameplay collision and NPC navigation are separate consumers of the road's shared serialized centerline.

Modules:
- RoadModel (portable C++): stable road ID, UTF-8 name, logical material reference, width in metres, editable ordered XZ control points. Uses double precision; ID is **not** a runtime entity handle.
- RoadMesher (portable C++): samples a terrain height callback; generates strip triangles with UV distance coordinates; does not touch OpenGL or editor state.
- RoadEditor (future Android): PNG guide alignment/calibration, opacity, top-down camera, pointer-offset brush, point placement/undo/redo, width/material inspector, delete/restore.
- TerrainLayers (future): chunked splat/weight maps for continuous finger painting and brush cursor. Do not increase the old 48-decal limit to thousands of draw calls.
- RoadRuntime (future): uses baked road geometry and material assets through AssetManager. Physics collider surfaces and NPC routing graph are compiled separately from authored road data.

## Implemented in this milestone
- `roads/road_network.h/.cpp`: portable road data, create/edit/remove, persistent unique IDs, all-or-nothing replaceAll; finite/bounded coordinates and width validation.
- Procedural terrain-conforming road **surface mesh** generator. Segment tessellation is configurable (0.25–16 m), UVs repeat by metre. Corner miters are clamped.
- Host C++17 tests for straight/bent roads, material/width persistence, invalid points and widths, atomic mesh and scene failure, stable IDs.
- CMake compiles portable module as separate static library in the Android APK. Runtime core is unchanged.

## Not yet implemented; do not report as complete
- No Road Editor buttons, 3D road rendering, touch input, PNG reference overlay, freehand continuous brush, chunked terrain splatmaps or persistent `.oezip` road serialization yet.
- No proper junction stitching, bridges/overpasses, sidewalks, camber, banked turns, curved spline interpolation, navmesh/pathfinding, vehicle lanes, collider physics, prefab instances or standalone game export.
- Straight pieces and angled control points are supported by the geometry producer; tight 180-degree turns and crossing roads need later corner/junction topology.
- Road surface Y is sampled from the terrain plus 3 cm to avoid coplanar z-fighting; this is visual preview data, **not collision**.
- Terrain height is supplied by caller: do not copy editor terrain logic into runtime core.
- Current coordinates limited to ±8192 m (OE editor 16 km span); later large-world origin shifting must preserve durable double-precision road data.

## Format & compatibility contract
Add a new `roads` array to an optional versioned scene extension (not to current v1 until a migration reader exists).
Proposed record (design only, **not yet parsed by code**):
```json
{
  "roadsFormat": 1,
  "roads": [{
    "id": "1",
    "name": "Main street",
    "material": "builtin:cobblestone",
    "widthMetres": 6.0,
    "controlPoints": [[10.0, 20.0], [40.0, 20.0]]
  }]
}
```
IDs serialized as decimal strings to avoid JSON 53-bit integer truncation. Preserve road control points as authored source; rendering meshes and collision/nav graphs are **derived caches**. Old scenes should load with an empty road network; never silently drop new roads on a save through an old reader.

## Implementation order
R2: RoadEditor point mode, selection, preview GPU mesh, input undo, road mesh elevation sync.
R3: PNG map reference with pixels-to-metres calibration, alpha, transform, tiles; not included in exported game package.
R4: continuous pointer-offset brush + chunked terrain weight masks with storage and version migration; keep old stamp scenes loadable.
R5: junctions and geometry stitching, elevation/cut-fill, physics surface shape, navgraph for future NPC; profiling and 4 GB Android tablet QA.

## Tests
```sh
g++ -std=c++17 -O2 -Wall -Wextra -Werror -pedantic \
  tests/road_network_smoke.cpp android/src/main/cpp/roads/road_network.cpp \
  -o /tmp/oe-roads-test
/tmp/oe-roads-test
```
Expected: `OE_ROAD_NETWORK_OK`.

Device-only checks will be added once an editor UI and GPU renderer are connected. CI success alone must not be represented as tested roads on the tablet.
