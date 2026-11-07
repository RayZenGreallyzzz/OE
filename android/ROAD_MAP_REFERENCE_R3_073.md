# OE Road Map Reference R3 — PNG city plan overlay (experimental)

Branch: `test/road-map-reference-r3-073`; base `test/road-editor-r2-072`. Debug package: `game.oe.editor.roadmap`.
Neither OE `main` nor the ORVETHRA game is modified. No merge without explicit approval.

## Included
- **Дороги → 🗺 Загрузить PNG-план города:** Android document picker accepts real PNG only, <=8 MiB source. Decodes offline to RGBA8 with a maximum rendered dimension of 2048 px. An intact original source PNG is stored by its SHA-256-derived key (ref_...png).
- The translucent image is a **reference**, not a gameplay object. It is draped over the heightfield in a bounded mesh patch near the camera, below editable road geometry. The original alpha channel is respected, with adjustable extra opacity.
- **Дороги → 📍 Положение и масштаб плана:** center X/Z in world metres, map width in metres (height follows PNG aspect ratio), opacity 5–95%. **👁 Show/hide** keeps image and calibration. **⬇ Вид сверху** focuses the reference; **🎥 3D** returns to perspective. One finger pans while in top view, two fingers pan/zoom; taps while in road mode create control points. Tracing remains segmented Road Editor R2 geometry.
- Native GL-thread PNG stage/commit/abort prevents broken new images replacing the active guide. The GLES texture and bounded mesh are rebuilt on EGL context loss or terrain/grid changes; only the visible piece is meshed.
- **Persistence:** scene v3 with `mapReference` metadata is written only when a guide is active. Existing v1 and v2 scenes load without a reference; v2 road data is still handled. The original PNG lives in `references/ref_<hash>.png` in app storage and `.oezip`. Project ZIP import checks exact paths, hash, size, PNG signature and presence of referenced files; invalid scenes are rejected before runtime scene swap. R2's v2 .oezip version-check bug has been fixed in this branch.

## Not included
- No cursor-offset freehand terrain painting, splat maps, real textured roads, point dragging, junction stitching, spline curves, bridges, nav/collision baking, standalone game export or game Runtime guide resource. PNG guide is editor-only and is never a Runtime asset.
- Native scene Undo/Redo tracks road/scene edits, **not reference calibration**; reference changes are saved by local Save / .oezip export. Guide images are stored at 8 MiB and rendered at <=2048 px: extremely detailed city plans may require chunked sources in later stages.
- The renderer is GLES3 and updates when requested; physical tablet behaviour, texture orientation, precision, quality and memory must be evaluated on the target device. An APK compilation alone is not device QA.

## On-device tablet test
1. Install R3 APK alongside OE R2. Open **☰ Каталог → Дороги → 🗺 Загрузить PNG-план города** and choose a **transparent** PNG with clearly marked streets and a compass indication. PNG should have actual alpha (no extra conversion from JPG).
2. Confirm the map appears semi-transparent over the terrain. In **📍 Положение и масштаб**, set center X/Z to city coordinates and choose width, e.g. 256 m, opacity 40%. Check aspect ratio and orientation.
3. Select **⬇ Вид сверху**. One-finger drag should pan, pinch should zoom, while the map remains top-down. Select **🛣 Новая дорога**, touch two road points on the PNG, then more points along a bend. Road geometry must display visibly above the guide.
4. Switch **👁 Show/hide** repeatedly; road geometry should be identical. Switch to 3D perspective, then back. In Economy/High graphic profiles verify alpha and no extreme GPU stalls.
5. Save scene, restart app and load; verify guide and roads restore. Export .oezip, reset and import; verify original PNG and road points. Also test importing a **v2 road project** and an older **v1 editor project**.
6. Test a corrupt PNG, fake JPG renamed PNG, oversized (>8MiB) PNG, invalid ref hash and path traversal in .oezip: these should reject safely without clearing an active project. Test Android pause/resume (EGL context re-creation).
7. Report screenshots and any issues with overlay placement and scrolling. All device tests are still pending.

Next: R4 cursor-offset continuous brush and scalable per-chunk terrain weight masks. Then R5 proper road intersections, physics and navigation integration.
