# OE 0.6.3 GLB Safety Test

This is a separate test APK based on 0.6.2. It installs as **OE GLB SAFETY TEST** under package `game.oe.editor.glbtest`. The original OE and low-power test builds and their private data are unchanged.

## Implementation

- Transactional native scene replacement: validates all entities, transforms and loaded GLB references, and swaps the scene once, only if every check succeeds. A failed load leaves the current entities and undo history intact. New GLB uploads are rolled back where safe.
- Caches models by content-addressed key. Subsequent loads skip parsing and GPU re-upload for resident models. Existing model files are still checked for existence and byte length; a cached model is not rehashed on each load.
- Computes conservative post-normalization bounding spheres while decoding GLB, then checks imported objects against all six frustum planes and a 230 m distance budget before GPU draw. Frustum culling does not change source geometry or CPU object state.
- Retains OE 0.6.2 terrain batching, baked static models, 75% 3D render resolution, ~30Hz touch-redraw cap, idle on-demand draw and atomic scene saves.

## Testing on the tablet

1. With the test app, create and save a scene containing two copies of one GLB; reload it. Verify both models remain visible and selectable.
2. Move imported objects behind the camera and back. The triangle count should change and the objects should reappear on return.
3. Import the same GLB twice and reload; confirm a repeated load reports reuse of a model in memory.
4. Deliberately try to load a scene whose required model file is missing (if using a test-only dataset), verify the current scene remains and can still be saved. Never intentionally corrupt an irreplaceable project.
5. Compare touch interaction and coil whine with OE 0.6.2.

A successful APK build does not prove runtime correctness on the user's hardware. No claim of guaranteed elimination of electrical buzzing.
