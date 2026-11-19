# OE R5.7 + Phoenix UV44/UV56 — integration on PR #31 (DRAFT)

## Decision

**The user selected PR #31 as the city engine foundation**, rather than
making the previous OE 0.7.13 v39/v40 branch the new city engine.

This experimental branch starts **exactly** at PR #31 head
`52bdc7813f6452d3cc9b0b152459898dbfbc6014`, then adds a private,
integrated Phoenix Builder studio. PR #31, its R5.6 base, `main`, the owner's
installed APK, their private models and original APKs are NOT modified.

## Two existing character controllers — KEEP BOTH

1. **R5.7 city/road walker**: native C++17 `WalkController`,
   `traversal_graph` and `walk_collision`, with bounded Android analog
   joystick, jump, gravity, roads, bridges, sloped bank joins, 60Hz steps.
   This controller remains in native `EditorActivity` and is stopped when
   leaving the editor.
2. **Original Phoenix character controller**: WebGL/Three.js
   `character-collision.js`, `touch-joystick.js`, `world.js` and
   `app.js`, with wall/door/floor/build-module collision, character and
   touch control. All four files in **UV44 and UV56 are byte-identical**,
   verified on owner APKs. Private stage packager refuses any change.
   The active UV56 `clean-app.js` *is changed* relative to UV44; do not
   claim that UV56 character behavior is fully verified on the device.
   Original full UV44 is retained, unchanged, as recovery/reference.

**Never replace the city walker with Phoenix's character controller,
or downgrade Phoenix wall/floor collision to the city's feet-point walker.**
A truly unified gameplay controller needs future cross-world collision
geometry and on-device QA, not a blind code copy.

## Scope of this initial merge into the R5.7 codebase

- One Android OE app contains the native city and a non-exported
  `PhoenixBuilderActivity`; no second Phoenix APK is needed.
- Native OE toolbar and `Здания`/`Рельеф` categories open the integrated
  studio with BUILD/LANDSCAPE requested tab.
- Within the Phoenix studio, both tabs use **the same UV56 Three.js scene**
  with buildings, terrain, texture/road tools and the existing Phoenix
  player physics; no scene reload between tabs.
- A backup original UV44 web/editor bundle remains accessible as a
  read-only reference in the sense that its *source bytes* cannot be edited
  by the app. User scene edits made there can still write the common project
  JSON; use a backup before trying recovery mode.
- R5.7 city autosaves its **unchanged scene v1–v8** using AtomicFile
  before entering Phoenix, refusing to switch if writing fails.
- Phoenix has its own native JSON project file and 3 atomic history slots;
  safe project save before switching to recovery or exiting.
- Phoenix finished GLB can return via app-private cache file to native
  city's existing `UniversalModelReader` + `GlbReader`, with a strict
  canonical path, real file size and maximum importer bound check.
  Current R5.7 city limits GLB to **24MiB**; large original GLB stays
  exportable by Android SAF and is **never downsampled automatically**.
- `PhoenixFileVerifier` reads completed Android provider copy back and
  verifies SHA-256 before the private temporary original is deleted.
- The private UV44+UV56 stager accepts exactly 7 different files (UV56
  `index.html`, CSS and five JavaScript modules), verifies source
  integrity, atomic folder swap, and refuses changes to private physics.

### Private staging (not the same as building a release APK)

```bash
# Read-only verification:
python3 tools/stage_uv44_uv56_private.py \
 --uv44 '/private/Phoenix_Builder_UV44_Install.apk' \
 --uv56 '/private/UV56_Landscape_Export_Performance_TEST.apk'

# On owner's private build machine only, never public GitHub:
python3 tools/stage_uv44_uv56_private.py \
 --uv44 '/private/Phoenix_Builder_UV44_Install.apk' \
 --uv56 '/private/UV56_Landscape_Export_Performance_TEST.apk' \
 --assets-root android/src/main/assets
```

Do not commit or upload `assets/phoenix/**`,
`assets/phoenix-uv56-overlay/**`, private GLBs, paid packs, project
snapshots, signing keys or owner account data.

## Explicit non-goals and outstanding work

- **DO NOT copy v40's `native_engine.cpp` or `ProjectArchive.java`
  wholesale.** Those use OE scene v1, while R5.7 uses scene **v1–v8**
  with medieval bridges, lakes, road texture assets and material layers.
- In particular, v40's UV56 heightfield serialization had a bug:
  native save wrote `version,areaSqM,nodes`, but reload expected
  `unit:'m',step:1`. This must be fixed and migrated to a validated R5.7
  scene **v9** (or a safe auxiliary asset) only after choosing schema and
  testing complete .oezip/v1-v8 round trips.
- This milestone does NOT yet transfer editable UV56 terrain, terrain
  paint, roads or collision from the Phoenix WebGL scene into the native
  GLES city. GLB model export is the current handoff.
- The native city walker and Phoenix walker remain distinct. Physics
  comparison, mesh colliders, shared heightfield and actual gameplay
  walking on exported Phoenix houses remain future work.
- No pixel-perfect shader/PBR fidelity is asserted for R5.7's 24MiB GLB
  importer. Complex houses can exceed the budget and require a separate
  upgrade, without harming original source bytes.

## Acceptance gates before any owner-only APK

1. Enter/exit Phoenix from R5.7 city with unsaved roads, lakes, material
   masks and walking preview. All city data survive save/restart.
2. Inside Phoenix build a house, walk around it, collide with walls, pass
   door gaps, use analogue joystick, place terrain and roads, undo/redo.
   Switch BUILD/LANDSCAPE in the **same scene**; no loss of original objects.
3. Export a small GLB into R5.7 and place it at real meter scale; export
   a large original to the device without modifying bytes.
4. Round-trip native R5.7 v1–v8 .oezip with road PNG/JPG, lakes,
   medieval bridges, map PNG and material layers intact.
5. Run R5.7 walker again across two connected bridge banks; Phoenix
   tests must not change its behavior or frame budget.
6. Test low RAM and EGL context recreation on the actual Allwinner A523;
   CI is insufficient for any statement of tablet reliability.

**No signed/full APK requested at this stage.** Debug source uses isolated
`applicationIdSuffix '.r57studio'` to avoid replacing either R5.7 test
`touchwalk` or owner's OE installation, if an isolated test APK is later
explicitly requested. Keep PR as Draft until owner approval.
