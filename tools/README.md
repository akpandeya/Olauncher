# Offline German dictionary

`german_dictionary.py` is a **build-time** tool (Python 3.9+, standard library only).
It is not shipped or run by Gradle. The app ships the generated gzip-compressed
SQLite database and its small provenance manifest in `app/src/main/assets/`.
Android's platform SQLite reads a verified, lazily unpacked copy in the app's
no-backup directory. No runtime library or network download is needed.
The asset uses `.gzip`, not `.gz`: Android packaging automatically expands and
renames `.gz` assets, which would break the runtime installer.

## Regenerate

Run from the repository root. Keep the raw extract in the ignored workspace,
never in Git:

```sh
mkdir -p .dictionary-work
curl --fail --location \
  https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl \
  -o .dictionary-work/german.jsonl
python3 tools/german_dictionary.py .dictionary-work/german.jsonl \
  app/src/main/assets/german-v1.sqlite.gzip \
  --source-sha256 b3328b24dfa186192ea6956f1ecd4a91b7b8c49781f99b6afcf1e2e1d10c59da \
  --source-bytes 1095258648
python3 -m unittest discover -s tools/tests -v
rm .dictionary-work/german.jsonl
```

The pinned snapshot was retrieved on 2026-10-23. Kaikki updates its download:
when deliberately refreshing, first verify that the HTTP transfer completed
and the file size matches the server's Content-Length, then pin its new byte size
and SHA-256. Do not change a pin just to accept a partial download. Regenerating
the same snapshot and SQLite version gives identical gzip bytes (`mtime=0`).
The manifest records hashes of both the compressed asset and installed database;
the database hash also keys the installed filename so app updates cannot reuse
stale data.

## Trimming and validation

- Every record must have nonempty `word`, `pos`, `lang_code`, and `senses`.
  Selected nested fields are type-checked; a form-of sense must identify its
  target. Missing glosses require the source's explicit `no-gloss` tag.
- Malformed records abort the build. Non-German and unsupported parts of speech,
  records with untagged forms, and no-gloss-only records are omitted
  entirely, never emitted as partial entries. Unsupported POS includes affixes,
  punctuation, characters and symbols.
- The entire input must be valid newline-terminated JSONL **and** match the pinned
  source byte size and SHA-256 before a staged output is created. This rejects
  truncation at a complete record boundary independently of any row count.
- Keep lemma, POS, gender, one plural, an English gloss (at most 160 characters),
  third-person singular present, simple past, and past participle.
- A `form_of` relation is not necessarily inflection: agent nouns (`Lehrer`),
  gendered counterparts (`Studentin`), gerunds and diminutives remain lexical
  entries with their own gender/plural. Grammatical inflection features are
  interpreted by POS; derivational noun tags/glosses take precedence.
- Index retained lemma identities and only the surface words of explicit
  inflection records whose target lemma exists with the same POS. Never turn a
  record's entire forms table into aliases: shared tables contain other pronouns,
  derived words and article fragments. For example `mich` maps only to `ich`,
  and `die` maps to the article/pronoun `der`, not the proper name `CIA`.
  Forms tables are used for the morphology summary, not as independent mapping
  evidence. Unattested table-only forms fall back online instead of guessing;
  unresolved or wrong-POS inflection targets are not emitted as definitions.
- The compressed budget is strictly **less than 15,000,000 bytes**. If necessary,
  retry deterministically without entries whose lexical senses are all tagged
  archaic, obsolete, rare, dated, historical or nonstandard. Otherwise fail and
  leave the previous output intact; never silently truncate the dictionary.
- SQLite integrity and foreign-key checks run before publication. Counts are
  reported for inspection, not used to prove source completeness.

The asset has 109,630 lexical entries and 362,523 indexed form/entry pairs,
occupies 6,434,596 compressed bytes (16,289,792 unpacked), and needs no rare-word
filtering. Full-source and fixture tests cover `gehen`, `ging → gehen`,
`Mädchen → das`, `Haus → Häuser`, `Lehrer`, `Studentin → die / Studentinnen`,
`mich → ich`, `die → der` (article/pronoun), parser failures, complete-line
truncation, deterministic output and the compressed budget.

Lookup meanings use a dedicated body-sized, multiline drawer layout with no
line/height cap; app names and unrelated result rows keep the existing layout.
`WordLookupRowsTest` checks full-text/action wiring and follows the adapter's
selected layout resource to verify wrapping and the absence of clipping limits.

## APK size comparison

With JDK 17, Android API 36 and `GITHUB_RUN_NUMBER=617` (versionCode 730):

| Artifact | Bytes |
| --- | ---: |
| Unchanged `origin/main` at `747b7ee1` (supplied baseline) | 15,869,086 |
| Feature `thumbline-debug.apk` | 22,299,027 |
| APK increase | 6,429,941 |
| Bundled gzip dictionary | 6,434,596 |

The APK ZIP layer also compresses the gzip asset, so its contribution to the APK
is slightly smaller than its standalone size.

## Device checks

`OfflineGermanAssetTest` checks the packaged gzip with Android's SQLite, without
changing settings or saved words; its uniquely named test database is removed
in `finally`. The initial implementation was checked manually with `:w ging`;
subsequent parser/presentation review fixes use host-only tests, with no further
phone access.

On a personal phone, **do not use `connectedDebugAndroidTest`**: this repository's
AGP/UTP runner uninstalls both the target and test apps after tests, including
target app data. Use an emulator, or (with device-test authorization) build via
`assembleDebugAndroidTest`, install only the test APK over an already installed
matching app, and invoke its runner manually:

```sh
adb install -r app/build/outputs/apk/androidTest/debug/thumbline-debug-androidTest.apk
adb shell am instrument -w -e class app.olauncher.OfflineGermanAssetTest \
  app.thumbline.debug.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall app.thumbline.debug.test
```

Never uninstall or clear the target `app.thumbline.debug` package to test this
feature, and do not change phone network, timeout or user settings.

## Licensing

The extract is from **English Wiktionary contributors**, processed by
[kaikki.org/wiktextract](https://kaikki.org/wiktextract/) and trimmed/indexed by
Thumbline. The modified dictionary is distributed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
See `LICENSES/Kaikki-German.txt`; the in-app Wiktionary footer includes the source
and licence links. App code remains GPLv3 and retains the Olauncher credit.
