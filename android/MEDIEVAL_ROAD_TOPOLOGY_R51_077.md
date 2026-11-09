# Orvetra Engine R5.1 — Medieval Connected Roads (experimental)

Branch: test/medieval-road-topology-r51-077; based on test/road-junction-editor-r5-076.
Test Android package: game.oe.editor.roadtopology. Do not merge into main or change ORVETHRA.

## What R5.1 adds beyond R5
R5 showed a disc at an intersection, without joining control points. R5.1 detects and bakes shared control points into the actual Road centerlines. Both streets get the same intersection coordinates, and a deterministic connection graph is derived. v5 road JSON and .oezip already preserve these points, so a new scene version is unnecessary.

- T: a new street endpoint within two metres of another street segment snaps to it. The host street is split at the junction control point.
- X: a crossing between two road segments inserts a point into both, producing four incident graph edges.
- Y and endpoint meetings: streets share one node and their graph edges connect.
- Road IDs, widths, materials and names survive baking. Graph node IDs are deterministic from centimetre-precision X/Z coordinates, but an edited/moved junction may change its node ID.
- Rebuilds are atomic. Invalid collinear overlaps or budget overflows leave the active network and undo stack untouched.
- Bounded work: 1200 scanned segments, 2400 nodes and edges, 96 shared junctions. Budgets target an Allwinner A523 / 4GB Android tablet.
- For previous scenes, import is untouched until the user activates the explicit 'Соединить старые дороги' command. Undo is available.

## Mobile UI
Каталог → Дороги → '🔗 Соединить старые дороги' converts legacy networks explicitly.
Каталог → Дороги → '✣ Узлы дорожной сети' reports shared nodes, nodes and edges.
Adding or moving a street point and rounding corners trigger atomic graph validation and baking.
Previously imported PNG map guide, layered material masks, road edit and scene v1–v5 compatibility remain.

## Limits, honesty and medieval art direction
This is a REAL connected CENTERLINE graph, not a watertight junction mesh. The temporary R5 visual junction discs remain. No physics, NPC navigation, traffic lanes, paved modern sidewalks or curbs are implemented. Overpasses, collinear overlaps, and grade separation are not supported yet. The graph is derived from roads, not a separate persisted authority. Each existing road keeps its ID even after inserting intersection points.
The user's world is MEDIEVAL: R5.2 should create smooth junction meshes with irregular cobblestone, earth and gravel transitions, grass edges, cart ruts and optional drainage ditches, not modern concrete curbs, asphalt or traffic lights.

## QA
Host test: tests/road_topology_smoke.cpp checks X/T/Y, endpoint snapping, deterministic IDs after reload, 4 graph edges of an X, collinear overlap refusal, duplicates and budget limits, with atomic failure.
Before installing any test APK, back up the earlier .oezip. On the actual tablet check: T/X/Y crossing, a new road ending in the middle of a street, existing street conversion, exact point edits, Undo/Redo, save/load and .oezip roundtrip, PNG overlay alignment, GLES draw order, material paint, heat and memory. CI alone does NOT prove tablet usability.

Next gates: R5.2 seamless medieval junction geometry and natural road edges, R5.3 collision/nav-ready street data, then OE Runtime Play/Stop, character/physics, interactive logic, standalone Android game runtime and OFFLINE on-device game APK builder.
