# OE 0.7.13 v40 — opt-in UV56 terrain height transfer to native OE city

Status: **DRAFT / source+CI only; on-device validation required**.

Owner goal: one OE app with the original Phoenix UV44 retained as an untouched
recovery baseline, UV56 building+landscape working inside one Three.js scene,
and a real bridge to the native city — without silently rewriting either
editor, risking installed saves, or copying private licensed models to GitHub.

## What is implemented

* In **OE ▾** inside the integrated UV56 studio, new action:
  **🗺 Передать высоты ландшафта в город OE**.
* Action explains the scope and asks owner confirmation. The studio writes
  a validated, atomic Phoenix JSON snapshot before closing; a failed save
  cancels handoff, without touching city or current studio.
* Native OE verifies the exact UV56 `terrain` v1 schema:
  `{version:1,areaSqM,unit:"m",step:1,nodes:[[x,z,h_cm,paint_id],...]}`.
  The original 1m sample positions and centimetre heights are not resampled
  or rounded. Native `phoenix_terrain.h` uses sparse node lookup and
  bilinear interpolation matching UV56 `LandscapeManager.heightAt`.
* The imported native city layer is separate from OE's pre-existing terrain
  brushes and paint. Houses, roads and normal editor assets remain.
  A repeat transfer replaces **only** the previous Phoenix height layer.
* GPU preview uses 1m vertex sampling near camera tiles affected by Phoenix
  edits, 4m LOD farther away. This changes only the GPU tessellation
  (not original node coordinates, JSON or exported Phoenix scene).
* Native `recordEdit()` saves the old layer: **↶ Undo** reverts a transfer.
  Native OE v1 snapshot serializes optional `phoenixTerrain`; project open,
  save, and `.oezip` round-trip this field. Older scenes without the field
  load with an empty imported layer. Whole-scene native replacement still
  validates EVERYTHING before mutation.
* `ProjectArchive.MAX_SCENE_BYTES` now allows 4MiB (formerly 512KB) because
  32,768 exact terrain nodes may exceed the old bound. 512MiB total archive
  safety budget and model/texture limits remain unchanged.

## Deliberate limits / why this is not a complete terrain bridge yet

1. **Height only.** UV56 tile images, 1024x1024 paint masks, road mesh and
   UV56 road borders do NOT yet transfer into native terrain. These stay
   intact and editable in the UV56 studio. A house+landscape **GLB** can still
   be exported by the existing Phoenix exporter and imported as a model;
   exact material equivalence is not promised.
2. For tablet safety, maximum **32,768 edited 1m nodes**, area 1000..2,000,000
   m², supported world 1024..16384m. Choose world size >= sqrt(areaSqM).
   Native ground heights must fit **-24..140 metres**. Invalid or oversized
   terrain is **rejected in full**; the UV56 project and previous native city
   are left untouched.
3. The native OE world already had broader low-resolution brushes. Their
   heights add to the imported UV56 heightfield. The native preview is not
   yet pixel-identical with UV56 Three.js (road surfaces, tile paint/PBR,
   shadows/IBL, horizon geometry, tablet thermals and FPS).
4. This is one Android app with an integrated UV56 live WebGL build/landscape
   space and **a separate native city GLES renderer**. Shared editor data
   transport is advancing; it is NOT a single unified GLES/WebGL context.
5. Never publish the owner's 1832+ Phoenix GLBs, textures, paid packs, saved
   projects, or signing key in the public GitHub. Never uninstall existing OE
   to apply a differently signed test APK.

## Real tablet QA acceptance

1. In UV56 studio, create a small, sharp, 1m-wide hill; verify height
   and undo in studio; confirm the original house is unchanged.
2. OE ▾ → **🗺 Передать высоты** → confirm; check city native terrain
   near origin, ensure existing placed buildings and roads remain.
3. In OE use ↶ undo and ↷ redo. Confirm only the imported height layer
   changes. Repeat transfer after modifying the same hill.
4. OE → save JSON → restart → load; also export .oezip/import it and
   verify exact terrain nodes as well as original city houses.
5. Test malformed/oversized source, not enough free memory, world size
   smaller than the UV56 area. Failure must leave old city intact.
6. Compare color, custom textures, paint, road meshes separately: they are
   NOT supported by this height-only bridge and should not be represented
   as already fixed. Full-source portable backup must still be exported
   from the UV56 Files tab.
7. Monitor FPS/RAM on the owner's Android tablet; test chunks at negative
   X/Z coordinates, 64m boundaries, and zoom transitions between LODs.

No release APK, no merge to `main`, and no device-success claim.
Rollback source branch: `backup/oe-v38-before-unified-studio-2026-11-19`.
