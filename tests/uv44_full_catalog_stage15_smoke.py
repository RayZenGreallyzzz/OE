#!/usr/bin/env python3
"""Stage15 - full original Phoenix UV44 model catalog in ONE native OE world.

Source-only smoke: the owner's private 1,832 PWB models are NEVER committed.
An APK device test must verify the Android picker, catalog, GLB placements.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
bundle=(root/"android/src/main/java/game/oe/editor/PhoenixUv44Bundle.java").read_text()
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
assert "UV44_ORIGINAL_MODELS=1832" in java
assert "ORIGINAL_PWB_MODELS=1832" in bundle
assert "models!=ORIGINAL_PWB_MODELS" in bundle
assert 'new File(base,"js/bundled-catalog.js").isFile()' in bundle
assert 'models="+ORIGINAL_PWB_MODELS' in bundle
assert "entries.length()!=UV44_ORIGINAL_MODELS" in java
assert 'id.matches("glb:pwb:[0-9a-f]{24}")' in java
assert 'new File(root,"pwb/"+id.substring(8)+".glb")' in java
assert "nativeUv44CatalogCache=entries;" in java
assert "HashSet<String> unique=new HashSet<>();" in java
assert "UV44_PAGE_SIZE=40" in java
assert "i<entries.length();i++" in java
assert 'files.size()<60' not in java, "catalog truncated to 60 model files"
assert 'showNativeUv44Page(null,"",0);' in java
assert 'showNativeUv44Page(folders.get(index-2),"",0);' in java
assert "showNativeUv44Page(category,query,page+1)" in java
assert "showNativeUv44Page(category,query,page-1)" in java
assert "listNativeUv44Parts(String query)" in java
assert 'title+" "+folder+" "+path' in java
assert "importModelFromUri(Uri.fromFile(files.get(begin+index)))" in java
assert "PhoenixUv44Bundle.install(this,apk)" in java
assert "nativeUv44CatalogCache=null;" in java
assert "nativePlaceImportedAtScreen" in java
assert "nativePlaceImportedAtScreen" in cpp
assert "gUv56Terrain" in cpp and "gAssets" in cpp
assert "version>=1&&version<=13" in (
    root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
print("OE_UV44_FULL_CATALOG_STAGE15_OK: 1832 original parts searchable and "
      "paged in single native OE canvas (no private PWB bytes included)")
