#!/usr/bin/env python3
"""Private staging of pristine UV44 plus only the UV56 terrain delta for ONE OE APK.

Never commit staged assets, licensed GLBs, private keys or user projects.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

UV56_DELTA=frozenset({
    "assets/index.html", "assets/clean.css",
    "assets/js/clean-app.js", "assets/js/export-glb.js",
    "assets/js/landscape-manager.js", "assets/js/landscape-paint.js",
    "assets/js/landscape-roads.js"
})

def files_from_apk(apk: Path) -> dict[str,bytes]:
    files={}
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            name=info.filename
            if info.is_dir() or not name.startswith("assets/"):
                continue
            parts=PurePosixPath(name).parts
            if ".." in parts or "\\" in name or len(parts)<2:
                raise ValueError(f"Unsafe APK asset path: {name}")
            if name in files:
                raise ValueError(f"Duplicate APK asset: {name}")
            files[name]=archive.read(info)
    return files

def verify_deltas(uv44: dict[str,bytes],uv56: dict[str,bytes]) -> dict:
    if "assets/index.html" not in uv44 or len(uv44)<100:
        raise ValueError("UV44 asset pack missing or incomplete")
    if not UV56_DELTA <= uv56.keys():
        raise ValueError(f"UV56 missing delta files: {sorted(UV56_DELTA-uv56.keys())}")
    if set(uv44)-set(uv56):
        raise ValueError("UV56 removed original UV44 assets")
    unexpected=[name for name in uv56 if name not in UV56_DELTA and
                (name not in uv44 or uv44[name]!=uv56[name])]
    if unexpected:
        raise ValueError("Unexpected changes in UV56; refuse to stage: "+
                         ", ".join(sorted(unexpected)[:15]))
    return {"original_files":len(uv44),"delta_files":len(UV56_DELTA),
            "shared_identical_files":len(set(uv56)-UV56_DELTA),
            "uv44_index_sha256":hashlib.sha256(
                uv44["assets/index.html"]).hexdigest()}

def stage(uv44_apk:Path,uv56_apk:Path,assets_root:Path) -> dict:
    uv44=files_from_apk(uv44_apk)
    uv56=files_from_apk(uv56_apk)
    info=verify_deltas(uv44,uv56)  # validate before writing private files
    for name,data in uv44.items():
        target=assets_root/"phoenix"/name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data)
        if target.read_bytes()!=data:
            raise IOError(f"UV44 SHA byte-identity lost: {name}")
    for name in UV56_DELTA:
        target=assets_root/"phoenix-uv56-overlay"/name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(uv56[name])
        if target.read_bytes()!=uv56[name]:
            raise IOError(f"UV56 delta byte-identity lost: {name}")
    return info

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--uv44",type=Path,required=True)
    parser.add_argument("--uv56",type=Path,required=True)
    parser.add_argument("--assets-root",type=Path,
                        help="android/src/main/assets (never commit)")
    args=parser.parse_args()
    if args.assets_root:
        result=stage(args.uv44,args.uv56,args.assets_root)
    else:
        result=verify_deltas(files_from_apk(args.uv44),
                             files_from_apk(args.uv56))
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if not args.assets_root:
        print("Read-only verification: no files were modified.")

if __name__=="__main__":
    main()
