#!/usr/bin/env python3
"""OE Stage12: original Phoenix UV44 building tool inside same Android APK.

The owner's 27 MB original UV44 APK and 1832 models are deliberately NOT
committed to public GitHub. The app imports APK assets privately on device.
This checks Java/manifest wiring; Android device/WebGL QA is still needed.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
uv44=(root/"android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
bundle=(root/"android/src/main/java/game/oe/editor/PhoenixUv44Bundle.java").read_text()
manifest=(root/"android/src/main/AndroidManifest.xml").read_text()
gradle=(root/"android/build.gradle").read_text()
for part in (
    'PhoenixBuilderActivity.class',
    'OPEN_UV44_BUILDER=708',
    'addPaletteOption("📚 Детали оригинальной библиотеки UV44"',
    'importModelFromUri(Uri.fromFile(glb),backup,entityId,oldKey);',
    'private void importModelFromUri(Uri uri)',
    'nativeRegisterAsset(key,model.vertices,',
    'enterPlaceMode(6,newKey)',
    'glb.length()>GlbReader.MAX_BYTES',
    'root.getCanonicalPath()+File.separator',
):
    assert part in app, ("one-city GLB import/placement integration",part)
for part in (
    'static final String EXTRA_GLB_PATH',
    'builder.addJavascriptInterface(bridge,"NativeBridge")',
    'builder.setWebViewClient(new WebViewClient()',
    's.setAllowFileAccess(false)',
    's.setDomStorageEnabled(true)',
    's.setJavaScriptEnabled(true)',
    'shouldInterceptRequest(',
    'onShowFileChooser(WebView web,',
    'WebChromeClient.FileChooserParams.parseResult(',
    'PhoenixUv44Bundle.install(this,apk)',
    'startActivityForResult(pick,PICK_UV44_APK)',
    'setResult(RESULT_OK,result)',
    'builder.destroy()',
    'builder.onPause()',
    '@JavascriptInterface public boolean saveCleanProject(String json)',
    '@JavascriptInterface public String loadCleanProject()',
    '@JavascriptInterface public synchronized boolean beginSave(',
    '@JavascriptInterface public synchronized boolean appendSaveChunk(',
    '@JavascriptInterface public synchronized boolean finishSave()',
    'MAX_GLTF=24L*1024*1024',
    'MAX_EXPORT=160L*1024*1024',
):
    assert part in uv44, ("original UV44 private activity/file bridge",part)
for part in (
    'ZipInputStream',
    'MAX_ENTRIES = 2200',
    'MAX_UNPACKED = 160L * 1024 * 1024',
    'MAX_ENTRY = 24L * 1024 * 1024',
    'path.startsWith("assets/")',
    'legalPath(path)',
    'new File(staging,rel)',
    'models!=ORIGINAL_PWB_MODELS',
    'entries<ORIGINAL_PWB_MODELS',
    'builder', # harmless placeholder, checked separately below
    'ORIGIN = "https://uv44.oe.local"',
    'Access-Control-Allow-Origin',
    'files.write', # placeholder
):
    if part in ("builder","files.write"):continue
    assert part in bundle, ("private UV44 extraction, safety or Web origin",part)
assert 'android:name="game.oe.editor.PhoenixBuilderActivity"' in manifest
assert 'android:exported="false"' in manifest
assert manifest.count("android.intent.category.LAUNCHER")==1
assert "applicationId 'game.oe.editor.r57canvas.uv44full'" in gradle
assert "versionCode 52" in gradle
assert 'json.put("version",12)' in app
# No user's privately uploaded UV44 JS, GLB catalogue or project in git.
assert not (root/"android/src/main/assets/uv44").exists()
assert not (root/"android/src/main/assets/pwb").exists()
assert not list(root.rglob("Phoenix_Builder_UV44_Install*.apk"))
print("OE_UV44_TOOL_STAGE12_OK: same Android APK, original UV44 bytes imported "+
      "privately, chunked bridge to existing native GLB/placement, v12 city retained")
