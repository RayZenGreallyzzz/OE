# OE 0.7.4–0.7.6 — experimental 3D Road Editor

**Branch:** `test/road-editor-074`; **base:** `test/basisu-ktx2-073`.
Isolated editor feature. Does **not** merge into `main` or the game runtime PRs.

**Installation safety:** the Android test app has its own application ID `game.oe.editor.roadstest` and launcher name **OE ROADS 0.7.6 TEST**. It installs alongside stable OE and the BasisU test. Existing locally stored projects remain in the original app; export a `.oezip` archive there and import it into this test app to try roads on a copy. Do not rely on test-only storage as your only backup.

## Use on Android

1. Open **☰ Каталог → Дороги → 🛣 Новая дорога**.
2. **Drag one finger** across terrain to paint a continuous road. Alternatively tap repeatedly to place control points. Touch points must be at least 0.5 m apart (drag samples at 2 m).
3. Press the floating **✓ Завершить дорогу**. A valid road requires at least two distinct points.
4. Open **Дороги → ✏ Редактировать узлы**, and drag the orange control handles to reshape a road.
5. Open **📋 Список дорог и выбор** to select another path.
6. **📏 Ширина** sets the new-road default and changes the selected road, from 2 to 18 m.
7. **〰 Разметка** toggles edge lines and dashed centre markings on the selected road.
8. **🗑 Удалить** removes the selected road, with confirmation and editor Undo/Redo.
9. Two-finger pinch/pan works in road editing mode; ordinary object/gizmo gestures work again on exit.

Roads are curved through control points (Catmull-Rom cubic Hermite), drawn in one cached OpenGL ES 3 buffer, and sampled against the current edited heightfield. The road mesh rebuilds only on road/terrain changes or EGL context recreation.

## Automatic road junctions (0.7.5 update to PR #20)

- **X crossings:** crossing pairs of sampled road spline segments create a shared graph junction even when neither road has an endpoint there.
- **T joins:** completing a new road automatically snaps each end to the nearest point on a different road centerline if within **3 metres**. The other road is not modified.
- **Reconnecting after edits:** moving a first/last control handle also snaps it to a nearby road on finger release. Each drag remains one Undo action.
- **Collinear joins:** exact shared endpoints of two aligned roads count as a connection.
- **Multiway joins:** junctions within 1.25 metres merge into one node with a set of connected road IDs.
- **Visible junctions (0.7.6):** a convex asphalt envelope joins the left/right edges of each approach, sized by the road width and direction. On sharp X and T junctions this yields a polygonal street mouth rather than a round disk. Junction triangles follow sculpted terrain and are rebuilt only when road or terrain geometry changes.
- **Diagnostics:** **☰ Каталог → Дороги → 🔗 Соединения и перекрёстки** reports node count, connected road pairs, and whether the junction cap has been reached.
- The connection graph is **derived** from the saved roads (no incompatible scene format change). Deleting/Undo/Redo/reloading a road automatically updates the connections.
- At most **512** junction nodes are generated in the preview; additional potential intersections are reported as overflow. Very complex road scenes may need further optimization and manual QA.
- Nearby parallel roads are *not* joined unless their endpoints are snapped. Overlaid collinear roads with no shared endpoint are **not** treated as a graph connection.

## 0.7.6 geometric junctions and precise lane-marking trimming

- `road_junction_geometry.h` derives a counterclockwise convex junction polygon from actual sampled centreline tangents and half-width offsets at each approach. X, T, diagonal and multi-arm intersections are handled by the same algorithm.
- Two straight, opposite, equal-width road approaches are left visually continuous instead of generating a separate crossing.
- Side lines and dashed center markings are clipped to polygon boundaries (using intervals of each stripe's actual offset path); they no longer run through intersection centers.
- Junction patches use triangles with boundary tessellation around every 2 metres and a small positive terrain offset to limit z-fighting.
- Render data is cached together with the road mesh on geometry/terrain edits and EGL recovery; moving the camera does not change it.
- This is still a **stylized convex intersection envelope**, not exact curb-radius geometry, turn lanes, stop bars, zebra crossings, priority signs, pedestrian routing or car simulation. Very acute angles and close neighbouring junctions need on-device QA.
- Geometry checks: `tests/road_junction_geometry_smoke.cpp` verifies X/T/diagonal/multiway widths, clipping and nearby distinct crossings.

## Persistence

- Scene JSON remains **version 1**, with optional `roads` array.
- Old scenes without `roads` import as before.
- Roads are saved in local scene JSON and in existing `.oezip` project archives.
- Road data participates in the **atomic** scene replacement: invalid road data rejects the whole scene and leaves the current scene unchanged.
- Road edits are included in Undo/Redo snapshots; a freehand stroke records a single Undo step.
- Shrinking the world rejects sizes that would clip road points.

Example schema:

```json
{"version":1,"worldSize":1024,"demoRoads":false,"items":[],"terrain":[],"terrainPaint":[],
"roads":[[1,6.0,1,[[0,0],[10,0],[20,6]]]]}
```

## Safety and limits

- Up to 24 roads; 48 control points per road; width 2–18 metres.
- The engine still displays its old built-in **demo roads** when enabled; custom road splines are separate and editable. Demo road geometry is *not* converted into editable paths.
- Intersecting roads have width-aware convex asphalt intersection surfaces with clipped lane markings; production curb-radius engineering and traffic-grade lanes are not implemented.
- Traffic rules/lanes, sidewalks, bridges, tunnels, vehicles, physics collisions, and NPC pathfinding are **not** implemented.
- JNI/GLES interaction stays on the GL thread. No continuous frame loop when the app is idle.
- Device testing is required, especially on Allwinner A523/4 GB RAM.

## Required QA before merge

- Draw tap-based and freehand roads at different zoom levels and on sculpted hills.
- Edit a middle control point; change width and line markings; Undo and Redo each.
- Save locally, reload, and export/import `.oezip`; inspect world size and original GLB textures.
- Load a pre-0.7.4 project; then test malformed JSON roads and verify no partial replacement.
- Shrink the world with roads near its edge (must reject when needed).
- Test X, T, 3-way junctions, exact collinear endpoints, and roads running close but not intersecting. Verify that lines stop across junction patches.
- Test a gap <3 metres before/after completing a road, and a gap >3 metres (must remain separate).
- Drag an endpoint toward another road; Undo and Redo the snap; remove one of the connected roads; save/reload the graph.
- Verify selection and gizmo manipulation after exit, frame time, memory, EGL loss and orientation changes.
- Run `tests/road_model_smoke.cpp`, `tests/road_network_smoke.cpp`, `tests/road_junction_geometry_smoke.cpp` and the GitHub Android APK workflow.

**Never merge or release without reviewing CI and testing on device.**
