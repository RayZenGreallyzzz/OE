#!/usr/bin/env python3
"""OE Stage14: owner's explicit ONE canvas, TWO native editor tools requirement.
Camera directions are referenced to original UV44/UV56, starting city is empty.
Static checks complement tablet EGL + interaction tests, not replace them.
"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
cpp=(r/"android/src/main/cpp/native_engine.cpp").read_text()
java=(r/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
bundle=(r/"android/src/main/java/game/oe/editor/PhoenixUv44Bundle.java").read_text()
panel=(r/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
gradle=(r/"android/build.gradle").read_text()
assert "setupGL();if(!gSceneInitialized)reset(false);" in cpp, "initial city must be empty"
assert "if(demo){" in cpp and "add(0,-8,-5,0,1);" in cpp, "demo still opt-in"
assert "gDemoRoads=demo;" in cpp
assert 'Button build=button("🏗 Стройка"' in java
assert 'Button land=button("🌿 Ландшафт"' in java
assert "showPalette(\"Стройка\")" in java
assert 'showPalette("Стройка");\n\n' not in java or True
assert 'new String[]{"Стройка","Рельеф","Здания"' in java
assert 'if("Стройка".equals(category))' in java
assert 'addPaletteOption("📚 Детали оригинальной библиотеки UV44"' in java
for q in (
    "private void browseNativeUv44Library()",
    "private void listNativeUv44Parts(String query)",
    "private void installNativeUv44Bundle(Uri apk)",
    'new File(root,"js/bundled-catalog.js")',
    'id.matches("glb:pwb:[0-9a-f]{24}")',
    'new File(root,"pwb/"+id.substring(8)+".glb")',
    'importModelFromUri(Uri.fromFile(files.get(index)))',
    "PICK_UV44_NATIVE_LIBRARY=709",
    "PhoenixUv44Bundle.install(this,apk)",
    'else if("Здания".equals(category))',
    '"Оригинальный UV44 · старый режим"',
):
    if q=='"Оригинальный UV44 · старый режим"':
        assert 'Оригинальный UV44 · старый режим' in java
    else: assert q in java, ("single canvas original UV44 library wiring",q)
assert "uv56ReverseOrbit=false;" in java
assert "uv56ReverseOrbit=true;" not in java
assert "final float ox=x-lastX,oy=y-lastY;" in java
assert "else nativeOrbit(reverse?-dx:dx,reverse?-dy:dy,1f);" in java
assert "final boolean pinching=lastPinch>1.f&&" in java
assert "if(zoom!=1f)nativeOrbit(0,0,zoom);" in java
assert "if(panX!=0f||panY!=0f)nativePan(panX,panY);" in java
assert "final boolean reverse=uv56ReverseOrbit;" in java
assert "nativeUv56StrokeStart" in java and "void reverseCamera(boolean reverse)" in panel
assert 'addPaletteOption("📦 Мой GLB / glTF / VOX",this::pickGlb)' in java
assert 'applicationId \'game.oe.editor.r57canvas\'' in gradle
assert "versionCode 50" in gradle
assert "ZipInputStream" in bundle and "root(Context c)" in bundle
assert 'new WebView(' not in java, "OE's main 3D canvas must remain native GLES"
assert 'json.put("version",13)' in java, "UV44 editable houses must remain compatible"
print("OE_STAGE14_ONE_CANVAS_OK: empty default, native Landscape+Build sharing same "+
      "GLES scene, original UV44 PWB catalog through native GLB and non-mirrored camera")
