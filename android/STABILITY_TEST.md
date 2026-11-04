# OE 0.6.1 Stability Test (separate Android app)

This is an isolated comparison build from OE 0.6, **not a replacement for the working OE app**. It has a separate Android package `game.oe.editor.stability` and label **OE STABILITY TEST**. It does not share private scene data with the main app.

Changes:

- Flat terrain around the camera is batched into **one** GLES3 draw call, instead of tens of separate tile draw calls. The same 64-meter zone colors are retained; no distant map is preloaded. Terrain GPU data is cached and refreshed only when moving to another 64-meter cell or changing world size.
- The visible ground is made of flat quads, not the previous small cube blocks; this intentionally simplifies geometry. The road overlay and existing procedural structures remain.
- Render statistics (draw calls, triangles, terrain tiles) appear in the editor; these count CPU-issued draw calls and geometry, **not electrical buzzing or real GPU power**.
- Scene saving is atomic with Android `AtomicFile`, including recovery of previous contents if a save was interrupted.
- Rendering remains `GLSurfaceView.RENDERMODE_WHEN_DIRTY` — no always-on frames, FPS polling loop or added periodic render timer.

## What to test on the actual tablet

1. Install the test APK alongside **OE Editor** (it has a different Android app ID).
2. With no models imported, compare the two apps while the camera is still and then while rotating the camera.
3. In **OE STABILITY TEST**, open an empty scene, move to another location, add a building, undo/redo and save/load.
4. Import the *same* GLB into the test app if desired; compare the electrical sound and responsiveness.
5. If the test app still buzzes or behaves worse, leave the main OE untouched; do not merge this experimental change.

**Limitations:** a successful GitHub APK compilation and the diagnostics overlay are **not** a tablet stability test. No guarantee of Unreal Engine performance or complete removal of coil whine; genuine stability requires tests on the target hardware, further renderer work and incremental regression checks.
