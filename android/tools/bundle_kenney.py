#!/usr/bin/env python3
"""Reproducible 105-object Kenney Retro Fantasy Kit bundle for OE Android.

The original archive's SHA-256 is PINNED to the user-provided asset pack.
The output includes original, unmodified GLB/PNG bytes and CC0 license.
Run before Gradle assembleDebug. The built-in archive is loaded on the
tablet without any internet dependency; source is downloaded only in CI.
"""
import argparse
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
        with urllib.request.urlopen(req, timeout=90) as response:
            data = response.read(MAX_SOURCE + 1)
    make_bundle(data, args.output)

if __name__ == "__main__":
    main()
