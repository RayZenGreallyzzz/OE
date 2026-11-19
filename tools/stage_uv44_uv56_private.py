#!/usr/bin/env python3
"""Private staging of pristine UV44 plus only the UV56 terrain delta for ONE OE APK.

Never commit staged assets, licensed GLBs, private keys or user projects.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import shutil
import tempfile
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
    # Both functions MUST share one UV56 CleanBuilder instance and one canvas,
    # not two separate WebView pages for building and terrain.
    html=uv56["assets/index.html"]
    js=uv56["assets/js/clean-app.js"]
    for marker in (b'id="modeBuild"',b'id="modeLandscape"'):
        if marker not in html:
            raise ValueError("UV56 unified scene tab missing: "+marker.decode())
    for marker in (b"$('modeBuild')",b"$('modeLandscape')",
                   b'this.bindLandscape()',b'this.landscape.restore('):
        if marker not in js:
            raise ValueError("UV56 integrated CleanBuilder marker missing: "+
                             marker.decode())
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
    assets_root.mkdir(parents=True,exist_ok=True)
    # Build complete replacements in a temporary directory. Replacing old
    # embedded Phoenix staging MUST NOT leave stale scripts/models behind.
    # On any failure, restore the previous private staging unchanged.
    with tempfile.TemporaryDirectory(prefix=".oe-uv-stage-",dir=assets_root) as tmp:
        staging=Path(tmp)
        for name,data in uv44.items():
            target=staging/"phoenix"/name
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(data)
            if hashlib.sha256(target.read_bytes()).digest()!=hashlib.sha256(data).digest():
                raise IOError(f"UV44 byte identity failed: {name}")
        for name in UV56_DELTA:
            target=staging/"phoenix-uv56-overlay"/name
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(uv56[name])
            if hashlib.sha256(target.read_bytes()).digest()!=hashlib.sha256(uv56[name]).digest():
                raise IOError(f"UV56 delta byte identity failed: {name}")
        installed=[]
        backups={}
        try:
            for folder in ("phoenix","phoenix-uv56-overlay"):
                dest=assets_root/folder
                if dest.is_symlink() or (dest.exists() and not dest.is_dir()):
                    raise ValueError("Refuse to replace unsafe Phoenix staging: "+str(dest))
                if dest.exists():
                    old=staging/("old-"+folder)
                    os.replace(dest,old)
                    backups[folder]=old
                os.replace(staging/folder,dest)
                installed.append(folder)
        except Exception:
            for folder in reversed(installed):
                dest=assets_root/folder
                if dest.is_dir():
                    shutil.rmtree(dest)
            for folder,old in backups.items():
                if old.exists():
                    os.replace(old,assets_root/folder)
            raise
        # Previous assets are discarded ONLY after both private staging
        # directories have been replaced successfully. Never installed data.
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
