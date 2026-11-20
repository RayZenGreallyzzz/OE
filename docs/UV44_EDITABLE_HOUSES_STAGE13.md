# Orvetra Engine Stage 13 — UV44 editable houses in one city

This work follows Stage12 / PR #51. The original **Phoenix Builder UV44**
scripts, visuals, models, character and drone were inspected directly from the
owner-provided `Phoenix_Builder_UV44_Install(2).apk`; **they are not committed
to public GitHub or modified**.

## Original UV44 source contract verified

- `assets/js/clean-app.js` implements `project()` (version 2 with objects,
  floor, textures and catalog references), `exportBackup()` and `exportGLB()`.
- `exportBackup()` uses `PhoenixFiles.savePortableProject()` and writes a
  self-contained `PhoenixBuilderBackupV1` file through the
  `NativeBridge.beginSave/appendSaveChunk/finishSave` chunk pipeline.
- `CleanBuilder.importBackup(File)` uses the original `ProjectOpen.read()`
  and `PhoenixFiles.unpackProject()` and prompts before replacing its local
  construction canvas. **No rewrite of stable UV44 JavaScript.**

## End-to-end Stage13 workflow

1. In OE, use **Здания → Phoenix Builder UV44** and build a small house.
2. In original UV44, choose **Сохранить проект (.phoenix.json)** to save the
   *portable editable* backup, and **then 📦 GLB** to export matching static
   render geometry. The order matters; stage13 clears the old GLB indicator
   when saving the backup.
3. Tap **🏠 В город**. On a bounded I/O worker, OE preserves that original
   portable backup in the private content-addressed `uv44_editable_houses/`
   folder and records its relationship to the canonical native OE GLB key.
4. Place the imported GLB in the same OE world (terrain, roads, lakes, city,
   collision walls, plans etc. remain intact).
5. Select that building in OE and choose
   **Здания → ✏ Редактировать выделенный дом UV44**.
6. The same internal UV44 building Activity opens. Tap **✏ Дом OE** to fetch
   that building's original portable source via the private local-origin
   WebResourceResponse route. The original
   `window.PhoenixClean.importBackup(File)` restores it, including textures.
7. Edit it, save portable project again, export GLB again, tap **🏠 В город**.
   OE validates and registers the new mesh, then native
   `nativeReplaceUv44House(entityId, oldKey, newKey)` updates **ONLY that
   one selected entity**, retaining X/Y/Z, rotation and scale in the shared
   R5.7 city. The edit is captured in the original Undo history.
8. Local saved scenes and exported .oezip use the *optional* `uv44Houses`
   editor metadata in **v13** only for linked houses. Other scenes retain
   their previous versions v1–v12. Original editable backups are separately
   stored in `builders/house_<sha24>.phoenix.json`, SHA validated during
   ZIP export/import and copied from a bounded staging dir. No binary
   user-private sources or large assets are in `scene.json`.

## Limits, honest status and user data protection

- This is **still a stable UV44 WebView editor inside ONE OE Android APK**
  with temporary GPU handover, not yet a C++ port of builder geometry and not
  yet a shared UV44/OE walking or drone controller.
- First stage13 archive cap: **3 distinct editable house sources per .oezip**,
  **96 MiB** max per source, **384 MiB** total unpacked archive. Local UV44
  library supports up to **8 linked models**.
- GLB limit remains **24 MiB / 90k imported triangles**, so start with a small
  test house and UV44 export texture quality 1024.
- Changing an original building's mesh can change the author's local pivot;
  OE preserves instance transforms, but visual placement/scale must still be
  verified on the user's Android tablet.
- Physically collidable terrain/barriers remain native OE; imported GLB mesh
  is STATIC rendered content, not automatic gameplay mesh colliders. We have
  not unified UV44/ OE physics yet.
- On tablet always test .oezip **export and import** BEFORE deleting any
  installed test APK. Do not uninstall the old builder or lose original
  Phoenix backup files until actual roundtrip is confirmed.
- If the owner chooses **«Передать только GLB»**, they are warned it is NOT
  editable; no false source pairing is created.
- Existing source library/model references are never published. Original
  building backups survive as private app data and inside exported v13 .oezip.

## Device QA checklist

Create UV44 house → save portable .phoenix.json → export GLB → place in OE →
save local OE → select that GLB → enter Builder → open «Дом OE» → edit window →
save portable again → export GLB → return; verify same position, one entity,
Undo works; export .oezip; load into a second fresh Stage13 install; verify
original source is still available for reediting. Check slow/large textures,
Android back navigation, file picker and GPU context restoration.

Do not merge into main or modify foundational PR #31 until the owner's tablet
testing confirms the original UV44 construction tools are stable.
