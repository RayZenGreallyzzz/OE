# OE 0.7.13 + Phoenix UV44: glTF PBR rendering, phase 1 (v34)

**Goal:** The owner builds a highly detailed medieval house in Phoenix and places
the same GLB into an OE city. When OE display resolution is already 100% and
texture preview 2048 px, the city still looks flatter than Phoenix. This
phase improves GLB material interpretation, not the owner's geometry.

## Implemented in source

- **glTF 2.0 material defaults**: an absent baseColorFactor is now white
  `[1,1,1,1]`, not the previous forced beige. Material/vertex factors
  remain linear; baseColor texture is decoded from sRGB correctly.
- **Smooth glTF vertex normals**: reads `NORMAL` accessor in addition to
  `POSITION`, transforms it using inverse-transpose (including node and
  entity non-uniform scales), and falls back to face normals when missing.
- **Normal maps**: `material.normalTexture` (UV0, embedded PNG/JPG/KTX2)
  and `scale` are imported; GLES3 uses derivative-based TBN to add
  per-pixel surface detail. glTF `TANGENT_0` itself is not imported yet.
- **Metal/roughness**: supports `pbrMetallicRoughness.metallicFactor`,
  `roughnessFactor`, and embedded `metallicRoughnessTexture`. glTF
  texture's **G channel** multiplies roughness, **B channel** multiplies
  metallic. Uses an approximate Cook-Torrance/GGX BRDF with sun and soft
  ambient light; old light is selectable under ⚙️ *Материалы GLB*.
- Source GLB bytes, saved city transforms and geometry are NOT transcoded,
  rewritten, downsampled or replaced. 55–100% resolution remains independent
  of material fidelity. Materials are just GPU preview data derived on load.
- If the GLES driver cannot compile/link the new shader, setupGL attempts
  a separate known-simple GLES3 legacy vertex + fragment shader so the
  owner's roads, terrain and editor UI stay visible.
- These changes apply only in `drawImported()`; city ground, road shader,
  gizmos and native small buildings stay on the existing rendering path.
- Mobile guardrails stay active: max 90,000 triangles/GLB, 32 MiB decoded
  texture RAM per model, 96 MiB total owner texture cache, max 16 texture
  slots. If a model exceeds its preview memory budget, the original GLB
  still exists on disk; OE should report failure rather than corrupting it.

## Still different from Three.js/Phoenix

This is **phase one of PBR**, not parity with Three.js. No IBL
(image-based environment lighting), occlusion map shading, emissive maps,
clearcoat, transmission, `KHR_materials_*` extensions, MikkTSpace
tangent import, alpha blending or physically accurate tone mapping yet.
Texture sampling/mipmap parity and broader material alpha semantics
are future work. Direct sunlight and ambient appearance may still differ.

## Test procedure on the owner's tablet

1. Install *only a correctly signed owner update* over v32/v33. Never delete
   OE just to install a mismatched test APK. Keep the original Phoenix intact.
2. Open a saved city with a house already placed. Use the same house GLB
   exported from Phoenix **with its embedded textures**. Keep resolution
   at 100% and texture preview at 2048 px.
3. ⚙️ → **Материалы GLB** → select **PBR (экспериментально)**. Look at
   stone/roof surface depth, metallic objects and sun highlights.
4. Switch to **Классическое**, compare from exactly the same camera angle.
   Saved objects, positions and source model bytes must be identical.
5. Rotate/scale the house, save city, restart OE, confirm that its normal
   shading and imported materials are still intact. Test a second house
   without normal maps and a GLB with metal/roughness map.
6. In low-memory mode, PBR still must not corrupt the source or saved city.
   Switch to Classic if the mobile shader is too slow.

## Release/signing constraint

GitHub Android CI builds and syntax smoke tests are required for this branch.
The installed owner's private APK signing key was generated and archived in a
previous conversation response but is not available in the present runtime.
Do not claim a new signed APK can update the existing installed app unless
the archived PKCS#12 private signing key has been supplied and verified against
the previously installed certificate.

**Scope:** draft PR #36 only, no merge to `main`, no private Phoenix assets,
paid model packs, or signing credentials in public GitHub.
