# Orvetra Engine — Medieval Modules R5.2b (variety + source backup)

**Branch:** `test/medieval-modules-variety-r52b-080` (based on R5.2a), **DRAFT** PR only.
**Protected source snapshot:** `backup/oe-medieval-r52a-2026-11-09` at commit `fb3f9bafc376f6daf4a8428dc56c94663a5de0b8`. This branch preserves the COMPLETE repository tree at that commit, including all its code, previous history, docs, and workflows. It is not your locally saved tablet project.
**Separate Android test app ID:** `game.oe.editor.medievalvariety` (ARM64 GLES 3, version 0.8.0 test).

## Twelve reusable medieval modules, zero external downloads

The 3 previous kinds (17–19) remain unchanged, and 9 additional kinds (20–28) use the same bounded pure C++17 `medieval::moduleParts` representation. All units are pre-baked once into one shared static mesh per kind, and draw with a single GLES call **per visible object**, without adding fragment shaders, texture downloads or new animation loops.

| Kind | Russian name | Use |
| --- | --- | --- |
| 17 | Каменный мост | Simple old stone bridge, approx. 8 m |
| 18 | Деревянный мост | Rustic timber crossing, approx. 8 m |
| 19 | Каменный фундамент | Stock tavern / forge building plinth, approx. 7 × 6 m |
| 20 | Узкий мостик | Unrailed 6 m footbridge over little ditch |
| 21 | Верёвочный мост | Timber/plank crossing with approximate rope rails, 9 m |
| 22 | Каменный брод | Stepping stones at shallow ford, ~8 m |
| 23 | Свайный фундамент | Elevated timber platform / piles over marshy ground, ~7 × 6 m |
| 24 | Руины фундамента | Stone plinth with broken low remnants, ~7 × 6 m |
| 25 | Основание хижины | Compact stone hut pad, ~4 × 4 m |
| 26 | Основание башни | Heavy square tower base, ~7 × 7 m |
| 27 | Широкий мост для телег | Wide, 10 m stone cart crossing |
| 28 | Настил через болото | Timber boardwalk on short driven posts, ~9 m |

Use `☰ Каталог → Мосты` or `☰ Каталог → Фундаменты`, tap the ground to place an item, then move/rotate/change Y and scale with the existing 3D gizmos. Old bridge shortcuts remain under `Дороги`. Exact coordinates, duplicated objects, removal and Undo/Redo work through the existing ordinary object editor.

To place a foundation directly under a stock tavern / forge / auction house, select that building, go to `Здания` or `Фундаменты`, tap `⬇ Под выбранное здание`, then pick stone, timber piles, ruins, or tower base. The small 4×4 hut foundation is intentionally excluded from automatic alignment (it is narrower than the stock 6×5 m buildings). The original building remains untouched. This is **one-time geometric alignment**; moving the building later does not automatically move the foundation.

## Compatibility and performance limits

- Kind values 17–28 require scene **v6**, and exported `.oezip` of such a city is not readable by older OE releases. A city using only kinds 0–16 remains in the earlier scene versions. Current EditorActivity/ProjectArchive/native parser now accept valid scene v1–v6 and guard against using kinds >16 with earlier versions.
- The pre-baked primitive meshes contain at most **40 boxes** (480 triangles) per kind; all twelve combined use <6000 triangles in shared static GPU data. Each placed object remains subject to OE's existing 300-object cap, on-screen frustum culling, and renderer quality settings. They can all be recoloured with the existing material controls.
- Bridges do not connect the existing road graph automatically, carve rivers/terrain, generate realistic stone masonry, simulate ropes, add AI navigation or create physics collision. The *bridges and their railings are visual editor modules*, not yet usable collision geometry in a game.
- Ruined foundation stones may protrude around a house; pile foundations are raised. For unusual terrain, place the module and adjust Y manually. No modern concrete curbs, asphalt or traffic lights.
- Older Android OE test versions can stay installed alongside this one due to unique debug package ID. App testing on the **actual Allwinner A523 4 GB tablet** is still required; CI passing is not a physical device test.

## Real backup: what is and isn't included

The backup branch on GitHub is a full immutable-by-convention pointer to the OE source tree **BEFORE** the nine-module expansion. Keep it untouched. The downloadable backup pack includes earlier debug APKs, their GitHub Actions ZIPs, version references and checksums; the separate new test APK is also included once available. These are **software recovery copies**, not a backup of the user's personal `.oezip` city projects.

For **tablet project backup**, open the active OE editor, export each city as `.oezip`, and copy it off the tablet (e.g. local USB / private external storage). Never rely solely on Android app private storage when changing test builds.

## Tablet acceptance tests

1. Create every type from `Мосты`, `Фундаменты` (7 and 5). Check all twelve kinds are visible in object lists and can be selected and moved.
2. Rotate the stone cart bridge over a dry ditch; place rope bridge, narrow boardwalk, shallow ford and swamp causeway. Inspect gaps, clipping, shadows and height; manually raise Y over water/ground.
3. Select each built-in building, pick four compatible auto-fit plinth variants and check that the original building's position/scale/ID is unchanged.
4. Save and reload a v6 city and export/import an `.oezip` with several varied modules, road junctions, PNG map reference and material masks; check Undo/Redo.
5. Background/restore OE (GL context rebuild): all shared meshes re-uploaded and visible.
6. Stress 40–100 instances at quality settings suitable for 4 GB RAM; monitor frame time, draw calls, heating and memory.

Road and game plan preserved: next true R5.3 road collision/nav-ready data, then Play/Stop, character and physics, and offline on-tablet **standalone game APK** exporter. `main` and the separate ORVETHRA game must remain unchanged.
