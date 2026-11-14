# Strategy: ingest Grammatik aktiv B2–C1 as the course's second book

Status: reviewed draft (Opus consult folded in, 2026-11-14). Complements
`ingestion.md`, which stays the pipeline of record; nothing here changes its
rules (two-pass reads, copyright rewrite, gates, one-lesson-per-PR).

## Source book (verified 2026-11-14)

Grammatik aktiv B2–C1 (Cornelsen 2017, Jin/Voß, ISBN 978-3-06-021482-2).
PDF lives in Google Drive; working copy goes to `.course-pages/grammatik-aktiv-b2c1.pdf`.

- 313 PDF pages, **no text layer** — same visual-read pipeline as the A1–B1 book.
- Page offset: **printed page N = PDF page N (offset 0)**, calibrated on the
  ToC (ToC printed 4–7 = PDF pages 4–7). Re-verify per runbook rule anyway.
- **88 chapters + 9 Partnerseiten**, ~30 Sprechtraining items (p232+),
  Anhang reference lists (p245–264), Lösungen (p265), Register (p310).
- Every chapter is badged **B2, C1, or B2+C1** (per-exercise badges inside).
- Chapters run longer than the sister book: several 4–6 page spreads
  (e.g. ch 15: 46–50, ch 34: 92–96, ch 45–46, ch 65).

Units, in book order:

| Unit | Chapters | P |
|---|---|---|
| Wortpositionen im Satz | 1–8 | P1 |
| Konjunktiv II | 9–13 | P2 |
| Passiv | 14–19 | P3 |
| Präpositionen | 20–30 | P4 |
| Verben, Adjektive, Nomen und ihre Ergänzungen | 31–35 | – |
| Bildung der Zeiten | 36–40 | P5 |
| Modalverben, lassen und (un)trennbare Verben | 41–46 | – |
| Nomen, Artikel und Pronomen | 47–54 | P6 |
| Adjektive | 55–58 | P7 |
| Indirekte Rede | 59–61 | – |
| Nebensätze | 62–72 | P8 |
| Besondere Wörter und Wortverbindungen | 73–81 | – |
| Umformung von Sätzen | 82–86 | P9 |
| Und noch mehr Wissenswertes | 87–88 | – |

## Identity and numbering

- **Lesson ids continue the existing namespace**: chapters → `L81–L168`,
  Partnerseiten → `P12–P20`, in book order. One id keyspace for learner
  progress; no book-2 prefix, so progress state and backup stay uniform.
- **Manifest**: new file `lessons-b2c1.tsv`, same columns as `lessons.tsv`.
  Separate file because page calibration, unit list and status tracking are
  per book; a single 189-row manifest would mix two sources.
- **`level` column carries the book's badge**: `B2`, `C1`, or `B2+C1`.
- Display names from the `grammar` column as before, never the book's
  sentence titles.

## Sequencing and the learner's actual state

The learner is working at B2 now; the A1–B1 line is ingested only to L14.
Therefore:

- The **B2–C1 line is the active study line**. A1–B1 ingestion (L15+) pauses,
  not aborts — resume later from its own manifest.
- The course screen grows a **book/level selector** (A1→B1 · B2→C1). Each line
  keeps its own next-up card and unit list; progress stays per lesson id.
- The book's own structure supports free order ("Alle Grammatikthemen können
  Sie unabhängig voneinander bearbeiten"), but the app keeps the
  next-up-in-book-order loop as the default. Jumping is allowed; skipping is
  not required to be hidden.

## Overlap with the A1–B1 book (link, don't merge)

Some B2–C1 topics deepen A1–B1 ones (Adjektivdeklination, temporale
Präpositionen, Zeiten, Negation). Rules:

- Never merge or reuse ids across books. A B2–C1 lesson that extends an
  A1–B1 lesson says so in a **`builds on: L<nn>`** line in the spec.
- Drill linkage stays per-rule: if the rule maps to an existing drill topic,
  reuse it and its pools; deeper rules get NEW ids under the same conventions.

## Drill engine impact (smaller than assumed)

`Drill.kt` on main already has 22 drill types, including most of this book's
core grammar: `passive`, `konjunktiv_ii`, `konjunktiv_i`, `genitive`,
`n_declension`, `werden`, `praeteritum`, `infinitiv_zu`, `modal_verbs`,
`relative_pronouns` — plus the A1–B1 basics. Consequences:

- **Map to existing types first.** `drill_topics` in the manifests holds
  drill-content spec slugs (hyphens: `konjunktiv-ii`, `infinitiv-zu`); each
  maps 1:1 to a `Drill.kt` enum id (`konjunktiv-ii` → `konjunktiv_ii`). In
  code, use the enum's exact underscore ids — the ids are stored in
  persistent data (scores, item history), so a near-duplicate spelling cannot
  be renamed away later.
- **Actually NEW** (expected, confirm per lesson): `funktionsverbgefuege`,
  `nominalisierung`, `modalpartikeln`, plus deepenings of existing topics
  (Passiv alternatives, indirekte Rede past forms, Genitiv prepositions).
- **Per-rule `level` field** in TEMPLATE.md: chapter badges (B2/C1/B2+C1) are
  too coarse — a B2+C1 chapter mixes B2 and C1 rules, and the manifest-level
  `level` column would drag C1 rules into a B2 learner's drill round. Rules
  carry their own badge; the chapter badge stays as the summary.
- **Transformation drills** (unit 82–86, Nominalisierung/Attributsätze/
  Präposition↔Adverb↔Konnektor): start with existing patterns — pick the
  correct rewrite, or fill a gap in the transformed sentence. A typed free
  rewrite has unbounded valid answers and can't be checked deterministically;
  if genuinely needed, route it through AI practice instead of a new engine
  pattern type.
- Drill-topic implementation lands **one PR per unit**, just before the
  learner reaches that unit — specs (wave 1) never wait on engine work.

## Scope boundaries

- **Anhang reference lists** (Präpositionslisten, unregelmäßige Verben):
  not lessons, not screens. They serve as fact sources for item pools while
  ingesting units 20–35. A lookup screen remains a separate feature decision.
- **Sprechtraining** (p232–243): audio-based; no audio pipeline in the app.
  Skipped with why, recorded in coverage, revisit never silently.
- **Partnerseiten P12–P20**: ingested as review-checkpoint lessons, same as
  P01–P11.
- **Lösungen / Register**: excluded (matches the existing decision record).

## Run shape

- Same runbook, same two gates (orchestrator table check → user approval).
- **Runbook needs book slots**: it currently hard-codes
  `.course-pages/grammatik-aktiv.pdf` and the A1–B1 offset note. Add
  source-PDF and printed↔PDF-offset slots, filled from the manifest row.
  Same for `course-screen.md`'s hard-coded "L14 of 91 · A1→B1" totals.
- **Land the design branch on main first**: the pipeline docs exist only on
  `docs/german-course-design` (commit `dfaa5bfe`); the runbook's read-first
  paths must exist on the branch lessons are ingested from.
- Spread budgets are bigger: 4–6 page chapters must use the **transcript
  cache** (mandatory for those, per `ingestion.md` stage-1 criteria), plus a
  **page budget per chapter** in the runbook slot — gate 1 checks reads
  against the reported page count, which needs a budget to check against.
- **Ordering comes from the manifest, never id sort**: as text, `L100` sorts
  before `L81`; add a test once ordering logic exists.
- **`builds on: L<nn>` links may target un-ingested lessons** (A1–B1 line is
  at L14). Un-ingested targets render as plain-text references on the read
  page, not links.
- **Pilot set before any batch**: `L81` (Wortposition, B2+C1), `L87`
  (C1-only, Fokuspartikeln), and one **Passiv-unit chapter** (e.g. ch 14 →
  L94) — the Passiv unit tests the riskiest assumption, reusing existing
  drill topics at B2 depth. `P12` follows right after the pilot triple.
  Purposes: level-column handling, C1-only pool depth, per-rule badge
  handling, Partnerseite-as-review fit.
- **Old drill-content specs are not retired by B2–C1 ingestion**: the passive
  and Konjunktiv item specs feed both books; retirement stays tied to the
  A1–B1 line replacing them.
- Batch mode only after the pilot passes both gates.

## Consulted decisions (Opus, 2026-11-14 — resolved)

1. **Ids continue the namespace** (`L81–L168`, `P12–P20`): one keyspace keeps
   progress, backup and next-up logic unchanged; the manifest file records
   the book.
2. **Header band toggle** (A1→B1 · B2→C1), defaulting to B2→C1: units below
   units would bury the active line under ~30 rows and break the per-book
   progress total.
3. **Specs never wait on engine work**; drill PRs land per **unit** (not per
   family), just before the learner reaches it — a lesson is only useful
   once linked.
4. **Anhang stays out of scope** as screens; the lists are fact sources for
   item pools while ingesting units 20–35. A lookup screen is a separate
   feature with no learning loop behind it.
5. **Transformation unit (82–86) starts on existing patterns** (choose the
   rewrite / gap in the transformed sentence); free typed rewrite is not
   deterministically checkable.
