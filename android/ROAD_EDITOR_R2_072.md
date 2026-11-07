# OE Road Editor R2 — touch-based road geometry TEST

Branch `test/road-editor-r2-072` based on `test/road-terrain-foundation-071` / PR #15.
Android package: `game.oe.editor.roadeditor` (debug). Installs separately from OE 0.7.0.
**Experimental**: neither OE `main` nor ORVETHRA is changed.

## What R2 actually implements
- New `☰ Каталог → Дороги` category. **Новая дорога**: first touch sets the starting point, second creates the first road section, further taps add connected sections. Two-finger pan/zoom works while placing points, and single-finger drag still orbits the camera.
- **Завершить** ends the current drawing gesture; **Выбрать дорогу** lists saved roads, and **Продолжить выбранную** appends more segments.
- Width dialog supports 0.25–32 metres. `Покрытие` offers four **color preview** materials (stone/dirt/sand/grass), not textured road assets yet.
- Undo/redo snapshots include roads; last point and road deletion can be undone.
- Portable C++ `Road Network` keeps durable identifiers, width, logical material and ordered control points. Its terrain ribbon mesh is converted into one GLES3 vertex buffer shared by road segments, refreshed after geometry or terrain changes. Up to 120k generated road vertices, validated before edit/load. Selected road points have visible markers.
- The generated geometry is **not** a collider, navmesh, autonomous intersection, or baked terrain paint. It follows the editor heightfield with a small elevation offset.
- Save/local load and .oezip project export/import now use **scene JSON v2** with a `roads` array; reading old scene v1 sets an empty road network. All new roads and other scene data validate before the native atomic swap. Saving a scene in v2 prevents older v1-only editors from silently loading and discarding road records. Always keep .oezip backups.

## Tablet smoke test
1. Install Road Editor R2 test APK **alongside** OE Game Runtime Core Test. Export an existing .oezip backup first.
2. Open `☰ Каталог → Дороги → 🛣 Новая дорога`. Tap two places on visible terrain (>=1 cm apart); stone-colored ribbon appears. Add 3–4 taps to form bends. Use two fingers to reposition the camera.
3. Open `Дороги → ↔ Ширина`, choose 2.5m then 8m, verify ribbon changes. Choose **Грунт** and verify preview color changes.
4. Choose `Завершить дорогу`, then `Выбрать дорогу`, then `Продолжить выбранную` and add a point. Test removing last point, Undo, Redo, and deleting a selected road.
5. Sculpt a nearby hill to verify the road mesh conforms to new terrain elevations on redraw.
6. Save locally, reopen the same build, load, compare road points/material/width. Export to .oezip, reset to empty map and re-import; verify all roads are restored. Try an old v1 .oezip, which should load with **zero roads** and unchanged legacy entities.
7. Test a deliberately malformed v2 road array in a disposable .oezip and confirm the old scene is preserved. Test switching Economy/High quality and EGL pause/resume.
8. Test on target Allwinner A523 / 4 GiB Android tablet; record actual heat, responsiveness, draw calls and screenshots. CI success alone is not device QA.

## Known limitations
- PNG map reference overlay, precise freehand brush, splatmaps, **actual photo/PNG cobblestone texture**, animated preview, intersections, spline smoothing, pavement sidewalks, terrain flattening, collision physics, nav graph and standalone game export are **NOT** implemented.
- Each tap adds one straight centerline segment; tight acute turns can have bevel/miter artifacts and overlapping intersections are not stitched.
- Scene JSON v2 is a new experimental format. v1 builds cannot read it. Other experimental test packages keep separate private storage; use the explicit .oezip export to move scenes.
- The lightweight GPU mesh is rebuilt only on road/terrain edits; markers are capped to ~64 for selected roads, and a conservative vertex budget prevents accidentally building excessively dense road networks.
- A road is authored scene data: the current Game Runtime Core probe copies old editor entities only and does **not** yet transfer roads to the playable world.

## Next features after R2
R3: optional transparent PNG map overlay with metres-per-pixel calibration, top-down view and opacity slider; offline reference storage.
R4: offset cursor and continuous painted terrain weight masks, separately chunked and memory bounded.
R5: road network junctions/sidewalks, physics surface generation, navigation graph adapters and chunk streaming.

Do not merge this draft until **tablet QA** and explicit approval.
