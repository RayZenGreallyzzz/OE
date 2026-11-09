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
SHA256 = "d3462b453018d0d150406d85af3c4ae1322ceef8f1754c4eb4e6355adfeaab0f"
OUT = pathlib.Path("android/src/main/assets/medieval/kenney_retro_105.zip")
SOURCE_PREFIX = "Models/GLB format/"
MAX_SOURCE = 4 * 1024 * 1024

def make_bundle(source: bytes, destination: pathlib.Path) -> None:
    if len(source) > MAX_SOURCE:
        raise ValueError("Unexpectedly large upstream download")
    actual = hashlib.sha256(source).hexdigest()
    if actual != SHA256:
        raise ValueError(f"Kenney kit SHA-256 mismatch: {actual}")
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
