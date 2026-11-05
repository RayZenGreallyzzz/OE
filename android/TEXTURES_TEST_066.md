# OE 0.6.6 Texture Tools — experimental Android release

Branch: `test/textures-ktx2-066`, based on `test/advanced-assets-065`. Test package `game.oe.editor.texturetest` installs alongside OE main, World Tools and Meshopt Test. **Do not merge to main yet.**

## Actual implementation

- Existing offline Meshopt decoding and all 0.6.4 world/project tools retained.
- **Embedded base-color PNG/JPEG** within GLB 2.0: reads `images[]`, `textures[]`, `materials[].pbrMetallicRoughness.baseColorTexture` and `TEXCOORD_0`, including normalized/quantized UV accessors. UV coordinates and per-primitive material texture slots are passed to GLES3, which samples diffuse textures in a lightweight shader. Original GLB is never rewritten.
- Multiple materials on one GLB: up to **4 unique baseColor textures** per imported asset, limited by **32 MiB decoded/packed texture bytes per GLB** and a **96 MiB GPU-resource source cache** for low-memory devices. The renderer reuses shared GPU assets rather than uploading each placed copy.
- **PNG/JPG texture reduction** on import: default **1024px**, optionally **2048px** via `Качество текстур`. Uses bounded BitmapFactory bounds/sample-size checks and scales only if needed. The saved .oezip archive still contains the original unchanged GLB.
- **KTX2 without supercompression** in explicitly supported Vulkan formats: RGBA8 (37/43), ETC2 RGB (147/148), ETC2 RGBA (151/152). Only non-array 2D images are supported, max selected size, first level used; ETC2 uploads directly as GPU block-compressed data on GLES3. The importer checks the KTX2 identifier, header, dimensions, level table, length and format.
- For glTF `KHR_texture_basisu` texture slots with a PNG/JPEG `source` fallback, when the KTX2 format is not supported the standard fallback is used. Incompatible required textures without fallback fail with a clear error, not a fake textured preview.
- Source GLB remains content-addressed and unchanged. Filesystem import/export, atomic scene replacement, camera/terrain/decor controls and culling are preserved.

## Deliberately NOT supported yet

- **Basis Universal ETC1S/UASTC transcoding** and compressed KTX2 with BasisLZ/Zstd. Common `KHR_texture_basisu` GLBs *without* a PNG/JPEG fallback remain unsupported until a real BasisU/libktx transcoder is integrated and tested. **Do not call this comprehensive KTX2 support.**
- Draco compressed geometry, full PBR metallic/roughness, normal/occlusion/emissive maps, `KHR_texture_transform`, alternate UV set `TEXCOORD_1`, skinning/animation playback, 3-tier automatic LOD and standalone scene GLB export.
- No separate external PNG/JPG painting UI yet; textures in this version come from GLB materials.

## Safety and resource limitations

- 24 MiB GLB source cap; 90k triangles; max 12 distinct GLB assets, 4 diffuse textures per GLB.
- Imported texture formats are validated and bounded before GPU allocation; unrecognized or malformed KTX2 data fails, keeping the current scene unchanged.
- On lost EGL context, retained immutable texture source data is re-uploaded.
- No continuous redraw when idle: retains `RENDERMODE_WHEN_DIRTY` and the previous 75% render resolution.
- KTX2 ETC2 must be **already GPU-native ETC2** (not BasisU). The 1024/2048 downsizing toggle applies to PNG/JPG; a larger ETC2 KTX2 is rejected instead of being silently resampled.

## Practical test on tablet

1. Import a small GLB with embedded PNG/JPG baseColor and `TEXCOORD_0`. Check that the texture appears on the object and UV orientation is correct.
2. Import a Meshopt-compressed GLB with embedded PNG/JPG texture. Check no geometry corruption and the texture displays.
3. Set texture quality to **2048**, import a different (not cached) model and compare sharpness and GPU responsiveness. Changing the setting does not retroactively reprocess already cached models.
4. Try a GLB with a GPU-native ETC2 KTX2 image; confirm it displays. Try a BasisU-only model; expect a meaningful rejection, unless the GLB provides a standard fallback image.
5. Place multiple copies of one textured GLB; confirm that GPU draw calls change with copies but textures are not decoded per copy.
6. Export the world to `.oezip`, import it again, compare the original unchanged texture-bearing GLB. Verify import failure does not erase prior scene.
7. Check thermal load, performance and any high-frequency buzzing on Allwinner A523 (4 GiB RAM).

CI compilation alone is not a real tablet hardware test.
