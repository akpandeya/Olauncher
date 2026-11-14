# Ingestion runbook: canned handoff for one lesson

One file is the entire instruction set for ingesting one lesson, so "ingest L15"
is a one-line ask. Per run, the orchestrator fills the slot with the lesson row
and launches a subagent. Nothing is committed until the orchestrator review AND
the user approval have both passed.

## Usage

- **"ingest `<id>`"** — orchestrator reads the row from `lessons.tsv`, fills the
  slot, launches the runbook as a subagent task, then runs the review gate.
- **Batch mode (later)** — the same file, run unattended, appends each report to
  a review queue; the user approves the queue before PRs open. Auto runs stop at
  the same gate; approval is never skipped.
- Pilot-proven by L14 (see the known-failure list below).

## Slot

- **id**: the lesson to ingest, filled by the orchestrator (`L15`, `P03`, …)
- **book**: source PDF and printed↔PDF offset, filled by the orchestrator from
  the book's manifest — book 1: `.course-pages/grammatik-aktiv.pdf`
  (offset: PDF index + 3 = printed page, re-verify); book 2:
  `.course-pages/grammatik-aktiv-b2c1.pdf` (offset: printed page = PDF page,
  calibrated 2026-11-14 on its ToC; re-verify, don't inherit)
- **page budget**: the orchestrator sets a max page-read count for the spread;
  the report must state actual pages read so gate 1 can check it. Spreads over
  3 pages must use a transcript cache (see `ingestion.md` stage 1).
- **row fields** (read from the book's `lessons.tsv` / `lessons-b2c1.tsv` at
  launch): title, `grammar` (this is the display name), printed pages, slug,
  drill topic list
- **output file**: `lessons/<id>-<slug>.md`
- **ordering**: lesson order comes from the manifest row position, never from
  sorting ids (as text, `L100` sorts before `L81`).

## Subagent task text (the canned handoff)

> You work in /Users/dipukumari/code/personal/Olauncher. Ingest ONE lesson of a
> German grammar course from a local PDF. Do NOT commit or stage. Modify only
> your output file and (on instruction) the manifest row.
>
> **Read first**: docs/design/course/ingestion.md (pipeline, privacy rules),
> TEMPLATE.md (output shape), LESSON row in lessons.tsv
> (`<ID>`: grammar "<display>", pages <pages>, slug <slug>, map to <topics>),
> and the existing drill-topic spec(s) listed in its row under
> docs/design/drill-content/ for rule-ID conventions.
>
> **Source**: .course-pages/grammatik-aktiv.pdf. Calibrate the printed↔PDF
> offset before the first read (L14 calibration was PDF index + 3 = printed
> page; re-verify, don't inherit). Read only what you need: the lesson spread,
> one re-read for verification, ToC pages only if recalibrating.
>
> **Two-pass rule**: after distilling, re-read the same spread and verify every
> grammar fact character by character: table cells, endings, umlauts, example
> frames. Anything ambiguous → mark UNSURE in the spec, never guess.
>
> **Copyright rules (strict)**: explanation prose, examples and miss lines are
> written fresh; grammar FACTS may match. Each German example sentence marked
> `new` or `pool:<topic>`. Display name = the manifest grammar column, never
> the book's sentence title. The file must contain no sentence from the book.
>
> **Format constraints**: pre-answer rule text ≤ 170 chars, never names the
> item-specific form; forms tables ≤ 3 rows × 4 columns; IDs lowercase ASCII
> with hyphens (ae/oe/ue/ss), never naming an answer; outline follows the
> spread's real pedagogical sequence.
>
> **Report format (mandatory, ≤ 40 lines)**: calibrated offset; files written;
> table rows for EVERY table in the spec, listed cell by cell; rule inventory
> (mapped vs NEW vs `new` vs `pool:` counts); the five checks pass/fail;
> UNSURE list; anything you skipped with why.

## Review gate 1 (orchestrator, before any commit)

1. Read the spec diff-level: table rows line by line (the slides' facts live
   there).
2. Self-check by the subagent is a claim, not evidence — the report's row
   listing plus a spot read of the file is the check.
3. Mapping sanity: every rule → existing drill topic or valid NEW ID.
4. Costs: direct PDF reads stay within the reported page count.

## Review gate 2 (user, everything before approving)

The user sees the spec file, the report summary, and any mock the review
produced. PR opens only after a clear yes. Branch naming per pipeline:
`course/<id>-<slug>`.

## Known failure modes (recorded from the L14 pilot)

- **Column-order transplant**: the book's article tables run maskulin |
  neutrum | feminin; our specs use m | f | n | pl. Rows transplanted without
  remapping produced wrong cells. This is the #1 check in review gate 1.
- **Self-check theatre**: a subagent can claim a two-pass verification pass
  without it being true. Evidence in the report, not the claim.
- **Allegedly read-but-not-read pages**: ask the report to name the exact page
  indices read; a missing ToC/verification read is a red flag.
