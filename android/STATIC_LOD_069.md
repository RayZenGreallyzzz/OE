# OE 0.6.9 — Meshopt Static LOD + Texture Paint TEST

**Experimental branch** `test/static-lod-069`. Installs separately as `game.oe.editor.lodtest`; all previous OE/editor apps, their local scenes, OE main and ORVETHRA are untouched. Built on 0.6.8 image painting features.

## Real implementation
- Android APK includes official MIT **meshoptimizer** remapping/simplification C++ sources, built into the app. No internet required to render or simplify GLBs.
- For static, unsupported-motion-free GLBs, generates at most 2 new geometry levels in memory using `meshopt_generateVertexRemap` and `meshopt_simplifyWithAttributes`. Normal, vertex color and UV are included in the error metric. Each GLB primitive/material draw batch is simplified separately to preserve material slots.
- LOD0 original rendered geometry is **never rewritten** or simplified destructively. LOD1 and LOD2 are optional cached GPU vertex arrays. Selected objects always use LOD0 for editing.
- Conservative distance selection: LOD1 around 55m, LOD2 around 125m, adjusted by object scale. No per-frame simplification, only camera-relative selection. If simplification cannot reduce enough or would exceed a 40 MiB per-session LOD source-memory cap, the original geometry is retained.
- Morph targets, skins and animations are excluded from automatic simplification. Source GLBs stay untouched and export archives retain exact originals. This release **does not play** skeletal animations.
- Renderer statistics report per-tier counts `LOD 0/1/2` so device testing can verify actual selection.
- All 0.6.8 editable PNG/JPG wall textures, terrain paint brushes, portable archives and 0.6.7 full-screen gizmos remain.

## Not implemented / not claimed
- Draco `KHR_draco_mesh_compression` decoding.
- Basis Universal ETC1S/UASTC KTX2 transcoder.
- GPU skinning, animation playback and mesh simplification of animated characters.
- Per-bone LOD, full PBR, standalone GLB/FBX world export, advanced terrain splatmaps.
- We cannot guarantee every static mesh yields exactly three distinct triangle counts; meshoptimizer may decide that a material/geometry is unsafe to simplify. LOD is a quality/safety tradeoff.

## Tablet QA
1. Import a **static** GLB with >=~450 triangles, move camera near then far; renderer stats should show LOD0 at short distance and LOD1/2 only when generated.
2. Compare near/far visuals, UVs and material seams; if any distortion, note model type and distances.
3. Test imported **animated/skinned/morph** GLB: must not be auto-simplified; the editor remains a *static preview*, not an animation player.
4. Paint custom stone PNG on a wall, paint sculpted hill, save and export/import .oezip; confirm source files retain exact hashes.
5. Compare idle battery use with prior version; on-demand GLES rendering and 75% EGL buffer remain.
6. Try invalid GLB, corrupt texture, bad ZIP: older scene must remain intact.

GitHub Action compilation and host smoke are required but do not replace on-device checks.
