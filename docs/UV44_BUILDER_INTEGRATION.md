# Orvetra Engine — UV44 integration contract (Stage 12)

Owner's primary requirement: **one engine**, where the stable Phoenix Builder UV44
constructs buildings, the native OE UV56 terrain defines the world, and the same
character can eventually build, arrange buildings in a city, and walk through it.
Do **not** replace UV44 with the experimental UV56 building code. Do **not**
rewrite original UV44 just to implement an editor feature.

## Verified against the owner-supplied APK (2026-11-20)

The supplied `Phoenix_Builder_UV44_Install(2).apk` contains **1,908 assets**:
73 original JavaScript modules; 1,832 `.glb` PWB library objects; the original
`index.html`, `clean.css`, Three.js, mode controls for building/character/drone,
`PhoenixFiles` for editable project and chunked GLB export. Decompressed
resource size is ~74.6 MiB. The APK is **not** committed to the public repo.

## Stage 12: first usable in-app compatibility boundary

- One installed Android OE application. Internal `PhoenixBuilderActivity` is
  non-exported, has no launcher icon, and temporarily takes over the GPU from
  `EditorActivity`. Only one renderer is active at a time.
- Owner selects UV44 APK **locally on tablet once** with SAF. Only APK
  `assets/` are validated/extracted to OE's private files; old Java code/DEX
  is not installed; byte-identical original JS and GLB are used.
- The original builder UI runs unchanged in an isolated, privately served
  WebView over local `https://uv44.oe.local/` resources, without remote
  scripts/network access. It supports its existing input file chooser.
- The compatibility `NativeBridge` exposes original signatures:
  `saveCleanProject`, `loadCleanProject`, `saveProject`,
  `loadProject`, `saveManualProject`, `beginSave`,
  `appendSaveChunk`, `finishSave`.
  Editable projects are stored privately in OE, not rewritten.
- On original UV44 **📦 GLB** action, chunked output is stored privately.
  The **🏠 В город** button returns the *finished static GLB* to the same
  `EditorActivity` → `UniversalModelReader` → `GlbReader` →
  `nativeRegisterAsset` → normal city placement on OE UV56 terrain.
- No new scene format: placed GLBs use the usual `.oezip` model refs and
  native OE physics and tools. Legacy scene versions 1–12 remain valid.

## Explicitly NOT completed in Stage 12

- Builder still uses original Three.js/WebGL while **editing**; this is not
  yet a native C++ port of the stable UV44 construction logic.
- The UV44 local character and drone are NOT the same native OE world
  controller yet. Do not tell the owner that this is completed.
- Editable UV44 scene data is not yet embedded into `.oezip`; only the
  exported static GLB transfers to the native scene. Original builder
  projects survive inside OE private storage.
- OE's current GLB import limit is **24 MiB**; use UV44 export texture
  quality 1024 to test larger buildings, or do additional asset pipeline work.
- The browser-to-city handoff needs real on-tablet testing for scale/pivot,
  GPU context restoration, large project memory and touch responsiveness.

## Next milestone, AFTER tablet QA

1. Store the editable UV44 project alongside imported GLB in the **same**
   portable OE project, with references and multi-house ownership.
2. Make city placement/transform of a saved UV44 house round-trip into the
   original builder for edits without losing relative/object coordinates.
3. Merge walking, drone and construction modes with **one native
   WalkController/world collision model**, suppressing unneeded handlers.
4. Eventually port stable UV44 features to native components one at a
   time with automated rendering and project-compatibility tests; do not
   do a blind rewrite of the entire builder.

Never upload the user's original builder APK, private saved projects, or
potentially licensed asset library to public GitHub without permission.
