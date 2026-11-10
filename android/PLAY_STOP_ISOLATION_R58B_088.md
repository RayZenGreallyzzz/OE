# Orvetra Engine R5.8b — isolated Play/Stop experiment (0.8.8)

**Experimental** branch: test/oe-r58b-isolated-playstop-2026-11-10,
stacked on R5.8a. Existing R5.6 backup and OE main untouched; no merge.
**Editor QA application**: game.oe.editor.playstop (Android ARM64 debug).
This is NOT a standalone game APK built offline by the tablet.

## What actually works in source and is covered by host checks

- **Play** instantiates a separate portable C++17 Runtime World,
  duplicating authoring scene object names/assets/transforms as ECS items.
  The preview creates its own transient player entity with camera component,
  keeps its own WalkController with road/bridge collision quads and an
  immutable-by-value copy of edited terrain brush heights, and uses fixed 60Hz
  ticks without unbounded catch-up.
- The scene's visible static objects are drawn from a copy of editor
  entities while Play runs; roads, bridge textures, terrain and lake GLES
  resources remain read-only shared renderer caches. The editor does not
  permit authoring changes, save, undo, redo or loading while Play is active.
  Runtime entities are independent of the editor's object list. This is
  a first isolated preview, NOT a fully independent game renderer.
- Player directional input is camera-relative. Dragging on the 3D view
  orbits a third-person camera that is separate from editor camera. The
  analog pad and jump input persist from R5.7. Stop restores editor
  camera/selection because Play does not write gTarget, gYaw, or gPitch.
- Play refuses to begin while a road, paint stroke, gizmo drag or placement
  is unfinished; it never silently commits a partially edited scene.
- Starting Play is atomic: it constructs a new scene and collision controller
  first. On failure, it does not modify the current authoring scene.
  Stop, Android pause, errors and imports cancel active or pending Play.
  Pending GL requests are guarded by a session-generation token.
- No new serialized fields. Scene versions 1–8 and .oezip archives unchanged.
  Debug package installs separately from previous R5.x experiments.

## Device QA still mandatory

1. Export your current city to an independent .oezip (GitHub sources do not
   contain your local city). Install R5.8b test APK on the target A523/4GiB.
2. Import city. Move camera to a road, select it and press ▶ Play.
   Verify joystick moves relative to camera, dragging rotates camera, and
   jump/fall continue at 60Hz fixed ticks.
3. While playing, check that the catalogue, Save and Undo are inaccessible,
   editor gizmos are hidden and there is no PNG city-plan overlay.
4. Press ■ Stop. Confirm the original editor camera, selection, roads,
   bridge/terrain, textures and unsaved changes remain exactly where
   they were. Use Undo/Redo and .oezip roundtrip.
5. Repeat with Play initiation immediately followed by Android background,
   Home gesture, rotation, project import, and quick Play/Stop taps.
   The runtime session must never come back after Stop or break file loading.
6. Test performance/temperature and memory on A523. GitHub Actions cannot
   replace physical device quality acceptance.

## Known scope limits

- Static world render resources are still shared read-only with editor.
- No runtime collision for walls, doors, railings or whole-body capsules,
  no animation, entity gameplay events, NPCs, or playable triggers.
- Play's player is a debug marker, not an animated character mesh.
- Physics runs only during moving, jumping or airborne input; no idle AI
  simulation yet. Camera and joystick intentionally prioritize low-power QA.
- There is no offline standalone APK builder on the tablet yet; that
  remains P4 / B1–B3 after physical Play/Stop validation and asset decoupling.

**Automated gates:** portable PlaySession runtime smoke, JNI/editor contract,
existing bridge bank visuals, road/materials/lake/scene QA and Android arm64
debug APK compile. Never merge without explicit approval and tablet QA.
