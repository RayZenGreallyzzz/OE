# OE R5.2a — Medieval Bridge and Foundation Modules (experimental)

**Branch:** `test/medieval-bridge-foundations-r53-079`, based on `test/medieval-junction-surfaces-r52-078`.
**Test app:** `game.oe.editor.bridgesfoundations` (ARM64 GLES3 Android).
**Do not modify or merge into OE `main`, and do not touch the separate ORVETHRA game.**

## Three lightweight editor-ready modules

OE already bakes multiple low-poly static pieces into **one draw call per instance**. This release reuses that system for new primitive object kinds and keeps three independent shared GPU meshes. Nothing is downloaded, no extra texture is required and the objects can be positioned in existing 3D/editor controls.

| Scene kind | Name | Approximate footprint | Description |
| :--- | :--- | :--- | :--- |
| 17 | Каменный мост | 8.5 × 3.7 m | Short old stone span with abutments, stepped stone supports, flat walkable-looking slab and low parapets |
| 18 | Деревянный мост | 8.4 × 2.9 m | Simple timber crossing with a dozen planks, horizontal beams, rails and posts |
| 19 | Каменный фундамент | 7.4 × 6.3 m | Low rough-stone rectangular pad with course-like edging and top at local y=+0.48 m |

The short bridge spans are open underneath; they do not include a physics collider or true parametric arched masonry. Each module uses **fewer than 40 pre-baked boxes** in a single shared static mesh; the existing object budget (maximum 300 objects) still applies.

### Direct editor controls

- `Каталог → Дороги → 🪨 Каменный мост · 8 м`: tap terrain to place. Rotate, move (including Y elevation), duplicate and scale with the existing gizmo/inspector. Bridges do NOT automatically cut riverbeds, attach to roads or place water.
- `Каталог → Дороги → 🪵 Деревянный мост · 8 м`: lightweight footbridge / packhorse crossing, placed in the same way.
- `Каталог → Здания → 🧱 Каменный фундамент · поставить` or `Каталог → Детали → 🧱 Фундамент 7×6 м`: place a module anywhere, including under imported GLB buildings after adjusting its size and position.
- `Каталог → Здания → ⬇ Фундамент под выбранное здание`: select a built-in tavern, forge or auction house, then tap this command. OE copies its X, Z, yaw, size and nonuniform scale to a new foundation; the foundation upper face aligns to the current building base, in one undoable action. The original building keeps its identity and coordinates. This is **not a live parent/child attachment**: moving the building later requires moving the foundation separately.

The bridge deck is placed around **1.5–1.8 m above the origin**; the module origin rests on the touched terrain. The operator may adjust Y to align banks or an existing road. Foundations have a local top y=+0.48 m, a partially buried lower portion and an extended edge beyond the stock building's 6×5 m body. There is no automatic terrain flattening.

### Scene archive compatibility

New module object kinds 17–19 use the unchanged item fields `type,x,z,y,yaw,size,sx,sy,sz,paint`, but require **scene version 6**. OE promotes a scene to v6 only when it includes at least one new module. Older modules stay in their existing scene versions. This version guard stops older editors from silently guessing the meanings of new object types.

ProjectArchive and Java/native loaders accept v1–v6, validate allowed object kinds and keep R4.1 weight masks, R3 PNG map guides, v4 paint tiles, original roads and `.oezip` export/import. Older OE editors **cannot open a v6 scene**: keep a backup and retain the separate previous APKs.

### Design limits and next milestones

- This milestone focuses on visual **editor modules**, not collision-ready, traversable bridge surfaces, supporting pillar foundations under arbitrarily shaped/rotated imported GLB assets, grouping or physical structural integrity.
- Only three finite predefined modules are available. This is not a modular procedural bridge generator: width/span changes use the standard object scale controls.
- Bridges do not automatically follow terrain, detect water, join road networks, or carve embankments. Stone arch silhouette is deliberately simple.
- No extra GPU textures, mesh downloads, shaders, lighting system, script engine, game Play/Stop or standalone on-tablet APK builder are introduced.
- **R5.3 remains road collision/nav-ready data** as in the agreed long-term roadmap, followed by gameplay Runtime and tablet-only APK game export.
- Real on-device QA on the Allwinner A523 tablet is pending even after successful CI builds.

### Tablet QA checklist

1. Export a safe backup `.oezip` from R5.2 before installing the new separate R5.2a test build.
2. In `Дороги`, place a stone bridge, a timber bridge; inspect from street-level and above, reposition their Y values to cross a shallow ditch and rotate across a curving path.
3. Select a built-in tavern in `Здания`, choose `⬇ Фундамент под выбранное здание`. Verify the pad aligns below the building and Undo/Redo restores both selection and objects. Try X/Y/Z scale and rotation.
4. Place a foundation separately from `Детали` and adjust scale under an imported GLB building. Verify it remains a separate object.
5. Save/reopen the scene, export/import `.oezip`, check types 17–19, coordinates, custom paint textures, roads and PNG guide. v6 projects must be rejected by older OE versions without damaging their active editor project.
6. Switch app to background and return to trigger an EGL context recreation; modules must reappear because their common meshes are regenerated.
7. Stress dozens of bridge/foundation modules on 4 GB RAM tablet; check GPU memory, draw calls, frame time and heat. Report any visible intersection, gap, floating foundation, selection or snap issue.

Full development priorities: `android/OE_DEVELOPMENT_ROADMAP.md`.
