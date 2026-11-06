# OE 0.7.0 Game Runtime Core TEST — milestone 1 of playable engine

**Status: portable game kernel + editor scene bridge; NOT YET PLAYABLE.**
Branch: `test/game-runtime-core-070` (based on experimental `test/universal-import-072`).
Android ID: `game.oe.editor.runtimecore` — isolated from 0.6.12 and stable OE main.
ORVETHRA remains a separate Godot MMO; its repository is NOT touched.

## Actual implementation

- `android/src/main/cpp/runtime/oe_runtime.h/.cpp` contains a standalone **C++17 portable runtime library**. It includes ZERO Java/JNI, GLES, Android, editor UI or file-system platform imports. Build it independently with g++/clang++ on Linux/Windows/Android.
- Entities use generation-tagged stable handles; invalid/stale handles are rejected after deletion, clear or scene replacement. Capacity bound at 4096 entities, default 4096.
- Data components: `Transform` (double-precision position, orientation quaternion, scale), `MeshRenderer` (asset ID, visibility), `Velocity` (metres/second), `Camera` (lens/clipping metadata), `Collider` (shape metadata **ONLY**, no collision detection). Entity names and optional components are independent.
- Simple deterministic movement system at **fixed simulation step 1/60s**, with pause/resume and maximum 8 substeps per incoming frame. Long stalls discard backlog instead of running unbounded work; rendering can interpolate using `StepResult::interpolationAlpha`. 60 Hz refers to the simulation, **not a 60 FPS promise**.
- `loadStaticScene()` validates and atomically replaces the runtime scene. Bad transforms/oversized asset IDs are rejected without altering the old world. Generation serials invalidate stale entity handles on replacement. `snapshot()` returns detached state for later render adapters or inspection.
- Android GLES editor has one **read-only bridge** in `native_engine.cpp` that copies existing native editor object positions/scales/yaw and mesh keys into a temporary standalone runtime `World`. This is **not** real Play mode. It creates a *test-only* velocity actor **inside the copy** and advances 120 fixed ticks; editor scene, models, physics and saving are unaffected.
- `☰ Каталог → Проект → 🧠 Ядро игры · проверить` shows number of copied objects, 120 fixed ticks / 2.00s, 2m test motion, and checks positions in the copy.
- CMake builds the portable source as `oe_runtime_core` static library, then links it into the Android `oe_native` shared library. Host smoke test `tests/runtime_core_smoke.cpp` compiles/runs without Android SDK and tests entity lifetime, invalid data handling, precise XYZ, scene atomicity, snapshots, camera/renderer/collider metadata and frame hitch/pause behavior.
- All editor features from 0.6.12 remain (full-screen, millimetre XYZ controls, Meshopt static LOD, GLB/gltf/VOX import, texture painting, quality profiles). No project serialization changes.

## Explicitly **not** part of milestone 1

- No ▶ Play button, playable character, virtual joystick or actual continuous simulation loop on Android. Those are milestone 2.
- No physics/gravity/raycast/character controller/collision resolution. A `Collider` in core is *metadata*, not a physics engine.
- No scripts, events or behavior graph, animation playback, sound, multiplayer, or standalone game APK export.
- No terrain, paint/brush physics, loaded texture bytes or actual GLES rendering are copied into runtime; the bridge currently transfers only editor objects and mesh references.
- No general-purpose ECS scheduler/system plug-in framework yet; component store plus one deterministic movement system is the minimum safe foundation.
- Draco and BasisU transcoding remain unsupported (same status as 0.6.12).

## Host build / smoke (no Android)

```sh
g++ -std=c++17 -O2 -Wall -Wextra -Werror -pedantic \
  tests/runtime_core_smoke.cpp android/src/main/cpp/runtime/oe_runtime.cpp \
  -o /tmp/oe-runtime-core-test
/tmp/oe-runtime-core-test
```

Expected: `OE_RUNTIME_CORE_OK: 60Hz, stable IDs, atomic scenes, pause, snapshots, components`.

## Tablet test

1. Install **OE GAME RUNTIME CORE TEST** alongside existing OE test builds. Old versions' private files are isolated; move a project via `.oezip` (same import version as 0.6.12).
2. Create several walls, trees or imported GLBs, then open `☰ Каталог → Проект → 🧠 Ядро игры · проверить`.
3. Verify diagnostics show correct object count and 120 simulation ticks/2 seconds; moving test actor reaches X=2.00m. The test actor must NOT appear in editor scene.
4. Close dialog, verify actual walls/tree positions and painter tools unchanged. Save/export/reopen the same scene and compare.
5. Switch Economy/High quality while editing and rerun the probe. No continuous CPU update should run while editor is idle.
6. On weak Allwinner A523 device check if diagnostic dialog opens responsively, without heat/long hangs. This is a quick in-memory test, **not an FPS benchmark**.

## Next deliberate milestones

1. **0.7.1:** real Play/Stop mode with a cloned scene and continuous fixed-step timekeeper; stop restores the original editor scene; ensure no data loss.
2. **0.7.2:** gameplay player controller, camera and input mapping.
3. **0.7.3:** collision and terrain physics (or integrated trusted physics library), spatial queries.
4. **0.7.x:** script lifecycle and reliable assets, UI/audio.
5. **1.0 acceptance:** standalone Android game APK built/exported from editor project and tested on device.

Do not merge to OE main or present this isolated foundation as a finished 3D game engine.
