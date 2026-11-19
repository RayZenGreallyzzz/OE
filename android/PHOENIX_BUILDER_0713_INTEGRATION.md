# OE 0.7.13 × Phoenix Builder UV44 — experimental building mode

This is an **isolated integration branch** created from
`backup/OE-0.7.13-xyz-gizmo-freeze-fix-2026-11-10`.
Do **not** merge it into `main` or replace the owner's stable Phoenix app.
The terrain, road editing, GLES 3.0 renderer and OE city JSON/scene formats
remain untouched. Only the top-level Android navigation and a new standalone
Phoenix Activity were added.

## Architecture

- **City mode (native OE):** original terrain, medieval roads, city GLB
  placement, city scene JSON project.
- **🏗 Здание → 🔥 Открыть Phoenix Builder UV44:** a separate offline Android
  WebView that hosts the owner-supplied **Phoenix UV44** HTML/JS as-is, with
  an optional non-invasive seven-tab UI adapter:
  **Строить**, **Гизмо**, **Объекты**, **Проёмы**, **Материалы**,
  **Геометрия**, **Файлы**.
- **Phoenix JSON project:** saved separately via the original
  `NativeBridge.saveProject`, `saveManualProject`,
  `saveCleanProject` JS APIs to an app-private atomic file.
- **Phoenix GLB/JSON export:** existing chunked JS
  `beginSave/appendSaveChunk/finishSave` interface -> Android SAF
  Create Document, no broad storage permission.
- **Return to city:** the Phoenix tab UI's `OEHost.closeBuilder()`
  or Android Back key opens a save reminder before closing the mode.
- **City import:** existing OE GLB file picker places an exported Phoenix
  house into the city. The handoff is manual; it is NOT an automatic
  import, and Phoenix JSON files are NOT interchangeable with OE scenes.

## Privately packaged full APK

The **public CI APK is code only**: because the stable Phoenix UV44 project
includes the owner's proprietary assets, they must never be committed to a
public repository. At packaging time, copy all files from the private
`Phoenix_UV44_Seven_Tabs_Full_Assets_OwnerOnly.zip`, stripping the
top-level ZIP directory while retaining `assets/... `, so inside the Android
APK each original web file appears at
`assets/phoenix/assets/index.html` and `assets/phoenix/assets/js/...`.
The original 1832 embedded GLB files must remain unchanged.

Optionally also privately pack the separately recovered medieval add-on from
the **0.7.11 APK** at
`assets/medieval/oe_medieval_editor_addon.zip`. The **0.7.11 application
itself is NOT the engine baseline**, it is only the source of the owner's
lost licensed medieval asset bytes. **Medieval Wood 4K is intentionally
excluded**.

The integration has its own app ID `game.oe.editor.phoenix013`, so it
does not overwrite existing OE 0.7.13 or stable Phoenix installs. The
original two applications should remain installed until device QA passes.

## Test plan

1. Start OE city; verify landscape editing and medieval roads unaffected.
2. Open Catalogue -> 🏗 Здание -> 🔥 Phoenix Builder UV44.
3. Verify seven labeled builder tabs, original tools and smooth scene orbit.
4. Build one wall, a room, and a door/opening with original gizmo.
5. Save a Phoenix JSON project; restart builder; reload without missing
   objects/textures.
6. Export a GLB using the Android file picker.
7. Return to city; import the GLB using normal OE model importer; place it.
8. Save and reopen **OE city project**, without losing the Phoenix project.
9. Confirm mobile stability with two applications' separate datasets.

Compile/smoke CI confirms source builds, not GPU, memory or device behavior.
Please do not claim to have done a real Android device test until one occurs.
