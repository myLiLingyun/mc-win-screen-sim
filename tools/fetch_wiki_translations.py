#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fetch the four Classical Chinese End Poem translations and write asset files.

The source pages are on 礦藝大典 (lzh.minecraft.wiki) and are licensed
CC BY-NC-SA 3.0. This tool only performs local conversion; it does not grant
any right to redistribute the translations.

Usage:
    python3 tools/fetch_wiki_translations.py
    python3 tools/fetch_wiki_translations.py --out /tmp/assets
    python3 tools/fetch_wiki_translations.py --english /path/end_poem_en.txt

Only Python's standard library and `curl` are required. The output files are
151 lines, aligned with the English end.txt; the player-name placeholder is
converted to PLAYERNAME, while the wiki's scrambled-text markers such as
[不可讀] are intentionally kept as literal text.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
from html.parser import HTMLParser
from pathlib import Path

BASE = "https://lzh.minecraft.wiki"
TRANSLATIONS = [
    ("shang", "上宮豐聰耳", "end_poem_lzh_shang.txt"),
    ("wang", "望海散人", "end_poem_lzh_wang.txt"),
    ("fang", "方矩憶靈", "end_poem_lzh_fang.txt"),
    ("ice", "IceWars1o1", "end_poem_lzh_ice.txt"),
]
PATH_PREFIX = "/w/終末之詩/文/譯文/"
OBFUSCATE = "\u00a7f\u00a7k\u00a7a\u00a7b"
NAME_PLACEHOLDERS = ("[名姓]", "[戲者之名]", "[戲者名]", "[Playername]")


class PoemSpanParser(HTMLParser):
    """Collect the direct poem spans and split each one at <br>."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.poem_depth = 0
        self.span_depth = 0
        self.current: list[list[str]] | None = None
        self.entries: list[list[str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = dict(attrs)
        if tag == "div":
            classes = (attrs_dict.get("class") or "").split()
            if self.poem_depth:
                self.poem_depth += 1
            elif "poem" in classes:
                self.poem_depth = 1
            return
        if not self.poem_depth:
            return
        if tag == "span":
            if self.span_depth == 0:
                self.current = [[]]
            self.span_depth += 1
        elif tag == "br" and self.current is not None:
            self.current.append([])

    def handle_endtag(self, tag: str) -> None:
        if tag == "div" and self.poem_depth:
            self.poem_depth -= 1
            if self.poem_depth == 0:
                self.current = None
                self.span_depth = 0
        elif tag == "span" and self.current is not None:
            self.span_depth -= 1
            if self.span_depth == 0:
                self.entries.append(["".join(part).strip() for part in self.current])
                self.current = None

    def handle_data(self, data: str) -> None:
        if self.current is not None and self.poem_depth:
            self.current[-1].append(data)


def fetch(url: str, destination: Path) -> None:
    if shutil.which("curl") is None:
        raise SystemExit("curl is required")
    result = subprocess.run(
        [
            "curl",
            "--fail",
            "--location",
            "--compressed",
            "--silent",
            "--show-error",
            "--user-agent",
            "Mozilla/5.0 (end-poem-asset-converter)",
            url,
            "--output",
            str(destination),
        ],
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"curl failed for {url}:\n{result.stderr.strip()}")


def clean_text(text: str) -> str:
    # The MediaWiki HTML indents block tags; do not keep that formatting.
    return re.sub(r"\s+", " ", text).strip()


def parse_translation(html_text: str, author: str) -> list[str]:
    parser = PoemSpanParser()
    parser.feed(html_text)
    entries = []
    for parts in parser.entries:
        # Each entry is "English<br/>translation" (occasionally a trailing br).
        nonempty = [clean_text(part) for part in parts if clean_text(part)]
        if len(nonempty) < 2:
            continue
        entries.append(nonempty[1])
    if len(entries) != 76:
        raise SystemExit(f"{author}: expected 76 translated lines, found {len(entries)}")
    return entries


def convert(english: Path, out_dir: Path) -> None:
    source = english.read_text(encoding="utf-8").splitlines()
    if len(source) != 151:
        raise SystemExit(f"{english}: expected 151 lines, found {len(source)}")
    source_indices = [i for i, line in enumerate(source) if line.strip()]
    if len(source_indices) != 76:
        raise SystemExit(f"{english}: expected 76 non-empty lines, found {len(source_indices)}")
    out_dir.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="end-poem-wiki-") as tmp:
        tmp_dir = Path(tmp)
        for key, author, filename in TRANSLATIONS:
            url = BASE + urllib.parse.quote(PATH_PREFIX + author, safe="/")
            page = tmp_dir / f"{key}.html"
            print(f"fetch {author}: {url}")
            fetch(url, page)
            chinese = parse_translation(page.read_text(encoding="utf-8", errors="replace"), author)

            output = [""] * 151
            for source_index, chinese_line in zip(source_indices, chinese):
                prefix = source[source_index][:2] if source[source_index].startswith("\u00a7") else ""
                text = chinese_line
                for placeholder in NAME_PLACEHOLDERS:
                    text = text.replace(placeholder, "PLAYERNAME")
                output[source_index] = prefix + text

            destination = out_dir / filename
            destination.write_text("\n".join(output) + "\n", encoding="utf-8")
            digest = hashlib.sha256(destination.read_bytes()).hexdigest()
            print(f"wrote {destination} ({destination.stat().st_size} bytes, sha256 {digest})")

    print("NOTE: the generated translations are CC BY-NC-SA 3.0; do not commit them")
    print("to this repository unless you are complying with that license.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--english", type=Path, default=Path("assets/end_poem_en.txt"),
                        help="English 151-line end.txt asset")
    parser.add_argument("--out", type=Path, default=Path("assets"),
                        help="output directory (default: assets)")
    args = parser.parse_args()
    convert(args.english, args.out)


if __name__ == "__main__":
    main()
