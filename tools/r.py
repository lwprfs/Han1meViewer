#!/usr/bin/env python3
"""
Extract hardcoded MissAV search filter lists from MissAvSearchScreen.kt
into a single assets/missav_options/tags.json file.

Output schema:
{
    "sort":    [{"search_key": "...", "name": "..."}, ...],
    "filter":  [{"search_key": "...", "name": "..."}, ...],
    "genre":   [{"search_key": "...", "name": "..."}, ...]
}

Usage:
    python3 tools/extract_missav_tags.py
"""

import re
import json
import pathlib
import sys

# ── Paths ─────────────────────────────────────────────────────────────────────
REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
KT_FILE   = REPO_ROOT / "app/src/main/java/com/yenaly/han1meviewer/MissAV/MissAvSearchScreen.kt"
OUT_FILE  = REPO_ROOT / "app/src/main/assets/missav_options/tags.json"

# ── Which Kotlin variable maps to which JSON key ──────────────────────────────
VARIABLE_MAP = {
    "sort":   "SORT_OPTIONS",
    "filter": "FILTER_OPTIONS",
    "genre":  "GENRE_OPTIONS",
}


def extract_list_from_kt(source: str, var_name: str) -> list[tuple[str | None, str]]:
    """
    Parses Kotlin lists like:
        private val SORT_OPTIONS = listOf(
            null to "Default",
            "released_at" to "Release Date",
            ...
        )
    Returns [(key_or_None, label), ...]
    """
    pattern = re.compile(
        rf"private\s+val\s+{re.escape(var_name)}\s*=\s*listOf\((.*?)\)\s*(?:\n|$)",
        re.DOTALL,
    )
    match = pattern.search(source)
    if not match:
        raise RuntimeError(f"❌ Could not find `{var_name}` in {KT_FILE.name}")

    body = match.group(1)
    pair_re = re.compile(r'(null|"[^"]*")\s*to\s*"([^"]*)"')

    pairs: list[tuple[str | None, str]] = []
    for m in pair_re.finditer(body):
        raw_key, label = m.group(1), m.group(2)
        key = None if raw_key == "null" else raw_key.strip('"')
        pairs.append((key, label))
    return pairs


def to_json_entries(pairs: list[tuple[str | None, str]]) -> list[dict]:
    """Drop null-key entries (like the 'Default' / 'All' chip) — UI handles those."""
    return [
        {"search_key": key, "name": label}
        for key, label in pairs
        if key is not None and key.strip() != ""
    ]


def main():
    if not KT_FILE.exists():
        sys.exit(f"❌ Source file not found: {KT_FILE}")

    source = KT_FILE.read_text(encoding="utf-8")
    output: dict[str, list[dict]] = {}

    for json_key, kotlin_var in VARIABLE_MAP.items():
        pairs = extract_list_from_kt(source, kotlin_var)
        output[json_key] = to_json_entries(pairs)
        print(f"  • {json_key:8s} ← {kotlin_var:16s} ({len(output[json_key])} entries)")

    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"\n✅ Wrote {OUT_FILE}")
    print(f"   Total entries: {sum(len(v) for v in output.values())}")


if __name__ == "__main__":
    main()