# OE Terrain Painter R4 — continuous texture brush TEST

Branch `test/terrain-painter-r4-074`, from R3 `test/road-map-reference-r3-073`.
Debug app: `game.oe.editor.terrainpaint`. OE `main` and ORVETHRA are unchanged.
Draft only; not approved for merging without tablet testing.

## Implemented
- `☰ Каталог → Текстуры → 🖌 Точная кисть R4 · рисовать` and `🧽 ... стереть`. Uses the selected imported PNG/JPG decoded to RGBA. Source image repeats in world coordinates every 6 metres, and is baked into a tile's composited RGBA pixels (no extra per-stamp GPU draw calls).
- Continuous one-finger paint. Cursor ring previews a radius of 0.5, 1, 2, 4, 8 or 16 metres, **70 dp above the finger** so it is visible. Strength 30/60/85/100%. Touch movement interpolates stamps in world coordinates rather than placing discrete circles. Two-finger pan/zoom cancels active stroke cleanly.
- `oe_terrain_paint` pure C++17 module `terrainpaint/paint_tiles.h/.cpp` stores up to **32 painted tiles** (each 64x64 metres and 128x128 RGBA pixels = 0.5m/pixel). Max CPU canvas size 2 MiB; each tile reused when repainted. Undo records one snapshot per stroke, not each motion event.
- GLES3 renderer displays composited tiles as terrain-following quad meshes. One RGBA GPU texture per visible edited tile, only refreshed on changes; geometry drawn only in visible area. Existing legacy 48-stamp terrain paintings are still displayed and still importable in v1–v3 scenes, but new R4 strokes do not count toward 48.
- Scene JSON **v4** embeds bounded Deflate-compressed, Base64 tile buffers `paintTiles: [[tileX,tileZ,encoded], ...]` and supports optional R3 `mapReference`; v4 `.oezip` exports/imports this self-contained data. Old v1/v2/v3 scenes still load, with empty R4 tiles. PNG city guide is editor-only and never a Runtime game asset.
- C++ host smoke tests exercise continuous paint, erase, limits, bounds, deterministic reload, and atomic duplicate rejection.

## What is NOT done
- Not independent editable per-material splat/weight layers: each 64m section holds a **single composited RGBA layer**. Repainting merges pixels, and undo restores previous composites. Later we can add truly separate material layers.
- R4 doesn't increase the **8 simultaneously registered user textures**, doesn't remove legacy 48-stamp support, and caps paint coverage at 32 tiles (at most 131,072 m² when tiles are entirely distinct).
- No textured RoadSystem geometry, spline editing, road crossings, terrain streaming, physics/nav Runtime, or standalone game export.
- Painted tile detail is 0.5m per pixel, not millimetre-level authoring. A more detailed editor could use 0.25m tiles later at a cost in memory.
- GPU stability, gesture latency, tile seam quality, compress/decompress performance, and thermal limits still require testing on the target 4 GB Android tablet.

## Manual QA checklist
1. Export an R3 backup before testing and install the isolated R4 APK.
2. Load or select a PNG/JPG paint texture in `Текстуры`; activate `Точная кисть R4`, radius 1–2m, strength 85%. Draw a 100m street in the PNG city plan; the brush ring should remain above your finger and the texture should form one continuous line.
3. Draw over a second time, test erase mode, then Undo/Redo. Switch to 3D, move camera away/back; texture must remain in place and follow edited hills.
4. Draw across a 64m tile border, verify no visible gaps or mirrored texture seams. Try 32 separate tiles and ensure the 33rd is refused without corrupting previous tiles.
5. Local Save/Load and .oezip export/import should round-trip tile pixels and R3 PNG guide; verify old scene v1–v3 import. Test tampered Base64/zlib compressed tile data; reject without altering the current scene.
6. Test two-finger pinch while painting, quick touch cancellation, switching textures/modes, Android background/resume (EGL reset), graphics presets and RAM use.
7. Capture screenshots and note any offset/gesture or memory issues. CI success alone is **not** physical tablet QA.

Next: R4.1 material-aware layer weights and finer cursor control; R5 road intersections/physics/navgraph after road editor usability is confirmed.
