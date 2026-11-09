# OE 0.7.9 — Medieval object library (experimental PR #20)

## Goal

Build a playable *realistic medieval* world with a touch of dark fantasy: houses
stay whole, while trees, battlements, towers, woodwork, walls and decorations
are individually selectable, placed and saved as independent editor entities.

The Android test app keeps its original package isolation from OE main.

## How to use

1. Install **OE 0.7.9 ASSET LIBRARY TEST**.
2. Open **☰ Каталог → Деревья / Дома / Крепость / Декорации**.
3. Select **📦 Добавить набор моделей ZIP** and choose one of the supplied
   ZIPs, e.g. `kenney_builtin_105.zip`,
   `OE_Trees_28_Separate_Lightweight.zip`, or
   `OE_Medieval_Trees_Separate_GLTF.zip`.
4. Select **📚 Выбрать отдельную модель из ZIP**, tap a named object and
   place it on the terrain. Move, rotate, scale, duplicate and delete it like
   an ordinary imported GLB. The original tavern, smithy and auction-house
   models are unchanged.
5. Save your world or export `.oezip` to back up placed models.

## What is implemented

- Persistent compressed **ZIP catalog** stored privately in
  `files/medieval-libraries`. The original compressed pack is kept intact
  without unpacking its complete contents. No network required.
- Indexed model chooser for `*.glb` and `*.gltf` inside ZIP, grouped into
  **Trees, Houses, Fortress, Decorations**. All 105 Kenney models and all 28
  separated tree entries can be independently selected from their ZIP files.
- **Lazy loading:** only the chosen model is converted/decoded and uploaded to
  GPU. Content-addressed `oe_<sha>.glb` is saved in the existing models
  folder, so selected objects are exported with the usual OE project archive.
  Multiple instances share one GPU resource.
- For **shared-texture tree glTF archives**, resolve its companion .bin and
  ../textures files safely inside the same ZIP; pack them as a single GLB
  preserving the exact original PNG/JPG encoded bytes (no downscale or
  recompression). For ordinary GLB archives the GLB bytes are preserved.
- **GPU model-cache cap: 32 distinct resources**, increased from 12. Unused
  resources can be evicted only when no editor entity or Undo/Redo snapshot
  refers to them. Existing texture and geometry safety limits still apply.
- ZIP limits: 190 MiB compressed, 550 MiB expanded, 650 filenames, 250 model
  entries, 24 MiB for any single model and eight installed libraries; directory
  traversal, duplicate filenames and oversized resources are refused.
- Existing 3D-import path, built-in houses, project JSON and scene data schema
  are unchanged.

## Important limitations

- **The large third-party ZIP assets are NOT bundled into the APK.**
  This keeps the test APK small. Import the ZIP once on the tablet;
  then its models appear in the on-device catalogue without extracting all
  texture files into app storage.
- **More entries than resident model slots** can appear in the list: 105 or 28
  *selectable* does not mean all can be rendered simultaneously. Up to 32
  **distinct** resident GLBs remain subject to a 96 MiB retained texture cache,
  a 40 MiB LOD cache and 90k-triangle per-model limit. Copies of one tree share
  the same GPU asset.
- Some original large tree models exceed 90k triangles. Their entries are
  visible but OE reports a clear error when one is too dense. Use a separate
  engine-ready decimated export for full coverage.
- A ZIP containing only FBX/Blend/OBJ cannot be imported as a catalog: convert
  it to separate GLB/glTF first. Existing whole buildings are not broken
  into doors/roof pieces unless they have independently exported objects.
- The renderer is still a **static** imported GLB viewer. Rig animation,
  alpha sorting, complex PBR, instance streaming and actual in-game collision
  are future work. Real Android tablet testing is still required.

## Device QA

- Import all 105 Kenney models and inspect categories, including fortress walls,
  tower, shrubs, roof modules and crates.
- Import the separate-tree ZIP; inspect 28 independent choices. Compare bark
  and leaf images with the original atlas; no pixel data changes.
- Select and place 2 copies of the same tree, duplicate, move and rotate.
  Then add one of the unchanged original built-in houses.
- Reopen the scene and export/import `.oezip` with placed assets; check
  every stored content-addressed model appears.
- Test insufficient storage, invalid ZIPs, unsafe ../ archive paths,
  wrong GLB header, large models and 32-slot boundary.
- Test memory and frame time with repeated instances and 4 GiB Android tablet.
- **Do not merge this draft into OE main without on-device QA.**
