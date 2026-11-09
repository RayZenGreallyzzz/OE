# OE Road Junction Editor R5 — experimental release

Branch: `test/road-junction-editor-r5-076`, based on `test/terrain-material-layers-r41-075`.
Separate Android package: `game.oe.editor.roadjunctions`.
**Do not merge** into `main` or modify ORVETHRA. Device testing and user approval are required.

## Why this milestone

OE R4.1 already supports independent material masks, PNG city plans and authored road ribbons, but the roads consist of sharp piecewise linear points. R5 provides a practical street layout workflow without mixing the Android editing UI into the portable game Runtime Core.

## Implemented

- **Editable control points**: from `☰ Каталог → Дороги`, select an existing road and choose `📍 Переместить точку касанием` or `⌨ Точные координаты точки`. A dialog lists X/Z coordinates. Long roads offer direct point number entry. Touch relocation uses the same terrain ray hit as road placement; numeric relocation uses exact metres. Invalid edits (outside world, sub-centimetre duplicate segment, excessive geometry) are refused without changing the road.
- **Endpoint snapping**: when adding or relocating an endpoint, OE snaps it to a DIFFERENT road's **existing endpoint within 2 metres**. This is limited to endpoint-to-endpoint snapping, not dragging onto any arbitrary segment; the latter requires future junction topology and segment splitting.
- **Editable curve smoothing**: `〰 Сгладить повороты` replaces sharp interior corners with bounded quadratic Bezier fillets generated as **new control points**, with selectable 1, 2, 4 or 8 m cut radius. First/last points, material, width and stable road ID are preserved. Existing version 5 scene format stores the result as ordinary ordered control points. Repeated smoothing inserts points again; use Undo to compare. Very sharp U-turns or over-budget results fail safely.
- **Intersection detection and visual preview**: independent C++17 `roads/road_junctions.h/.cpp` locates centreline line-segment crossings between distinct roads (including X/T and coincident endpoints) and derives small terrain-following disc patches at the intersections. The patches are appended to existing dynamic GLES3 road buffers; no scene-format migration required. Max **96 detected junction patches**, **1800 segments** scanned (disabled if exceeded); mesh growth respects the existing road GPU vertex budget.
- **Visual control point handles**: selected road's points remain visible; the selected point highlights green. Undo/Redo works because edits are recorded in OE's bounded scene history. New intersections are recalculated only when road/terrain changes.
- **Project compatibility**: v1–v5 scene import, v5 terrain masks, PNG map reference and `.oezip` serialization are preserved; roads keep their durable IDs. No change to the scene or Runtime Core data schema.
- Host unit tests cover endpoint snap, X/T preview, curved fillets, abnormal input, maximum complexity and non-mutation on failure.

## Explicitly not implemented

- The intersection patches are **visual**; they are not a watertight stitched road mesh, proper curved kerbs, sidewalks, multi-road lane topology, elevation-separated bridges, vehicle rules, collider, navigation graph or playable Runtime behavior.
- A road crossing another road at its middle does **not** automatically split the existing road into graph nodes yet. Graph connectivity should not be inferred from visual overlap for future NPC navigation.
- Automatic detection intentionally excludes parallel or overlapping collinear roads. In a crowded map exceeding limits the editor still draws all base roads but junction decoration is disabled.
- Full point dragging with finger following the control point continuously is NOT present: user selects a point then taps a new position. This avoids accidental camera drags on a mobile tablet.
- No device profiling yet. GLES3 performance, transparent overlap and thermal stability on a 4 GB Allwinner A523 tablet still need testing.

## Device QA steps

1. Export a backup of the R4.1 project before testing. Install R5 APK as a separate application, import the backup `.oezip`.
2. In `Дороги`, select an existing road. Open `📍 Переместить точку касанием`, select a point then tap a new location. Confirm the green handle and road ribbon move, Undo/Redo restore positions.
3. Try `⌨ Точные координаты точки`, change X and Z by a few metres. Verify decimal precision and rejection of invalid or duplicate points.
4. Create two perpendicular roads forming an X, and another terminating at an existing road endpoint. Inspect coloured intersection centre patches; `✣ Перекрёстки · информация` reports intersection count. Compare top-down PNG-guide view and perspective.
5. Select a sharply bent road and apply smoothing 2 m then Undo. Verify the control points and road rendering change predictably, and save/reload the project and export/import `.oezip`.
6. Verify saved R4.1 material masks, PNG guide, imported GLB, the previous R4 composite brush, v1/v2/v3/v4 scene import, and crash-free app background/resume (EGL context recreation).
7. Check heavy street grids, crossing count limit, CPU-submit counters, screenshots, memory and thermal behaviour; adjust limits only after physical testing.

## Next steps

R5.1: segment splitting and canonical topology nodes so that intersecting lines connect as an actual editable street graph. R5.2: stitched junction mesh and sidewalks, material blending and lane edge geometry. Later: collision shapes / physics and navigation adapters in dedicated Runtime systems.

**Do not represent a passing CI/Android compile as physical tablet QA.**
