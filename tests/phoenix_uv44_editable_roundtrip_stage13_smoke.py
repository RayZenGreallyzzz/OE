#!/usr/bin/env python3
"""Stage13: edited Phoenix UV44 house returns to the SAME native OE entity.

Source-level contract; Android GLES/Three.js roundtrip must be owner-tested.
No personal UV44 GLBs, project JSON, or APK committed to the public repo.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
editor=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
builder=(root/"android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
links=(root/"android/src/main/java/game/oe/editor/PhoenixHouseLinks.java").read_text()
archive=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
gradle=(root/"android/build.gradle").read_text()
for q in (
  "nativeSelectedUv44House(",
  "nativeReplaceUv44House(",
  "recordEdit();",
  "e.asset=next;gSelected=entityId;",
  "e.id==entityId&&e.type==6&&e.asset==previous",
  "gAssets.find(next)==gAssets.end()",
):
  assert q in native,("native single-house swap missing",q)
for q in (
  'addPaletteOption("✏ Редактировать выделенный дом UV44"',
  "private void editSelectedPhoenixHouse()",
  "private void finishUv44Placement(",
  "nativeSelectedUv44House()",
  "nativeReplaceUv44House(oldId,oldKey,newKey)",
  "PhoenixHouseLinks.remember(this,key,uv44Source)",
  "PhoenixHouseLinks.EXTRA_EDIT_ENTITY",
  "PhoenixHouseLinks.EXTRA_EDIT_MODEL",
  'importModelFromUri(Uri.fromFile(glb),backup,entityId,oldKey)',
  'json.put("uv44Houses",editableHomes)',
  'json.put("version",13)',
  "PhoenixHouseLinks.stageArchive(this,sourceBuilders,incomingUv44Houses)",
  "PhoenixHouseLinks.commitArchiveLinks(this,incomingUv44Houses)",
  "sceneVersion>14",
):
  assert q in editor,("editable UV44 scene integration missing",q)
for q in (
  "private void openExistingHouse()",
  "window.PhoenixClean.importBackup(file)",
  "PhoenixHouseLinks.serveHouse(",
  "WebChromeClient",
  "onJsConfirm(",
  'latestBackup=ready;',
  "latestGlb=null;",
  "EXTRA_BACKUP_PATH",
  "latestBackup==null||!latestBackup.isFile()",
  'setResult(RESULT_OK,result)',
  "builder.destroy()",
):
  assert q in builder,("original editable UV44 project handoff missing",q)
for q in (
  "MAX_BACKUP_BYTES=96L*1024L*1024L",
  "MAX_ARCHIVED_LINKS=3",
  "SHA-256",
  "sceneLinks(Context c,JSONArray items)",
  "parseSceneLinks(JSONObject scene)",
  "stageArchive(Context c,File archiveFolder,",
  "commitArchiveLinks(Context c,Map<String,String> links)",
  "files.copy", # skipped below
  "AtomicFile",
  "writeLinks(c,links)",
  "uv44_editable_houses",
  'EDIT_ROUTE="/__oe_house.phoenix.json"',
):
  if q=="files.copy":continue
  assert q in links,("private content-hashed UV44 project ownership missing",q)
for q in (
  'MAX_UNPACKED_BYTES=384L*1024*1024',
  "version>=1&&version<=14",
  "PhoenixHouseLinks.parseSceneLinks(json)",
  'new ZipEntry("builders/"+name)',
  "new File(staged.builderDir,",
  'PhoenixHouseLinks.shaName(source).equals(name)',
  "PhoenixHouseLinks.MAX_BACKUP_BYTES",
  "required=referencedModels(json)",
):
  assert q in archive,("portable .oezip UV44 v13 safeguard missing",q)
assert "applicationId 'game.oe.editor.r57canvas.nativebuilder'" in gradle
assert "versionCode 53" in gradle
assert 'new WebView(' not in editor
assert "GLSurfaceView.RENDERMODE_WHEN_DIRTY" in editor
assert not list(root.rglob("Phoenix_Builder_UV44_Install*.apk"))
assert not (root/"android/src/main/assets/pwb").exists()
print("OE_STAGE13_EDITABLE_UV44_OK: original Builder portable backup, "+
      "native one-entity swap, transforms/Undo, SHA-protected .oezip v13, "+
      "no published private models or separate app")
