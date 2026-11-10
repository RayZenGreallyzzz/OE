# OE R5.6 — First actual terrain/road/bridge walking collisions (experimental)

**Branch:** `test/walk-collision-preview-r56-084` (stacked on R5.5 road textures).
**Source backup:** `backup/oe-r55-road-textures-2026-11-10` at `3abfe48f4f546fd7df02ed09cb9fc977c46a90b6`.
**APK:** `game.oe.editor.walkcollision`, `0.8.4-walk-collision-r56-test`, arm64 GLES3 only.
**Safety:** changes only in test branch with draft PR; OE `main`, Phoenix Builder and unrelated ORVETHRA remain untouched. No merge without user approval.

## Real implementation (as opposed to R5.4 metadata)

- New portable C++17 `runtime/walk_collision.{h,cpp}` is linked into the existing `oe_runtime_core` library, used by the native Android editor and available for a future game runtime. Character feet are **physically resolved** against the edited terrain heightfield and triangle planes of R5.4 road strips and connected bridge decks, not simply drawn on screen with static positions.
- Fixed 60Hz integration: walking at **3.5 m/s**, gravity **−18 m/s²**, jump initial speed **6.3 m/s**, step up ≤ **0.65 m**, terrain/quad gradient ≤ **0.85**. Fixed tick count per action is limited 1–180; movement directions normalized.
- Road support is the actual terrain-following quad with +0.14m road surface lift; a bridge uses the independent flat bridge Y from R5.4. Unconnected bridge surfaces are **not registered** as walkable platforms; a riverbed below a connected bridge cannot teleport its walker onto the deck overhead. The landscape remains the physical support where roads don't exist.
- A high wall or steep uphill rejects horizontal movement; stepping off a high downward drop is allowed and gravity resolves the fall to the terrain. Walking/jumping are deterministic and do not mutate authored scene data. A functional `applyToWorld(World&,EntityId)` adapts the solved player-feet transform into OE Runtime ECS: collision solver is no longer a passive road metadata object.
- An editor-only **ghost walker** appears as a colored marker at the selected road's first point (or editor camera target if no road selected). Color green = grounded on terrain/road, gold = grounded on connected bridge, red = in the air. The preview marker is not a scene entity and cannot be serialized to scene v8 or `.oezip`.
- Native JNI on the GLES thread uses the same `WalkController` class and the same existing `oeCurrentTraversalGraph`. The editor has a **`Дороги → 🧍 Физика R5.6: путник на дороге`** menu plus N/S/E/W controls, jump, status and stop. Every button advances precisely **60 simulation ticks** (one simulated second), not real-time touch-and-hold yet. This is a deliberate isolated debug preview, not final game input.
- The test stops and clears derived collision state on project edits, Undo/Redo, Reset, or scene replacement. Existing camera/view may follow the ghost, but geometry/roads/terrain/bridge data and save format are unchanged. No Unity/Unreal-level full physics engine claimed.

## Actual tablet test

1. **Back up your city** as `.oezip`; install R5.6 separately, keeping your old R5.5 package.
2. Create two roads approaching opposite ends of a stone/wooden bridge and use **`Мосты → 🧲 Подогнать выбранный мост`**. Check **`👣 Проверить проходы`** shows both ends connected. Terrain under the bridge may be a low river/lake basin (water remains visual only).
3. Choose one bank's road in **`Дороги → 📋 Выбрать дорогу`**. Tap **`🧍 Физика R5.6: путник на дороге`**. A green person-shaped marker should appear at the first road point. The camera follows the marker when moving.
4. Use **north (−Z)**, **south (+Z)**, **east (+X)**, **west (−X)** commands. Each moves for 1 simulated second at walking speed, if collision allows it. On the bridge the marker is gold; over the riverbed without bridge clearance the character remains below the deck, cannot teleport up.
5. Tap **`🦘 Путник: прыжок`**, observe red in flight and green/gold on landing. Use **`🔍 Статус столкновения`** for coordinates, support surface and simulation ticks.
6. Create a high step or steep hillside with terrain brush and verify the walker cannot climb the wall. At an actual downhill ledge it should fall and land, not hover.
7. Stop preview; edit terrain, Undo and reopen the scene: **no ghost should be saved**, and all R5.5 road textures, city PNG guide, lakes, medieval bridge/foundations and soil material layers should still be intact.
8. Check performance, GLES context loss after sleep/background, and display/touch responsiveness on **Allwinner A523 / 4 GiB RAM**. Physical tablet QA has **not** been run by GitHub CI.

## Limits / next milestone

- **This IS floor-contact resolution and gravity for a small single test actor**, but **NOT** the full Play/Stop game or a native Android game-exported APK. There is no real-time drag joystick, camera-follow smoothing, character model/animation, NPC routing, collision against vertical building walls or railings, swimming, water buoyancy, dynamic rigid bodies, ramps automatically inserted at bridge mouths, or Android game APK exporter.
- The solver uses a point under the character's feet, not a swept capsule/continuous collision shape. It can miss thin walls (none are included) and has no entity/entity or ceiling collision. At 60 Hz a high-speed physics system would require swept collision.
- R5.4 quads are a simplified approximation to road strips, not the exact R5.2 texture mesh at every curve; physical A523 tests may reveal minor gaps, especially at intersections and uneven banks.
- Bounds: 3000 walk quads (as before), 120 bridges, 0.65m maximum step-up, 180 fixed ticks per action. Strict finite coordinates, terrain heights, and quad degeneracy checking abort before mutating the current preview.
- **Next:** improve bridge/ribbon seam collision and tackle the user's road quality observations; then integrate actual Play/Stop with editor scene copy and touch-controlled walking. Build a separate Android Game Runtime and later create standalone **offline signed game APKs directly on the tablet**, without needing GitHub Actions/PC, as the user ultimately wants.
