# OE Engine — complete 3D game runtime roadmap (2026-11-05)

## Scope and non-goals
OE is an **experimental Android C++17 + Java + GLES3 scene editor**, not yet a complete general-purpose game engine. The primary ORVETHRA MMORPG runs in a **separate Godot project**. Never conflate repos or migrate content until the OE Runtime passes a feature-parity and device test gate.

The goal is a portable 3D runtime with stable scenes, reusable game logic, debugging/profiling, support for modest Android hardware (including 4 GiB RAM tablets), and a standalone build pipeline. No engine can guarantee a particular FPS on every GPU, CPU and driver.

## Architecture contracts (recommended sequence)
| Milestone | New capability | Minimum acceptance criterion |
|:--|:--|:--|
| M1 — Hardware & profiling | GLES GPU capability discovery, RAM-aware graphics presets, render-scale switching, CPU/GPU/frame counters, thermal hints | Profile is shown to user, persists across launches, switches without deleting scene, QA on ≥3 device classes |
| M2 — Runtime core | Separate editor and standalone runtime; components (Transform, MeshRenderer, Camera, Collider, Script), deterministic fixed timestep with variable render interpolation; prefab/scene IDs | Runs a minimal exported interactive scene without editor UI, fixed timestep CI tests |
| M3 — Physics / movement | Broad phase (spatial grid), collision shapes, character controller, terrain collision, raycast, layers, deterministic stepping | Player walks on hills, cannot pass through wall, stable on 30 and 60 Hz |
| M4 — Gameplay scripting | Sandboxed logic API, script lifecycle/events, input action mapping, versioned serialization and crash isolation | User can create an interactive collectible and a door using only editor |
| M5 — Animation / assets | Complete static GLB and compressed Draco/Meshopt/KTX2; skinning, morph support, animation blending, PBR materials | Official glTF sample fixtures, no silent animation stripping, source GLB bitwise intact |
| M6 — Graphics quality | Shadows, baked lighting, instancing, occlusion, texture streaming, correct transparency and gamma, shader compilation cache | View measured in GPU profiler on low / middle / high device |
| M7 — World loading | Asset dependency graph, world chunk index, asynchronous I/O/decoding, CPU/GPU memory budgets, eviction, world origin | Walk across 16km world while memory stays bounded with many loaded assets |
| M8 — Audio / UI / nav | 3D sound/music mixer, responsive UI/HUD widgets, pathfinding/navmesh, enemy AI utilities | Small offline 3D game with combat, UI and audio works end to end |
| M9 — Build/export | Importable project format, stable resource identifiers, compiler/build assets, separate Android APK/AAB game template, signing/export wizard | Editor creates standalone installable game APK that loads original scene and responds to input |
| M10 — Desktop & networking | Render hardware abstraction GLES/Vulkan, platform input/audio/file layers; authoritative networking optional | Headless tests + Windows/Linux game builds; networking only when required |

## Quality profiles (first implementation target)
Economy: 55% buffer, 3x3-radius terrain tiles (7×7), 130m culling, 12 terrain paint decals. Low: 70%, 4-radius tiles (9×9), 180m, 24 decals. Balanced: 85%, radius5 (11×11), 230m, 36 decals. High: 100%, radius6 (13×13), 300m, 48 decals.

These affect **editor viewport rendering**, not a future game's simulated FPS. 1024px GLB source texture import is standard except High (2048px). Editing projects is unchanged; scene geometry/data is never deleted by changing quality.

Auto chooses Economy for ≤2GiB or software rendering; Low for ≤4GiB or limited GL texture capability; Balanced for larger memory. This initial conservative heuristic **cannot measure GPU speed** and does not automatically claim the optimum. Profiles must be user-overridable.

Future: time-smoothed per-frame CPU/GPU latency estimates, Android Frame Pacing and ADPF thermal callbacks with hysteresis, supported ASTC/ETC2 resource delivery, profiled default budgets, performance recording/export, continuous CI/device farm hardware matrix.

## Performance acceptance (targets, not guarantees)
- Basic playable low-detail scene: 30 FPS **target** on a representative Android 4 GiB device, budget ≤33.3 ms/frame. 60 FPS target for capable devices requires ≤16.7 ms/frame.
- Record 1% slow frames, 95th/99th frame duration, CPU vs GPU time, memory and thermal throttling after **15–30 minutes** sustained playback.
- Test at least Android low/Mali, mid/Adreno and high/Adreno or desktop; test device-specific driver bugs (not just SoC name).
- Editor remains event-driven / no idle redraw; game runtime *will* need a paced continuous tick.
- Asset import must never silently lose animation or alter source data.

## Status at inception
0.6.10: Android GLES3 editor, GLB Meshopt static preview, partial KTX2 (GPU-native ETC2 only), static Meshopt LOD, imported custom PNG/JPG paint with decals, world sculpting, XY(Z) touch gizmos, free 1mm nominal steps, .oezip archives. Known missing: Draco, BasisU transcoding, skeletal animations, physics, scripting, runtime player loop, game APK export, Windows/Linux runtime, advanced profiling/thermal adaptation.

All experimental changes belong in draft PR branches; never merge to OE main or alter ORVETHRA without explicit permission.
