# Orvetra Engine R5.5 — Real PNG/JPG road textures & repeating UVs (EXPERIMENTAL)

**Branch:** `test/road-texture-uv-r55-083` (stacked on bridge editor R5.4).
**Source backup:** `backup/oe-r54-before-road-textures-2026-11-10` at `7704635c0f16272e5617ea7608be79fd351e0a07`.
**APK:** `game.oe.editor.roadtextures` / `0.8.3-road-textures-r55-test`, ARM64 GLES3 only.
**Safety:** draft PR only; never modify/merge OE `main`; do not touch Phoenix Builder or unrelated ORVETHRA.

## The workflow the user asked for

1. **`Каталог → Дороги → PNG-план города`**: import an existing transparent city street plan PNG and align its scale/position/top-down view. It is only a VISUAL GUIDE; there is no automatic street recognition or generated centreline from pixels.
2. **`Дороги → Новая дорога · точки`**: tap along the road marked on your guide, add as many editable control points as required, choose width, then end the road. Existing R5.1 real T/X/Y control point connections, R5.2 junction stitching, R5.3 water, R5.4 bridge approach metadata continue.
3. **`Дороги → 🖼 Загрузить PNG/JPG для дороги`**: import a cobblestone, dirt or gravel TILEABLE diffuse image, limit original file to 8 MiB and decoded 1024×1024 max, shared with the existing paint-texture library (at most 8 GPU-resident images).
4. **`Дороги → 🖼 Выбрать PNG/JPG-текстуру дороги`**: select a saved image for the currently selected street and all subsequent streets. **`🪨 Нанести активную текстуру`** applies an already GPU-loaded image; `🎨 Покрытие дороги` still offers four low-power colour presets. Existing materials without custom images continue to render as before.
5. **`Дороги → 📏 Повторение камней по дороге`**: set repeating texture size to **0.5, 1, 2, 4, 8, 16 or 32 metres** per repeat on the selected street and as the default for newly created streets. This changes actual road UV coordinates, not physical street geometry.
6. Look at long winding streets and X/T/Y intersections: the street body repeats PNG/JPG along distance measured from its control points, so cobbles don't stretch along a 100-metre alley. For a joined junction, the fill uses planar world-XZ repeat mapping in the **source street material**, instead of stretching one arbitrary texture tile across its fan triangles. The visual seam at the edge of different texture orientations/materials is not yet a seamless blended shader. Roads still follow terrain.
7. Export city as `.oezip`. The scene records each custom `material=tx_<digest>.(png|jpg)`, optional `tileMetres`, all road points, PNG city plan reference, lakes, layered materials and medieval modules. The archive now includes all road-referenced PNG/JPG image files under `textures/`, with existing checksums and safe memory/size limits. Undo/Redo of material and repeat edits is supported.

## Implementation constraints

- Portable `roads::Road` adds validated `tileMetres` at the end of its data type, default 4m; old 5-field C++ initializers remain valid. C++ core rejects nonfinite, <0.5 or >32m widths. No need to resample road mesh when only material or UV size changes; it invalidates the existing road GPU cache only.
- The existing shader already accepts 2D `uTexture`, RGBA8 decoded files and `GL_REPEAT`; R5.5 binds the correct cached texture for each road draw range and keeps texture ownership in the shared paint library. No new shader programs or runtime downloads.
- R5.1 road IDs and topology/undo stay unchanged, R5.2 stitched geometry keeps common boundary positions; a joined intersection source material is deterministic, from one of the incident road IDs. At mixed-material junctions, a hard material boundary can remain; **photorealistic edge blending** is not implemented.
- For a given road, the existing geometry UV expresses half-metres. At draw-data creation the scale `2 / tileMetres` yields one repeat every chosen number of metres. At junction fill, world-XZ `x / tileMetres, z / tileMetres` avoids stretched fan UV.
- New custom textures or non-default repeat sizes require **scene v8**; old v1–v7 scenes load with 4m default and their original built-in colours. Scene v8 may contain optional v7 lakes. Older OE APK versions cannot read v8 cities; back up your city as `.oezip` first.
- At most 8 shared image keys (including terrain/material paint and objects). Image imports hashed, validated and loaded on worker thread, with GPU registration on GL thread; e.g. no arbitrary absolute path, no remote network source.
- A PNG used as a transparent street-plan overlay and a PNG used as a road **material** are separate concepts. The plan does NOT become road geometry or road material automatically.

## Validation

CI C++17 `road_texture_smoke.cpp`: road scale bounds, geometry UV along 50m road, material identity/tiling across X intersection, scene roundtrip to an independent network, atomic failure for invalid scale.

Python contract `road_texture_integration_smoke.py`: C++ GLES texture binding, archive asset lookup, JNI road tile array, Java PNG library picker/road material settings, v8 file checks and backwards compatibility. Existing earlier smoke tests retain coverage for bridges, water, terrain layers, road topology and original editor tools.

**Physical tablet QA on Allwinner A523 4GB RAM is still required.** First export a safety copy `.oezip`. Place a transparent PNG map, trace several straight and curved streets, import several image materials, vary scale on two roads, inspect T/X/Y seams, save/restart/import `.oezip`, test Undo/Redo and terrain edits, background/foreground GLES context loss and memory/FPS. If the city has custom PNG/JPG road surfaces, only open it in R5.5+.

## Product goal

After user visual QA of roads, proceed to road/bridge collision integration, playable **Play/Stop**, touch-controlled player and physics, interactivity and a separate standalone Android game runtime, then the **offline APK game exporter directly on the tablet without PC/GitHub Actions**. The GitHub Actions APK here is the OE editor test app, **not** the locally exported game APK.
