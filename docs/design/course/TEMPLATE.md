# Lesson spec: L<nn> — <display name from manifest grammar column>

- **id**: L<nn> — stable for the life of learner data
- **level**: B2 | C1 | B2+C1 — the manifest badge. Per-RULE level below
  overrides it: a B2+C1 chapter mixes B2 and C1 rules, and drill rounds /
  next-up must hold C1 rules back from a B2 learner.
- **book reference**: printed page(s); transcript `.course-pages/md/L<nn>-p<ppp>.md` (not in repo)
- **builds on**: L<nn> of the other book when this deepens an already-ingested
  lesson (plain-text reference; render as a link only when that lesson exists)
- **maps to drills**: <existing topic keys> — NEW rules: <list or none>
- **status**: ingested

## Outline (the book's pedagogical sequence, our content)

1. Presentation — what is presented, our wording.
2. Recognition — the pattern-spotting step.
3. Guided practice — exercise pattern shapes (gap, choice, word order).
4. Use — production task shape.
5. Summary — forms table / one-look overview.

## Rules

### <rule name> (<existing topic rule IDs | NEW: proposed id>)

- **rule (pre-answer)**: our short text, no item-specific form named.
- **level**: B2 or C1 — from the book's per-rule badge; defaults to the
  manifest level when the badge is chapter-wide.
- **forms table**: built from our items; caption rules same as drill-content.
- **examples**: each marked `new` or `pool:<topic>`; at least one `new`.
- **miss lines**: only where a wrong form has a real reason.

(copy nothing from the transcript; every German sentence here is ours)

## Exercise patterns (shape only)

- <pattern>: what the learner does; items drawn from app pools or written new.

## Drill linkage

- rule → existing drill rules / proposed NEW IDs.
- item-pool additions needed (which nouns, which pronouns, which verbs).

## Checks

- [ ] coverage: every rule and pattern on the spread is mapped or flagged with why
- [ ] rewrite: no sentence from the book appears in this file
- [ ] mapping: every rule maps to an existing topic or is NEW with a valid ID
- [ ] table fidelity: verifying pass done in stage 1 (date/round noted)
- [ ] IDs: lowercase ascii (ae/oe/ue/ss), hyphens, no answer in any ID
