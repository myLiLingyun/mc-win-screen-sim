#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Extract the assets needed by this player from a Minecraft 26.2 jar.

The jar supplies the English text, fonts, shaders and textures. It does not
contain Unifont zip or the credits music; use find_minecraft_assets.py for
those external objects.

Usage:
    python3 tools/extract_minecraft_assets.py 26.2-Fabric.jar
    python3 tools/extract_minecraft_assets.py 26.2-Fabric.jar --out assets

This tool does not make any NetEase/Bedrock path assumption. Use
import_end_text.py for a Bedrock/APK credits/end.txt, and
fetch_wiki_translations.py for the four CC BY-NC-SA Classical Chinese texts.
"""

from __future__ import annotations

import argparse
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
            target = out / destination
            if destination == "font/include/unifont.json" and target.is_file() and target.stat().st_size > 0:
                # The jar's copy is an empty provider list; keep a real Unifont
                # index written by find_minecraft_assets.py even if the tools
                # are run in the other order.
                print(f"  keep existing {target}")
                continue
            write_bytes(target, zf.read(source))

        for source in sorted(names):
            if source.startswith(SHADER_INCLUDE_PREFIX) and source.endswith(".glsl"):
                relative = source[len(SHADER_INCLUDE_PREFIX):]
                write_bytes(out / SHADER_INCLUDE_DEST / relative, zf.read(source))
    return missing


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Minecraft client jar")
    parser.add_argument("--out", type=Path, default=Path("assets"),
                        help="destination asset directory (default: assets)")
    args = parser.parse_args()

    if not args.jar.is_file():
        raise SystemExit(f"jar not found: {args.jar}")
    if not zipfile.is_zipfile(args.jar):
        raise SystemExit(f"not a zip/jar: {args.jar}")

    print(f"jar: {args.jar}")
    print(f"out: {args.out.resolve()}")
    missing = extract_jar(args.jar, args.out)
    if missing:
        print("missing required jar entries:")
        for name in sorted(missing):
            print(f"  {name}")
        raise SystemExit(1)

    print(f"done: {args.out.resolve()}")


if __name__ == "__main__":
    main()
