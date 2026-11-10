# Orvetra Engine (OE) — agreed development roadmap

**Status:** working plan, 2026-11-09. **Priority agreed with the user:** finish Road & Terrain before switching the main development focus to playable Runtime and on-tablet Android APK export.

**Safety:** OE is a separate project from the ORVETHRA game. Never modify ORVETHRA or OE `main` as part of experimental milestones without explicit user authorization. Keep new code in experimental branches, draft PRs, and test apps. Android tablet target: Allwinner A523, 4 GiB RAM; maintain predictable CPU/GPU/RAM budgets. Passing CI does not mean tablet QA.

## 0. Existing experimental editor foundation — built, requires physical QA

- R1: portable C++ road network and terrain-conforming ribbon geometry — draft PR #15.
- R2: road control points, width/material preview, selection/undo/save — draft PR #16.
- R3: transparent PNG city-plan guide, top-down camera, portable source PNG in .oezip — draft PR #17.
- R4: finger-offset continuous texture brush; 64 m composited paint tiles, compressed project data — draft PR #18.
- R4.1: separate per-material weight masks; eraser for selected material, v5 scene format — draft PR #21.
- R5: editing control point positions (touch or X/Z), rounding corners, endpoint snap, visual crossing patches — draft PR #22.

These features are implemented in experimental branches with successful automated build/tests but **not confirmed on the physical target tablet**. Current R5 intersections are visual preview discs only, not actual connected topology; no Play/Stop or on-device APK builder yet.

## 1. FINISH THE ROADS FIRST (priority before game Runtime)

### R5.1 — real connected road graph (experimental code, tablet QA pending)
- Split road centerline segments when new roads terminate or cross them; shared junction node IDs, stable editable road IDs and topology.
- T, X, Y junctions; endpoint-to-segment snapping and validation of edge cases (parallel overlap, near crossings, sharp bends and tiny segments).
- Bounded operations; accurate save/load and .oezip roundtrip; undo/redo of graph edits.
- Portable host tests plus Android debug build and on-tablet touch QA.

### R5.2 — joined medieval junction mesh (experimental implementation; physical QA pending)
- Replace approximate crossing patches with **stitched** meshes, avoiding gaps, overlaps and Z-fighting.
- Correct widths, corner radii, worn cobblestones and dirt/gravel material transitions; medieval grass edges, wagon ruts and drainage ditches instead of modern asphalt, concrete curbs or sidewalks.
- Terrain conformity and predictable vertex/draw-call budgets on A523.
- Top-down PNG guide alignment and precise control-point editing validation.

### R5.2a — lightweight medieval bridge and building foundation modules (experimental implementation; device QA pending)
- Two short bridges: pre-baked stone span (8 m) and timber footbridge (8 m); modular stone base for existing 6×5 m buildings.
- Place/rotate/scale directly from the editor; one action places a foundation aligned under the selected built-in building.
- Constant-size shared C++17 geometry without new runtime shaders, separate per-object textures or internet access; all original editor controls and undo remain.
- Scene v6 only when new object types are present; previous scene versions v1–v5 continue loading. Keep a .oezip backup before importing v6 into older editor builds.
- Not water/terrain carving, automatic bridge-to-road endpoints, collision, physical stone arch construction or grouped parent-child building transforms.

### R5.2b — medieval bridge/foundation variations and code backup (experimental implementation; tablet QA pending)
- Preserve R5.2a at GitHub source branch `backup/oe-medieval-r52a-2026-11-09` before modifying new variants.
- Add 9 more lightweight structures: narrow footbridge, rope bridge, stepping-stone ford, timber pile foundation, ruined stone plinth, hut foundation, tower foundation, wide cart bridge, and raised swamp boardwalk — 12 total.
- Dedicated `Мосты` and `Фундаменты` tabs and selectable 4-style auto-fitted foundation placement under stock buildings.
- Maintain one shared static mesh per module kind, <=40 boxes and 480 triangles per mesh, <=300 editor objects, and backwards scene v1–v6 import (new objects remain v6).
- Backup packaged APKs and release archives are not a backup of the user's local `.oezip` cities. Export those separately on the tablet.
- Bridges still have no game physics/automatic road attachment and do not cut riverbeds. R5.3 remains road collision and nav foundations.

### R5.3 — terrain and lake-water editor (experimental; requested before road collision)
- The terrain raise/lower brush remains the excavation method. Add `Вода` editor category to fill basins with translucent terrain-clipped water and editable water level/radius.
- Up to 8 lakes with bounded cached geometry; water remains purely visual, **not** a swimmable/physical volume.
- Scene v7, Undo/Redo, explicit `.oezip` validation and forward compatibility check; older v1–v6 scenes continue to load. GitHub backup R5.2b: `backup/oe-medieval-r52b-2026-11-10`.
- Current priority shift was user-requested; road collision/nav is retained next, **not silently dropped**.

### R5.4 — road navigation and collision-ready data
- Build a separate road graph for future vehicles/NPCs, with junction and direction metadata.
- Provide simplified collision mesh or collision shapes for medieval roads, bridges and natural road shoulders (not modern sidewalks/curbs).
- Test crossing connectivity, saving, scene loading, and performance. Avoid claiming full traffic AI at this stage.

### Road quality gate
- Test actual road creation, point moving, smoothing, snapping, multiple T/X/Y junctions, PNG overlays, layered materials, Undo/Redo, local save and .oezip import/export on the target Android tablet.
- Address bugs before starting major unrelated functionality. Preserve backward compatibility with old scene versions or provide an explicit migration path and backups.

## 2. REAL GAME RUNTIME (after roads quality gate)

### Runtime P1 — Play/Stop
- Make a runtime-only copy of an editable scene and start/stop safely without losing unsaved editor changes.
- Separate editor-only resources and gizmos from playable content.
- Basic game entities, update loop, camera and input, with predictable pause/resume and memory usage.

### Runtime P2 — controllable character and physics
- Touch input/joystick; spawn/player camera; walking, gravity and jump.
- Collision against terrain, objects and eventually road/sidewalk shapes.
- Physics behaviour and frame-time diagnostics on A523.

### Runtime P3 — interactive game logic
- Event/action system or bounded script support, triggers, interactable objects, levels and simple NPC behaviour.
- Scene serialization for runtime components, platform-safe sandboxing, tests, documentation.

### Runtime P4 — standalone game runtime
- Lightweight Android shell containing only gameplay code and necessary resources; **no editor UI** in a published game.
- Loading exported project data, images, models, scenes and controls.

## 3. ON-DEVICE APK EXPORT — required end goal

### Build B1 — project package
- Validate game-specific dependencies, assets, game icon, app name and unique Android application ID; produce clean game-only bundle.
- Export errors should not corrupt the open OE project.

### Build B2 — APK builder running ON THE TABLET
- Prefer a precompiled/reusable ARM64-compatible game-runtime template and Android packaging instead of recompiling the C++ engine for every export.
- Confirm compatible on-device Android build tools (aapt2/zipalign/apksigner or equivalent), SDK/platform assets and signing keys. Never assume desktop-hosted x86-64 tools run on ARM64.
- Package, zipalign where required, sign, verify and save an installable standalone `.apk` to an Android-accessible location. No PC or GitHub Actions required for end-user builds.
- Support offline build after dependencies are installed, low-memory streaming, meaningful progress/error messages, cancellation, and cleanup.
- **Acceptance test:** make a simple 3D level using OE, press Play, press Stop, choose Build APK on the Allwinner A523 tablet, install the resulting game APK, run it **without OE installed**, and verify graphics, player input, collisions and resource loading. Long build time is acceptable if it works.

### Build B3 — scale and polish
- Profile memory, shader compilation, draw calls, app size, terrain/road streaming and heat; introduce settings for low-end Android devices.
- Preserve a small reliable sample project and automated build/roundtrip regression tests.

## Non-goals and truthful constraints
- The goal is a **usable independent Android game engine/editor with on-tablet APK export**, inspired by Unity/Unreal workflows, not feature-by-feature parity with Unreal Engine 5.
- No claim that Play/Stop, full game physics, NPC navigation, standalone export or local Android APK compilation exists until it is implemented and validated.
- Always distinguish successful CI APK builds of the **OE editor** from successful user-device builds of a **standalone game APK**.
- Do not start the next milestone simply because a previous experimental APK compiled. Get real tablet validation and preserve project backups.

## How to continue future chats
Reference `android/OE_DEVELOPMENT_ROADMAP.md` from the experimental R5 branch. **Continue with R5.1 connected roads**, then R5.2, R5.3, then Runtime P1–P4 and APK Build B1–B3. This file is the durable technical plan even when chat memory is unavailable.
