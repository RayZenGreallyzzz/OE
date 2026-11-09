# OE Terrain Material Layers R4.1 — experimental

Branch `test/terrain-material-layers-r41-075`, based on R4 `test/terrain-painter-r4-074`.
Separate debug package: `game.oe.editor.materiallayers`. Neither `main` nor ORVETHRA is changed. Keep this pull request **draft** until device QA and user approval.

## Architectural change

R4 painted one merged RGBA surface per tile: after painting stone and then grass, an eraser removed both colours. R4.1 introduces **a distinct 8-bit weight mask per selected texture** in each edited terrain tile.

- 64 m tile size, 128×128 weight mask = **0.5 metre per texel**.
- Maximum **32 tiles**, **4 independently editable texture masks per tile**, and 8 source PNG/JPG images loaded at once. Maximum weight data = 32×4×16,384 bytes = **2 MiB**. Existing R4 composite data (up to 2 MiB) may coexist during migration.
- Source material is a content-addressed `tx_<24 hex>.png` / `.jpg` from OE's existing asset library; no custom per-brush GPU objects.
- At display time the GLES3 fragment shader samples the original diffuse image with world-aligned UVs plus the separate GL_R8 mask with tile-local UVs. This means editing a stone mask does not affect neighbouring grass and dirt masks. The preview is composited by order of stored layers and GLES alpha blending.
- The brush samples interpolated points along the touch segment. A cursor appears **70 dp above the finger**, with a 0.5–16 m radius and 30/60/85/100% coverage. Eraser changes **only the selected material**; it does not erase other material masks. Source image texture and layer masks are independent of the road object's geometry and runtime entity ID.
- Once a stroke ends, a single undo snapshot is recorded, with a conservative **12-state history cap** to avoid excessive 4 GiB tablet RAM use.
- Native scene validation builds a replacement model before swapping scene data. Invalid tiles, duplicate keys, malformed masks, and absent source materials cannot overwrite a currently loaded scene.
- Material-mask GPU textures are derived caches and are recreated after EGL context loss, undo/redo or scene loading.
- `oe_terrain_paint` C++17 module remains portable; Android UI/shader has its own adapter.

## UI in the test build

`☰ Каталог → Текстуры`:
- `🧩 Слои R4.1 · рисовать` — active PNG/JPG image only; start painting with a touch and continue by dragging.
- `🧽 Слои R4.1 · стереть материал` — erases the **currently selected** source material, leaves other masks intact.
- `📏 Радиус и сила кисти` — choose size/coverage.
- `🖌 Старый композит R4` retained to preserve R4 projects and compare performance.
- Finger remains below visible cursor; two-finger gestures stop paint and pan/zoom the map.

## Scene format v5

The scene extension `paintLayers` is a list of records:
```json
{
  "version": 5,
  "paintLayers": [
    [-1, 0, "tx_111111111111111111111111.png", "<deflate/base64 RGBA *alpha mask* bytes>"]
  ]
}
```
The example's explanatory field above represents **16,384 raw single-channel bytes (not RGBA)** before Deflate+Base64 encoding. Existing v4 `paintTiles` RGBA composite tiles and R3 `mapReference` may coexist in v5 scenes. v1–v4 scenes remain readable; only R4.1 writes v5. Old OE builds cannot read v5 — keep source and `.oezip` backups.

`ProjectArchive.referencedTextures()` includes mask material keys so portable exports bundle the PNG/JPG sources needed to reconstruct them. Validation includes a maximum total of eight distinct source images and a scene size limit of 8 MiB.

## Limits / next work

- **Not yet** a physically based material / splat shader with normal and roughness maps; 1 diffuse texture and 1 mask per layer. Each material currently adds one draw call to each visible edited tile (max 128 if all 32 tiles use all 4).
- No new road topology, true junction mesh, pedestrian pavements, point dragging, auto-tracing from PNG, GPU texture arrays, tile streaming, material library beyond eight textures, physics, navigation, game runtime Play/Stop, or independent game APK export.
- The 32-tile budget limits authored coverage: this is a RAM-safe tablet prototype, **not unlimited large-world streaming**.
- Physical Android tablet QA and a real city project import/export roundtrip remain pending. CI cannot prove GPU mask orientation, transparency, draw-call cost or thermal throttling.

## On-device QA

1. Export existing R3/R4 city project `.oezip` before installing this separate app. Import it into R4.1.
2. Select a PNG stone texture. Draw a 2-metre road with `🧩 Слои R4.1 · рисовать`. Select grass and draw across the road.
3. Select stone again and use `🧽 Слои R4.1 · стереть материал`. Verify grass remains while stone vanishes.
4. Use Undo/Redo, switch to top-down PNG guide, pan/zoom, test terrain elevation edits and GL context restoration (background/reopen).
5. Save locally and export/import `.oezip`; compare all mask coverage and texture source links after restart. Test a corrupted Base64 mask and duplicate key/tile pairs in a disposable archive; it must reject without clearing the active project.
6. Stress up to 4 masks/tile, try a fifth to confirm safe refusal; test 32 tiles then a 33rd. Report draw calls, CPU submit, visual seams and heat.
7. Test v1/v2/v3/v4 project imports, and verify R4 composite paintings remain visible.

Next: develop R4.2 material blending/texture management, chunk streaming; later R5 road intersections and Physics/Nav integration. Do not merge without tablet feedback.
