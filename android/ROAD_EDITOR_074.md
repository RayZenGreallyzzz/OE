# OE 0.7.4–0.7.8 — experimental 3D Road Editor

**Branch:** `test/road-editor-074`; **base:** `test/basisu-ktx2-073`.
Isolated editor feature. Does **not** merge into `main` or the game runtime PRs.

**Installation safety:** the Android test app has its own application ID `game.oe.editor.roadstest` and launcher name **OE MEDIEVAL ROADS 0.7.8**. It installs alongside stable OE and the BasisU test. Existing locally stored projects remain in the original app; export a `.oezip` archive there and import it into this test app to try roads on a copy. Do not rely on test-only storage as your only backup.

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

## 0.7.7 — Sidewalks, curbs and pedestrian crossings

- **Sidewalks:** 1.8-metre concrete strips along both sides of every editable road, following the same sculpted terrain samples as the asphalt. They do not change original terrain and never enter the game physics system.
- **Curbs:** raised vertical kerb faces along the inner sidewalk edge, and exterior pavement side walls; all geometry uses a single cached road GLES 3 VBO.
- **Intersection corners:** polygonal sidewalk aprons are generated around only the non-driving boundary edges of an X/T intersection. Approach mouths remain free of sidewalks and curbs.
- **Crosswalks:** white zebra stripes are placed perpendicular to each open approach, 1 m outside the polygon mouth, with 1.35 m depth. Too-short dead-end arms and placements touching another intersection/map edge are skipped rather than render floating stripes.
- **Settings:** ☰ Каталог → Дороги → **🚶 Тротуары и бордюры** or **🚸 Пешеходные переходы** toggles all scene roads; choices are persisted in version-1 JSON using optional booleans `roadSidewalks` and `roadCrosswalks`. Old scenes default to both enabled. Undo/Redo records toggles.
- **Performance:** features are generated only when scene roads, terrain, or the settings change (or EGL context is restored); no independent animation loop. Limits on roads and crossing counts remain in place.
- **Boundaries:** this is *visual* street furniture, not walkable collision meshes or accessible curb ramps. No zebra traffic signals, traffic controls, NPC route planning, tactile paving, slope engineering, or real-world road standard certification.
- **Tests:** `tests/road_pedestrian_geometry_smoke.cpp` validates four-way and T crossings, corner curbs avoiding road mouths, crossing bar widths/orientation, short roads, old/no junctions and map borders.

## 0.7.8 — Medieval Streets, with subtle dark-fantasy elements

Goal: a grounded medieval town with a little mysterious fantasy at the castle, ruined temple and older neighbourhoods — NOT a modern traffic simulation. Existing asphalt remains available for other OE worlds.

**Choose a surface in ☰ Каталог → Дороги → 🪨 Покрытие дороги**:

| ID | Surface | Intended use | Rendering |
| --- | --- | --- | --- |
| 0 | Asphalt | Compatibility and modern scenes | Old charcoal road, optional white/yellow markings |
| 1 | Cobblestone | Town streets, squares, castle entrance | Warm-grey grouted stone blocks with deterministic variation |
| 2 | Dirt | Cart lanes, village outskirts | Brown earth, patches and two wagon wheel ruts |
| 3 | Gravel | Fort tracks and courtyard edges | Fine uneven grey-brown pebbled patches |
| 4 | Ancient dark slate | A little dark fantasy around ancient ruins | Cool slate-blue stone blocks with rare bluish accents |

**🏰 Средневековый пресет улиц** chooses **4 m cobblestone**, disables modern road markings as the *default for newly created roads* and turns off scene-wide modern concrete sidewalks and zebra crossings. It **does not delete or recolour existing roads**. Any road can be changed independently using the surface selector. The existing surface selectors for width, edit handles, snap to other roads and Undo/Redo continue working.

**Geometry & performance:** the four medieval surfaces use small precomputed vertex-colour quads, not imported texture assets, custom shaders, CPU-per-frame randomness or network downloads. Per-road detail budgets share an upper bound of 14,000 quads across the scene; long or complex roads show a lower sampling density rather than allocate extra VRAM. Cobblestone and ancient-slate crossings inherit the predominant incoming road's surface colour and get restrained inlaid stone detail. Dirt roads display basic twin cart ruts. No material changes the collision model.

**Persistence:** scene JSON version remains **1**. Older roads use `[id,width,markings,[[x,z],...]]` and load as asphalt (material ID 0). New roads append the optional fifth field: `[id,width,markings,[[x,z],...],material]`. Material IDs outside 0–4 or invalid road structures reject the scene before replacement. Legacy projects do not silently change styles. Updated projects also travel in `.oezip` archives.

**Medieval art direction:** keep most streets grounded in European medieval architecture (small irregular cobbles, dirt, gravel, wooden carts, stone castles), with the dark-slate surface reserved for special quarters. This feature does *not* yet add rune emission, custom PBR materials, real 3D loose stones, castle props, decals, wet surfaces, shadows or atmospheric lighting.

**Tablet acceptance tests:** paint a cobblestone main street, a dirt path next to houses, a gravel road toward the wall and a short ancient-slate lane. Test intersections between mixed styles and a very long road at 4 GB RAM. Switch materials on selected roads, Undo/Redo, save/load, export/import .oezip, reopen a legacy four-element road, and verify that setting material 0 preserves old modern markings. Verify setting the medieval preset does not unexpectedly recolour existing roads.

## 0.7.9 — Medieval object ZIP library

See `android/ASSET_LIBRARY_079.md` for the separate model library and device QA.
New catalogue tabs: **Дома**, **Деревья**, **Крепость**, **Декорации**.
The original built-in ready-made houses and existing scene format are unchanged.
Install a ZIP containing separate GLB or glTF models, then pick and place one
asset at a time. All original source textures are kept byte-identical; the
GPU preview may reduce texture resolution according to the existing quality
setting. The large asset ZIPs are user-imported, not embedded into the APK,
so baseline app installation remains small.

## Persistence

- Scene JSON remains **version 1**, with optional `roads` array.
- Old scenes without `roads` import as before. Scenes without `roadSidewalks` / `roadCrosswalks` default to enabled.
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

- Up to 24 roads; 48 control points per road; width 2–18 metres; five surface materials including asphalt.
- The engine still displays its old built-in **demo roads** when enabled; custom road splines are separate and editable. Demo road geometry is *not* converted into editable paths.
- Intersecting roads have width-aware convex asphalt intersection surfaces with clipped lane markings; production curb-radius engineering and traffic-grade lanes are not implemented.
- Traffic rules/lanes, accessible curb ramps, bridges, tunnels, vehicles, physics collisions, and NPC pathfinding are **not** implemented.
- JNI/GLES interaction stays on the GL thread. No continuous frame loop when the app is idle.
- Device testing is required, especially on Allwinner A523/4 GB RAM.

## Required QA before merge

- Draw tap-based and freehand roads at different zoom levels and on sculpted hills.
- Edit a middle control point; change width and line markings; Undo and Redo each.
- Save locally, reload, and export/import `.oezip`; inspect world size and original GLB textures.
- Load a pre-0.7.4 project; then test malformed JSON roads and verify no partial replacement.
- Shrink the world with roads near its edge (must reject when needed).
- Test X, T, 3-way junctions, exact collinear endpoints, and roads running close but not intersecting. Verify that lines stop across junction patches.
- Verify both road sidewalks and raised curb faces follow sculpted hills without covering the vehicle approach.
- Verify zebra stripes appear ahead of X/T road mouths, and no crossings float beyond a road end, on neighbouring intersections, or outside the map.
- Turn sidewalks and crossings off independently, Undo/Redo, save/load local JSON, and export/import `.oezip`. Test old scene v1 files with settings omitted.
- Test a gap <3 metres before/after completing a road, and a gap >3 metres (must remain separate).
- Drag an endpoint toward another road; Undo and Redo the snap; remove one of the connected roads; save/reload the graph.
- Verify selection and gizmo manipulation after exit, frame time, memory, EGL loss and orientation changes.
- Run `tests/road_model_smoke.cpp`, `tests/road_network_smoke.cpp`, `tests/road_junction_geometry_smoke.cpp`, `tests/road_pedestrian_geometry_smoke.cpp`, `tests/road_materials_smoke.cpp` and the GitHub Android APK workflow.

**Never merge or release without reviewing CI and testing on device.**
