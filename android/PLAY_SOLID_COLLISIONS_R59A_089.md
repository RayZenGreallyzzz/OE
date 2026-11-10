# Orvetra Engine R5.9a — first solid collision for game preview

Experimental branch: test/oe-r59a-play-solid-collision-2026-11-10.
Based on R5.8b draft PR #34; OE main, the R5.6 source backup, previous
draft branches and separate ORVETHRA game remain unchanged.

## Implemented (experimental, requires tablet QA)

- The Play-only portable C++17 walker accepts up to 300 validated
  oriented bounding boxes (OBBs) with height. Each physics tick checks
  a swept 28cm-radius horizontal character footprint against boxes,
  approximating the upright 1.7m player body. It blocks crossing solid
  obstacles at 60 Hz and tries simple X/Z sliding against walls.
- Collision boxes are derived from authored **stock buildings (0–2)**,
  **solid wall segment (10)**, and **pillar (15)** types. Their footprints,
  vertical offsets and rotations follow source GLES box geometry. This is
  conservative box collision, not triangle-perfect capsule physics.
- Only the Runtime PlaySession receives a separate obstacle snapshot.
  The old editor debug ghost/walker, save/load, Undo/Redo, scene and
  terrain authoring are unchanged. Play rejects spawning inside a solid.
- No inferred collision for open door frames, windows, terrain foundation
  pads, roofs, stairs, bridges/parapets, tree foliage, imported GLB objects,
  or textures. These remain intentionally non-solid in this prototype.
- No new saved scene fields or archive format. Previous v1-v8 and .oezip
  compatibility unchanged; existing material/lake/road/bridge code reused.
- Android debug QA package game.oe.editor.solids, version
  0.8.9-play-solid-collision-r59a, installs separately from R5.8b.

## Acceptance on real Allwinner A523 tablet

1. Export a separate .oezip from the old editor before testing. GitHub
   source backups are NOT backups of your locally saved city files.
2. Import the city into the test APK and put a wall perpendicular to a
   medieval road. Start Play from the selected road away from that wall.
3. Move against the wall, then diagonally along it. Player should not
   pass through, but should move along the wall rather than stick.
4. Check rotated wall segments, a pillar and a stock building, then jump
   toward a tall wall. Normal jumps should not teleport through a wall.
5. Verify that an open door frame is still passable (it has no collider yet);
   imported GLB assets are also not yet collidable. An unsupported obstacle
   must not be silently claimed as physical.
6. Stop, change roads and houses, Undo/Redo, reload and .oezip roundtrip.
   All unsaved editor objects and old camera position must remain intact.
7. Measure FPS, memory, battery and heat with a dense scene. CI only checks
   portable code and Android compilation, NOT actual tablet frame time.

## What is still missing

- Rounded swept capsule and triangle/mesh collision, whole-body head clearance,
  correct stair stepping, parapets, holes/door cutouts, full imported GLB physics.
- AI, animation, scripting, trigger/collision events and a game-only renderer.
- Truly standalone game APK export produced *offline on the tablet*; Github
  Actions here only builds the OE **editor** test app.
- End-user physical QA and explicit approval to merge this draft.

Do not merge the draft without device acceptance. Next: player/mesh collision
accuracy, modular collider authoring, Runtime P3 and isolated game export P4.
