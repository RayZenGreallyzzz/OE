#!/usr/bin/env python3
"""Reproducible 105-object Kenney Retro Fantasy Kit bundle for OE Android.

The original archive's SHA-256 is PINNED to the user-provided asset pack.
The output includes original, unmodified GLB/PNG bytes and CC0 license.
Run before Gradle assembleDebug. The built-in archive is loaded on the
tablet without any internet dependency; source is downloaded only in CI.
"""
import argparse
import concurrent.futures
import urllib.parse
import hashlib
import io
import pathlib
import urllib.request
import zipfile

UPSTREAM = (
    "https://kenney.nl/media/pages/assets/retro-fantasy-kit/"
    "cf3f41b752-1774770658/kenney_retro-fantasy-kit.zip"
)
# Pin the actual uncompressed model+texture payload instead of the enclosing
# ZIP metadata. Different ZIP compression/timestamp wrappers are allowed only
# if all 105 original meshes and all 10 original images are byte-identical.
CONTENT_SHA256 = "893063a957225f1fe722dd2f968d431d1065e8a7ca1567d13f03abca61134551"
TEXTURES_SHA256 = "117dd53a8fe5dee201d1361988867d799ab25656d3f6429c490590dbfb0d972c"
OUT = pathlib.Path("android/src/main/assets/medieval/kenney_retro_105.zip")
SOURCE_PREFIX = "Models/GLB format/"
MAX_SOURCE = 4 * 1024 * 1024
MIRROR_ROOT = (
    "https://raw.githubusercontent.com/eturner58/game-assets/"
    "fc2cd355a8e7c1d8e625fd650abf64f50a1fddaa/"
    "kenney/3D assets/Retro Fantasy Kit/"
)
# Pinned mirror commit. Every remote file is still validated against the
# original user-supplied complete 115-resource manifest (not just a few names).
MIRROR_MODELS = """barrels.glb battlement-corner-inner.glb battlement-corner-outer.glb battlement-half.glb battlement.glb bricks.glb column-damaged.glb column-paint-damaged.glb column-paint.glb column-wood.glb column.glb detail-barrel.glb detail-crate-ropes.glb detail-crate-small.glb detail-crate.glb dock-corner.glb dock-side.glb fence-top.glb fence-wood.glb fence.glb floor-flat.glb floor-stairs-corner-inner.glb floor-stairs-corner-outer.glb floor-stairs.glb floor-steps-corner-inner.glb floor-steps-corner-outer.glb floor-steps.glb floor.glb ladder.glb overhang-fence.glb overhang-round-railing.glb overhang-round.glb overhang.glb pulley-crate.glb pulley.glb roof-corner.glb roof-edge.glb roof-high-side-corner-inner.glb roof-high-side-corner.glb roof-high-side.glb roof-side-corner-inner.glb roof-side-corner.glb roof-side.glb roof.glb stairs-corner.glb stairs-stone.glb stairs-wood.glb structure-cross.glb structure-pole.glb structure-poles.glb structure-wall-cross.glb structure-wall.glb structure.glb tower-base.glb tower-edge.glb tower-paint-base.glb tower-paint.glb tower-top.glb tower.glb tree-large.glb tree-shrub.glb wall-detail.glb wall-door.glb wall-flat-gate.glb wall-fortified-door.glb wall-fortified-gate-half.glb wall-fortified-gate.glb wall-fortified-half.glb wall-fortified-paint-door.glb wall-fortified-paint-gate.glb wall-fortified-paint-half.glb wall-fortified-paint-window.glb wall-fortified-paint.glb wall-fortified-window.glb wall-fortified.glb wall-gate-half.glb wall-gate.glb wall-half.glb wall-low.glb wall-paint-detail.glb wall-paint-door.glb wall-paint-flat.glb wall-paint-gate.glb wall-paint-half.glb wall-paint-window.glb wall-paint.glb wall-pane-door.glb wall-pane-paint-door.glb wall-pane-paint-window.glb wall-pane-paint.glb wall-pane-painted-wood-door.glb wall-pane-painted-wood-window.glb wall-pane-painted-wood.glb wall-pane-window.glb wall-pane-wood-door.glb wall-pane-wood-window.glb wall-pane-wood.glb wall-pane.glb wall-window.glb wall.glb water.glb wood-floor-half.glb wood-floor-quarter.glb wood-floor-railing.glb wood-floor.glb""".split()
MIRROR_TEXTURES = """Textures/barrel.png Textures/cobblestone.png Textures/cobblestoneAlternative.png Textures/cobblestonePainted.png Textures/details.png Textures/fence.png Textures/planks.png Textures/roof.png Textures/tree.png Textures/water.png""".split()
assert len(MIRROR_MODELS)==105 and len(MIRROR_TEXTURES)==10


def verified_mirror_source() -> bytes:
    """Fallback when Kenney's own download endpoint returns HTML to CI."""
    paths = ["Models/GLB format/" + n
             for n in MIRROR_MODELS + MIRROR_TEXTURES] + ["License.txt"]

    def fetch_one(name: str):
        safe_path = urllib.parse.quote(name, safe="/")
        url = MIRROR_ROOT.replace(" ", "%20") + safe_path
        request = urllib.request.Request(
            url, headers={"User-Agent": "OE-0.7.10-CI-Kenney-CC0"})
        # Raw GitHub files are short, unlike the 523 MB source repository.
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=50) as response:
                    data = response.read(1_500_000)
                if not data or len(data) >= 1_500_000:
                    raise ValueError("Unexpected mirror file size: "+name)
                return name, data
            except Exception:
                if attempt==2:
                    raise
        raise AssertionError("unreachable")

    # Fetch only 105 small original meshes and 10 PNGs, without checking
    # out or downloading the mirror repository's 523 MB asset collection.
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        payloads = dict(pool.map(fetch_one, paths))
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6) as archive:
        for name in sorted(paths):
            archive.writestr(name, payloads[name])
    print(f"Fetched {len(paths)} original files from pinned GitHub mirror.")
    return output.getvalue()

def make_bundle(source: bytes, destination: pathlib.Path) -> None:
    if len(source) > MAX_SOURCE:
        raise ValueError("Unexpectedly large upstream download")
    archive_sha = hashlib.sha256(source).hexdigest()
    print(f"Upstream container SHA-256: {archive_sha}; ZIP header: {source[:12]!r}")
    with zipfile.ZipFile(io.BytesIO(source), "r") as original:
        all_names = original.namelist()
        models = sorted(n for n in all_names if
                        n.startswith(SOURCE_PREFIX) and n.endswith(".glb"))
        textures = sorted(n for n in all_names if
                          n.startswith(SOURCE_PREFIX+"Textures/") and
                          not n.endswith("/") and n.lower().endswith((".png", ".jpg", ".jpeg")))
        if len(models) != 105 or not textures:
            raise ValueError(
                f"Need 105 separate GLBs and textures: {len(models)} GLBs, "
                f"{len(textures)} textures")
        if len(set(x.rsplit("/", 1)[-1] for x in models)) != 105:
            raise ValueError("Duplicate model basenames")
        content_hash = hashlib.sha256()
        texture_hash = hashlib.sha256()
        for name in sorted(models + textures):
            data = original.read(name)
            key = name[len(SOURCE_PREFIX):].encode("utf-8")
            payload = key + bytes((0,)) + len(data).to_bytes(8, "little") + data
            content_hash.update(payload)
            if name in textures:
                texture_hash.update(payload)
        actual_content = content_hash.hexdigest()
        actual_textures = texture_hash.hexdigest()
        print(f"Resource manifest SHA-256: {actual_content}; textures: {actual_textures}")
        if actual_content != CONTENT_SHA256 or actual_textures != TEXTURES_SHA256:
            raise ValueError("Original user's 105 GLBs / 10 PNGs differ from upstream; refusing to change textures")
        license_name = "License.txt" if "License.txt" in all_names else "LICENSE.txt"
        if license_name not in all_names:
            raise ValueError("Missing Kenney CC0 license")
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destination, "w",
                             compression=zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for name in models + textures:
                # Flatten the original GLB folder but preserve Textures/* links.
                target = name[len(SOURCE_PREFIX):]
                data = original.read(name)
                out.writestr(target, data)
            out.writestr("LICENSE.txt", original.read(license_name))
    with zipfile.ZipFile(destination, "r") as output:
        actual_models = [n for n in output.namelist() if n.endswith(".glb")]
        assert len(actual_models) == 105, "Bundle is incomplete"
        assert any(n.endswith(".png") for n in output.namelist())
        bad = output.testzip()
        if bad:
            raise ValueError(f"ZIP CRC mismatch in {bad}")
    print(f"Bundled {len(models)} separate original Kenney GLBs and "
          f"{len(textures)} original texture files in {destination} "
          f"({destination.stat().st_size} bytes); SHA-256 pinned.")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path,
                        help="Use an existing verified upstream ZIP instead of downloading")
    parser.add_argument("--output", type=pathlib.Path, default=OUT)
    args = parser.parse_args()
    if args.source:
        data = args.source.read_bytes()
    else:
        req = urllib.request.Request(UPSTREAM,
                   headers={"User-Agent":"OE-asset-pack-builder/0.7.10 (+Kenney CC0)"})
        try:
            with urllib.request.urlopen(req, timeout=90) as response:
                data = response.read(MAX_SOURCE + 1)
        except Exception as exc:
            print(f"Kenney source unavailable: {exc}; using pinned mirror.")
            data = b""
        if not zipfile.is_zipfile(io.BytesIO(data)):
            print("Official source was not a ZIP; using original-asset mirror.")
            data = verified_mirror_source()
    make_bundle(data, args.output)

if __name__ == "__main__":
    main()
