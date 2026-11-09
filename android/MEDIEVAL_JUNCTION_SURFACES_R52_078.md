# OE R5.2 — Medieval Joined Junction Surfaces (experimental)

**Branch:** `test/medieval-junction-surfaces-r52-078` based on R5.1 `test/medieval-road-topology-r51-077`.
**Separate Android test package:** `game.oe.editor.medievaljunctions`, ARM64 debug.
**Do not merge to OE main or touch the separate ORVETHRA game.**

## What changed

R5.1 gave streets a real connected X/T/Y centreline graph but continued to render independent road ribbons with overlapping circular crossing decals. R5.2 renders road surfaces that terminate at mathematically matched junction cap edges, then stitches the enclosed junction polygon with shared boundary vertices.

- A new pure C++17 portable module `roads/medieval_surface.{h,cpp}` consumes `RoadTopology` and the existing terrain-height callback, producing `MedievalSurface` with individually selectable road-ribbon meshes and nonselectable junction fill meshes.
- At each shared road node, incident half-widths and street bearings determine a safe centre radius. Road segments stop at their cap boundaries; the polygon connects the same cap vertices by an upward-wound centre fan. For supported X/T/Y shapes, this removes the deliberate overlap between road strips and the old R5 circular patches.
- Width and height follow the same terrain as the original roads (road surface height +0.14 m; this is a rendering separation above other overlays, **not a raised sidewalk or modern curb**).
- For winding roads that do not touch other streets, the previous R5 ribbon builder is reused unchanged.
- Subtle, deterministic world-space colour variation across triangle vertices suggests older worn cobbles/dirt, without fragment-shader loops, textures downloaded at runtime, or animation costs. Available built-in preview colours still include cobblestone, dirt, sand and grass.
- GLES3 caches the rebuilt road mesh until road revision or terrain revision changes. Road selection, road-width editing, layer paints, Undo/Redo and v1–v5 project serialization are retained. The graph is derived; no new JSON schema is required.
- A new menu action **`🪨 Проверить швы перекрёстков R5.2`** reports whether the joined renderer is active, junction count and triangle budget.
- Edge-case safety: extremely close adjacent junctions, parallel overlapping rays, 96+ junctions, >16 rays at one node, overly wide roads and invalid/NaN terrain height fail as an atomic mesh build. Those maps retain the **legacy R5 road-and-patch visual fallback** instead of showing broken partial geometry.
- New host geometry tests include X/T/Y, exact shared cap vertex positions, terrain conformity, proper winding, no-junction R5 compatibility, and failure atomicity.

## Honest limitations

R5.2 is **seamless at the road ribbon/junction cap in the horizontal XZ plane for supported ordinary X/T/Y crossings**. It is not yet an advanced welded/mitered UV unwrapping system, seamless blended dirt material at boundaries, surface collision geometry, pathfinding, stable overpasses, automatic building avoidance, terrain-carving ruts or actual flowing drainage ditches.

Natural dirt shoulder geometry, worn stone diffuse patterns, cobble-edge masks, wagon ruts and medieval gutters need separate art/geometry work after reviewing on-device appearance. **No modern concrete curbs, standardized sidewalks, asphalt, or traffic lights.**

The physical tablet has not yet been used for QA. The renderer may fall back on problematic shapes by design; the status diagnostic distinguishes the supported joined case.

## Physical QA on Allwinner A523 / 4 GiB Android tablet

1. Export a backup of your R5.1 `.oezip` before trying this separate R5.2 package.
2. Make a T crossing with one path ending near the centre of another, a perpendicular X and a 3-road Y.
3. View each joint from top-down with the PNG plan, then at street level. Check for gaps, visible overlaps and flashing (Z-fighting), and whether bumpy terrain alters the edges.
4. Open `Дороги → 🪨 Проверить швы перекрёстков R5.2` and confirm the stitched status and number of triangles.
5. Try a sharp corner, several very close junctions and unusually wide roads; verify the safe older surface fallback rather than crashes or scene corruption.
6. Verify `📍 Переместить точку`, `⌨ Точные координаты`, `〰 Сгладить повороты`, Undo/Redo, scene save/reload and .oezip import/export.
7. Verify older R1–R5 projects and preservation of PNG-guide position, R4.1 material layers and imported 3D assets; report FPS, draw calls, memory and temperature.

## Longer-term plan

Next is review/refinement of medieval road-edge aesthetics, then R5.3 road collision / navigation-ready graph for future characters and NPCs. After roads and physical QA: editor Play/Stop, player/physics and Runtime Core gameplay, followed by an offline on-tablet builder that packages and signs standalone **game** APKs (not just the OE editor APK). This remains the agreed development roadmap in `android/OE_DEVELOPMENT_ROADMAP.md`.
