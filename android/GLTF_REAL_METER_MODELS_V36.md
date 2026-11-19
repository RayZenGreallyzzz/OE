# OE 0.7.13 Phoenix UV44 — true-meter GLB models and large castles (v36)

## Why
An owner exports a detailed house/castle from Phoenix. The 26.4 MiB GLB
was blocked by an out-of-sync 24 MiB Phoenix bridge guard even though
the Java GLB reader allows up to 64 MiB. Worse, OE's previous importer
**always scaled any GLB to a 6-meter maximum dimension**; that would
silently turn a 700-meter-long castle into a tiny ornament.

## In v36 (no changes to Phoenix's HTML, JS, project editor or model packs)
- glTF 2.0 geometry is treated in **meters**: one source unit becomes one
  world meter. A 700m bounding-box side stays 700m when the object is placed
  with `size=1`. Translation to centre the X/Z footprint and ground its
  minimum Y is still applied but is not a scale operation. Original .glb
  bytes (and all Phoenix saved projects/texture bytes) are untouched.
- Native scene renderer allows metric bounds larger than 100m, computes
  and retains the real min/max X/Y/Z, and shows dimensions in meters in
  the selected-object information panel.
- Imported-model picking uses actual metric bounding boxes rather than
  assuming every model is 7m wide and 6m high.
- On placement of giant imports (bounding radius >=55m), the orbit camera
  automatically frames the object; the normal camera behavior is preserved
  for smaller houses. Camera distance zoom allows up to 2400m, and its far
  plane increases dynamically from 1800m, preserving usable depth precision.
  Panning after focusing a giant castle no longer snaps the target back down.
- Old 24MiB blockers on Phoenix→OE city handoff and on OE built-in library
  imports are removed; filenames/error strings no longer advertise "24MiB".
  A file of **26.4 MiB** is eligible to reach the real GLB validation stage.
- A file that exceeds the **current 64 MiB in-memory Java decoder** is
  offered as original GLB for export to the device from Phoenix, rather than
  silently risking Android out-of-memory. Separate streaming / multi-mesh
  loading for 170 MiB+ full GLBs is a future architecture change; the editor
  can already keep 170 MiB Phoenix project backups independently of city import.
- Import preview budgets are increased in lockstep to **240,000 triangles**
  and **64 MiB decoded textures per model**, with a 128 MiB GPU texture-cache
  budget. Those are device-safety memory budgets, not rescaling limits. An
  export larger than 24MiB is not automatically guaranteed to load if it
  exceeds real complexity limits. Make failure explicit and keep the original
  model available. Models larger than these budgets require streaming.

## Exact test on tablet
1. Build a 700m long castle in Phoenix and export GLB; place it in OE.
2. In selected-object details, check the X or Z dimension reads ~700m.
3. Orbit/zoom so both ends are visible; walk/tap different castle walls.
4. Save project and reopen; ensure geometry remains meter-correct and the
   original Phoenix editing project is unchanged.
5. Test an ordinary ~6m house and compare it to the castle. Confirm both
   are at their own real scales.
6. For an export >64MiB, save it to device rather than discarding it;
   full large-asset streaming is not complete in v36.

## Release safety
Use the **same permanent owner-only 2026 PKCS#12 signing identity** first
shipped with v35 (cert SHA-256
`edca742dbebab11e9b36b5fee15af0475a0e57315a1173c1d076689d61e262bb`).
Same applicationId `game.oe.editor.phoenix013fix1`, increased versionCode 36.
Do not publish private APK model packs, Phoenix internals, signing keys, or
passwords to GitHub. Do not claim device-side success before owner QA.
