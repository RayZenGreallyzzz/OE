# OE 0.7.13 + Phoenix UV44 — model fidelity versus display resolution

**Owner-requested behavior:** A detailed house built in Phoenix must retain the
original GLB geometry and full embedded textures. Changing the Android image
quality may reduce the *display resolution* for FPS/battery, but must not
rewrite, resample, or overwrite the original building/model.

## Implemented in v32

- Top-right **⚙️ Настройки OE** →
  **🖼 Разрешение изображения** (auto, 55%, 70%, 85%, 100%).
- This screen-setting changes *only* the `GLSurfaceView` EGL
  framebuffer/render scale, via `nativeQualityScale(displayTier)` and
  `surface.applyRenderScale(scale)`.
- It **never** invokes `nativeSetQualityProfile()` while changing the
  display resolution, never changes the in-memory GLB preview
  `textureLimit`, and never writes or replaces a saved GLB.
- Existing Android display preference `oe_quality_tier` is migrated to new
  independent preference `oe_display_resolution`.
- The native renderer's view-distance/LOD profile is configured just once on
  GL context start from a GPU/RAM safety recommendation, independently of the
  screen-resolution option. Rendering may still use temporary distance-based
  LOD. All original source geometry stays intact.
- **🧱 Детализация текстур GLB на экране**, independently configurable
  1024/2048 px, controls only the GPU preview of newly loaded GLB textures.
  The original embedded texture bytes in the stored GLB are **never
  resampled**. This setting is persisted as
  `oe_glb_preview_texture_limit`. Previously loaded GLBs may remain in
  GPU cache until a full OE process restart.
- Preview GPU texture decoding has low-memory bounds (up to 32 MiB per model);
  even at 2048 px a complex house can reach the device limit, where a lower
  preview setting is appropriate. The original GLB is retained regardless.
- Shader/lighting/PBR differences between Phoenix's Three.js view and OE's
  native GLES3 renderer remain; 100% render resolution alone cannot promise
  pixel-identical appearance to Phoenix.

## Model integrity

In city import, the canonical GLB `byte[]` is saved verbatim under a
content-addressed filename in `getFilesDir()/models/`. `GlbReader.decode`
creates temporary GPU vertices/textures, not a replacement GLB. The city
scene uses the model key and placement values; portable `.oezip` exports
include the original model bytes. Lower display resolution does not change
world coordinates, transform, GLB materials, or the exported source file.

## Verification

- Smoke test ensures selectDeviceQuality cannot invoke
  `nativeSetQualityProfile`, alter `textureLimit`, or decode a GLB.
- Test checks the new screen/texture settings stay separate and retain
  backwards-compatible owner preferences.
- Android compile + CI host smoke tests are required.
- Actual tablet QA: import a textured GLB from Phoenix, place once,
  switch 55%→100%, ensure object and texture appearance improve in clarity
  without changing shape or placement; restart OE with 2048 preview enabled,
  compare texture detail and saved city on reopen; monitor FPS/memory.
- Private owner-only Phoenix/medieval assets and signing key must not be
  placed in the public repository. No changes to `main` or ORVETHRA.
