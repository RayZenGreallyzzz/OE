# OE 0.7.9–0.7.10 — Medieval object library (experimental PR #20)

## Goal

Build a playable *realistic medieval* world with a touch of dark fantasy: houses
stay whole, while trees, battlements, towers, woodwork, walls and decorations
are individually selectable, placed and saved as independent editor entities.

The Android test app keeps its original package isolation from OE main.

## 0.7.10 — Kenney assets are truly BUILT INTO the APK

The previous 0.7.9 APK only included an empty ZIP importer and depended on a
user adding a library manually. 0.7.10 corrects that mistake:

- Android CI downloads the official *Kenney Retro Fantasy Kit 2.0* archive
  and verifies its exact SHA-256 before shipping it:
  `d3462b453018d0d150406d85af3c4ae1322ceef8f1754c4eb4e6355adfeaab0f`.
- The build extracts and embeds **105 individual GLBs**, their **10 original
  PNG textures**, and the **CC0 licence** into the APK as a compact ZIP asset.
  Every GLB and PNG is copied byte-for-byte from the verified original pack.
- On first opening the library, a background worker copies this one compressed
  archive to the app's private library index, without placing any models or
  allocating GL resources. Thereafter, open **Дома / Деревья / Крепость /
  Декорации → 📚 Встроенные модели + свои ZIP**. No file picker, Wi-Fi or
  extra ZIP download is needed for these 105 built-in models.
- Original Kenney GLBs point at **external** sibling `Textures/*.png` files.
  On selecting an object, the importer safely resolves those references
  inside the bundled ZIP and packages the **unaltered PNG image bytes** into
  the selected GLB. Without this step many models would appear untextured.
- **105 catalogue entries is not 105 GPU objects loaded at launch.** Only
  individually selected/used models are decoded into the renderer. At most
  32 different GLB resources can be resident, subject to memory limits.
  Duplicate scene instances share resource data.
- The **two Kenney plant models** `tree-large.glb` and `tree-shrub.glb`
  are included among the 105. The separate realistic **28-tree atlas is
  still an optional external ZIP**; bundling its 19 MiB in the APK requires
  an additional decision and independent asset-rights/format testing.
- This version is isolated and unmerged. Test model previews/textures,
  save/restore, APK size and 4 GiB tablet memory before release.

## How to use

1. Install **OE 0.7.10 BUILTIN MEDIEVAL ASSETS TEST**.
2. Open **☰ Каталог → Деревья / Дома / Крепость / Декорации**.
3. **105 Kenney objects are already included**. For *additional* sets only, select **📦 Добавить набор моделей ZIP** and choose one of the supplied
   ZIPs, e.g. `kenney_builtin_105.zip`,
   `OE_Trees_28_Separate_Lightweight.zip`, or
   `OE_Medieval_Trees_Separate_GLTF.zip`.
4. Select **📚 Встроенные модели + свои ZIP**, tap a named object and
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

- **105 small Kenney GLBs and their original textures ARE bundled into APK.**
  The 28 large realistic tree models remain separately importable in ZIP form;
  they are not bundled at this stage. Bundled Kenney models appear offline.
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
