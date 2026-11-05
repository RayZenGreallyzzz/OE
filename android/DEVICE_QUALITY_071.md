# OE 0.6.11 — Hardware-aware Graphics Quality TEST

Isolated Android test application `game.oe.editor.qualitytest` built on experimental OE 0.6.10. This branch modifies **neither OE main nor the separate ORVETHRA game**.

## What works in this test branch
- **Проект → ⚡ Графика и качество**: four user-controlled profiles (Экономия/Низкое/Баланс/Высокое) plus an automatic conservative recommendation. The selection persists across OE restarts.
- **Проект → 🖥 Информация о GPU**: OpenGL ES vendor, GPU renderer string, API version, GL_MAX_TEXTURE_SIZE, GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, ASTC extension detection, device RAM, and initial recommended profile. No internet permission required.
- Genuine GLES viewport control: the GL buffer follows the selected profile (55%, 70%, 85%, 100%) while keeping the same physical full-screen editor and touch mapping.
- Actual GPU draw-work changes: cached 64m terrain tile radius (3/4/5/6), static and imported object culling range (~130/180/230/300m), distance-based static GLB LOD thresholds, and maximum visible painted terrain decals (12/24/36/48) per frame. The render target updates when changing the selection, and the tile cache is invalidated.
- Default image quality for **new GLB imports** is 1024px for Economy/Low/Balanced and 2048px for High; original GLB and user PNG/JPG files are never altered.
- Stats overlay includes last CPU draw-call *submission time* plus draw calls/triangles and selected profile. Editor remains `RENDERMODE_WHEN_DIRTY`: an idle frame rate is not meaningful and not measured.

## Real limitations
- **AUTO is a heuristic**, not machine learning or a GPU benchmark. It uses system RAM, max GLES texture size and software GPU detection. <=2 GiB/software => Economy; <=4 GiB => Low; otherwise Balanced (never defaults to High). GPU drivers often advertise features without indicating performance.
- This is **editor-only** quality tuning, NOT a finished game runtime, 30/60 FPS guarantee, ADPF thermal feedback, Vulkan renderer or responsive dynamic-resolution scaling.
- Removing objects from the current view at lower quality is temporary distance culling. No scene content is deleted; changing quality does not change the saved JSON/oezip project.
- CPU submission duration is NOT GPU elapsed time or total frame time; use Android GPU Inspector/Perfetto and real device tracing for deep bottleneck diagnosis.
- ASTC report indicates availability of GL ASTC extension; no ASTC asset generation/transcoding is provided yet. ETC2 texture preview support from 0.6.6 remains.

## Tablet verification
1. Install OE DEVICE QUALITY TEST alongside previous OE test apps (package is separate).
2. Open **☰ Каталог → Проект → 🖥 Информация о GPU** and check GPU vendor, GL version, RAM and texture-size output.
3. Open **⚡ Графика и качество**: switch Economy, Low, Balanced and High. Compare the visibility distance/terrain range, sharpness and responsive touch axes.
4. Export a textured, painted LOD test scene as `.oezip`; reimport and verify no objects/textures are lost on profile changes.
5. Close/reopen app; verify choice persists. Set AUTO; on a device <=4 GiB it should select Low unless GPU capability forces Economy.
6. Monitor temperature and power use over >15 minutes, plus frame pacing when moving the camera continuously. We do not promise specific FPS until measured on hardware.
7. Malformed GLB or archive files must still fail safely. All existing 0.6.10 millimetre XYZ positioning, textures, hills, LOD, undo, import/export must keep working.

See `docs/OE_ENGINE_MASTERPLAN.md` for the complete path to a 3D runtime, physics, scripting, animation, export, PC support and performance instrumentation.
