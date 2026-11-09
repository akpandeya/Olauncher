# Course screen: progress, next-up, collapsible header (draft)

Learner-facing surface for the ingested course. Draft: no mock yet, no code.
Supersedes the hub's Grammar Rules button as the front entry to grammar.

## What this fixes

- Progress is invisible today: rules are a flat index, no sense of done/next.
- Finding the next thing to learn costs scrolling through a list.
- Expensive content (header) can't get out of the way.

## The daily loop needs no scrolling

```
┌──────────────────────────────────────┐
│ header (pinned)                       │  ← tap ^ to minimize
│ Grammatik  ▓▓▓░░ L14 of 91 · A1→B1   │
└──────────────────────────────────────┘
┌──────────────────────────────────────┐
│ NEXT UP (card)                        │  ← Start: the only tap
│ Artikel: definit, indefinit, kein     │    needed to keep going
│ 2 Regeln · 4 Drills · A1              │
└──────────────────────────────────────┘
  (body below: units, collapsed)
```

- The next-up card is *the* default view. After a lesson is marked done, the
  card advances in place to the next lesson — no scroll, no navigation.
- Partnerseiten (P01–P11) surface as review checkpoints in the card slot: a
  unit's Partnerseite becomes the next-up item when the unit's lessons are done.
- "Start" opens the lesson read page; its footer button chains straight to the
  drill round limited to that lesson's linked rule IDs.

## Collapsible header

- Expanded (default when returning): course progress — lessons done / total,
  level band (A1→B1), current unit name.
- Minimize state: tap the header's chevron, or it auto-minimizes on scroll
  down. Minimized = one line: progress bar + "L14". Tap to re-expand.
- The minimize state persists per app start.

## Progress marking

- Rows show state: ✓ done (with done date), ▶ started, blank todo.
- Unit rows: title + `x/y` notches; a fully done unit collapses to one
  summarized row (can re-expand to browse).
- Done lessons stay reachable: reopen in read mode; their drills remain
  linked for review (normal review schedule, not a second system).

## Lesson detail (read mode)

Teaching text from the lesson spec: outline steps, per-rule explanations,
forms tables, examples. Same trim rules as drill help: nothing names an item's
answer. Footer: "Üben" → drill round for the lesson's rules, then back to the
course screen with the next-up card advanced.

## Data

- Per-lesson progress keyed by the stable row id (`L14`, `P03`):
  `{state: todo|started|done, doneAt, last_score_pct}`.
- Stored in the existing German store; included in Backup export/import.
- Re-ingestion or spec rewrites must never reset progress (key stays `L14`).
- Ingestion `status` (lessons.tsv) is content production; learner state is
  runtime. They share only the id key.

## Open questions

- Where the entry sits in the drill hub: does the course screen *replace*
  the Grammar Rules button, or sit above it as a new top card?
- Whether retired rules (old drill topics without a course lesson yet) keep a
  fallback entry during the transition.
- Ties into AI practice: offer a chat about a lesson ("Warum?") from the read
  page, reusing the existing Ask-AI thread model?
