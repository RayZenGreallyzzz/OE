# OE 0.7.13 + Phoenix UV44 — texture sampling for GLB/city (v35)

## Scope

The owner confirmed that even **100% display resolution** and **2048px texture preview**
look noticeably flatter and blurrier than Phoenix Builder UV44 on the tablet.
This is not fixed by rescaling the screen or altering the original GLB.
After v34's PBR normal maps/metal-roughness import, v35 addresses how
the GPU samples steep roofs, stone walls, and small distant building details.

## Implemented

- Existing `TextureReader` decodes a bounded **GPU preview copy** of embedded
  GLB PNG/JPEG/KTX2 textures. Source GLB bytes and saved project files are unchanged.
- Before v35, native GLES3 set `GL_TEXTURE_MIN_FILTER = GL_LINEAR` for
  every uploaded texture and did not generate mipmaps, which produces visual
  aliasing on roofs/distant surfaces.
- New ⚙️ Settings item **«Чёткость текстур под углом»**:
  - **Базовая** (0): `GL_LINEAR` without sampling extra levels;
  - **Сглаженная** (1): RGBA8 automatic mipmaps + trilinear
    `GL_LINEAR_MIPMAP_LINEAR` for distant surfaces;
  - **Высокая** (2, default): same plus up to 4× anisotropy on
    GLES devices advertising `GL_EXT_texture_filter_anisotropic`.
- The extension is checked using the GLES3 `GL_NUM_EXTENSIONS`/
  `glGetStringi` query at each EGL context creation, and max supported
  anisotropy is queried/clamped. Unsupported devices fall back to trilinear.
- **ETC2 base-level textures** remain bilinear; the GLES driver may not support
  `glGenerateMipmap` on compressed images. A failed RGBA8 mip generation also
  falls back to bilinear without corrupting stored source assets.
- Current GPU texture objects are updated on the GLES render thread after a
  settings change; preference `oe_glb_texture_sampling` is persisted and
  restored after a renderer context recreation.
- Settings modify **only GPU filtering**; they never change model vertices,
  image byte payloads, world scene, shading PBR factors, or exported GLB.

## Verify on device

Open the same saved house. Compare the roof at 30–50° camera angle,
first using **Базовая**, then **Сглаженная**, then **Высокая** at 100%
display resolution and 2048px GLB preview. Watch roof tiles and stone
walls for shimmer during camera motion; compare average frame rate and
GPU memory under the same conditions. A small or absent difference when
the GPU lacks the anisotropic extension is expected.

## Remaining differences from Phoenix

Texture filtering cannot replace:
- higher-resolution textures than the current 2048px GPU cap;
- environment illumination, shadows and tone mapping;
- physically accurate glTF PBR IBL or full UV tangent-space parity;
- KTX2 compressed mipmap chain support.

Keep the v34 source archive, v35 branch, and owner's installed app intact.
No buildings are destructible in this phase. Source is public only where
it contains no owner's private Phoenix/medieval pack assets or signing keys.
Do not distribute v35 as a separately signed Android app; updates require
the matching owner-specific APK certificate private key.
