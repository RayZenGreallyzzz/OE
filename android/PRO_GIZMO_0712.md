# OE 0.7.12 — XYZ 360° construction editor gizmo

Experimental isolated package: `game.oe.editor.gizmo012`. This app is
**independent** of the existing 0.7.11 test installation, because the paid
asset APK is signed with a new local-only test key and must not overwrite
user worlds from other test releases. It is not merged into main.

## What's improved

The old editor's `⟳ Повернуть` only allowed Y/yaw. In 0.7.12,
`⟳ XYZ 360°` uses a real 3D rotation manipulator drawn at the selected
object's midpoint. Three wide touch-target rings are rendered as only
three draw calls in GLES 3.0:

- **Red / X** — forwards/backwards tilt and standing up a sideways house.
- **Green / Y** — turn a whole building around the vertical axis.
- **Blue / Z** — side-to-side tilt / correct another lying orientation.

Touch a ring and drag to rotate. The screen ray intersects the desired
world-axis plane, so rotation follows the ring regardless of camera view;
edge-on rings use a bounded drag fallback. The editor stores a normalized
**quaternion**, not accumulated Euler coordinates, so the three rings work
even when a building was already tilted by 90°.

The `⚙ Точно` panel is scrollable, with `X ±90°`, `Z ±90°`,
`XYZ точно` (precise numeric degrees on X/Y/Z), `15°: вкл/выкл`
and `⟲ Сброс`. To stand a house up: select it, switch to
`⟳ XYZ 360°`, then drag the red/blue ring or use `X ±90°` /
`Z ±90°`. Finish with `↥ Поставить на землю`: unlike the previous
`К земле` control, it calculates the **actual lowest vertex of the
rotated and scaled imported GLB**, avoiding half-buried buildings.

Rotation is part of existing scene JSON v1 as **optional**
`qx,qy,qz,qw` numbers. Older scenes without those keys keep their old
yaw, and new scenes keep all three axes after save/load, undo/redo and
duplication. Existing source PNG/JPG/GLB bytes are never modified.

## Full medieval assets

The **CI-built APK is code-only**: it includes the 105 Kenney assets but
cannot contain purchased original files uploaded in this private chat. The
owner's separately delivered full APK also includes the original already
verified `OE_Medieval_Editor_145_Original_Textures.zip` inside
`assets/medieval/oe_medieval_editor_addon.zip`, same 116 modular FBX
geometries, 28 separate original-textured trees, 1 original stylized
building GLB. **Medieval Wood is intentionally excluded.**

The full APK is signed with a local independent v2 debug/test certificate,
and the encrypted unsigned source assets stay private. The exact asset bytes
are checked after signing. Each user edition has a dedicated applicationId
to avoid overwriting a different-signed APK and its worlds.

## Device test checklist

1. Select a sideways building, press `⟳ XYZ 360°`. Confirm three colored
   **rotation rings** around its center.
2. Drag **red X** ±90°. If it is still sideways, try **blue Z** ±90°.
3. Press `↥ Поставить на землю`. Confirm bottom aligns to the terrain
   without changing original texture detail.
4. Drag **green Y** for free rotation; rotate through 360°.
5. Enable 15° snap, drag rings, then disable and compare results.
6. Open numeric `XYZ точно`, enter X=90,Y=0,Z=0; then reset.
7. Save scene, undo/redo and duplicate, reopen and ensure full orientation
   and GLB/texture content survive.
8. Verify frame time on a real Android tablet.

Source tests: `tests/editor_gizmo_rotation_smoke.cpp` verifies world XYZ
composition, 360° identity and normalized quaternions. GitHub Android CI
also compiles the JNI renderer and Android UI. Physical device QA pending.
