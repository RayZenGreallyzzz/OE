# Orvetra Engine v37: shared houses, instance-only material variants

## Owner goal

Build a house (e.g. 50×50 meters) in Phoenix UV44 once, place 20/50 copies
in an OE city, change roof tiles and stone/wood colors independently on each
house without duplicating model geometry or common textures.

## Delivered in this phase

- **One GLB, many city Entities**. Imported mesh is content addressed by the
  `oe_*.glb` key. 50 copies all point to one resident GPU `Asset`;
  this is **shared resource reuse**, not yet true GPU draw-call instancing.
- GLB import preserves source **glTF material IDs and labels per triangle
  batch**. In the house inspector, choose **«🏠 Материалы крыши/стен»**,
  select a source material (named roofs, walls, stone, etc., if available),
  then choose a tint or personal PNG/JPEG replacement.
  When glTF materials are unnamed, user may select the available material
  slot instead. If the original GLB author merged roof and walls into the
  same material slot, changing that slot necessarily changes both; real
  per-surface independent styling then needs material boundaries in the GLB.
- Appearance variants live in each **Entity** as `materialStyles`: a small
  material index + RGB multiplier + optional content-addressed texture key.
  Different house copies can look different without changing their source GLB,
  original UV44 builder files, or neighbouring copies.
- Custom texture files use a `tx_<hash>.png/jpg` key. A reused identical
  texture in the same city is GPU-registered only once in the shared paint
  texture cache (`gPaintTextures`); repeated application to many houses
  doesn't duplicate stored image bytes. Different glTF models' *embedded*
  texture resources are not yet deduplicated across model keys.
- Material overrides survive **scene.json**, undo/redo and **.oezip**
  export/import; legacy world files without `materialStyles` remain valid.
  Archive scanning includes material override texture references.
- **«🏘 Ряд ×10»** and **«🏙 Квартал ×50»** actions create 9 or
  49 clones of the selected original, respectively, using a single undo
  checkpoint. Each clone references the same GLB and hash-addressed images;
  spacing uses a conservative real-world bounding square of its geometry.
  Grid placement is all-or-nothing if the map is too small or 300-entity
  runtime cap is exceeded.
- A top-bar **«🏗 Стройка»** entry still opens the familiar UV44 builder
  and returns to the current city editor. It is a navigation step toward a
  unified workflow; **the Phoenix builder is still a separate Android
  Activity/WebView with its own canvas**, not yet a truly shared 3D scene.
- Same app ID `game.oe.editor.phoenix013fix1`, versionCode 37, and the
  **same owner-only permanent signing key** as v35 and v36.

## Do not overclaim

- Memory saving relates to **identical GLB and identical named custom
  textures**. GPU memory usage is not literally constant when adding 50
  objects: per-instance transforms and metadata cost a little; render draw
  calls and fragment workload grow. **True batched GPU instancing** and
  distance-based occlusion are next steps.
- Different custom texture images cost their own memory **once per unique
  image**, not once per house. Embedded GLB images are currently shared by
  all instances *of the same model*, not across unrelated GLB imports.
- The existing GLB decoder still has a 64 MiB input-size safeguard; huge
  170 MiB+ castles require a separate streaming asset pipeline. Neither
  geometry nor source imagery is resized to meet city budget.
- Material tint and override texture work with glTF *material slots*. They
  are not automatic pixel-by-pixel semantic masks for roofs and walls. UV
  mapping, tiling and original material grouping matter. Materials retain
  the imported mesh geometry and real world meter dimensions.
- Phoenix's tools, seven tabs, proprietary assets, private signing secret,
  and JSON source editing are **not changed**.
- Device-side visual and performance verification remains necessary.

## On-tablet test

1. Import a house via Phoenix → City or an existing GLB.
2. Place it, choose «🏘 Ряд ×10» or «🏙 Квартал ×50» in «Точно».
   Increase map size before placing big buildings.
3. Select a copy, choose «🏠 Материалы крыши/стен», pick material
   «roof»/«tiles» if named, choose reddish/green/darker tint.
4. Select another copy; verify its materials are unchanged.
5. Import a PNG/JPG from «Текстуры», then apply the same custom
   texture to several copies: all should reference the same image.
6. Save the city, export .oezip, reopen; check all 50 copies retain
   individual tints and source GLB remains byte-identical.
7. Compare frame rate when drawing 1, 10, 50 buildings; shared storage
   does not yet imply one draw call.

This file contains **no owner assets or signing secrets**.
