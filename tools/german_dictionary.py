#!/usr/bin/env python3
"""Trim a pinned, complete Kaikki German JSONL extract using only Python's stdlib."""

import argparse
import collections
import dataclasses
import gzip
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import unicodedata

SOURCE_URL = "https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl"
SUPPORTED_POS = frozenset({
    "adj", "adv", "article", "conj", "contraction", "det", "intj", "name",
    "noun", "num", "particle", "phrase", "postp", "prep", "prep_phrase",
    "pron", "proverb", "verb",
})
RARE_TAGS = frozenset({"archaic", "obsolete", "rare", "dated", "historical", "nonstandard"})
INFLECTION_TAGS = frozenset({
    "accusative", "alternative", "comparative", "dative", "feminine", "genitive",
    "imperative", "indicative", "infinitive", "masculine", "neuter", "nominative",
    "participle", "past", "plural", "present", "preterite", "singular",
    "subjunctive", "superlative",
})
NON_FORM_TAGS = frozenset({"table-tags", "inflection-template", "class", "auxiliary", "diminutive"})
BUDGET_BYTES = 15_000_000


class InvalidRecord(ValueError):
    pass


def text(value, field):
    if not isinstance(value, str) or not value.strip() or "\0" in value:
        raise InvalidRecord(f"{field}: expected nonempty text without NUL")
    return unicodedata.normalize("NFC", value.strip())


def objects(value, field, nonempty=False):
    if not isinstance(value, list) or (nonempty and not value) or any(
        not isinstance(item, dict) for item in value
    ):
        raise InvalidRecord(f"{field}: expected {'nonempty ' if nonempty else ''}object list")
    return value


def strings(value, field):
    if not isinstance(value, list):
        raise InvalidRecord(f"{field}: expected text list")
    return [text(item, field) for item in value]


@dataclasses.dataclass(frozen=True)
class Entry:
    lemma: str
    pos: str
    gender: str
    plural: str
    gloss: str
    present: str
    past: str
    participle: str
    aliases: tuple
    targets: tuple
    rare: bool


def parse_record(record):
    """Malformed records abort generation; structurally valid unsupported records are omitted."""
    if not isinstance(record, dict):
        raise InvalidRecord("record: expected object")
    lemma = text(record.get("word"), "word")
    pos = text(record.get("pos"), "pos")
    language = text(record.get("lang_code"), "lang_code")
    senses = objects(record.get("senses"), "senses", nonempty=True)
    forms = objects(record.get("forms", []), "forms")
    lexical = []
    targets = set()
    for sense in senses:
        tags = set(strings(sense.get("tags", []), "sense.tags"))
        glosses = strings(sense.get("glosses", []), "sense.glosses")
        if not glosses and "no-gloss" not in tags:
            raise InvalidRecord("sense: missing glosses (without no-gloss tag)")
        form_of = objects(sense.get("form_of", []), "sense.form_of")
        for target in form_of:
            targets.add(text(target.get("word"), "form_of.word"))
        if "form-of" in tags and not form_of:
            raise InvalidRecord("form-of sense: missing target")
        if not form_of and "form-of" not in tags and glosses:
            lexical.append((glosses[0], tags))
    validated_forms = []
    unsupported_form = False
    for form in forms:
        value = text(form.get("form"), "form.form")
        tags = set(strings(form.get("tags", []), "form.tags"))
        if not tags:
            unsupported_form = True
        validated_forms.append((value, tags))
    if language != "de" or pos not in SUPPORTED_POS or unsupported_form:
        return None
    if not lexical and not targets:
        return None

    def choose(predicate):
        # Headword forms precede tables; ignore rare variants when selecting the summary.
        return next((value for value, tags in validated_forms
                     if predicate(tags) and not (tags & (RARE_TAGS | NON_FORM_TAGS))), "")

    gender_tags = set().union(*(tags for _, tags in lexical)) if lexical else set()
    gender = "/".join(article for tag, article in (
        ("masculine", "der"), ("feminine", "die"), ("neuter", "das")
    ) if tag in gender_tags) if pos in {"noun", "name"} else ""
    aliases = {lemma}
    for value, tags in validated_forms:
        if (tags & INFLECTION_TAGS and not tags & NON_FORM_TAGS
                and len(value) <= 80 and not any(c.isspace() for c in value)
                and value not in {"—", "-", "?"}):
            aliases.add(value)
    gloss = lexical[0][0] if lexical else ""
    if len(gloss) > 160:
        gloss = gloss[:157].rsplit(" ", 1)[0] + "…"
    return Entry(
        lemma, pos, gender,
        choose(lambda tags: "plural" in tags and
               ("nominative" in tags or not tags & {"genitive", "dative", "accusative"}))
        if pos in {"noun", "name"} else "",
        gloss,
        choose(lambda tags: {"present", "singular", "third-person"} <= tags
               and not tags & {"subjunctive", "participle"}) if pos == "verb" else "",
        choose(lambda tags: bool(tags & {"past", "preterite"}) and
               not tags & {"participle", "subjunctive", "plural"} and
               ("third-person" in tags or not tags & {"first-person", "second-person"}))
        if pos == "verb" else "",
        choose(lambda tags: {"past", "participle"} <= tags) if pos == "verb" else "",
        tuple(sorted(aliases)), tuple(sorted(targets)),
        bool(lexical) and all(tags & RARE_TAGS for _, tags in lexical),
    )


def read_source(source, expected_sha256, expected_bytes):
    entries = []
    digest = hashlib.sha256()
    size = 0
    counts = collections.Counter()
    with source.open("rb") as stream:
        for number, line in enumerate(stream, 1):
            digest.update(line)
            size += len(line)
            if not line.endswith(b"\n"):
                raise InvalidRecord(f"line {number}: unterminated JSONL record")
            try:
                entry = parse_record(json.loads(line))
            except (ValueError, UnicodeError) as error:
                raise InvalidRecord(f"line {number}: {error}") from error
            counts["source_records"] += 1
            if entry is None:
                counts["unsupported_records"] += 1
            else:
                entries.append(entry)
    # This independently rejects truncation at a *valid line boundary*, not just broken JSON.
    if size != expected_bytes or digest.hexdigest() != expected_sha256:
        raise InvalidRecord("source bytes/SHA-256 do not match the pinned complete extract")
    return entries, dict(counts)


def write_database(entries, path, drop_rare=False):
    with sqlite3.connect(path) as db:
        db.executescript("""
            PRAGMA page_size=4096;
            PRAGMA user_version=1;
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY, lemma TEXT NOT NULL, pos TEXT NOT NULL,
                gender TEXT NOT NULL, plural TEXT NOT NULL, gloss TEXT NOT NULL,
                present TEXT NOT NULL, past TEXT NOT NULL, participle TEXT NOT NULL
            );
            CREATE TABLE lookup (
                form TEXT NOT NULL, entry_id INTEGER NOT NULL REFERENCES entries(id),
                PRIMARY KEY (form, entry_id)
            ) WITHOUT ROWID;
        """)
        targets = collections.defaultdict(list)
        pending = []
        kept = 0
        for entry in entries:
            if drop_rare and entry.rare:
                continue
            if not entry.gloss:
                pending.append(entry)
                continue
            kept += 1
            db.execute("INSERT INTO entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                       (kept, entry.lemma, entry.pos, entry.gender, entry.plural, entry.gloss,
                        entry.present, entry.past, entry.participle))
            targets[(entry.lemma, entry.pos)].append(kept)
            db.executemany("INSERT OR IGNORE INTO lookup VALUES (?, ?)",
                           ((form, kept) for form in entry.aliases))
        for entry in pending:
            for target in entry.targets:
                for entry_id in targets.get((target, entry.pos), []):
                    db.executemany("INSERT OR IGNORE INTO lookup VALUES (?, ?)",
                                   ((form, entry_id) for form in entry.aliases))
        db.commit()
        if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise InvalidRecord("generated SQLite database failed integrity check")
        if db.execute("PRAGMA foreign_key_check").fetchall():
            raise InvalidRecord("generated SQLite database has dangling form mappings")
        return {"entries": kept, "forms": db.execute("SELECT count(*) FROM lookup").fetchone()[0]}


def compress(source, destination):
    with source.open("rb") as raw, destination.open("wb") as output:
        with gzip.GzipFile(filename="", mode="wb", fileobj=output, mtime=0, compresslevel=9) as zipped:
            for block in iter(lambda: raw.read(1024 * 1024), b""):
                zipped.write(block)


def build(source, output, expected_sha256, expected_bytes, budget=BUDGET_BYTES):
    if budget <= 0:
        raise ValueError("budget must be positive")
    if expected_bytes <= 0 or not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("pin a nonempty source with its lowercase SHA-256 and byte size")
    # Validate the *entire* source before even creating a staging output.
    entries, stats = read_source(source, expected_sha256, expected_bytes)
    if not any(entry.gloss for entry in entries):
        raise InvalidRecord("source contains no supported lexical definitions")
    output.parent.mkdir(parents=True, exist_ok=True)
    database = output.with_name(output.name + ".sqlite.partial")
    compressed = output.with_name(output.name + ".partial")
    try:
        for drop_rare in (False, True):
            database.unlink(missing_ok=True)
            stats.update(write_database(entries, database, drop_rare))
            compress(database, compressed)
            if compressed.stat().st_size < budget:
                break
        else:
            raise InvalidRecord("dictionary exceeds compressed budget even after dropping rare entries")
        stats.update({
            "schema_version": 1, "source_url": SOURCE_URL,
            "source_sha256": expected_sha256, "source_bytes": expected_bytes,
            "database_sha256": hashlib.sha256(database.read_bytes()).hexdigest(),
            "database_bytes": database.stat().st_size,
            "compressed_sha256": hashlib.sha256(compressed.read_bytes()).hexdigest(),
            "compressed_bytes": compressed.stat().st_size,
            "dropped_rare": drop_rare,
            "license": "CC BY-SA 4.0",
            "attribution": "English Wiktionary contributors; kaikki.org/wiktextract",
        })
        compressed.replace(output)
        return stats
    finally:
        database.unlink(missing_ok=True)
        compressed.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--source-bytes", required=True, type=int)
    args = parser.parse_args()
    stats = build(args.source, args.output, args.source_sha256, args.source_bytes)
    metadata = args.output.with_suffix(".json")
    metadata.write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
