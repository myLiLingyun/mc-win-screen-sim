#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Find a Minecraft Java asset store and copy the external player assets.

Minecraft keeps Unifont and the credits music outside the client jar, in:

    <minecraft>/assets/indexes/<id>.json
    <minecraft>/assets/objects/<first-two-hash-chars>/<hash>

This script locates the Minecraft directory on Windows, macOS or Linux, picks
an asset index that contains all required objects, and copies Unifont +
alpha.ogg into the destination assets tree. It never downloads anything.

Usage:
    python3 tools/find_minecraft_assets.py
    python3 tools/find_minecraft_assets.py --out assets
    python3 tools/find_minecraft_assets.py --minecraft-dir "D:\\Games\\.minecraft"
    python3 tools/find_minecraft_assets.py --list
    python3 tools/find_minecraft_assets.py --index 32
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import zipfile
from pathlib import Path

EXTERNAL_OBJECTS = {
    "font/include/unifont.json": "minecraft/font/include/unifont.json",
    "font/unifont.zip": "minecraft/font/unifont.zip",
    "sounds/alpha.ogg": "minecraft/sounds/music/game/end/alpha.ogg",
}
UNIFONT_LICENSE = "font/UNIFONT-LICENSE.txt"


def candidate_roots() -> list[Path]:
    roots: list[Path] = []

    def add(path: Path | None) -> None:
        if path is None:
            return
        try:
            roots.append(path.expanduser())
        except Exception:
            pass

    env = os.environ.get("MINECRAFT_DIR")
    if env:
        add(Path(env))

    add(Path.home() / ".minecraft")

    if sys.platform == "win32":
        appdata = os.environ.get("APPDATA")
        if appdata:
            add(Path(appdata) / ".minecraft")
        profile = os.environ.get("USERPROFILE")
        if profile:
            add(Path(profile) / "AppData" / "Roaming" / ".minecraft")
    elif sys.platform == "darwin":
        support = Path.home() / "Library" / "Application Support"
        add(support / "minecraft")
        add(support / ".minecraft")
    else:
        add(Path.home() / ".var" / "app" / "com.mojang.Minecraft" / ".minecraft")
        add(Path.home() / "snap" / "minecraft" / "common" / ".minecraft")

    # Keep order, remove duplicates.
    unique: list[Path] = []
    seen: set[str] = set()
    for root in roots:
        key = str(root)
        if key not in seen:
            seen.add(key)
            unique.append(root)
    return unique


def has_asset_store(root: Path) -> bool:
    return (root / "assets" / "indexes").is_dir() and (root / "assets" / "objects").is_dir()


def find_minecraft_dir(explicit: Path | None) -> Path:
    if explicit is not None:
        root = explicit.expanduser()
        if not has_asset_store(root):
            raise SystemExit(f"not a Minecraft directory (no assets/indexes + objects): {root}")
        return root

    checked = candidate_roots()
    for root in checked:
        if has_asset_store(root):
            return root

    print("no Minecraft asset store found. Checked:", file=sys.stderr)
    for root in checked:
        print(f"  {root}", file=sys.stderr)
    raise SystemExit("use --minecraft-dir <path> (the directory containing assets/)")


def list_indexes(root: Path) -> list[Path]:
    indexes = root / "assets" / "indexes"
    return sorted(indexes.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)


def select_index(root: Path, wanted: str | None) -> Path:
    if wanted:
        direct = Path(wanted).expanduser()
        if direct.is_file():
            return direct
        name = wanted if wanted.endswith(".json") else f"{wanted}.json"
        named = root / "assets" / "indexes" / name
        if named.is_file():
            return named
        raise SystemExit(f"asset index not found: {wanted}")

    candidates = list_indexes(root)
    for candidate in candidates:
        try:
            data = json.loads(candidate.read_text(encoding="utf-8"))
        except Exception:
            continue
        objects = data.get("objects", {})
        if all(key in objects for key in EXTERNAL_OBJECTS.values()):
            return candidate
    raise SystemExit("no asset index contains Unifont and alpha.ogg; use --index")


def object_path(root: Path, digest: str) -> Path:
    return root / "assets" / "objects" / digest[:2] / digest


def verify_and_copy(root: Path, data: dict, key: str, destination: Path) -> None:
    entry = data["objects"][key]
    digest = entry["hash"]
    source = object_path(root, digest)
    if not source.is_file():
        raise SystemExit(f"object missing: {source}\nrun the Minecraft launcher once to repair assets")
    actual = hashlib.sha1(source.read_bytes()).hexdigest()
    if actual.lower() != digest.lower():
        raise SystemExit(f"hash mismatch for {source}: expected {digest}, got {actual}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(source.read_bytes())
    print(f"  {destination} ({source.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("assets"),
                        help="destination assets directory (default: assets)")
    parser.add_argument("--minecraft-dir", type=Path,
                        help="explicit Minecraft directory containing assets/")
    parser.add_argument("--index", help="asset index name (e.g. 32) or path")
    parser.add_argument("--list", action="store_true",
                        help="list candidate Minecraft directories and indexes, then exit")
    args = parser.parse_args()

    if args.list:
        for root in candidate_roots():
            mark = "OK" if has_asset_store(root) else "-"
            print(f"[{mark}] {root}")
            if has_asset_store(root):
                for index in list_indexes(root):
                    print(f"       {index.name}")
        return

    root = find_minecraft_dir(args.minecraft_dir)
    index = select_index(root, args.index)
    data = json.loads(index.read_text(encoding="utf-8"))
    print(f"minecraft: {root}")
    print(f"index:     {index}")
    print(f"out:       {args.out.resolve()}")

    for destination, key in EXTERNAL_OBJECTS.items():
        if key not in data.get("objects", {}):
            raise SystemExit(f"{index.name}: missing object {key}")
        verify_and_copy(root, data, key, args.out / destination)

    unifont = args.out / "font" / "unifont.zip"
    try:
        with zipfile.ZipFile(unifont) as zf:
            for name in ("LICENSE.txt", "LICENSE"):
                if name in zf.namelist():
                    license_data = zf.read(name)
                    license_path = args.out / UNIFONT_LICENSE
                    license_path.parent.mkdir(parents=True, exist_ok=True)
                    license_path.write_bytes(license_data)
                    print(f"  {license_path} ({len(license_data)} bytes)")
                    break
    except zipfile.BadZipFile:
        print(f"warning: {unifont} is not a readable zip; license not extracted")

    print(f"done: {args.out.resolve()}")


if __name__ == "__main__":
    main()
