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
assert "versionCode 32" in gradle
# Graphics settings must live in the always-visible top-right toolbar,
# use existing native quality implementation, and persist the selection.
assert 'Button settingsButton=button("⚙️",this::showEngineSettings);' in editor
assert 'top.addView(settingsButton,new LinearLayout.LayoutParams(dp(46),dp(43)));' in editor
assert 'private void showEngineSettings(){' in editor
settings = editor.split('private void showEngineSettings(){',1)[1].split(
    'private void chooseDeviceProfile(){',1)[0]
assert '.setTitle("⚙️ Настройки OE")' in settings
assert '🖼 Разрешение изображения' in settings
assert 'Детализация текстур GLB на экране' in settings
assert '.setItems(sections,' in settings
assert '.setMessage(' not in settings  # Android bug: hides the options
assert 'else if(which==1)chooseTextureLimit();' in settings
assert 'else showDeviceCapabilities();' in settings
assert 'qualityPrefs.getInt("oe_display_resolution",' in editor
assert 'qualityPrefs.getInt("oe_quality_tier",-1)' in editor  # migration
assert 'textureLimit=getPreferences(MODE_PRIVATE).getInt("oe_glb_preview_texture_limit",1024);' in editor
assert '.putInt("oe_display_resolution",selected).apply();' in editor
assert '.putInt("oe_glb_preview_texture_limit",textureLimit).apply();' in editor
assert 'surface.applyRenderScale(scale);' in editor
assert 'nativeSetQualityProfile(rendererTier)' in editor
assert 'QUALITY_MODES' in editor
resolution_change = editor.split('private void selectDeviceQuality(int selected){',1)[1].split(
    'private void showEngineSettings(){',1)[0]
# Never switch geometry LOD, culling or texture decode when merely changing
# screen resolution. GLB source bytes are never rewritten.
assert 'nativeSetQualityProfile' not in resolution_change
assert 'textureLimit=' not in resolution_change
assert 'GlbReader.decode' not in resolution_change
assert 'nativeQualityScale(displayTier)' in resolution_change
assert 'getPreferences(MODE_PRIVATE).edit()' in resolution_change
assert 'Файл проекта' not in resolution_change
print("OK: screen resolution changes only GLES buffer scale; source GLB quality untouched")

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
recovery_list_dialog = recovery_dialog.split(
    '.setTitle("🛟 Копии Phoenix — JSON без текстур")', 1
)[1].split('.setItems(items,', 1)[0]
assert '.setMessage(' not in recovery_list_dialog
print("OK: Android GLB destination buttons and JSON recovery list are visible")

print("OK: Phoenix JSON snapshots, fallback, SAF retry, city handoff, and app ID unchanged")
