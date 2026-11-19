# OE 0.7.13: UV44 + UV56 in ONE owner app (experimental mode integration)

**User requirements (2026-11-19)**

1. **🏗 Стройка** uses the *entire original stable UV44 bundle*, unchanged byte-for-byte.
2. **🌿 Ландшафт** exposes all UV56 landscape controls: raise/lower, ridge,
   smooth, flatten, precision brush in cm, terrain tile import/paint, road
   editing/marking, undo/redo, project save, GLB export, large-project behavior.
3. One Android OE package and one user-facing mode menu, **not two separately
   installed Phoenix APKs**. Preserve the original work and safe backups.
4. Do not touch the public/main/stable builder code, Phoenix GLBs/purchased
   assets, Android signing keys, or the separate ORVETHRA game repository.

## Implemented in this isolated source branch

* City OE now offers **🏗 Стройка UV44** and **🌿 Ландшафт UV56** entries.
* Both open **the same PhoenixBuilderActivity within the same OE package**
  `game.oe.editor.phoenix013fix1`. It offers an always-accessible **OE ▾**
  mode selector without editing UV44's HTML/JS.
* Build mode serves `assets/phoenix/assets/**` (pristine original UV44).
* Landscape mode serves `assets/phoenix-uv56-overlay/assets/**` for exactly
  seven altered/new files from UV56 and falls back to **the same original
  UV44 bytes** for everything else. In landscape mode UV56's original
  `clean-app.js` runs its landscape tools **in one Three.js scene with its
  building objects**. No building GLB, library or geometry is patched.
* Both mode URLs use the same HTTPS origin, maintaining WebView IndexedDB
  storage for imported textures and private GLB references.
* Before a mode switch, a native confirmation calls the existing
  `NativeBridge.saveCleanProject` atomic snapshot machinery. On save failure
  switching is CANCELLED; do not discard current work.
* The UV44 JSON writer does not know `terrain`. The bridge preserves the
  previous landscape field when a UV44-originating building save omitted it,
  so a normal mode switch does not silently erase terrain. Landscape mode
  writes the complete terrain field normally.
* The first saved project is auto-loaded on entering each WebView mode.
* No APK, licensed private files or keystore are committed here.

## Package the *owner's* APK contents privately

This repo deliberately does **not** ship the original owner assets.

```bash
python3 tools/stage_uv44_uv56_private.py \
  --uv44 "/path/to/Phoenix_Builder_UV44_Install.apk" \
  --uv56 "/path/to/UV56_Landscape_Export_Performance_TEST.apk"
# The first run only verifies identities and changes; it writes no files.

python3 tools/stage_uv44_uv56_private.py \
  --uv44 "/path/to/Phoenix_Builder_UV44_Install.apk" \
  --uv56 "/path/to/UV56_Landscape_Export_Performance_TEST.apk" \
  --assets-root android/src/main/assets
```

The packer rejects any other changed UV44 resources in UV56, before writing.
Staged directories `android/src/main/assets/phoenix/` and
`android/src/main/assets/phoenix-uv56-overlay/` are gitignored.
No private GLB, paid packs, project files, keys, or owner material goes into
GitHub Actions. The public CI APK is code only and cannot exercise the full
private builder without owner-provided staged assets.

## Limitations — DO NOT misrepresent

* This is **ONE APK and shared project storage**, with two HTML scene variants
  inside one WebView Activity. Switching modes saves/reopens a 3D scene.
  It does **NOT** yet make native GLES3 city and Phoenix WebGL one GPU renderer
  or one continuously live canvas. A real cross-renderer city/landscape data
  adapter will be a separate milestone requiring device QA.
* The original UV44's *portable* backup/export does **not** know about UV56
  terrain. Export the full portable Phoenix JSON **from Landscape UV56 mode**
  to include the terrain data; original building-only mode saves terrain
  safely in native project snapshots but cannot guarantee terrain in its
  own portable export.
* Replacing the entire project using UV44 without matching terrain metadata
  can be ambiguous. First export a full portable backup from Landscape mode.
  No destructive new-project/reset workflow has yet passed device tests.
* This branch does not solve any existing UV56 landscape regression or
  guarantee that imported landscape GLB and the OE native terrain match.
* No evidence of on-device FPS, saved-project compatibility, export/import
  round-trip, pixel-perfect materials or signing compatibility is asserted.
* **Never uninstall or overwrite the owner's current app to test**.
  In-place update requires the matching owner's permanent PKCS#12
  certificate (fingerprint documented in PR #36); it is not present here.

## Mandatory device acceptance criteria (before any release/merge)

1. Launch OE, enter UV44 and build/edit the existing house **without changes
   to tools, snapping, materials, or gizmo**.
2. Save, switch in the same app to UV56 and load the same house. Verify
   landscape tools work and building objects remain in that scene.
3. Draw height, precision 1cm brush marks, uploaded terrain tiles, painted
   masks, and roads/curbs. Undo/redo and repeat many strokes.
4. Switch to UV44 and back. Confirm exact building coordinates and terrain,
   including tiles; verify native JSON snapshot history does not silently lose
   prior work. Try interrupted Android picker and low-storage cases.
5. Export a full portable JSON while in **UV56 landscape** and import it
   into a clean isolated test profile; compare included images/GLBs.
6. Export combined GLB and load in native OE city; inspect slope, terrain
   textures, road boundaries, house scale, source textures, graphics and FPS.
7. Check old OE native terrain/road tools are unaffected. Keep PR #36 draft,
   keep `main` untouched, and never ship with an unmatched signing cert.

**Current state:** source staged for CI; device QA and full native-city
integration still outstanding.
