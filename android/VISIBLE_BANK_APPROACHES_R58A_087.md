# Orvetra Engine R5.8a — visible medieval road-to-bridge approaches

**Status:** experimental code and Android QA build. Not a final Play/Stop milestone.
**Source branch:** test/oe-r58a-visible-bank-seams-2026-11-10, stacked on R5.7.1.
The R5.6 source backup and OE main remain unchanged; the separate ORVETHRA game
is unrelated and untouched.

## Implemented

- Collision geometry is the single authority. TraversalGraph produces
  bank-to-bridge quadrilaterals only when both ends attach to real safe road
  dead ends, and now tries the next best safe end if the nearest one is sideways
  or too steep. The existing 2.5m reach and 0.65m rise limits remain intact.
- The portable bridge_approach_visual module converts physically validated
  approach quads to two visible triangles each. Upward winding, shared road
  terminal cap, and a 1.6cm render-only offset prevent incorrect faces
  and z-fighting. It does not invent a half bridge or suspended floor.
- The existing GLES3 road shader and buffer render short stone/dirt/grass
  approach skins. Each inherits the corresponding road's builtin material
  or PNG/JPG texture and repeat scale. Seams with matching textures are batched
  to reduce draw calls. This is NOT a detailed stone arch, wall or hand-built
  railing geometry; it is a visible road-to-deck surface skin.
- Bridges are hashed by their actual x/y/z/yaw/scale/type. Move/rotate/scale,
  delete, Undo/Redo, terrain edits and scene changes update the cached visuals;
  camera movement and the temporary ghost do not force per-frame mesh rebuilds.
- No new save format: every seam and material source ID is derived at runtime,
  stays outside the editor scene, and leaves legacy scenes v1-v8/.oezip intact.
- Android test application: game.oe.editor.bankseams, version
  0.8.7-visible-bank-seams-r58a, separately installable from R5.7 and R5.7.1.

## Tablet QA — required

1. Export a separate .oezip city backup before testing. The GitHub code backup
   is NOT a backup of local city models, textures or saved work.
2. Install the ARM64 R5.8a test APK on the Allwinner A523. Import an existing
   city with cobblestone streets, PNG/JPG textures, a lake and road ends.
3. Place a timber/stone bridge with a 0.5–2m approach gap on both ends. Verify
   both approach skins appear, and the temporary walker crosses them without
   falling into the riverbed.
4. Change road material and texture tiling, then rotate/scale/move the bridge.
   Seams must update, and must disappear when the link becomes unsafe or one
   bank is disconnected. Verify no old floating geometry remains.
5. Undo/Redo, modify terrain and reload/export .oezip. Confirm existing roads,
   objects, textures, lake levels and materials remain unchanged.
6. Profile FPS, GPU draw calls, RAM and heat on the actual tablet with several
   bridges; CI builds alone do not verify performance on this target.

**Still pending:** complete isolated Play/Stop game snapshot, runtime camera,
solid wall/railing/capsule collisions, NPC gameplay, offline standalone game
APK export and physical tablet quality acceptance. These are future milestones.

**CI:** Portable visual smoke, bank collision regression, static JNI/renderer
integration smoke and Android debug APK build. Do not merge the draft PR before
physical tablet validation and explicit approval.
