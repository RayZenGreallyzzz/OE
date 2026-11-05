# OE 0.6.12 — Universal 3D Import TEST (partial, honest support matrix)

Draft branch `test/universal-import-072`, based on OE 0.6.11. Android package `game.oe.editor.importtest`, installed separately. **Main and ORVETHRA game untouched**.

## Imported formats — actually implemented

| Source | Runtime importer | Notes |
|:--|:--|:--|
| GLB 2.0 | YES, static preview | Hierarchy/TRS, POSITION float/quantized, indices, COLOR_0, TEXCOORD_0, baseColor diffuse PNG/JPEG, GPU-native ETC2/raw RGBA8 KTX2. Limited PBR. |
| Meshopt GLB | YES | EXT_meshopt_compression and KHR_meshopt_compression bufferViews decoded by offline C++; native memory bounds checked. |
| **.gltf JSON** | **Embedded BIN and images ONLY** | Single base64 `data:...` BIN buffer + optional base64 data URI PNG/JPG/KTX2; auto-converted to canonical GLB2. External .bin, .png, .jpg, images via sibling paths and multiple buffers are rejected with a helpful error; use Blender's embedded GLB export for those. |
| **MagicaVoxel .vox v150** | **Single model** | SIZE + XYZI + optional RGBA, supports exact built-in default palette, Z-up -> Y-up, greedy merges matching-color exposed faces, discards completely hidden voxel faces. `PACK` >1 / multiple models are rejected. Scene transforms, materials/alpha and animation are not supported. |
| KTX2 | Partial, unchanged | Unsupercompressed RGBA8/ETC2 images embedded in GLB; BasisU ETC1S/UASTC and Zstandard KTX2 transcode not yet bundled. |
| Draco | **NO** | KHR_draco_mesh_compression geometry is rejected; do not claim support. |

The newly generated GLB for VOX and embedded .gltf uses a content-addressed model filename. **The original `.vox`/`.gltf` source bytes remain untouched and are retained separately** in OE private storage; portable .oezip includes `models/oe_*.glb` AND optional original `sources/oe_*.vox`/`sources/oe_*.gltf` when referenced. Every original's canonical conversion is checked against the GLB SHA key on archive import. Older OE test versions cannot import `.oezip` with the new `sources/` entries; export from 0.6.12 and import into 0.6.12 or newer.

## Safety & performance

- Input GLB/VOX/glTF cap 24 MiB. GLB output <=24 MiB, max 90k triangles, 12 unique GLB assets; voxels <=250k, volume <=256³; VOX visible faces are greedy-merged. Unsupported shapes reject rather than silently corrupt or lose input. Device may run out of memory on extreme inputs; use modest models first.
- glTF 2.0 `COLOR_0` VEC3/VEC4 float or normalized integer is now multiplied by baseColorFactor, preserving vertex colouring, voxel palettes, and meshopt-accessor colour data.
- The static importer does not play skin/animation/morph targets, and animated GLBs are intentionally excluded from automatic simplification. This does **not** fulfill full glTF compatibility yet.
- Existing OE features remain: full-screen touch editor, unsnapped millimetre XYZ positioning, texture paint, stage import/export, 3-level static LOD, quality profiles and GPU diagnostics.
- When in doubt, original source bytes are more important than simplifying a preview; no destructive source edits.

## Manual tests on Android tablet

1. Import a small single-model MagicaVoxel `.vox` with 2–10 palette colours and no PACK scene: place, check palette, winding, internal faces and orientation.
2. Import `.gltf` JSON with `buffers[0].uri=data:application/octet-stream;base64,...` and embedded PNG data URI; confirm colour and UV match Blender's GLB.
3. Import old uncompressed GLB with vertex `COLOR_0`, then with Meshopt compressed attributes. Check rendering and bounding sphere.
4. Save project and export to `.oezip`. Confirm ZIP has `models/oe_*.glb` and `sources/oe_*.vox` or `sources/oe_*.gltf`. Import the same archive into this test version and compare.
5. Try `.vox` with >1 models and external `.gltf` resources: expect a clear error; currently open scene must not be erased.
6. Try Draco-only and BasisU-only assets: should reject with explicit guidance, not pretend the texture is visible.
7. Switch to Economy profile and test a textured 3D model in motion; compare GPU heat, touch control, memory and draw calls.

**Android CI compilation and host smoke do not prove the importer succeeds on your exact MagicaVoxel/Blender exports.** Please share failing files (if safe) or screenshots for targeted fixes.
