# Course ingestion pipeline

Turns a German grammar book (the printed source kept in `.course-pages/`) into the
app's own lesson library: one rewritten lesson spec per book topic, mapped onto the
drill system, one PR per topic. The book and any verbatim transcription never enter
the repo.

Two books, one pipeline and one lesson-id namespace:

- **Book 1 — Grammatik aktiv A1–B1**: source `.course-pages/grammatik-aktiv.pdf`,
  manifest `lessons.tsv` (L01–L80, P01–P11). Offset: PDF index + 3 = printed
  page (re-verify per run).
- **Book 2 — Grammatik aktiv B2–C1**: source
  `.course-pages/grammatik-aktiv-b2c1.pdf`, manifest `lessons-b2c1.tsv`
  (L81–L168, P12–P20). Offset: printed page = PDF page − 1, i.e. PDF page =
  printed + 1 (recalibrated 2026-11-15 on the ToC; re-verify per run). See
  `b2c1-ingestion-strategy.md` for the
  per-rule level badges, drill-type reuse and scope boundaries.

Decision record: the appendix (Anhang, verb tables, Lösungen, Register) is not
ingested in either book. The old drill-content topic specs are superseded and
are retired as course lessons replace them — but only by book-1 lessons;
book-2 lessons deepen the same topics (passive, Konjunktiv, Genitiv) and must
not retire specs book 1 still depends on.

## Layout

- `lessons.tsv` — book 1 manifest (Grammatik aktiv A1–B1), transcribed from
  the book's table of contents. One row per topic lesson and per Partnerseite.
  The plan of record: `status` is content-production state
  (`todo` → `ingested` → `linked`).
- `lessons-b2c1.tsv` — book 2 manifest (Grammatik aktiv B2–C1), same columns;
  ids continue the namespace (L81–L168, P12–P20). Level column carries the
  book's per-chapter badge (`B2`, `C1`, `B2+C1`).
- `b2c1-ingestion-strategy.md` — book 2 strategy (consulted): identity,
  sequencing, drill reuse, scope boundaries.
- `TEMPLATE.md` — the shape of a lesson spec.
- `lessons/` — committed lesson specs (`L14-<slug>.md`).
- `.course-pages/` at the repo root — gitignored. Holds the source PDFs
  (`grammatik-aktiv.pdf`, `grammatik-aktiv-b2c1.pdf`) and any standalone page
  photos; optional transcript cache under `md/` (see stage 1). Nothing here is
  ever committed or shipped.
- `course-screen.md` — the learner-facing course screen draft.

## Pipeline (three stages)

### Stage 1 — read the spread (transcript only as a cache)

Default path is direct: read the PDF pages for one lesson spread, distill in the
same session (stage 2), then run the second verification pass on the same pages.
No transcript file is written.

Write a verbatim transcript at `.course-pages/md/L014-p038.md` only when the
spread will be revisited: table-dense lessons with follow-up work, sessions
expected to span a boundary, or an unattended batch run. Transcripts are
disposable scratch; nothing in them is committed.

- Two-pass rule applies either way: after distilling a spread, re-read the same
  PDF pages once and verify every table cell, ending and umlaut character by
  character. Misread table cells are the main factual risk.
- Calibrate the printed-page → PDF-page offset once on the book's own ToC
  before the first lesson.

### Stage 2 — distill (committed output)

From the PDF spread (or the stage-1 transcript when one exists), write the
lesson spec per TEMPLATE.md, at `lessons/L<nn>-<slug>.md`. Everything
learner-facing is rewritten:

- Grammar *facts* (endings, word order, suffix rules, verb government) are
  unprotectable facts and may match the book.
- Explanations, examples and exercise patterning are our own text. No sentence
  from the book appears in the repo. An example sentence in a spec is either an
  existing app drill sentence or new writing, marked as such.
- Display names of lessons come from the manifest `grammar` column, never the
  book's sentence titles.

### Stage 3 — link + PR

- Map every rule to an existing drill topic or flag NEW with a proposed rule ID.
- Set the manifest row to `ingested`, note the transcript filename.
- One lesson per PR: branch `course/l14-artikel-definit-indefinit-kein`.
- `linked` status happens when the lesson is wired into the app (hub, course
  screen, drills) — a separate code PR per batch of lessons.

## Checks (same spirit as ../drill-content)

1. **Coverage** — every rule and exercise pattern on the spread is mapped or
   flagged as skipped (with why).
2. **Rewrite** — no sentence from the book is reused. Every German example
   sentence is marked `new` or `pool:<topic>`.
3. **Mapping** — every rule maps to an existing drill topic or is NEW with an
   ID that follows the existing conventions (lowercase ascii, hyphens, no
   answer in the ID).
4. **Table fidelity** — tables verified against the PDF in the stage-1 second
   pass.
5. **IDs** — `L<nn>` for lessons, `P<nn>` for Partnerseiten, stable for the
   life of learner data.

## Learner progress vs ingestion progress

The manifest `status` tracks content production only. Learner progress
(todo/started/done, scores) is app runtime state, keyed by the stable lesson
ID, and must survive re-ingestion and reordering. See course-screen.md.

## Model

The agent is the ingestion engine. `ingest-runbook.md` is the canned subagent
handoff: "ingest `<id>`" fills the slot and launches it. Every run ends at the
review gates — orchestrator review, then user approval — before any commit;
batch mode later feeds the same gates instead of bypassing them. Nothing
commits until everything has been looked at.

Batch mode (scripted, unattended) is a later step once the spec shape is
stable, and can reuse stage-1 transcripts without re-reading the PDF.
