# OE 0.6.4 — World Tools Test (experimental, separate Android app)

**Application:** `game.oe.editor.worldtest`. Its scene files are completely separate from the original OE app, `OE STABILITY TEST` and `OE GLB SAFETY TEST`. This version does not merge to `main`.

## Portable project import/export

Tap **Экспорт проекта** to select a destination in the Android system file manager and write `ORVETHRA-World.oezip`, a standard ZIP with `scene.json` and `models/<sha256-based name>.glb`. Only referenced source GLBs are included. This allows taking your whole editable world to another Android device or test build. Tap **Импорт проекта** to select this `.oezip` and restore it.

Archive import limits: 128 MiB uncompressed total, 24 MiB per GLB, max 12 models, 200 KiB scene, 64 height brushes. Rejects path traversal, duplicate ZIP members, unknown paths, missing models, and SHA mismatch. Imported files are validated in a staging directory. The GLES scene is swapped **only after all validation and uploads pass**; on failure the old scene remains. Some verified-but-unreferenced models can remain cached if an import fails at the GPU stage.

**Not supported yet:** exporting procedural OE buildings to stand-alone GLB/FBX, exporting a working Android game APK directly from the editor, importing arbitrary FBX/OBJ, animated/skinned GLB meshes, textures or materials. Existing GLB import still supports only static untextured geometry with vertex colors.

## Detail editing

Built-in placeable objects: tavern, forge, auction house, lantern, fountain, tree, rock cluster, bush, bench; imported static GLB models. Select any object and adjust X/Z, Y, rotation and overall scale; choose the step sizes in **Точность**. **К земле** snaps the selected entity to the terrain surface.

## Mountain sculpting

Choose **⛰ Гора +** to raise terrain or **⛰ Рельеф −** to lower it. Select brush footprint and strength with **Кисть рельефа**, then tap a ground location. Each stamp has a smooth falloff with configurable radius 32–256 m and height 8–60 m. All stamps are saved in the local scene and portable archive, and participate in Undo/Redo.

Renderer caches 64-m tiles in one GPU buffer; flat tiles remain 2 triangles, sculpted tiles are 8×8 tessellated quads with computed normals. Maximum 64 stamps per scene to bound CPU and memory work on Allwinner A523. This is **procedural heightfield terrain**, not arbitrary voxel caves, mesh editing, splines or GPU sculpting; very steep near-vertical cliffs are not yet represented. Terrain stamps do not automatically adjust existing placed objects; use **К земле** as needed.

## On-device smoke test

1. Create a small hill near the center, add bush/rock/tree, move one piece by 0.1 m, rotate and scale, save.
2. Undo/redo a hill, then save/load. Verify the exact hill returns and props stay in their new positions.
3. Import a small supported GLB, place it, export `.oezip`, then select **Новая сцена** and import the archive. Verify hill, GLB, and decor come back.
4. Try a deliberately truncated archive on a throwaway test file. It must not erase the current scene.
5. Compare camera idle/movement noise and responsiveness against OE 0.6.3. Idle rendering is still `RENDERMODE_WHEN_DIRTY`; this change does not guarantee that electrical coil whine disappears.

A successful GitHub APK build only verifies compilation. Gameplay and scene stability must be tested on the actual tablet.
