#!/usr/bin/env python3
"""
Extract hardcoded MissAV filter lists from MissAvSearchScreen.kt
(or from an existing tags.json) and split them into:

    assets/missav_options/tags.json    ← sort + filter
    assets/missav_options/genres.json  ← genre (with grouping)

Usage:
    python3 tools/extract_missav_options.py
"""

import re
import json
import pathlib
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
KT_FILE   = REPO_ROOT / "app/src/main/java/com/yenaly/han1meviewer/MissAV/MissAvSearchScreen.kt"
OLD_TAGS  = REPO_ROOT / "app/src/main/assets/missav_options/tags.json"
OUT_TAGS  = OLD_TAGS
OUT_GENRES = REPO_ROOT / "app/src/main/assets/missav_options/genres.json"

VARIABLE_MAP = {
    "sort":   "SORT_OPTIONS",
    "filter": "FILTER_OPTIONS",
    "genre":  "GENRE_OPTIONS",
}

# ── Genre grouping rules ──────────────────────────────────────────────────────
GENRE_GROUPS: list[tuple[str, list[str]]] = [
    ("Quality",        ["Hd", "4K", "Full Hd", "Ultra Slim Pixelated", "Slim Pixelated", "High Quality Vr"]),
    ("Relationship",   ["Wife", "Mother", "Sister", "Elder Sister", "Stepmother", "Young Wife",
                        "Incest", "Adultery", "Cuckold", "Couple", "Friend", "Dating", "In Love",
                        "Childhood", "Widow", "Married Woman"]),
    ("Occupation",     ["Female Teacher", "Private Teacher", "Nurse", "Female Doctor", "Doctor",
                        "Secretary", "Ol", "Female Boss", "Female Investigator", "Lecturer",
                        "Flight Attendant", "Waitress", "Maid", "Anchorwoman", "Model",
                        "Advertising Idol", "Idol", "Artist", "Entertainer", "Subordinate Or Colleague",
                        "Hotel Owner", "Ordinary Person", "Various Occupations", "Racing Girl"]),
    ("Location",       ["Hot Spring", "Outdoors", "Outdoor Exposure", "Hotel", "Car Sex",
                        "Bathtub", "Clinic", "Campus Story", "Travel", "Bubble Bath",
                        "School", "Public"]),
    ("Appearance",     ["Big Breasts", "Small Breasts", "Beautiful Breasts", "Super Breasts",
                        "Big Breast Fetish", "Breast Milk", "Nice Ass", "Big Ass", "Beautiful Legs",
                        "Slim", "Petite", "Tall Lady", "White Skin", "Black Hair", "Long Hair",
                        "Short Hair", "Shaving", "Muscle", "Baby Face", "Cute", "Hot Girl",
                        "Pretty Girl", "Beautiful"]),
    ("Sex Acts",       ["Creampie", "Oral Sex", "Blowjob", "Forced Blowjob", "Tit Job", "Footjob",
                        "Cunnilingus", "Anal", "Anal Sex", "Fingering", "Masturbate", "Masturbation",
                        "Squirting", "Orgy", "Bukkake", "Group Bukkake", "Swallow Sperm",
                        "Doggy Style", "Ride", "Face Ride", "69", "3P", "4P", "Kiss",
                        "Lesbian Kiss", "Lesbian"]),
    ("Fetish",         ["Fetish", "Foot Fetish", "Butt Fetish", "Underwear", "Pantyhose",
                        "Stocking", "Knee Socks", "Bubble Socks", "Swimsuit", "Sailor Suit",
                        "Gym Suit", "Bloomers", "One Piece Dress", "Kimono", "Yukata",
                        "Mini Skirt", "Short Skirt", "Cosplay", "Uniform", "Glasses Girl",
                        "Catwoman", "Bunny Girl", "Naked Apron", "Business Clothing",
                        "Sportswear", "School"]),
    ("Toys & Play",    ["Toy", "Vibrator", "Vibrating Egg", "Dildo", "Restraint", "Bondage",
                        "Tied Up", "Rope", "Fist", "Foreign Object Penetration", "Hysteroscope",
                        "Tentacle", "Enema", "Aphrodisiac", "Massage Oil", "Rejuvenation Massage",
                        "Esthetic Massage", "Massage"]),
    ("Extreme",        ["Sm", "Torture", "Rape", "Gang Rape", "Humiliation", "Insult",
                        "Shame And Humiliation", "Shame", "Cruel", "Fighting", "Violence",
                        "Gore", "Defecation", "Stool", "Drink Urine", "Urinate", "Urination",
                        "Scat", "Extreme Orgasm", "Mind Break", "Brainwashed", "Imprisonment",
                        "Blackmail", "Molester", "Train Molestation", "Molestation"]),
    ("Theme",          ["Plot", "Documentary", "Fantasy", "Science Fiction", "Sci-Fi",
                        "Super Power", "Supernatural", "Historical", "Delusion", "Multiple Stories",
                        "Best, Omnibus", "Collection", "Actress Collection", "Original", "Indie",
                        "Similar", "Time Stops", "Heaven Tv", "Limited Time", "Full"]),
    ("Demographic",    ["High School Girl", "Female College Student", "Married Woman",
                        "Mature Woman", "Missy", "Lolita", "Thirty", "Promiscuous", "Promiscuity",
                        "Slut", "Horny Slut", "Bitch", "Virgin", "Pregnant Woman", "Pregnant",
                        "Proud Pussy", "Nice"]),
    ("Censorship",     ["Uncensored Leak", "Uncensored", "Exclusive"]),
    ("Language",       ["English Subtitle"]),
]

FALLBACK_GROUP = "Other"


def assign_group(name: str) -> str:
    name_lower = name.lower()
    for group, keywords in GENRE_GROUPS:
        for kw in keywords:
            if kw.lower() in name_lower:
                return group
    return FALLBACK_GROUP


# ── Kotlin list parser ────────────────────────────────────────────────────────
def extract_list_from_kt(source: str, var_name: str) -> list[tuple[str | None, str]]:
    pattern = re.compile(
        rf"private\s+val\s+{re.escape(var_name)}\s*=\s*listOf\((.*?)\)\s*(?:\n|$)",
        re.DOTALL,
    )
    match = pattern.search(source)
    if not match:
        raise RuntimeError(f"❌ Could not find `{var_name}` in {KT_FILE.name}")
    body = match.group(1)
    pair_re = re.compile(r'(null|"[^"]*")\s*to\s*"([^"]*)"')
    pairs = []
    for m in pair_re.finditer(body):
        raw_key, label = m.group(1), m.group(2)
        key = None if raw_key == "null" else raw_key.strip('"')
        pairs.append((key, label))
    return pairs


def to_json_entries(pairs, with_group: bool = False):
    out = []
    for key, label in pairs:
        if key is None or key.strip() == "":
            continue
        entry = {"search_key": key, "name": label}
        if with_group:
            entry["group"] = assign_group(label)
        out.append(entry)
    return out


# ── Source selection: prefer Kotlin file, fall back to existing tags.json ─────
def load_source() -> dict[str, list[dict]]:
    """
    Returns {"sort": [...], "filter": [...], "genre": [...]}.
    First tries MissAvSearchScreen.kt; if that fails (e.g. lists already removed),
    falls back to reading the existing tags.json.
    """
    if KT_FILE.exists():
        source = KT_FILE.read_text(encoding="utf-8")
        try:
            result = {}
            for json_key, kotlin_var in VARIABLE_MAP.items():
                pairs = extract_list_from_kt(source, kotlin_var)
                result[json_key] = to_json_entries(pairs, with_group=False)
            print("📥 Extracted from Kotlin source")
            return result
        except RuntimeError as e:
            print(f"⚠️  {e}")
            print("↪ Falling back to existing tags.json")

    if not OLD_TAGS.exists():
        sys.exit(f"❌ Neither {KT_FILE.name} nor {OLD_TAGS.name} is usable")

    data = json.loads(OLD_TAGS.read_text(encoding="utf-8"))
    print(f"📥 Loaded from {OLD_TAGS}")
    return {
        "sort":   data.get("sort", []),
        "filter": data.get("filter", []),
        "genre":  data.get("genre", []),
    }


def main():
    data = load_source()

    # ── tags.json = sort + filter only ───────────────────────────────────
    tags_payload = {
        "sort":   data.get("sort", []),
        "filter": data.get("filter", []),
    }
    OUT_TAGS.parent.mkdir(parents=True, exist_ok=True)
    OUT_TAGS.write_text(
        json.dumps(tags_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # ── genres.json = genre with groups ──────────────────────────────────
    genre_entries = []
    for entry in data.get("genre", []):
        new_entry = {
            "search_key": entry["search_key"],
            "name": entry["name"],
        }
        # If the old tags.json already had a group, keep it; otherwise assign
        new_entry["group"] = entry.get("group") or assign_group(entry["name"])
        genre_entries.append(new_entry)

    OUT_GENRES.write_text(
        json.dumps(genre_entries, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # ── Report ───────────────────────────────────────────────────────────
    print(f"\n✅ Wrote {OUT_TAGS}")
    print(f"   • sort:   {len(tags_payload['sort'])} entries")
    print(f"   • filter: {len(tags_payload['filter'])} entries")

    print(f"\n✅ Wrote {OUT_GENRES}")
    print(f"   • genre: {len(genre_entries)} entries")

    groups: dict[str, int] = {}
    for e in genre_entries:
        groups[e["group"]] = groups.get(e["group"], 0) + 1
    for g in sorted(groups):
        print(f"      - {g}: {groups[g]}")


if __name__ == "__main__":
    main()