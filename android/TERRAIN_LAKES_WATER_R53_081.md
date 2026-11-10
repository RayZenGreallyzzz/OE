# Orvetra Engine R5.3 — Terrain + Lake Water (experimental)

**Branch:** `test/terrain-lakes-water-r53-081` on R5.2b. **Separate debug app:** `game.oe.editor.terrainlakes`, GLES3 arm64, `0.8.1-terrain-lakes-water-r53-test`.
**Safety:** GitHub backup of complete preceding OE code at `backup/oe-medieval-r52b-2026-11-10`; previous R5.2a snapshot remains `backup/oe-medieval-r52a-2026-11-09`. Do not modify OE `main` or the unrelated ORVETHRA game. Draft PR only, never merge without approval.

## Terrain and lakes: actual editor workflow

The existing `Каталог → Рельеф` tool already allows lowering/raising terrain, configurable deformation radius/height (64 max strokes). Water surfaces **do not replace or modify terrain**.

New `Каталог → Вода`:
1. **`⛏ Вырыть чашу для озера`** selects the lower-terrain brush (64 m radius, 12 m depth). Touch ground to create a basin, repeat as needed. Existing `Рельеф` brush settings remain usable.
2. **`📏 Радиус и глубина заполнения`** chooses water surface radius **16/32/64/96/128/160 m** and target water elevation **2/4/8/12/20/30 m above the centre of the basin** at placement time.
3. **`🌊 Залить озеро касанием`** lets you touch the lowest part of the basin. The editor creates a water body with an independently editable constant height and a circular maximum domain. Water is **clipped by sampled terrain**, so it appears only where the lake surface is above the landscape, up to its maximum radius. The actual shoreline approximates the basin geometry, not a solid circle painted over hills.
4. **`📋 Выбрать существующее озеро`** selects any lake from a labelled list (position, water height and radius). **`⬆/⬇`** raises/lowers water **1 m**; **`↔`** extends/reduces radius **8 m** per press. **`🗑 Удалить выбранное озеро`** removes water while keeping all terrain edits.
5. **`↶ Undo / ↷ Redo`** include lake creation, edits and deletion. The terrain height brush and lake water continue to be independent. Background/foreground EGL context recreation invalidates the disposable GPU buffers, but keeps the water records.

## What the renderer really does

- Portable bounded C++17 `water/lake_surface.{h,cpp}` computes a radial triangulated lake at a **flat constant water level** and clips each triangle against the ground sampled at its vertices, interpolating to approximate shoreline intersections. Water outside the selected domain or below ground is not drawn. Rivers and noncircular editable shore paths are NOT implemented yet.
- GLES3 draws all currently stored lakes as **one cached transparent GPU mesh**, with stationary blue-green variation and 77% alpha, against the existing terrain, roads and medieval assets. No dynamic water simulation, reflection/refraction, underwater physics, wave textures, new shader program, particle system or network asset download.
- Up to **8 lakes**, radius **8–160 m**, surface level **−24 to +160 m**, entire circular domain inside the current world; total scene water triangles capped at **50,000**. Caches update when terrain or lakes change, not every frame. This limits RAM/GPU load on Allwinner A523 (4 GiB RAM).
- Lake centre is obtained by the existing terrain-touch ray test. Place shallow lakes after excavating the basin. A lake submerged below ground is valid but *invisible until you raise its level*.
- The water is visually translucent, but **not a physics collider or buoyancy volume**; roads and bridges are not automatically connected to shorelines.

## Storage, compatibility and safety

- New lakes are serialized in **scene v7** as `"lakes": [[centerX, centerZ, radius, waterLevel], ...]`.
- When there are no lakes, export removes the empty lake array and preserves the previously appropriate scene v2–v6. New object kinds 17–28 remain supported in v6/v7.
- ProjectArchive preflights v7 and the lake limits. Java validates each record and the C++ **nativeReplaceScene** stages all lake data before atomically committing a new project, alongside models, roads, ground paint and material layers. Invalid records must not overwrite the open city.
- `.oezip` archives now contain the same `scene.json` plus existing assets, PNG map reference, terrain deformation and paint layers. A city containing lakes is **not readable by older OE versions** without a v7 reader. Export backup copies before migrating.
- The GitHub source snapshot is NOT a backup of user-created private tablet cities. Back up city projects from the tablet via `.oezip` and copy the file to a separate location.

## QA — actual Allwinner A523 tablet not yet tested

1. Backup project `.oezip`; install R5.3 separately. Open `Каталог → Рельеф` and lower ground; use `Вода → Вырыть чашу` as a shortcut.
2. Set lake radius 64 m, fill +8 m, tap centre. Zoom around shore: only submerged places should be water coloured; hills must remain above it.
3. Use pond 16 m and lake 160 m; check quality/FPS, edge seams and camera navigation.
4. Select a lake in list, change level ±1 m and radius ±8 m. Observe the new shoreline. Delete lake and verify depression remains; Undo restores the water surface.
5. Create a scene containing R5.2b medieval bridges, roads, grass/cobblestone material layers, a PNG city plan and lakes. Export/import a v7 `.oezip`; compare water positions/levels and other assets.
6. Try a malformed or oversized scene: loader must reject it, preserving the current editor. Reopen old v1–v6 projects and verify no phantom lakes.
7. Sleep/background/restore tablet: water surface should return after EGL recreation.

### Roadmap after water

This milestone was prioritised at the user's request while the road collision/nav task was still pending. **Next is R5.4 road-to-bridge endpoints, navigation metadata and simplified collision surfaces**, then real Play/Stop, character/physics, game logic and an **offline standalone game APK builder inside OE on the tablet**. No modern concrete curbs, asphalt or traffic lights.
