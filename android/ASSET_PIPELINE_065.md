# OE 0.6.5 — Advanced Assets / Meshopt Foundation TEST

**Isolation:** package `game.oe.editor.assettest`, independent private storage and project data from main OE and other test packages. Draft branch `test/advanced-assets-065`; do not merge before real GLB test assets and on-device verification.

## Actually implemented

- Vendored **meshoptimizer v1.3** MIT-licensed decoder source is built into the native Android APK, and requires **no network connection** at runtime.
- Loads glTF2 GLB `EXT_meshopt_compression` and `KHR_meshopt_compression` packed `bufferViews`: ATTRIBUTES, TRIANGLES, INDICES modes; NONE/OCTAHEDRAL/QUATERNION/EXPONENTIAL/COLOR filters. Bounds checks before native allocation and decoded total limit 48 MiB, per bufferView 32 MiB. Standard 24 MiB GLB input limit remains. Shared decoded views are cached within each decode, not re-expanded for each accessor.
- Position accessors support float32, signed and unsigned 8/16-bit (quantized) with appropriate glTF normalized conversion.
- **Source files are immutable** and stored with a SHA-derived key; the portable `.oezip` export includes original GLB bytes. A rendered model uses decoded in-memory data, not a rewritten GLB.
- All OE 0.6.4 World Tools (project archive import/export, sculpting, detail editing, per-axis scale, existing GLB GPU cache) are retained.
- Host Meshopt codec round-trip test for attributes and both index modes is run in GitHub Actions; Android APK compilation runs separately.

## Not implemented: DO NOT CLAIM SUPPORT

- **Draco** `KHR_draco_mesh_compression` has no decoder in the APK; importer rejects such geometry.
- **KTX2** `KHR_texture_basisu` has no transcoder nor texture-enabled shader. Imported textured GLB static previews show flat material colors; never treat as a textured playable model.
- **Automatic LOD 0/1/2** is NOT implemented in OE 0.6.5; cannot infer LOD safety from codec integration.
- **Texture downsampling** to 1024/2048 px is NOT implemented.
- **Skeletons/animations** are not played or skinned; original bytes still retain all animation/skin data in project storage. No geometry optimizer is applied to these models.
- `.gltf` external assets, Draco-compressed, morph/sparse accessors, advanced materials, PBR and textured rendering are outside this stage.

## Manual verification

1. Import an ordinary small uncompressed GLB and confirm unchanged appearance from 0.6.4.
2. Import an `EXT_meshopt_compression` GLB with FLOAT32 POSITION, uint16 indices and attribute filter NONE; confirm it renders. Try quantized positions separately.
3. Reload the same model and confirm it is reused rather than reuploaded.
4. Export the project as `.oezip`, import in this isolated package and confirm original GLB is preserved exactly.
5. Try truncated/invalid Meshopt data. It should reject with an error without clearing the open scene.
6. Compare responsiveness, memory and coil whine on the Android 15 Allwinner A523 tablet. A successful compile is NOT a physical GPU stress test.

## Next releases

Texture and UV pipeline first, then integrate and test Draco/libktx transcoding with explicit license notices and compatibility checks. 3-tier LOD must exclude or properly preserve skinned and morphing geometry; it should never silently mutilate an animated original. Performance and correctness gates must precede any `main` merge.
