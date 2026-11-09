# OE 0.7.11 — FULL MEDIEVAL EDITOR (isolated paid-asset test)

## Installable APK supplied separately

The **81.56 MiB** signed test APK for `game.oe.editor.medieval011`
is provided to the owner through the ChatGPT conversation along with the
`OE_Medieval_Editor_145_Original_Textures.zip` importable asset backup.

**These purchased model archives are NOT committed to GitHub or downloadable
through public CI.** The CI artifact is a code-only base APK. For the complete
APK, the verified source pack is inserted into the base APK at
`assets/medieval/oe_medieval_editor_addon.zip` and the APK is signed with a
fresh test-only Android v2 key. It has its own applicationId to avoid modifying
the user's existing installed OE app. A plain GitHub Actions APK does NOT
contain these paid packs.

## What's actually in the final, externally delivered APK

- Original embedded **Kenney** 105 distinct GLBs and 10 original PNGs
  (baseline bundled independently).
- **116 unique medieval environment FBX geometries** converted from the
  original user-provided FBX to independent static glTF models, retaining
  original UVs and material grouping. A single prototype is exported
  for each repeated source geometry, avoiding 10,000 identical instances.
- **28 separately importable trees** from the engine-ready tree ZIP, with
  all the original JPEG/PNG bytes unchanged. The meshes retain the earlier
  engine-ready geometry reduction; they are NOT original polygon-for-polygon.
- Original **stylized modular medieval building pack GLB** included
  byte-for-byte, fully intact, as a standalone large selectable asset. It is
  a combined scene in the source; it is not split into individual original
  houses. The FBX house variants are separately selectable.
- All **4 original environment PNGs** including the ~32MiB Ground.png are
  preserved byte-for-byte. Original building GLB contains its original
  16 embedded PNGs, unchanged.
- **No Medieval Wood**; user expressly excluded it.

## Editor integration

AssetLibrary first seeds Kenney, then searches APK resources for
`medieval/oe_medieval_editor_addon.zip`. The extra bundle is copied,
validated and indexed once, without extracting 100+ meshes and textures to
GPU. The initial model chooser includes
`Дома`, `Деревья`, `Крепость`, `Декорации`, `Окружение`.

An object is decoded when selected, not when browsing. The normal
content-addressed GLB saves into the existing editor project / .oezip.
32 unique resident resources still bound GPU usage and active scene objects
remain protected during GPU eviction. Source originals are preserved on disk.

Expanded loader limits allow the owner's original large models and textures:
64 MiB GLB (previously 24 MiB), 40 MiB image (24 MiB), and up to 8 base
textures (previously 4), but the GPU texture preview still samples to
1024/2048 pixels according to the chosen quality setting. It does NOT
render every 4K texture at full resolution on a low-memory tablet.

## Validations performed

- Both GitHub Actions checks passed for the code-only base APK.
- Final APK contains the complete nested 145-model extra ZIP plus Kenney.
- All original PNG and JPG bytes match the user-provided source archives;
  the original 53-MiB building GLB is byte-identical.
- All 116 FBX exports have a valid glTF 2.0 JSON, external BIN and
  referenced source texture paths; sample RH5 house opened in trimesh.
- ZIP CRC and file counts verified; final Android APK v2 signature
  independently checked cryptographically against the digest algorithm
  used in the original CI-signed APK. Final APK has a distinct package name.
- Real tablet rendering and texture sampling is UNVERIFIED until user tests.

## Device QA (very important)

1. Install `OE_0.7.11_Full_Medieval_Editor_Test_145.apk` **without
   uninstalling OE 0.7.10** (different application ID).
2. Open the editor catalogue `Дома`. Tap
   `Встроенные модели + свои ZIP` and select `house-...RH5`, then place.
3. Repeat for another RH/BH house, `Крепость` tower, `Окружение`
   lamp/grass/rock, and `Деревья` T1, T5; rotate and duplicate objects.
4. Try the combined original building GLB; it is heavy and may fail on a
   low-memory device even though source bytes are preserved.
5. Check textured preview vs source. New import can reduce GPU preview
   to 1024/2048; **source PNG/JPG never rewritten**.
6. Save, reopen and export/import .oezip to confirm asset persistence.
7. If automatic seeding fails, use `Добавить набор моделей ZIP` and select
   the separately supplied 145-model bundle. Check free storage (>=300MB
   recommended) to avoid incomplete indexing.

**Do not merge draft PR #20 to main.**
