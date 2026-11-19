#!/usr/bin/env python3
"""Regression test for the owner's Stage 2 tablet bug report.

We deliberately DO NOT claim these static checks replace device painting
or the Phoenix UV56 1024x1024 material masks.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
native=(root/"android/src/main/cpp/native_engine.cpp").read_text()
editor=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
gallery=(root/"android/src/main/java/game/oe/editor/NativeLandscapePanel.java").read_text()
arch=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
for marker in (
    "if(gBrushes.empty()&&gUv56Terrain.nodeCount()==0)",
    "nativeUv56LastStrokeError(", "gUv56LastStrokeError=2",
    "gUv56LastStrokeError=3", "gUv56LastStrokeError=4",
    "gUv56Terrain.affectsTile(tile.x,tile.z)",
    "const float matStep=oe::terrainpaint::kTileMetres/static_cast<float>(divisions)",
    "const Vec3 n=unit({-dx,1.f,-dz})",
    "static constexpr size_t MAX_PAINT_TEXTURES=32,MAX_PAINT_CACHE_BYTES=128u*1024u*1024u",
    "nativeUv56StrokeMove(", "nativeUv56StrokeEnd(",
    "runtime/walk_collision.h", "water/lake_surface.h",
):
    assert marker in native, ("native regression",marker)
for marker in (
    'Intent.EXTRA_ALLOW_MULTIPLE,true',
    'intent.getClipData().getItemCount()',
    'importPaintTextures(new ArrayList<>(selected))',
    'StandardCopyOption.ATOMIC_MOVE',
    'MAX_TILE_LIBRARY=32',
    'safeTileLabel(uri)', 'tile_title_',
    'nativeUv56LastStrokeError()',
    'else if(reason==3)',
    'new NativeLandscapePanel(',
):
    assert marker in editor, ("Android regression",marker)
assert 'MAX_TEXTURES=32' in arch
assert 'version>=1&&version<=9' in arch
assert 'shown>=8' not in gallery
assert 'shown>=32' in gallery
assert 'tileTitles.get(key)' in gallery
assert '2×2 repeat' in gallery
assert 'source.recycle();' in gallery
print("OK: UV56 height hit-test, touch failure diagnostics, 32 SAF bulk tiles, "
      "higher-detail native material rendering and old v1-v9 projects")
