# Tools

## build_words.py

`tools/build_words.py` is a Python 3 (standard library only) script that generates `app/src/main/assets/words_de.tsv` by downloading and joining:
1. The **kaikki.org** German extract of English Wiktionary (machine-readable JSONL dump).
2. The **Hermit Dave FrequencyWords** German frequency list (derived from OpenSubtitles 2018).

Both source datasets and the resulting TSV are licensed under **CC BY-SA 4.0**.

### Usage

```bash
python3 tools/build_words.py
```

Optional arguments:
- `-o`, `--output`: Output path for the TSV (default: `app/src/main/assets/words_de.tsv`).
- `--cache-dir`: Cache directory for downloads (default: `tools/.cache`).
- `--count`: Number of vocabulary words to output (default: `1500`).

### Caching and Git
Raw dumps are downloaded to `tools/.cache/` and are gitignored. Existing cached files are reused across runs. Raw dumps must never be committed to version control.

### Stable IDs
Word progress in Thumbline is persisted by integer `id`. When `build_words.py` is re-run against an existing `words_de.tsv`, it preserves existing lemma IDs so that words are never renumbered. Any newly added lemmas receive IDs incremented from the highest existing ID.

### Filtering Rules
The generator filters candidates to produce a clean learning vocabulary:
- **Lemmas only**: Inflected forms (`form-of`, `alt-of`) are excluded.
- **Excluded categories**: Proper nouns, abbreviations, acronyms, and function words (articles, pronouns, conjunctions) are excluded.
- **Senses**: Archaic, obsolete, dated, and dialect senses are excluded.
- **Nouns**: Nouns are formatted with their grammatical gender article (`der`, `die`, `das`) in the `de` column and include their nominative plural in the `plural` column when one exists.
- **English glosses**: The `en` column contains up to 2 clean, short English glosses.
- **Examples**: The `example` column contains a usage example only when it is under 90 characters and not a dated historical quotation.

### Attribution
See `app/src/main/assets/WORDS_ATTRIBUTION.txt` for full licensing and attribution information.
