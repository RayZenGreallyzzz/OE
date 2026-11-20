#!/usr/bin/env python3
"""Stage16 contracts for native Phoenix construction (not full UV44 parity)."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
j=(r/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
c=(r/"android/src/main/cpp/native_engine.cpp").read_text()
m=(r/"android/src/main/cpp/build/phoenix_native_modules.h").read_text()
room=(r/"android/src/main/cpp/build/phoenix_room_workflow.h").read_text()
a=(r/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
g=(r/"android/build.gradle").read_text()
assert "kFirst=29,kLast=56" in m
assert "case 53:case 54:case 55:case 56:" in m
assert "const float width=2.f,height=2.6f" in m
assert "const float bottom=kind==53?0.f:" in m
assert "kRoomStep=2" in room
assert "kRoomStorey=2.6f" in room
assert "roomPlan(int metres,bool ceiling" in room
assert "const int expected=roomCount(metres,ceiling);" in room
assert "nativeBuildPhoenixRoomAtScreen" in j and "nativeBuildPhoenixRoomAtScreen" in c
assert "oe::phoenix::roomPlan(metres,ceiling==JNI_TRUE" in c
assert "recordEdit();  // Entire room is one atomic Undo step." in c
assert "nativeSetPhoenixWallOpening" in j and "nativeSetPhoenixWallOpening" in c
assert "nativeSnapPhoenixSelected" in j and "nativeSnapPhoenixSelected" in c
assert 'addPaletteOption("🧰 Нативные формы UV44' in j
assert 'addPaletteOption("🏠 Комната UV44' in j
assert "enterPlaceMode(29+index,null);" in j
assert "enterPlaceMode(100+metres,null);" in j
assert "nativePlaceAtScreen(kind,px,py);" in j
assert 'json.put("version",14);' in j
assert "if(sceneVersion<1||sceneVersion>14)" in j
assert "e.type>56" in j
assert a.count("version>=1&&version<=14")==2
assert "kind<=56" in a
assert "oe::phoenix::kLast" in c
assert "if(oe::phoenix::valid(type))return oe::phoenix::name(type);" in c
assert "game.oe.editor.r57canvas.nativebuilder" in g
assert "versionCode 53" in g
assert 'addPaletteOption("📚 Детали оригинальной библиотеки UV44"' in j
print("OE_PHOENIX_STAGE16_OK: 28 native shapes + 2m roomPlan + actual "
      "void geometry + socket snapping + scene v14 persistence")
