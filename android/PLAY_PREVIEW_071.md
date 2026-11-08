# OE 0.7.1 — experimental Play/Stop preview

**Branch:** `test/play-mode-071`. **Base:** `test/game-runtime-core-070`. Do not merge into `main` without Android tablet QA.

## Scope
- Press **▶ Играть** in the top bar or Project catalog.
- The native JNI bridge copies editor entity transforms and asset keys into a new `oe::runtime::World`. It also freezes a separate copy of renderable entities for the GLES preview.
- Runtime camera actor moves at 4 m/s using 60 Hz fixed-step `World::advance`; rendering is continuous only while Play is active.
- Hold on-screen arrows to move relative to camera; swipe the 3D view to orbit.
- Press **■ Остановить** to discard runtime state and restore editor camera, scene and editing UI.
- Editor object list, terrain, textures, scene save data and undo history are not written by simulation.

## Limitations
- Camera-walk preview only; no visible controlled character, collision resolution, gravity, jumping or scripts.
- Static terrain and asset resources are reused in the GLES renderer (they cannot be edited in Play mode).
- Runtime render objects are a snapshot; no runtime spawning or object animation is rendered.
- 60 ticks/s is the simulation time step, **not** a measured graphics FPS.
- The previous PR's deterministic host tests cover the core. This Play/Stop Android UI and EGL lifecycle **have not been verified on a real tablet**.

## Device QA checklist
1. Build and install the preview APK from the branch. Create a scene with built-in objects and an imported GLB.
2. Capture editor scene JSON and camera position; press Play. Check the Play-only directional pad and Stop button.
3. Hold each movement button; drag the camera; verify input stops on touch release, including cancellation.
4. Stop and compare scene JSON, selected object, undo/redo behavior, GLB rendering and camera position.
5. Test Play/Stop repeatedly, pause/resume app during Play, rotate screen, and EGL surface recreation.
6. Test slow devices and thermal/performance behavior; inspect Android logs for crashes/GL errors.
