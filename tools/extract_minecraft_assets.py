#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Extract the assets needed by this player from a Minecraft 26.2 jar.

The jar supplies the English text, fonts, shaders and textures. Modern
Minecraft installations keep Unifont and the credits music in the external
asset-object store; when --minecraft-dir is available, the matching index is
used to copy those files too.

Usage:
    python3 tools/extract_minecraft_assets.py 26.2-Fabric.jar
    python3 tools/extract_minecraft_assets.py 26.2-Fabric.jar --out assets
    python3 tools/extract_minecraft_assets.py 26.2-Fabric.jar \\
        --minecraft-dir ~/.minecraft --index 32

This tool extracts only the files the player uses. The optional --zh-jar
copies a NetEase Chinese text entry from another jar; the NetEase text remains
subject to NetEase's rights and is for local use only. Use
fetch_wiki_translations.py for the four CC BY-NC-SA Classical Chinese texts.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

# destination inside assets/ -> path inside the jar
JAR_FILES = {
    "end_poem_en.txt": "assets/minecraft/texts/end.txt",
    "font/default.json": "assets/minecraft/font/default.json",
    "font/include/default.json": "assets/minecraft/font/include/default.json",
    "font/include/space.json": "assets/minecraft/font/include/space.json",
    "font/include/unifont.json": "assets/minecraft/font/include/unifont.json",
    "font/ascii.png": "assets/minecraft/textures/font/ascii.png",
    "font/accented.png": "assets/minecraft/textures/font/accented.png",
    "font/nonlatin_european.png": "assets/minecraft/textures/font/nonlatin_european.png",
    "shaders/core/text.vsh": "assets/minecraft/shaders/core/text.vsh",
    "shaders/core/text.fsh": "assets/minecraft/shaders/core/text.fsh",
    "shaders/core/position_tex_color.vsh": "assets/minecraft/shaders/core/position_tex_color.vsh",
    "shaders/core/position_tex_color.fsh": "assets/minecraft/shaders/core/position_tex_color.fsh",
    "shaders/core/rendertype_end_portal.vsh": "assets/minecraft/shaders/core/rendertype_end_portal.vsh",
    "shaders/core/rendertype_end_portal.fsh": "assets/minecraft/shaders/core/rendertype_end_portal.fsh",
    "textures/end_sky.png": "assets/minecraft/textures/environment/end_sky.png",
    "textures/end_portal.png": "assets/minecraft/textures/entity/end_portal/end_portal.png",
    "textures/credits_vignette.png": "assets/minecraft/textures/misc/credits_vignette.png",
    "textures/minecraft.png": "assets/minecraft/textures/gui/title/minecraft.png",
    "textures/edition.png": "assets/minecraft/textures/gui/title/edition.png",
    "textures/pack.png": "pack.png",
}
SHADER_INCLUDE_PREFIX = "assets/minecraft/shaders/include/"
SHADER_INCLUDE_DEST = "shaders/include"
EXTERNAL_OBJECTS = {
    "font/include/unifont.json": "minecraft/font/include/unifont.json",
    "font/unifont.zip": "minecraft/font/unifont.zip",
    "sounds/alpha.ogg": "minecraft/sounds/music/game/end/alpha.ogg",
}
OPTIONAL_EXTERNAL = {"font/include/unifont.json", "font/unifont.zip", "sounds/alpha.ogg"}
UNIFONT_LICENSE = "font/UNIFONT-LICENSE.txt"


def write_bytes(destination: Path, data: bytes) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    print(f"  {destination} ({len(data)} bytes)")


def extract_jar(jar: Path, out: Path) -> set[str]:
    missing: set[str] = set()
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for destination, source in JAR_FILES.items():
            if source not in names:
                missing.add(destination)
                continue
            write_bytes(out / destination, zf.read(source))

        for source in sorted(names):
            if source.startswith(SHADER_INCLUDE_PREFIX) and source.endswith(".glsl"):
                relative = source[len(SHADER_INCLUDE_PREFIX):]
                write_bytes(out / SHADER_INCLUDE_DEST / relative, zf.read(source))
    return missing


def locate_index(minecraft_dir: Path, wanted: str | None) -> Path | None:
    if wanted:
        candidate = Path(wanted)
        if candidate.is_file():
            return candidate
        candidate = minecraft_dir / "assets" / "indexes" / f"{wanted}.json"
        if candidate.is_file():
            return candidate
        raise SystemExit(f"asset index not found: {wanted}")

    indexes = minecraft_dir / "assets" / "indexes"
    if not indexes.is_dir():
        return None
    candidates = sorted(indexes.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    for candidate in candidates:
        try:
            data = json.loads(candidate.read_text(encoding="utf-8"))
        except Exception:
            continue
        objects = data.get("objects", {})
        if all(key in objects for key in EXTERNAL_OBJECTS.values()):
            return candidate
    return candidates[0] if candidates else None


def extract_external(minecraft_dir: Path, index: Path, out: Path) -> set[str]:
    data = json.loads(index.read_text(encoding="utf-8"))
    objects = data.get("objects", {})
    missing: set[str] = set()
    print(f"external asset index: {index}")
    for destination, key in EXTERNAL_OBJECTS.items():
        entry = objects.get(key)
        if not entry:
            missing.add(destination)
            continue
        source = minecraft_dir / "assets" / "objects" / entry["hash"][:2] / entry["hash"]
        if not source.is_file():
            missing.add(destination)
            continue
        # Prefer the external unifont.json over the intentionally empty jar copy.
        write_bytes(out / destination, source.read_bytes())

    unifont = out / "font" / "unifont.zip"
    if unifont.is_file():
        try:
            with zipfile.ZipFile(unifont) as zf:
                for name in ("LICENSE.txt", "LICENSE"):
                    if name in zf.namelist():
                        write_bytes(out / UNIFONT_LICENSE, zf.read(name))
                        break
        except zipfile.BadZipFile:
            print("  warning: unifont.zip is not a readable zip")

    return missing


def extract_chinese(jar: Path, entry: str, out: Path) -> None:
    if not jar.is_file() or not zipfile.is_zipfile(jar):
        raise SystemExit(f"not a readable jar: {jar}")
    with zipfile.ZipFile(jar) as zf:
        if entry not in zf.namelist():
            raise SystemExit(f"{jar}: entry not found: {entry}")
        data = zf.read(entry)
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        try:
            text = data.decode("gb18030")
            print("note: NetEase text was GB18030; converting to UTF-8")
        except UnicodeDecodeError as exc:
            raise SystemExit(f"{jar}: Chinese text is neither UTF-8 nor GB18030: {exc}")
    if len(text.splitlines()) != 151:
        print(f"warning: Chinese text has {len(text.splitlines())} lines, expected 151")
    destination = out / "end_poem_zh.txt"
    write_bytes(destination, text.encode("utf-8"))
    print("warning: the optional NetEase Chinese text is not covered by this")
    print("repository's licenses; keep it for local use only and do not commit it.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Minecraft client jar")
    parser.add_argument("--out", type=Path, default=Path("assets"),
                        help="destination asset directory (default: assets)")
    parser.add_argument("--minecraft-dir", type=Path, default=Path.home() / ".minecraft",
                        help="Minecraft directory for external objects (default: ~/.minecraft)")
    parser.add_argument("--index", help="asset index name or path (default: auto)")
    parser.add_argument("--no-external", action="store_true",
                        help="do not look for Unifont/music in the asset-object store")
    parser.add_argument("--zh-jar", type=Path,
                        help="optional NetEase client jar containing a Chinese end.txt")
    parser.add_argument("--zh-entry", default="assets/minecraft/texts/end.txt",
                        help="Chinese text entry inside --zh-jar")
    args = parser.parse_args()

    if not args.jar.is_file():
        raise SystemExit(f"jar not found: {args.jar}")
    if not zipfile.is_zipfile(args.jar):
        raise SystemExit(f"not a zip/jar: {args.jar}")

    print(f"jar: {args.jar}")
    print(f"out: {args.out.resolve()}")
    missing = extract_jar(args.jar, args.out)

    if args.zh_jar:
        extract_chinese(args.zh_jar, args.zh_entry, args.out)

    if not args.no_external:
        index = locate_index(args.minecraft_dir, args.index)
        if index is None:
            print(f"warning: no asset index under {args.minecraft_dir / 'assets' / 'indexes'}")
        else:
            missing |= extract_external(args.minecraft_dir, index, args.out)

    required_missing = sorted(m for m in missing if m not in OPTIONAL_EXTERNAL)
    optional_missing = sorted(m for m in missing if m in OPTIONAL_EXTERNAL)
    if optional_missing:
        print("optional external assets unavailable (English still works):")
        for name in optional_missing:
            print(f"  {name}")
    if required_missing:
        print("missing required jar entries:")
        for name in required_missing:
            print(f"  {name}")
        raise SystemExit(1)

    print(f"done: {args.out.resolve()}")


if __name__ == "__main__":
    main()
