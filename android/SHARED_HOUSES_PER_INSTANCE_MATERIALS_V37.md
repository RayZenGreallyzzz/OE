# OE v37 — shared GLB houses, distinct per-house roofs/walls (phase 1)

## Owner's goal (2026-11-19)

- Build a 50×50m house once in Phoenix UV44; place 20–50 copies in one OE
  world; DO NOT load/upload 20–50 duplicate 50MiB models.
- Each house may have a different slate roof, limestone wall, darker timber,
  while all copies referencing the same source GLB and original texture bytes.
- Reusing the *same* image as a material in 50 buildings must cost ONE
  content-addressed paint texture resource, not 50 new GPU images.
- Gradually make Builder a visible **Construction Mode** of the *same OE*
  experience, retaining Phoenix's original building UI and all licensed assets.

## Implemented in v37 source

### Content-addressed shared geometry
`gAssets` already stored one immutable native `Asset` per hash-based
`oe_<sha>.glb` key. Each `Entity` holds only a reference. Duplicating a
house copies the Entity transform/styles, **not** the original mesh, GPU
buffers, texture allocations, or source bytes. Project export similarly
uses sets of model filenames; one source GLB is archived once.

### Per-house, per-material styling
- `GlbReader.Result` now preserves original glTF material IDs and names
  per draw batch. Geometry, normals, UVs, PBR factors and base texture bytes
  remain unchanged.
- Each `Entity` has a local sparse map of `MaterialStyle`
  (material ID → linear RGB multipliers and optional texture key).
- GLES renders every batch with the corresponding style for **that house**:
  optional multiplicative tone (including brighten and darken), optional
  base-color texture image fetched from shared `gPaintTextures` by hash.
  Normal/roughness/metallic maps continue to come from original glTF.
- Selected house → **⚙ Точно → 🏠 Материалы крыши/стен** → named GLB material
  → choose tint or a previously imported PNG/JPG; reset color/texture
  independently. This is material-level: it requires Phoenix's exporter to
  assign different glTF material IDs to roof and wall surfaces.
- Original `.glb`, Phoenix JSON, UV44 WebView source and data are never
  rewritten by recolor/texture operations.
- Per-object `materialStyles` lists (ID, RGB, texture hash) are persisted
  with the scene. Older saves without this field are valid; on import, the
  native renderer validates all keys/material IDs and applies changes only
  after the entire scene validates. Undo/redo snapshots copy the sparse map.
- `.oezip` export tracks all color-variant texture references and adds each
  distinct image **once** using content-hashed `tx_...png/jpg` keys, even
  when hundreds of buildings share that material.
- Safety guards: 32 *distinct* user paint textures and up to 96 MiB of
  cached preview images; 32 distinct GLB models/512 MiB bounded uncompressed
  scene archive, but up to 300 placed native Entities sharing those assets.
  Real device GPU budgets are still finite; no false unlimited-memory claim.
- Native render statistics now include count of resident GPU models and
  placed objects, so sharing can be observed. Clone placement offset takes
  the real metric width of the source object into account.
- Top toolbar includes one-tap **🏗 Стройка**; the builder still opens as
  the unchanged Phoenix UV44 mode and returns to city using the existing
  Android bridge and save. This is NOT yet one live shared GLES/WebGL canvas.

## IMPORTANT NOT YET IMPLEMENTED

- **Hardware GPU Instancing** (`glDrawElementsInstanced` / per-instance
  matrices) is *not* in this phase. One geometry/texture resource is shared,
  but each visible house still has per-material draw calls and GPU scene
  processing. Performance when 50 huge castles are simultaneously visible
  must be profiled on the owner's device.
- Material names originate in the source GLB; if Phoenix exported one
  combined material for an entire house, OE cannot independently recolor
  roofs versus walls without semantic grouping or re-export. No "fake"
  material segmentation is claimed.
- Phoenix uses its original builder WebView; a genuine single 3D world
  canvas shared between city and construction would require a staged
  integration that preserves its tools/UVs, not a cosmetic title swap.
- Still missing image-based lighting (IBL), soft shadows, tone mapping parity
  with Phoenix, and on-device FPS/image comparisons.
- An especially large >64MiB *exported GLB* still requires a streaming
  loader. A 170MiB Phoenix project JSON/ZIP is not identical to a 170MiB
  rendered GLB.

## Test

1. Open same GLB twice. Duplicate 20+ times and observe resident model
   count unchanged while number of placed objects rises.
2. Choose roof material of house A, set warmer color. Verify B unchanged.
   Set house B darker slate roof and the same shared texture on 10 copies.
3. Save and relaunch; export/import `.oezip`; check all per-instance styles.
4. Verify source GLB and Phoenix project hashes unchanged.
5. Test color editing and texture override with both PBR and Classic mode.
6. Test builder mode via always-visible top bar and return to intact city.
7. Measure FPS/draw calls on owner tablet before claiming GPU Instancing.

## Release/security

Use the SAME owner-only permanent PKCS#12 signing certificate as v35/v36,
SHA-256 `edca742dbebab11e9b36b5fee15af0475a0e57315a1173c1d076689d61e262bb`.
AppID unchanged: `game.oe.editor.phoenix013fix1`; versionCode 37.
Keep private Phoenix assets, signing key/password and user model packs out of
public GitHub.
