# OE 0.7.13 × Phoenix UV44 — reversible GLB daylight material preview (v33)

**User priority:** Houses built in Phoenix should look less flat in the OE city,
without degrading their GLB files or sacrificing the graphics/display scaling
options introduced in v32.

## Implementation — experimental, owner device QA pending

- **⚙️ Настройки OE → ☀️ Освещение зданий GLB** has two clearly labeled
  modes: **Мягкое дневное** (new, enabled by default) and **Классическое**
  (exact previous v32 shader calculation). The choice is persisted as the
  Boolean `oe_glb_soft_lighting` in the OE Android preferences.
- The native GLES3 shader uses `uImportedLighting` only during
  `drawImported`. It is disabled after each imported model draw, so built-in
  houses, roads, terrain, gizmos and terrain paint retain their own original
  lighting. The native Boolean is set from Java through
  `nativeSetImportedLighting` on the GL thread, including context recreation.
- New mode correctly treats glTF baseColor **texture bytes** as sRGB
  (`pow(texel.rgb, 2.2)`), multiplies linear vertex/material tint factors,
  applies soft cool sky ambient plus warmer directional sunlight in
  approximately linear space, then re-encodes the display color to sRGB.
  Existing GLB texture GPU upload remains unchanged.
- Old mode retains the original ambient + Lambert formula
  `uColor*tint*texel.rgb*(.52+.48*sun)` with no new conversion.
- No original GLB data, images, object transform, JSON, `.oezip`,
  geometry, UVs, Phoenix Builder files, or purchased assets are rewritten.
  Full-quality source model bytes are stored under OE's content-addressed
  `getFilesDir()/models/` regardless of display resolution.
- Frame resolution and GLB preview texture cap remain independent
  (Auto/55%/70%/85%/100% vs 1024/2048 px); selecting lighting does not modify
  either.
- **Not full PBR or pixel-perfect Three.js parity:** no normal maps,
  metallic/roughness reflections, environment maps, shadow maps, tone
  mapping/exposure controls or physical material extension compatibility in
  this milestone. Lighting is purely a reversible visual preview improvement.
- Low-memory Allwinner A523 tablet target; the gamma conversion adds GPU math
  to GLB fragments. Test device FPS/thermals and revert to Classic if too slow.

## Verification and owner testing

1. Open a saved OE city with a house imported from Phoenix **with textures**.
2. Switch to high display resolution (100%) to compare clarity, NOT fidelity.
3. Open **⚙️ → ☀️ Освещение зданий GLB → Мягкое дневное**.
4. Rotate the view around wood, stone, roof and chimney. Confirm texture
   colors appear more natural and surfaces are not overexposed.
5. Switch to **Классическое** and back several times, checking that object
   placement, door cutouts and other scene objects do not shift or disappear.
6. Save OE city, close/reopen it and verify the selected lighting mode persists.
   Export/reimport `.oezip` and compare content of GLB file with previous
   source if available.
7. Compare FPS and GPU/RAM usage on device; specifically test a large
   multi-textured building and mixed terrain/roads.
8. Confirm Phoenix JSON/GLB export and texture import still work in their
   own editor; the native shader is isolated from WebView.
9. Source smoke checks GLES GLB-only lighting uniforms and preference wiring;
   Android CI compiles C++/Java but does not run the GLES shader on-device.

Keep PR #36 **draft**. Keep `main`, stable Phoenix UV44 and separate ORVETHRA
unchanged. Private owner assets and signing material must not enter GitHub.
