import copy
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import sys
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import german_dictionary as dictionary

FIXTURE = ROOT / "tools/tests/fixtures/german.jsonl"

GEHEN_PRESENT = {
    ("gehe", "first-person indicative present singular"),
    ("gehst", "indicative present second-person singular"),
    ("geht", "indicative present singular third-person"),
    ("gehen", "first-person indicative plural present"),
    ("geht", "indicative plural present second-person"),
    ("gehen", "indicative plural present third-person"),
}
MACHEN_PRESENT = {
    ("mache", "first-person indicative present singular"),
    ("machst", "indicative present second-person singular"),
    ("macht", "indicative present singular third-person"),
    ("machen", "first-person indicative plural present"),
    ("macht", "indicative plural present second-person"),
    ("machen", "indicative plural present third-person"),
}
SEIN_PRESENT = {
    ("bin", "first-person indicative present singular"),
    ("bist", "indicative present second-person singular"),
    ("ist", "indicative present singular third-person"),
    ("sind", "first-person indicative plural present"),
    ("seid", "indicative plural present second-person"),
    ("sind", "indicative plural present third-person"),
}


class GermanDictionaryTest(unittest.TestCase):
    def setUp(self):
        self.directory = ROOT / ".dictionary-work" / ("test-" + uuid.uuid4().hex)
        self.directory.mkdir(parents=True)
        self.addCleanup(shutil.rmtree, self.directory)
        self.source = self.directory / "source.jsonl"
        self.original = FIXTURE.read_bytes()
        self.source.write_bytes(self.original)
        self.sha256 = hashlib.sha256(self.original).hexdigest()
        self.output = self.directory / "german.sqlite.gz"

    def build(self, **kwargs):
        return dictionary.build(self.source, self.output, self.sha256, len(self.original), **kwargs)

    def database(self, asset=None):
        path = self.directory / "dictionary.sqlite"
        path.write_bytes(gzip.decompress((asset or self.output).read_bytes()))
        db = sqlite3.connect(path)
        db.row_factory = sqlite3.Row
        self.addCleanup(db.close)
        return db

    def lookup(self, db, form):
        return db.execute(
            "SELECT e.* FROM lookup l JOIN entries e ON l.entry_id=e.id "
            "WHERE l.form=? ORDER BY CASE WHEN e.lemma=? THEN 0 ELSE 1 END,e.id LIMIT 2",
            (form, form)
        ).fetchall()

    def all_targets(self, db, form):
        return db.execute(
            "SELECT e.* FROM lookup l JOIN entries e ON l.entry_id=e.id "
            "WHERE l.form=? ORDER BY e.id", (form,)
        ).fetchall()

    def forms_rows(self, db, lemma):
        return [tuple(row) for row in db.execute(
            "SELECT f.form, f.tags FROM forms f JOIN entries e ON e.id=f.entry_id "
            "WHERE e.lemma=? AND e.pos='verb' ORDER BY f.form, f.tags", (lemma,))]

    def present_cells(self, db, lemma):
        return {tuple(row) for row in db.execute(
            "SELECT f.form, f.tags FROM forms f JOIN entries e ON e.id=f.entry_id "
            "WHERE e.lemma=? AND e.pos='verb' AND f.tags LIKE '%indicative%present%'",
            (lemma,))}

    def test_trims_fields_and_resolves_form_of_even_before_lemma(self):
        self.build()
        db = self.database()
        for form in ("gehen", "ging", "gingen"):
            row = self.lookup(db, form)[0]
            self.assertEqual("gehen", row["lemma"])
            self.assertEqual(("geht", "ging", "gegangen"),
                             (row["present"], row["past"], row["participle"]))
            self.assertEqual("to go, to walk", row["gloss"])
        self.assertEqual("das", self.lookup(db, "Mädchen")[0]["gender"])
        self.assertEqual("Häuser", self.lookup(db, "Haus")[0]["plural"])
        self.assertEqual("Haus", self.lookup(db, "Häuser")[0]["lemma"])
        for non_form in ("sein", "de-conj", "Häuschen", "gieng"):
            self.assertEqual([], self.lookup(db, non_form))

    def test_derived_nouns_keep_their_own_gender_plural_and_inflections(self):
        self.build()
        db = self.database()
        for form, lemma, gender, plural in (
            ("Lehrer", "Lehrer", "der", "Lehrer"),
            ("Lehrers", "Lehrer", "der", "Lehrer"),
            ("Studentin", "Studentin", "die", "Studentinnen"),
            ("Studentinnen", "Studentin", "die", "Studentinnen"),
        ):
            with self.subTest(form=form):
                rows = self.all_targets(db, form)
                self.assertEqual([(lemma, gender, plural)],
                                 [(r["lemma"], r["gender"], r["plural"]) for r in rows])
                self.assertTrue(rows[0]["gloss"])

    def test_shared_paradigms_and_article_fragments_never_become_mappings(self):
        self.build()
        db = self.database()
        for surface, lemma, pos in (
            ("mich", "ich", "pron"), ("die", "der", "article"),
            ("ich", "ich", "pron"), ("du", "du", "pron"), ("er", "er", "pron"),
        ):
            with self.subTest(surface=surface):
                self.assertEqual([(lemma, pos)], [(r["lemma"], r["pos"])
                                                 for r in self.all_targets(db, surface)])
        # Tables alone are not independent evidence, even when the cell is valid for its owner.
        for unattested in ("das", "Studenten"):
            self.assertEqual([], self.all_targets(db, unattested))

    def test_inflection_targets_require_a_retained_lemma_with_the_same_pos(self):
        extra = (
            b'{"word":"wrong-pos","pos":"noun","lang_code":"de","senses":'
            b'[{"glosses":["plural of gehen"],"tags":["form-of","plural"],"form_of":[{"word":"gehen"}]}]}\n'
            b'{"word":"missing-target","pos":"noun","lang_code":"de","senses":'
            b'[{"glosses":["plural of absent"],"tags":["form-of","plural"],"form_of":[{"word":"absent"}]}]}\n'
        )
        raw = self.original + extra
        self.source.write_bytes(raw)
        dictionary.build(self.source, self.output, hashlib.sha256(raw).hexdigest(), len(raw))
        db = self.database()
        self.assertEqual([], self.all_targets(db, "wrong-pos"))
        self.assertEqual([], self.all_targets(db, "missing-target"))

    def test_rejects_missing_or_malformed_required_and_nested_fields(self):
        valid = json.loads(self.original.splitlines()[1])
        for field in ("word", "pos", "lang_code", "senses"):
            broken = copy.deepcopy(valid)
            del broken[field]
            with self.subTest(field=field), self.assertRaises(dictionary.InvalidRecord):
                dictionary.parse_record(broken)
        for patch in (
            {"word": ""}, {"word": "x\0y"}, {"senses": []}, {"senses": [{}]},
            {"senses": [{"glosses": "not a list"}]},
            {"senses": [{"glosses": ["form"], "tags": ["form-of"]}]},
            {"senses": [{"glosses": ["form"], "form_of": [{}]}]},
            {"forms": {}}, {"forms": [{"tags": ["plural"]}]},
            {"forms": [{"form": "foo", "tags": "plural"}]},
        ):
            with self.subTest(patch=patch), self.assertRaises(dictionary.InvalidRecord):
                dictionary.parse_record({**valid, **patch})

    def test_unsupported_records_never_emit_entries(self):
        valid = json.loads(self.original.splitlines()[1])
        for patch in (
            {"lang_code": "en"}, {"pos": "unknown"},
            {"forms": [{"form": "used with a verb"}]},
            {"senses": [{"tags": ["no-gloss"]}]},
        ):
            with self.subTest(patch=patch):
                self.assertIsNone(dictionary.parse_record({**valid, **patch}))

    def test_bad_input_never_creates_or_replaces_output(self):
        bad_inputs = (
            self.original + b'{"word":',
            self.original.rstrip(b"\n"),
            self.original + b'{"word":"incomplete","pos":"verb","lang_code":"de"}\n',
            self.original.rsplit(b"\n", 2)[0] + b"\n",
        )
        for bad in bad_inputs:
            self.source.write_bytes(bad)
            self.output.unlink(missing_ok=True)
            with self.assertRaises(dictionary.InvalidRecord):
                self.build()
            self.assertFalse(self.output.exists())
            self.output.write_bytes(b"previous good dictionary")
            with self.assertRaises(dictionary.InvalidRecord):
                self.build()
            self.assertEqual(b"previous good dictionary", self.output.read_bytes())
            self.assertEqual([], list(self.directory.glob("*.partial")))

    def test_checksum_rejects_valid_line_boundary_truncation_even_with_matching_size(self):
        truncated = self.original.rsplit(b"\n", 2)[0] + b"\n"
        self.source.write_bytes(truncated)
        with self.assertRaisesRegex(dictionary.InvalidRecord, "SHA-256"):
            dictionary.build(self.source, self.output, self.sha256, len(truncated))
        self.assertFalse(self.output.exists())

    def test_empty_or_wholly_unsupported_source_cannot_publish_empty_dictionary(self):
        with self.assertRaises(ValueError):
            dictionary.build(self.source, self.output, hashlib.sha256(b"").hexdigest(), 0)
        unsupported = b'{"word":"x","pos":"symbol","lang_code":"de","senses":[{"glosses":["x"]}]}\n'
        self.source.write_bytes(unsupported)
        with self.assertRaisesRegex(dictionary.InvalidRecord, "no supported lexical"):
            dictionary.build(self.source, self.output, hashlib.sha256(unsupported).hexdigest(), len(unsupported))
        self.assertFalse(self.output.exists())

    def test_output_is_reproducible_and_budget_filters_rare_entries_deterministically(self):
        full_stats = self.build()
        full = self.output.read_bytes()
        self.assertEqual(full_stats, self.build())
        self.assertEqual(full, self.output.read_bytes())
        reduced_stats = self.build(budget=len(full))
        self.assertTrue(reduced_stats["dropped_rare"])
        self.assertLess(reduced_stats["compressed_bytes"], len(full))
        self.assertEqual(reduced_stats, self.build(budget=len(full)))
        db = self.database()
        self.assertEqual([], self.lookup(db, "Sintemal"))
        for form in ("gehen", "ging", "Mädchen", "Haus"):
            self.assertTrue(self.lookup(db, form))
        previous = self.output.read_bytes()
        with self.assertRaisesRegex(dictionary.InvalidRecord, "budget"):
            self.build(budget=1)
        self.assertEqual(previous, self.output.read_bytes())

    def test_verb_conjugation_forms_cover_the_full_present_paradigm(self):
        self.build()
        db = self.database()
        self.assertEqual(MACHEN_PRESENT, self.present_cells(db, "machen"))
        self.assertEqual([
            ("mach", "imperative second-person singular"),
            ("macht", "imperative plural second-person"),
        ], [row for row in self.forms_rows(db, "machen") if "imperative" in row[1]])

    def test_non_verb_entries_have_no_conjugation_forms(self):
        self.build()
        db = self.database()
        for lemma in ("Haus", "Mädchen", "Lehrer"):
            with self.subTest(lemma=lemma):
                self.assertEqual(0, db.execute(
                    "SELECT count(*) FROM forms f JOIN entries e ON e.id=f.entry_id "
                    "WHERE e.lemma=?", (lemma,)).fetchone()[0])

    def test_conjugation_forms_exclude_alternative_auxiliary_metadata_periphrastic_rows(self):
        self.build()
        db = self.database()
        self.assertEqual([], db.execute(
            "SELECT form, tags FROM forms "
            "WHERE form IN ('gieng', 'machet', 'de-conj', 'sein', 'haben', "
            "'habe gemacht')").fetchall())
        self.assertEqual(0, db.execute(
            "SELECT count(*) FROM forms WHERE tags LIKE '%alternative%' OR "
            "tags LIKE '%auxiliary%' OR tags LIKE '%inflection-template%' OR "
            "tags LIKE '%multiword-construction%'").fetchone()[0])

    def test_homonymous_forms_with_distinct_tags_are_all_stored(self):
        self.build()
        db = self.database()
        self.assertEqual([
            ("macht", "imperative plural second-person"),
            ("macht", "indicative plural present second-person"),
            ("macht", "indicative present singular third-person"),
            ("macht", "present singular third-person"),
        ], [row for row in self.forms_rows(db, "machen") if row[0] == "macht"])

    def test_stats_report_schema_version_2_and_conjugation_form_count(self):
        stats = self.build()
        db = self.database()
        self.assertEqual(2, stats["schema_version"])
        self.assertEqual(db.execute("SELECT count(*) FROM forms").fetchone()[0],
                         stats["conjugation_forms"])
        self.assertEqual(15, stats["conjugation_forms"])

    def test_shipped_asset_metadata_integrity_and_real_lookups(self):
        asset = ROOT / "app/src/main/assets/german-v2.sqlite.gzip"
        metadata = json.loads(asset.with_suffix(".json").read_text())
        compressed = asset.read_bytes()
        raw = gzip.decompress(compressed)
        self.assertLess(len(compressed), dictionary.BUDGET_BYTES)
        self.assertEqual(metadata["compressed_bytes"], len(compressed))
        self.assertEqual(metadata["compressed_sha256"], hashlib.sha256(compressed).hexdigest())
        self.assertEqual(metadata["database_bytes"], len(raw))
        self.assertEqual(metadata["database_sha256"], hashlib.sha256(raw).hexdigest())
        db = self.database(asset)
        self.assertEqual("ok", db.execute("PRAGMA integrity_check").fetchone()[0])
        self.assertEqual(2, db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        self.assertEqual(2, metadata["schema_version"])
        self.assertEqual(metadata["conjugation_forms"],
                         db.execute("SELECT count(*) FROM forms").fetchone()[0])
        for lemma, expected in (("gehen", GEHEN_PRESENT), ("machen", MACHEN_PRESENT),
                                ("sein", SEIN_PRESENT)):
            with self.subTest(lemma=lemma):
                self.assertEqual(expected, self.present_cells(db, lemma))
        self.assertEqual("gehen", self.lookup(db, "gehen")[0]["lemma"])
        self.assertEqual("gehen", self.lookup(db, "ging")[0]["lemma"])
        gehen = self.lookup(db, "gehen")[0]
        self.assertEqual(("geht", "ging", "gegangen"),
                         (gehen["present"], gehen["past"], gehen["participle"]))
        self.assertEqual("das", self.lookup(db, "Mädchen")[0]["gender"])
        self.assertEqual("Häuser", self.lookup(db, "Haus")[0]["plural"])
        for surface, lemma, gender, plural in (
            ("Lehrer", "Lehrer", "der", "Lehrer"),
            ("Lehrers", "Lehrer", "der", "Lehrer"),
            ("Studentin", "Studentin", "die", "Studentinnen"),
            ("Studentinnen", "Studentin", "die", "Studentinnen"),
        ):
            with self.subTest(surface=surface):
                self.assertEqual([(lemma, gender, plural)],
                                 [(r["lemma"], r["gender"], r["plural"])
                                  for r in self.all_targets(db, surface)])
        self.assertEqual([("ich", "pron")],
                         [(r["lemma"], r["pos"]) for r in self.all_targets(db, "mich")])
        die = self.all_targets(db, "die")
        self.assertTrue(die)
        self.assertTrue(all(r["lemma"] == "der" and r["pos"] in {"article", "pron"} for r in die))
        self.assertEqual(("der", "article"), (self.lookup(db, "die")[0]["lemma"],
                                             self.lookup(db, "die")[0]["pos"]))


if __name__ == "__main__":
    unittest.main()
