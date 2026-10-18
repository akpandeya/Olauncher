#!/usr/bin/env python3
"""Build German vocabulary list from Wiktionary (kaikki.org) and open frequency data.

Downloads:
- kaikki.org German extract of English Wiktionary (JSONL)
- Hermit Dave FrequencyWords German list (CC BY-SA 4.0, from OpenSubtitles 2018)

Outputs:
- app/src/main/assets/words_de.tsv (header: id\\tde\\tplural\\ten\\texample)
- app/src/main/assets/WORDS_ATTRIBUTION.txt

Stdlib only (Python 3).
"""

import argparse
import gzip
import json
import os
import re
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

KAIKKI_URL = "https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl.gz"
FREQ_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_50k.txt"

EXCLUDED_POS = {
    "article", "pron", "conj", "det", "contraction", "name",
    "character", "symbol", "punct", "suffix", "prefix",
    "interfix", "circumfix", "infix", "intj", "particle"
}

PRIMARY_PREPOSITIONS = {
    "ab", "an", "auf", "aus", "außer", "bei", "bis", "durch",
    "für", "gegen", "hinter", "in", "mit", "nach", "neben",
    "ohne", "seit", "über", "um", "unter", "von", "vor", "zu", "zwischen"
}

EXCLUDED_WORDS = {
    # Personal & object pronouns
    "ich", "du", "er", "sie", "es", "wir", "ihr",
    "mich", "dich", "ihn", "ihm", "ihr", "uns", "euch", "ihnen", "mir", "dir",
    # Possessive pronouns
    "mein", "meine", "meiner", "meines", "meinem", "meinen",
    "dein", "deine", "deiner", "deines", "deinem", "deinen",
    "unser", "unsere", "unserer", "unseres", "unserem", "unseren",
    "euer", "eure", "eurer", "eures", "eurem", "euren",
    # Articles & determiners
    "der", "die", "das", "des", "dem", "den",
    "ein", "eine", "einer", "eines", "einem", "einen",
    "kein", "keine", "keiner", "keines", "keinem", "keinen",
    "alle", "aller", "alles", "allen", "allem",
    "jeder", "jede", "jedes", "jedem", "jeden",
    "beide", "beider", "beides", "beidem", "beiden",
    "mancher", "manche", "manches", "solcher", "solche", "solches",
    "einige", "einiger", "einiges", "einigen", "einigem",
    # Conjunctions & relative/demonstrative/interrogative pronouns
    "und", "oder", "aber", "denn", "sondern", "dass", "daß", "weil", "wenn", "ob",
    "wie", "als", "man", "jemand", "niemand", "etwas", "nichts",
    "wer", "was", "wo", "wann", "warum", "wieso", "weshalb",
    "welcher", "welche", "welches", "welchen", "welchem", "welcher",
    "dieser", "diese", "dieses", "diesen", "diesem", "dies",
    # Particles & discourse markers
    "ja", "nein", "doch", "mal", "halt", "eben", "nun", "schon", "bloß",
    "so", "da", "hier", "dort", "dann", "auch", "noch", "nur", "sehr", "immer", "wieder",
    # Interjections
    "hallo", "ach", "hey", "hi", "na", "au", "oh", "aha", "oje", "tschüss", "tschüs"
}

ATTRIBUTION_TEXT = """Thumbline German Word List Attribution

The German word list (`words_de.tsv`) is compiled from high-quality open data sources and is released as a derived work under the Creative Commons Attribution-ShareAlike 4.0 International License (CC BY-SA 4.0):
https://creativecommons.org/licenses/by-sa/4.0/

Sources & Credits:

1. English Wiktionary (German Entries)
   - Authors: Wiktionary contributors
   - URL: https://en.wiktionary.org/
   - License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)
     https://creativecommons.org/licenses/by-sa/4.0/

2. Kaikki.org / Wiktextract
   - Author: Tatu Ylonen
   - URL: https://kaikki.org/dictionary/German/
   - Project: https://github.com/tatuylonen/wiktextract
   - Description: Machine-readable JSONL dictionary extracts of Wiktionary.
   - License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)

3. Hermit Dave FrequencyWords (German Corpus)
   - Author: Hermit Dave
   - URL: https://github.com/hermitdave/FrequencyWords
   - Derived from: OpenSubtitles 2018 corpus (http://opus.nlpl.eu/OpenSubtitles2018.php)
   - License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)
     https://creativecommons.org/licenses/by-sa/4.0/

Derived Work Notice:
This compiled dataset (`words_de.tsv`) is a derived work produced by filtering, ranking, and synthesizing entries from the sources above. In accordance with the ShareAlike provisions of CC BY-SA 4.0, `words_de.tsv` is distributed under the Creative Commons Attribution-ShareAlike 4.0 International License (CC BY-SA 4.0).
"""


def download_file(url: str, dest_path: Path):
    """Download a file from url to dest_path if it doesn't already exist."""
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    if dest_path.exists() and dest_path.stat().st_size > 0:
        print(f"Cached file exists: {dest_path}")
        return

    tmp_path = dest_path.with_suffix(dest_path.suffix + ".tmp")
    print(f"Downloading {url} -> {dest_path}...")
    headers = {"User-Agent": "OlauncherWordBuilder/1.0"}
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp, open(tmp_path, "wb") as f_out:
        total = int(resp.headers.get("Content-Length", 0))
        downloaded = 0
        chunk_size = 1024 * 1024  # 1MB
        while True:
            chunk = resp.read(chunk_size)
            if not chunk:
                break
            f_out.write(chunk)
            downloaded += len(chunk)
            if total > 0:
                percent = downloaded * 100 // total
                print(f"\r  {downloaded // (1024 * 1024)}MB / {total // (1024 * 1024)}MB ({percent}%)", end="", flush=True)
            else:
                print(f"\r  {downloaded // (1024 * 1024)}MB downloaded", end="", flush=True)
        print()
    tmp_path.rename(dest_path)
    print(f"Saved: {dest_path}")


def load_frequency_list(freq_file: Path) -> dict:
    """Load word -> frequency count mapping from frequency text file."""
    print(f"Loading frequency list from {freq_file}...")
    freq = {}
    with open(freq_file, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                word = parts[0].lower()
                try:
                    count = int(parts[1])
                    freq[word] = count
                except ValueError:
                    continue
    print(f"Loaded {len(freq)} frequency entries.")
    return freq


def clean_gloss(g: str) -> str:
    """Clean gloss string by removing parentheticals, brackets, and extra spaces."""
    g = re.sub(r"\[[^\]]*\]", "", g)
    g = re.sub(r"\([^)]*\)", "", g)
    g = g.replace("(", "").replace(")", "").replace("[", "").replace("]", "")
    g = re.sub(r"\s+", " ", g).strip(" ,;:")
    return g


def read_existing_tsv_ids(tsv_path: Path) -> dict:
    """Read existing lemma (de) -> id mapping from committed TSV to ensure stable IDs."""
    existing_ids = {}
    if not tsv_path.exists():
        return existing_ids

    print(f"Reading existing IDs from {tsv_path}...")
    with open(tsv_path, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            if idx == 0:
                continue  # skip header
            parts = line.rstrip("\r\n").split("\t")
            if len(parts) >= 2:
                try:
                    row_id = int(parts[0])
                    de = parts[1]
                    existing_ids[de] = row_id
                except ValueError:
                    continue
    print(f"Found {len(existing_ids)} existing IDs (max ID: {max(existing_ids.values(), default=0)}).")
    return existing_ids


def build_words(cache_dir: Path, output_tsv: Path, target_count: int = 1500):
    """Main build process."""
    cache_dir.mkdir(parents=True, exist_ok=True)
    kaikki_path = cache_dir / "kaikki.org-dictionary-German.jsonl.gz"
    kaikki_uncompressed = cache_dir / "kaikki.org-dictionary-German.jsonl"
    freq_path = cache_dir / "de_50k.txt"

    # Step 1: Download sources
    download_file(FREQ_URL, freq_path)
    if not kaikki_uncompressed.exists():
        download_file(KAIKKI_URL, kaikki_path)

    freq_map = load_frequency_list(freq_path)
    existing_ids = read_existing_tsv_ids(output_tsv)

    # Step 2: Parse Kaikki German JSONL
    source_file = kaikki_uncompressed if kaikki_uncompressed.exists() else kaikki_path
    print(f"Scanning Wiktionary extract from {source_file}...")

    open_func = gzip.open if str(source_file).endswith(".gz") else open

    verb_forms = set()
    lowercase_lemmas = set()
    interjection_lemmas = set()
    entries_by_word = {}

    with open_func(source_file, "rt", encoding="utf-8") as f:
        for line_num, line in enumerate(f):
            if line_num > 0 and line_num % 100000 == 0:
                print(f"  Processed {line_num} lines...")
            try:
                data = json.loads(line)
            except json.JSONDecodeError:
                continue

            w = data.get("word", "").strip()
            pos = data.get("pos", "")
            senses = data.get("senses", [])

            is_form_of = any(
                "form-of" in s.get("tags", []) or "alt-of" in s.get("tags", [])
                for s in senses
            )

            if is_form_of:
                if pos in ("verb", "adj", "pron", "article", "det"):
                    verb_forms.add(w.lower())
                continue

            if pos in ("intj", "particle"):
                interjection_lemmas.add(w.lower())
                continue

            if pos == "prep" and w.lower() not in PRIMARY_PREPOSITIONS:
                continue

            if w and w[0].islower() and pos in ("verb", "adj", "adv", "prep", "num"):
                lowercase_lemmas.add(w.lower())

            # Filter valid senses
            valid_senses = []
            for s in senses:
                stags = s.get("tags", []) or []
                if any(t in stags for t in (
                    "form-of", "alt-of", "archaic", "obsolete", "dated",
                    "historical", "dialect", "dialectal", "abbreviation",
                    "proper-noun", "gerund"
                )):
                    continue
                glosses = s.get("glosses", [])
                if not glosses:
                    continue
                if any(g.lower().startswith((
                    "abbreviation of", "acronym of", "initialism of",
                    "short for", "gerund of"
                )) for g in glosses):
                    continue
                valid_senses.append(s)

            if valid_senses and pos in ("noun", "verb", "adj", "adv", "prep", "num"):
                # Prefer nouns over non-nouns for capitalized words, and prefer entries with more senses
                if w not in entries_by_word:
                    entries_by_word[w] = (data, valid_senses)
                else:
                    existing_data = entries_by_word[w][0]
                    if w[0].isupper() and pos == "noun" and existing_data.get("pos") != "noun":
                        entries_by_word[w] = (data, valid_senses)
                    elif len(valid_senses) > len(entries_by_word[w][1]):
                        entries_by_word[w] = (data, valid_senses)

    print(f"Extracted {len(entries_by_word)} candidate lemmas.")

    # Step 3: Process and filter candidates
    print("Filtering candidates and extracting metadata...")
    candidates = []

    for w, (data, valid_senses) in entries_by_word.items():
        pos = data.get("pos")
        w_lower = w.lower()
        if (
            len(w) <= 1
            or w_lower in EXCLUDED_WORDS
            or w_lower in interjection_lemmas
            or pos in EXCLUDED_POS
        ):
            continue

        gender = None
        plural = ""

        if pos != "noun":
            # Non-nouns in German must be lowercase
            if not w[0].islower():
                continue
        else:
            # Nouns in German must be capitalized
            if not w[0].isupper():
                continue
            # Nominalized verbs/adjectives where the lowercase lemma is primary
            if w_lower in lowercase_lemmas and w not in ("Morgen", "Essen", "Leben", "Zeit"):
                continue

            tags = (data.get("tags", []) or []) + [t for s in valid_senses for t in s.get("tags", [])]
            for t in tags:
                if t == "masculine":
                    gender = "der"
                    break
                elif t == "feminine":
                    gender = "die"
                    break
                elif t == "neuter":
                    gender = "das"
                    break
            if not gender:
                for h in data.get("head_templates", []):
                    args = h.get("args", {})
                    for a in args.values():
                        if a.startswith("m"):
                            gender = "der"
                            break
                        elif a.startswith("f"):
                            gender = "die"
                            break
                        elif a.startswith("n"):
                            gender = "das"
                            break
                    if gender:
                        break

            if not gender:
                continue

            # Extract plural form
            for f in data.get("forms", []) or []:
                ftags = f.get("tags", []) or []
                if "plural" in ftags and not any(bad in ftags for bad in ("obsolete", "archaic", "rare", "dative", "genitive")):
                    f_form = f.get("form", "").strip()
                    if f_form and f_form != "-":
                        plural = f_form
                        break

        # Extract up to 2 short English glosses
        gloss_list = []
        sorted_senses = sorted(valid_senses, key=lambda s: 1 if "auxiliary" in s.get("tags", []) else 0)
        for s in sorted_senses:
            for g in s.get("glosses", []):
                cg = clean_gloss(g)
                if cg and cg not in gloss_list and len(cg) <= 50:
                    gloss_list.append(cg)
                    if len(gloss_list) == 2:
                        break
            if len(gloss_list) == 2:
                break

        if not gloss_list:
            continue
        en_gloss = "; ".join(gloss_list)

        # Extract usage example (< 90 chars, not a quotation or dated reference)
        example = ""
        for s in sorted_senses:
            for ex in s.get("examples", []):
                if ex.get("type") == "quotation" or ex.get("ref"):
                    continue
                txt = ex.get("text", "").replace("\n", " ").replace("\t", " ").strip()
                if 0 < len(txt) < 90:
                    example = txt
                    break
            if example:
                break

        frequency = freq_map.get(w_lower, 0)
        if frequency > 0:
            de_str = f"{gender} {w}" if gender else w
            candidates.append({
                "raw_word": w,
                "de": de_str,
                "plural": plural,
                "en": en_gloss,
                "example": example,
                "frequency": frequency,
                "pos": pos
            })

    # Sort candidates by frequency descending
    candidates.sort(key=lambda x: -x["frequency"])
    selected = candidates[:target_count]
    print(f"Selected {len(selected)} lemmas for output.")

    # Step 4: Assign stable IDs
    max_existing_id = max(existing_ids.values(), default=0)
    next_id = max_existing_id + 1

    final_rows = []
    for item in selected:
        de = item["de"]
        if de in existing_ids:
            item_id = existing_ids[de]
        else:
            item_id = next_id
            existing_ids[de] = item_id
            next_id += 1

        final_rows.append({
            "id": item_id,
            "de": item["de"],
            "plural": item["plural"],
            "en": item["en"],
            "example": item["example"]
        })

    # Sort by ID ascending
    final_rows.sort(key=lambda x: x["id"])

    # Step 5: Write TSV
    output_tsv.parent.mkdir(parents=True, exist_ok=True)
    print(f"Writing {len(final_rows)} rows to {output_tsv}...")
    with open(output_tsv, "w", encoding="utf-8", newline="\n") as f:
        f.write("id\tde\tplural\ten\texample\n")
        for row in final_rows:
            f.write(f"{row['id']}\t{row['de']}\t{row['plural']}\t{row['en']}\t{row['example']}\n")

    # Step 6: Write attribution file in same assets directory
    attribution_file = output_tsv.parent / "WORDS_ATTRIBUTION.txt"
    print(f"Writing attribution to {attribution_file}...")
    with open(attribution_file, "w", encoding="utf-8", newline="\n") as f:
        f.write(ATTRIBUTION_TEXT)

    print("Build completed successfully!")


def main():
    parser = argparse.ArgumentParser(description="Build German vocabulary TSV from Wiktionary and frequency data.")
    repo_root = Path(__file__).resolve().parent.parent
    default_output = repo_root / "app" / "src" / "main" / "assets" / "words_de.tsv"
    default_cache = repo_root / "tools" / ".cache"

    parser.add_argument("-o", "--output", type=Path, default=default_output, help="Output TSV path")
    parser.add_argument("--cache-dir", type=Path, default=default_cache, help="Cache directory")
    parser.add_argument("--count", type=int, default=1500, help="Target row count (default: 1500)")
    args = parser.parse_args()

    build_words(args.cache_dir, args.output, args.count)


if __name__ == "__main__":
    main()
