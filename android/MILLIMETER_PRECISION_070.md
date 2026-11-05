# OE 0.6.10 — Millimetre Free Placement TEST

**Isolated package:** `game.oe.editor.millitest`. Based on OE 0.6.9 static LOD + texture paint; no changes to OE `main` or ORVETHRA game. Different app storage; transfer scenes with an `.oezip` export/import.

## Functionality
- Existing 3D axis handles allow free continuous movement without grid snapping.
- In the contextual inspector tap **⚙ Точно** → **Точность** to choose step: 1mm / 5mm / 1cm / 5cm / 10cm / 25cm / 50cm / 1m.
- Default precision step is **1 mm** for X/Z/Y buttons. A dedicated **Шаг 1 мм** shortcut resets it instantly.
- Tap **📍 XYZ вручную** for three separately editable coordinates. Input is in metres; `0.001` = 1 mm. Supports negative values, standard decimal points and commas. Coordinates are applied together with a single Undo entry; out-of-bounds/invalid values are rejected, never silently clipped.
- Position display shows 3 digits after the decimal; JSON project snapshots show 6 digits. Saved scene format **version 1** remains compatible with OE 0.6.9/0.6.8 (new precision uses existing float coordinate fields).
- Terrain snapping **К земле** remains opt-in and is not activated automatically. The imported GLB and built-in decorative detail object positions are editable independently of terrain tile boundaries.

## Honest accuracy limits
- Runtime object coordinates remain IEEE-754 **float32** on the native renderer. At the edge of the largest supported 16km world (±8192m), float32 spacing is about 0.0009766m = 0.977mm, so values entered as `8191.123` may be represented with a few tenths of a millimetre error. Near the world origin precision is much finer.
- This is **millimetre-step editing**, not guaranteed submillimetre engineering CAD precision across the whole world. Supporting exact millimetre/integer local coordinates at arbitrarily large positions would require a double-precision scene representation or floating-origin architecture.
- Free gizmo movement follows screen-space finger distance and is visually precise but not guaranteed to land on an exact 1mm target. Use XYZ numeric input to align pieces.
- Placement remains restricted to the map bounds and editable entity height -24..160 m; only *grid snapping* is removed. Existing edit limitations (max 300 entities etc) still apply.

## Manual tablet QA
1. Choose **Детали → Стена**, place two walls; choose one. Verify 3D handle drag moves smoothly **without snapping to 64m terrain tiles**.
2. Open **⚙ Точно → Точность → 1 мм** and press X+ five times. Selection label should advance ~0.005m. Press Undo once to revert the last 1mm increment.
3. Tap **📍 XYZ вручную**. Set e.g. X=12.001, Y=0.003, Z=-8.007. Confirm 3-decimal readout and correct placement, then Undo once to revert the whole set.
4. Save locally, close and reopen, Load. Verify XYZ values and no coordinate truncation. Export to `.oezip`, import and verify again.
5. Use **К земле** intentionally; ensure normal X/Y/Z operations did not automatically snap the piece to the terrain.
6. Test near the far end of a 16km world: values are float32-quantized but should maintain approximately 1mm responsiveness. Compare visual stability on your Android tablet.

CI build validates compilation, not touchscreen or GPU behavior on hardware. Do not merge without device checks.
