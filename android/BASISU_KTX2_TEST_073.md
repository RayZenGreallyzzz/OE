# OE 0.6.13 — Basis Universal KTX2 offline transcoder TEST

Test-only draft based on OE 0.6.12 Universal Importer. Android package `game.oe.editor.basisutest`. Main OE and ORVETHRA game unchanged.

## Actual new code
- Vendored official Binomial LLC **Basis Universal transcoder** (Apache 2.0) and its Zstandard decoder (BSD/GPL dual; BSD permitted) in the APK, all copied from GitHub upstream. Build requires **no network runtime**.
- For KTX2 `vkFormat=0`, native `ktx2_transcoder` validates the source, allows only 2D, one face, non-array **ETC1S and UASTC LDR** images, decodes to **ETC2 RGBA8 GPU blocks**. Output is passed through existing GLES3 compressed texture upload, with a 1024/2048 px cap.
- If source KTX2 has mip levels, importer selects the first level fitting the texture quality limit. Otherwise an oversized BasisU KTX2 is rejected rather than loading an unbounded source image.
- Existing KTX2 unsupercompressed raw RGBA8 and ETC2 textures, PNG/JPEG, Meshopt GLBs, VOX single-model greedy mesh, embedded .gltf JSON, and COLOR_0 remain.
- The source KTX2, glTF/GLB and VOX are not rewritten. .oezip retains canonical GLB and VOX/.gltf originals. Cached GPU ETC2 data is derived, not written into source files.

## Deliberate limits
- This is a **test build**, not yet proven on a physical GPU. Only LDR ETC1S and UASTC4x4 output; HDR ASTC/KTX2 modes and cubemaps/arrays are rejected. Some Zstd-compressed KTX2 UASTC may work through the bundled decoder; must test with real files.
- Draco decoder, external glTF files, glTF skeletal animation playback, full PBR and complete MagicaVoxel scene-graph/multimodel import are not done.
- KTX2 decoder memory and 2D limits apply on worker thread, results use hardware-standard ETC2 on GLES3 (no ASTC output). GPU-native images retain first original mip level when accepted.
- Older OE versions may reject .oezip containing new `sources/` entries.

## Tablet test
1. Import a GLB containing a KHR_texture_basisu KTX2 **ETC1S** base-color image and TEXCOORD_0. Check texture appears. Repeat with **UASTC** (with or without Zstd).
2. Compare same source material exported as PNG. Detect wrong UV orientation, palette, alpha, gamma.
3. Force Economy (1024px) and High (2048px) before importing different models; compare texture cache, heat and responsiveness.
4. Re-import the GLB after app relaunch; ensure the source still loads and no crash occurs in the transcoder.
5. Test malformed KTX2, huge KTX2, HDR, cubemap and external resource references: report a clear error, preserve scene.
6. Import VOX/.gltf and export/import .oezip; ensure source files survive byte-for-byte.
7. Device test on Allwinner A523 (4GB RAM) is mandatory before any main merge.

Automated Android build success demonstrates API/linkage, **not** ETC1S/UASTC texture correctness across many KTX2 fixtures.
