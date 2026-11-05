# OE 0.6.7 Full-Screen Touch Editor TEST

This is a **draft, isolated test branch** `test/editor-ui-gizmos-067` based on `test/textures-ktx2-066`, not a main release. Android application ID `game.oe.editor.uitest`; it installs alongside the other OE test builds and uses separate private files. Neither OE main nor ORVETHRA was modified.

## Implemented
- Immersive full-viewport 3D surface. Only a compact translucent top toolbar and optional tiny diagnostics overlay remain permanently visible.
- Toggle **☰ Каталог**. Left drawer includes six categories: **Здания / Детали / Декор / Рельеф / Текстуры / Проект**. Vertical scroll, safe close, and automatic collapse after choosing a placement or terrain tool.
- Inspector only after selecting an entity. Default inspector includes item summary and **↔ Двигать / ⟳ Повернуть / ⤢ Масштаб**, ground snap, duplicate and delete. Tap **⚙ Точно** to reveal numeric-step X/Z/Y movement, yaw and scaling buttons (including independent X/Y/Z).
- 3D axis gizmo drawn on selected object; X red, Y green, Z blue. Touch the colored axis segment and **drag**: translation along the corresponding world axis, independent-scale along that axis, or yaw around Y in rotate mode. Only Y is active for rotation; pitch and roll are not implemented. Camera and view respond to single-finger orbit and two-finger pan/pinch when not dragging an axis.
- One Undo record per drag, not one per touch-move frame; gliding/GLB/terrain/source format and 0.6.6 material/texture pipeline preserved.
- RENDERMODE_WHEN_DIRTY and 75% EGL buffer kept to reduce battery and GPU noise. No per-frame animation loop was added.

## Known limitations
- The gizmo uses simple geometric handles, not Photoshop/Blender style rings/arrows. Hitting an axis depends on screen-space projection and should be tested on the actual tablet.
- No free pitch/roll object rotation, mesh vertex editing or terrain texture painting.
- Importing **external PNG for a single wall** is not yet implemented; base-color textures are still drawn from GLB. KTX2 GPU-native is supported, BasisU ETC1S/UASTC transcoding is not. Draco, live skeletal animation and automatic LOD still require distinct implementation and QA. Original GLBs continue unchanged.
- Full immersive UI may require a swipe from edge to reveal Android system navigation.

## Tablet test
1. Open OE Fullscreen Editor Test: 3D is full screen; no permanent top object carousel or bottom multi-row inspector.
2. Tap **☰ Каталог**; choose **Детали → Стена**; drawer closes. Tap terrain to place one wall.
3. Select it, inspect the XYZ arrows, drag red X, green Y and blue Z. Undo once; the whole continuous move should revert.
4. Tap Rotate; drag green Y to rotate around vertical. Tap Scale; drag X or Z. Tap **⚙ Точно** to adjust 0.1m precision.
5. Try a two-finger pan and pinch without moving an object. Test object selection, cancel placement and reset.
6. Save scene, export `.oezip`, import again, and verify terrain, positions, nonuniform scales and GLB texture previews.
7. Compare thermals, framerate and electrical coil sound to 0.6.4 and 0.6.6. Only hardware test can confirm responsive touch and power.

**Do not merge** to main without physical test, CI success and permission.
