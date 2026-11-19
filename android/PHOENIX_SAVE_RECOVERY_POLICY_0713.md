# OE 0.7.13 / Phoenix Builder UV44 — Save Recovery Quality Gate

**Priority:** save the owner's current building before any more APK replacements.
**Branch:** `feature/oe-0713-phoenix-uv44-builder` (draft PR #36).
**No changes to OE main, ORVETHRA MMO, original Phoenix UV44, or private paid GLB assets.**

## Device problem actually reported (2026-11-18)

The owner's installed OE Phoenix screen displayed
`Ошибка GLB: Android отклонил сохранение` and
`Ошибка резервной копии: Android не принял часть файла проекта`.
The screenshot shows a constructed medieval building **still present**.
Previously, the bridge's `beginSave` rejected all new exports while a prior
completed file had `pendingSave`, and `appendSaveChunk` silently rejected
large portable project files at 256 MiB; a failed SAF copy deleted the staged
file even when the destination provider refused writing.

## Source-side protection added

1. Queue completed GLB and portable JSON exports independently of the Android
   file picker so one pending dialog cannot invalidate the next file.
2. Stream chunks directly to disk, permit up to 1 GiB only if space remains,
   and give the user a readable error when an Android file write fails.
3. Keep the completed export after a SAF failure and offer **Retry**, rather
   than deleting its only temporary copy.
4. Preserve **three previous known-good internal project JSON snapshots**.
   Validate version 2 and the `objects` array before replacing the live JSON.
   Write a `.new` file, fsync and atomic rename; rotate older snapshots only
   while the live project is intact.
5. On corrupt/missing main JSON, load the newest valid prior snapshot.
   Android Back → **🛟 Копии** lets the owner export JSON to device or restore
   an older internal snapshot. Restores preserve the prior current save in
   snapshot history.
6. These small internal snapshots include scene objects/texture *references*,
   **not** all texture bytes. The portable `.phoenix.json` backup produced
   by the Phoenix Files tab is still required for a full rebuild; it includes
   IndexedDB images and imported models.
7. Do not delete, uninstall, or clear installed app data until a *real*
   portable full backup has been exported and verified.

## Signing barrier: why CI build isn't an in-place update yet

The earlier owner-only APKs were built with separate ephemeral signing keys.
Their APK v2 certificate SHA-256 values are:

| Owner-only APK | SHA-256 signing certificate |
| --- | --- |
| OE_0.7.13_Phoenix_Builder_7Tabs_Full_Medieval_TEST.apk | `e6a03dd6e342b6d3317ae51b4acfbb55c865f2ee3db47412ad3c35dd3ab3660a` |
| OE_0.7.13_Phoenix_UV44_Texture_Graffiti_GLBCity_Fix_TEST.apk | `979c74e66e2b6324b44c73bb3620055a3c788a524c37fa367d1abad64b2e2eca` |
| OE_0.7.13_Phoenix_UV44_Install_Fixed_16K_Aligned.apk | `02b8b7db5c77a7f582c548da7d624dc923b6f619f59b42b5ac8b888a69f22f43` |

These certificate digests were extracted from the actual APK v2 signing
blocks of the mounted APKs. A certificate/public key is not the private key.
The original private signing key is not present in the current workspace.
Even when `applicationId` matches
`game.oe.editor.phoenix013fix1` and versionCode increments from 29 to 30,
Android will reject an in-place update signed by a different key.

**Do not claim that an update will preserve owner data until the exact
signing private key is recovered or a verified app-data recovery path works.**

## Non-destructive rescue for current installed application

The conversation deliverable `OE_Phoenix_Installed_Project_ReadOnly_Backup_Tools.zip`
contains a Python 3/Android platform-tools script that runs
`adb exec-out run-as game.oe.editor.phoenix013fix1` to retrieve the
application's `files`, `app_webview`, preferences and databases into a
local TAR archive on a connected computer. This only works if the APK installed
is the debuggable Phoenix OE test build and the computer is trusted. It does
not change the tablet. The archive is private, **not for GitHub**.

## Before labeling save changes as working on tablet

- Open an already-built house (not a new one); save JSON twice, restart app,
  verify rooms/walls still exist, restore snapshot #1.
- Export `.phoenix.json` with multiple custom PNG/JPG/WebP images and GLBs,
  validate file size; import into a fresh *test* session and verify all assets.
- Export GLB first to device, then city, then device again; verify Android
  picker retries after cancel and after permission/space errors.
- Confirm Android background/pause and sudden process death during save cannot
  corrupt *previous* known-good JSON.
- Repeat with deliberate low-space conditions, and report clear errors.
- Verify exact installed app package AND cert, and create a full backup
  before any APK signing/change of package/installation.

**CI compilation and host tests are not equivalent to physical tablet QA.**
