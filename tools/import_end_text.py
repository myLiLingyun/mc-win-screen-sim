#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Import a Bedrock/APK credits/end.txt as an assets text file.

The tool deliberately does not decide whether the source is English, Chinese
or another language. Pass --output to choose the destination name. The source
line endings are preserved; only a BOM/encoding conversion may change bytes.

Examples:
    # Windows Bedrock
    python3 tools/import_end_text.py \
      "C:\\FeverApps\\MCBedrock\\data\\credits\\end.txt" \
      --output end_poem_zh.txt

    # Android APK (the entry you supplied)
    python3 tools/import_end_text.py Minecraft.apk \
      --entry assets/assets/credits/end.txt \
      --output end_poem_zh.txt

The source text remains subject to Mojang/Microsoft's rights. This tool is for
local asset preparation only; do not commit the imported text.
"""

from __future__ import annotations

import argparse
import zipfile
from pathlib import Path

DEFAULT_ANDROID_ENTRY = "assets/assets/credits/end.txt"


def read_input(path: Path, entry: str) -> tuple[bytes, str]:
    if not path.is_file():
        raise SystemExit(f"input not found: {path}")
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as zf:
            names = zf.namelist()
            if entry not in names:
                candidates = [n for n in names if n.endswith("credits/end.txt")]
                hint = "\n  ".join(candidates) if candidates else "(none found)"
                raise SystemExit(
                    f"{path}: entry not found: {entry}\ncredits/end.txt candidates:\n  {hint}"
                )
            return zf.read(entry), entry
    return path.read_bytes(), str(path)


def decode(data: bytes, requested: str | None) -> tuple[str, str]:
    encodings = [requested] if requested else ["utf-8-sig", "utf-16", "utf-8", "gb18030"]
    for encoding in encodings:
        if encoding is None:
            continue
        try:
            return data.decode(encoding), encoding
        except UnicodeDecodeError:
            continue
    raise SystemExit("cannot decode text as UTF-8/UTF-16/GB18030; use --encoding")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Bedrock end.txt or an APK/zip")
    parser.add_argument("--output", required=True,
                        help="destination filename under --out, e.g. end_poem_zh.txt")
    parser.add_argument("--out", type=Path, default=Path("assets"),
                        help="destination directory (default: assets)")
    parser.add_argument("--entry", default=DEFAULT_ANDROID_ENTRY,
                        help=f"APK entry (default: {DEFAULT_ANDROID_ENTRY})")
    parser.add_argument("--encoding", help="force source encoding")
    args = parser.parse_args()

    if not args.output.endswith(".txt") or "/" in args.output or "\\" in args.output:
        raise SystemExit("--output must be a plain .txt filename")

    data, source = read_input(args.input, args.entry)
    text, encoding = decode(data, args.encoding)
    lines = text.splitlines()
    nonempty = [line for line in lines if line.strip()]
    if not nonempty:
        raise SystemExit(f"{source}: no non-empty lines")

    destination = args.out / args.output
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(text.encode("utf-8"))

    print(f"source: {args.input} ({source})")
    print(f"decoded as: {encoding}")
    print(f"lines: {len(lines)} ({len(nonempty)} non-empty)")
    print(f"first: {nonempty[0][:120]!r}")
    print(f"last:  {nonempty[-1][:120]!r}")
    print(f"wrote: {destination.resolve()}")
    print("warning: Bedrock/Mojang text is not covered by this repository's licenses;")
    print("         keep this file local and do not commit it.")


if __name__ == "__main__":
    main()
