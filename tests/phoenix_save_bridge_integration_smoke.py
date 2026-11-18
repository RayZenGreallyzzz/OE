#!/usr/bin/env python3
"""Static guard against losing Phoenix JSON / GLB on OE Android bridge regressions.

The Android APK compilation job separately confirms Java syntax. This smoke
protects source invariants only; it does not replace tablet QA.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bridge = (ROOT / "android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
editor = (ROOT / "android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
gradle = (ROOT / "android/build.gradle").read_text()

def require(tokens):
    for token in tokens:
        assert token in bridge, f"missing Phoenix safety invariant: {token}"

require([
    "private static final int SAVE_HISTORY=3;",
    "projectBackup(slot)", "readValidProject", "validProjectJson",
    'doc.optInt("version",-1)==2', 'doc.opt("objects") instanceof JSONArray',
    "out.getFD().sync();", "StandardCopyOption.ATOMIC_MOVE",
    "atomicCopyFile(live,projectBackup(1))",
    "atomicWriteFile(live,data);",
    "for(int slot=1;slot<=SAVE_HISTORY;slot++)",
    'setNeutralButton("🛟 Копии"',
    "exportRecoveryJson", "confirmRestoreJson",
    "pendingQueue.offer(ready);", "showNextSave();",
    "private boolean fileCopyInProgress=false;",
    "if(pendingSave!=null||fileCopyInProgress)return;",
    "Ошибка копирования Phoenix", "Повторить",
    "if(stream!=null)abortSave();",
    "getLastSaveError()",
    "MAX_EXPORT_BYTES=1024L*1024*1024",
])
assert "if(stream!=null||pendingSave!=null)return false;" not in bridge
assert "if(available>0&&available<bytes.length+DISK_RESERVE)" in bridge
assert "if(available>0&&available<data.length*2L+DISK_RESERVE)" in bridge
assert "private File projectFile()" in bridge
assert 'startActivityForResult(new Intent(this,PhoenixBuilderActivity.class),PHOENIX_BUILDER)' in editor
assert "applicationId 'game.oe.editor.phoenix013fix1'" in gradle
assert "versionCode 31" in gradle
# Android AlertDialog.Builder.setMessage and setItems share the same content
# region: if both are set, the message wins and the destination list vanishes.
# The GLB export must use explicit buttons instead.
export_dialog = bridge.split('private void showNextSave(){', 1)[1].split(
    'private void startDeviceSave(){', 1)[0]
assert '.setPositiveButton("🏰 В город OE"' in export_dialog
assert '.setNeutralButton("📱 На устройство"' in export_dialog
assert '.setNegativeButton("Отмена"' in export_dialog
assert '.setItems(' not in export_dialog
# The "recovery copies" list also must not combine list content and message.
recovery_dialog = bridge.split('private void showProjectRecovery(){', 1)[1].split(
    'private void exportRecoveryJson(', 1)[0]
assert '.setItems(items,' in recovery_dialog
assert '.setMessage(' not in recovery_dialog
print("OK: Android GLB destination buttons and JSON recovery list are visible")

print("OK: Phoenix JSON snapshots, fallback, SAF retry, city handoff, and app ID unchanged")
