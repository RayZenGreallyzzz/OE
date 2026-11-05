# OE 0.6.8 — External PNG/JPG Texture Paint Test

Test-only branch `test/texture-paint-export-068`, based on full-screen editor 0.6.7. Android package `game.oe.editor.painttest`, installed alongside previous versions with isolated data. `main` and ORVETHRA unchanged.

## Features implemented
- Native OpenGL ES 3 custom RGBA texture cache: 8 content-addressed images, <= 1024 px each and at most 32 MiB retained uncompressed source in the editor. No network connection.
- Android system image picker allows external PNG/JPG; original source remains unchanged. Files stored inside OE private storage with SHA256-derived names and bounded size <= 8 MiB each.
- **Текстуры → Мои текстуры → Нанести на выбранную деталь** overrides an object's base-color material in a lightweight GLES shader. Applies to built-in walls, floors, roofs, doors, trees, rocks, etc., and to GLB diffuse preview with TEXCOORD_0. For built-ins, planar UVs are generated during mesh baking; control repetitions by using existing independent object scale.
- **Текстуры → Покрасить землю** creates smooth fading texture patches following the current deformed heightfield. Brush radii 4/8/16/32/64m; 48 stamps limit to bound GPU/CPU use. Drawn only when near camera; no idle render loop.
- Paint changes participate in Undo/Redo, local JSON save/load and portable `.oezip` with `textures/tx_<hash>.png|jpg` source files, plus unchanged GLBs; importing checks hashes, exact files and size; native scene swap is atomic after all resources validate.
- Keeps full-screen touch-axis gizmos, on-demand rendering, 75% resolution, meshopt compressed GLB and baseColor embedded PNG/JPEG + GPU-ready ETC2 KTX2.

## Limitations, clearly separated
- This is **decal-based terrain painting**, not a full landscape multi-layer splat map. Overlay stamps cost additional draw calls when in view, so use sparingly on Allwinner A523.
- External images are limited to 1024 px for this experimental painting workflow. 2048px toggle applies only to textures read from GLB. Image UV tiling for built-in props is basic planar mapping.
- No Draco, Basis Universal ETC1S/UASTC transcoding, automatic three-tier LOD, full PBR materials, skeletal animation playback, direct project-to-GLB export or production server multiplayer. These require separate implementation and verification; no claims of support.
- Large assets, broken archives, unsupported formats or too many images are rejected, preserving the existing scene.

## Manual tablet tests
1. Place wall, tap to select, choose image from gallery in **Текстуры → Загрузить свой PNG/JPG**, then **Нанести на выбранную деталь**. Move, rotate and resize wall using color axes.
2. Paint a hill with PNG grass/stone texture, verify brush fade and no image flickering, undo once and redo.
3. Save scene; close/reopen the app; load scene and verify painted walls and landscape.
4. Export `.oezip` and import into this app after creating a new blank scene. Verify intact original PNG/JPG and GLB resources.
5. Try an image larger than 8MiB, nonimage file renamed to JPG, and a truncated `.oezip`: the current scene must remain unchanged.
6. Pan/pinch zoom and verify average device responsiveness, absence of excessive heat and visual stuttering.
7. All GUI tests must be performed on the actual tablet. CI passing proves compilation, not display correctness.

