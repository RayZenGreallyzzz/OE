# Orvetra Engine R5.7 — Real touch-walking preview and graded bank-to-bridge contacts

**Branch:** `test/road-bridge-ramps-touch-r57-085` stacked on approved-for-testing (NOT merged) R5.6.
**Backup:** `backup/oe-r56-before-touch-walk-2026-11-10` at commit `0cc04961600557e84035c2cff15900ee37cdd9a0`.
**Android test app:** `game.oe.editor.touchwalk` version `0.8.5-bank-ramps-touch-r57-test`.
**No changes to OE `main`, Phoenix Builder or the separate ORVETHRA game.** Never merge without user approval.

## What is actually implemented

The R5.6 60Hz C++17 `runtime::WalkController` remains the single feet-collision solver. R5.7 makes two targeted improvements:

**1 — Physical collision support for the previously empty road-to-bridge gap.**

R5.4 can match bridge mouths to the ends of nearby roads up to 2.5 m away, but the old collision only contained the road and bridge itself. There was no walk surface in between, so in a riverbank valley a walker could fall through a physical hole at the joint. This release generates compact **four-corner approach quads** for each bank of a bridge ONLY after both ends genuinely attach to distinct road dead ends, the road direction matches the mouth, the step height is within the previous 0.65 m safety limit, and the grade of a noncoincident approach is no steeper than 0.85 (metres rise per metre horizontal). Gap ≤0.08 m requires no additional quad. The approach is as narrow as its connected road and bridge deck, not wider than either. A one-bank bridge, stepping-stone ford, steep approach or road passing sideways **never creates an invisible walkable shortcut**.

The original bridge deck quad, road IDs, terrain, saved materials and nav graph edges are unchanged; approach quads are transient CPU collision data, not a modification to geometry saved in `.oezip`. They are tagged `bridge=true` so the existing collision walker admits them only when the bridge connects **both** banks. `TraversalGraph::approachSurfaces` exposes the actual count for diagnostic review.

**2 — Analog on-screen touch controls in the editor.**

Use `Каталог → Дороги → 🧍 Физика R5.6: путник на дороге` (also in Bridges). Select an authored road before entering the preview to spawn on the first control point. On success the catalogue closes and shows a **circular analog pad at bottom left**, **🦘 Прыжок** and **✕ Тест** at bottom right. Hold and slide the pad to move in world X/Z; release/cancel resets both axes immediately. Existing one-second directional menu controls remain as a fallback.

Java schedules a maximum of one GPU-thread physics event at a time. **When moving/jumping/airborne**, one event advances **two fixed 60Hz ticks** and draws at roughly **30 FPS** (Android may reduce actual rate under load; no unbounded time catch-up). **When idle and grounded**, the simulation stops rendering continuously, conserving battery on Allwinner A523 with 4GiB RAM. Head-up diagnostic updates are throttled (~350ms). A jump is edge-triggered once per tap; gravity continues during airborne idle. Controls stop and disappear on ✕, Android onPause, or the start of an authoring/edit action, and native preview state is cleared. No ghost actor is serialized, all `.oezip` scenes remain **v1–v8** compatible.

**The joystick movement currently follows fixed world compass X/Z**, not camera-relative direction. This is an intentional intermediate Play/Stop readiness mode. The camera view still uses the editor camera and follows the ghost target, with no physical collider attached to the camera.

## QA and limits

1. Export a safe `.oezip` of your city. Install R5.7 separately (old debug versions stay installed). Open a world with medieval roads, one transparent guide PNG, PNG/JPG road stone textures, T/X/Y crossings, lake basin and bridge.
2. Create a pair of road ends ~1–2 metres away from opposite bridge mouths. Align the selected stone bridge via `Мосты → 🧲 Подогнать выбранный мост`. Inspect traversal stats; the physical connection should now include two bank-transition surfaces if both end gaps exceed 8cm.
3. Start the walker from one of those roads, hold right/left on the circular pad to cross the gap. The green/gold marker should remain above the bank/bridge rather than dropping down to the waterless riverbed.
4. With one bank missing or with an unreasonable incline, the crossing should NOT be marked fully connected. Walk carefully toward its missing end: gravity and the terrain floor still apply; there is no invisible bridge.
5. Tap jump while the thumb remains on the movement pad. Confirm takeoff/landing and no sticky movement if the finger is released or a second finger leaves. Open `🔍 Статус столкновения` for actual support surface.
6. Press ✕, open the drawer and modify a road, Undo/Redo, export/re-import your city; the test actor must be gone and all saved buildings, lake water, road images, materials, bridges and imported assets must remain intact. Background and resume should stop the test safely.
7. Observe memory/FPS/battery on your actual ARM64 Android tablet. This cannot be validated by CI.

Strict host tests: `tests/bridge_bank_transition_smoke.cpp` exercises road-to-deck physical walking across a **1.2m bank gap** over a 4m-deep riverbed, both connected approaches, grade rejection, one-sided connection rejection and atomic failures. `tests/touch_preview_integration_smoke.py` verifies Java touch pointer release, bounded worker queue, GL-thread-only native calls, pause and scene isolation. All older R5.6 collision, road UV, junction, lakes, mesh/collisions and scene tests still run.

**Still not done:** visible stone/wood geometry spanning each bank gap (this release repairs *collision* seams, not rendered road mesh), swept capsule collision, wall/railing collisions, swimming/buoyancy, NPC routefinding, real player animation, joystick-relative camera, complete Play/Stop isolated scene/runtime, separate Android game runtime and the offline standalone game APK builder on the tablet without PC. Those remain next, after actual tablet QA.

## Product direction

Complete the road/bridge visual-contact quality gate and **then** implement independent runtime scene copy, Play/Stop, camera/player and game logic, with final separate offline native game APK export directly on the Android tablet. This R5.7 APK is still the **OE editor QA build**, not a game exported by the tablet.
