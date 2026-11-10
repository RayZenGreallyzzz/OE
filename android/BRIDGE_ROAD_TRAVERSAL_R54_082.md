# Orvetra Engine R5.4 — Bridge-to-road traversal & walk surface data (experimental)

Branch: `test/bridge-road-navigation-r54-082` (stacks on lake editor R5.3).
Previous source snapshot: `backup/oe-r53-lakes-2026-11-10` at `f5551d3ed5105c934141d8919aeeb5b415a26c42`.
Test Android app: `game.oe.editor.bridgeroads`, arm64 GLES3, version `0.8.2-bridge-road-navigation-r54-test`. Do **not** merge into OE `main` or touch the unrelated ORVETHRA game. This milestone also does NOT change Phoenix Builder.

## Exactly what R5.4 delivers

- Portable bounded C++17 `roads/traversal_graph.{h,cpp}` derives a **read-only navigation-ready graph and simplified horizontal/terrain-following walk-surface quads** from the existing R5.1 road topology and the R5.2b medieval bridge entities. This changes no persisted scene format; legacy v1–v7 projects keep their roads, entities, lakes, materials and .oezip structure.
- Every graph road edge produces one narrow collision/traversal QUAD sampled from its terrain at all four corners, at the familiar visual road lift of 0.14 m. This is CPU metadata, **not** a registered physics-engine collision shape yet.
- Six continuous bridge types (stone 17, timber 18, narrow footbridge 20, rope 21, wide cart bridge 27, swamp causeway 28) provide one flat walking-deck quad and a bridge graph edge. **Stepping stones (type 22)** are not declared a continuous walking surface; foundations (19,23–26) are not bridges. Railings, bridge support piers and building walls have no collision data yet.
- Each bridge end can attach only to a REAL dead-end node of a nearby road, ≤2.5 metres away horizontally, at walking-level difference ≤0.65 metres, and with a street tangent pointing towards the bridge (not a street passing sideways). Both ends must connect to **different nodes** for a complete two-bank link. A steep bank is **not silently teleported**. The graph describes how a future controller could cross the bridge; it does not move a character today.
- `Каталог → Мосты → 👣 Проверить проходы по мостам` (also in `Дороги`) shows road sections, bridges with two/one/zero attached banks, road/bridge walk quads and graph edge counts.
- `Каталог → Мосты → 🧲 Подогнать выбранный мост к дорогам`: select an installed bridge and ensure two nearby road dead ends approach it. After confirmation OE proposes a corrected centre X/Z, rotation and Y level from those ends, with a one-step Undo. This does not move or split roads, modify the terrain/lake, resize the bridge, create invisible ramps, or save new graph objects. If both ends are unsuitable, nothing changes.
- Correct bridge yaw conversion: editor scenes store **degrees** and their Y-axis rotation moves local +X towards world -Z; the portable graph uses **radians** with XZ +Z convention. This was explicitly accounted for rather than treating editor yaw as radians.

## Constraints / honesty

The graph is derived on demand, not cached permanently or serialized. Maximum 120 bridges per calculation, up to 2400 road edges, 3000 resulting surface quads (<=4 corners each), and at most 6 continuous bridge shape kinds. More content or invalid non-finite meshes fail atomically without mutating the existing result. Road centreline topology limits from R5.1 still apply.

**This milestone is not full game collision, autonomous NPC pathfinding, character/controller movement, dynamic bridge geometry, terrain carving, automatic road meshes extending up onto the bridge deck, bridge foundations, rope simulation, reflections, or current-game APK export.** It is a deterministic bridge/road walkable-surface adapter to be consumed by OE Runtime in later milestones. The rendered model/road seam may still need manual visual polishing: navigation graph connectivity does not automatically change the 3D terrain shape.

For height alignment, the two nearest banks must be close in elevation (difference ≤1.2 metres) and near bridge endpoints; it is intentionally rejected when slopes require physical ramps. A bank road's end must point out of the bridge, not along its side. Road IDs, bridge entity IDs, existing Undo/Redo and scene JSON are unchanged. These safety checks help avoid inventing walkable links through a lake or cliff.

## QA

Host tests `tests/bridge_traversal_smoke.cpp` verify:
- two distinct road dead ends attach to an aligned stone bridge, including the two approach edges and a bridge edge;
- initial unaligned deck must NOT connect despite perfect horizontal centre;
- safe suggested Y, XZ position and yaw from endpoints; remote placement suggestion must not move anything;
- one-bank and fully disconnected cases;
- side-passing streets and rotated bridges are NOT entrances;
- unsafe vertical drop is not traversable; steep two-bank slope cannot be auto-aligned;
- stepping-stone ford does NOT pretend to be a continuous slab;
- each of the five additional supported bridges produces exactly one deck quad;
- invalid input, duplicate entity IDs, too many instances, and NaN heights leave output unchanged.

`tests/bridge_editor_integration_smoke.py` covers CMake/JNI/Java wiring, editor DEGREES → graph RADIANS conversion and inverse, diagnostics, and Undo integration.

**Real Allwinner A523 / 4 GiB tablet QA is still required** even if GitHub Actions and the arm64 editor APK build pass. Make a backup of your city using `.oezip` from the tablet before installing new test builds. For the first test, create two roads that end near opposite mouths of a stone bridge. Select the bridge (tap or object list), use `🧲 Подогнать`, then check `👣 Проверить`. Move/rotate the bridge or raise one bank excessively: the graph should show a lost connection. Save and reload; since graph is recomputed from scene entities, connections should reappear. Check Undo, PNG plan overlays, lakes and layered ground materials.

## Next practical steps

R5.5 will be **runtime integration** of these collision quads plus real elevation-compatible road/bridge visual joints, collision underfoot and safe platform transitions. Afterwards OE Runtime Play/Stop, touch controller, terrain/water collision, NPC routefinding and game logic, then a real standalone offline Android game APK exporter **built on the tablet without a PC** remain the agreed product plan. Do not turn this into an editor-only dead end.
