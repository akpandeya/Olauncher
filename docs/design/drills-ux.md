# Drills UX: menu, rounds and summary

Part of epic #532 (German drills). This is the agreed design. It changes how
drills are started, run, summarised and ranked. It does not add new drill content.

## Summary

- A Drills card on Today and a Drills button on Words give a one-tap way in.
- A full-screen hub lists the 9 drill types, weakest first.
- A round is 5 to 8 shuffled items, at most about 12 interactions, 2 to 3 minutes.
- First exposure is always a tap question. Typing is a graduation, not a second round.
- Wrong answers are corrected inline: red flash, one-line rule, one tap to continue.
- A near-miss spelling gets one retry instead of a wrong mark.
- The summary is an adaptive screen: every item listed, misses at the top, scrolling when long.
- Ranking uses a smoothed score over decayed counts, so old rounds fade.
- The drawer gets an optional search command only. No permanent rows.

Today the entry point is a popup picker (`openDrillsPicker` in
`app/src/main/java/app/olauncher/ui/words/WordsFragment.kt`).

## Entry points

1. Today card, "Drills". Shown whenever German is on (same rule as the Words card,
   `WordsCardLogic.isAvailable`).
   - Default line: "Suggested: mixed (A · B) · Start 5" — the mixed round of the
     2 to 3 weakest types (see Mixed rounds below).
   - After a round: "Last round: <type, or A · B · C for a mix> · <result>", for
     example "5 of 6".
   - Tap starts the suggested round. The card is a standing offer, not a to-do count.
2. Words page header. A visible "Drills" button next to the title. The title tap
   still opens the Drills hub (today it opens the popup picker). The long-press menu
   entry "Drills" also opens the hub.
3. Drawer search command. See the Out of scope and optional notes below.

The suggested round is the same mix the hub shows as "Practise weakest" (the 2 to 3
weakest types), or the last round's type or mix when no type has a score yet.

## Hub

A full screen, not a dialog. It uses the same frame as the drill screen: scrim
background, 24dp side padding, a scrolling body.

Top of the screen, two suggestion buttons:

- "Continue: <last type or mix>" starts a round of the last type (or mix) played.
- "Practise weakest: <A · B · C>" starts the mixed round: a round of the 2 to 3
  lowest-ranked types that have a score.

Below, the 9 types ordered weakest first. Types never played go in a group labelled
"New", after the ranked ones. Each row shows:

- the type name (the existing `drill_type_*` strings),
- the score, shown as a percentage once the type has about 2 rounds, else "1 round",
- the count of rounds played,
- a thin bar for the score.

A row tap always starts a 5 to 8 item round of that single type. Mixed rounds come
only from the suggestion buttons, the Today card and the drawer (see Mixed rounds
below). No sub-menu.

## Rounds

Decided (#639, device test 2026-10-26): a round must be short enough to finish. The
old shape — a choice round over 10 items followed by a typed round over the same 10,
with requeues inside each — grew to 20+ question screens and 50 to 60 interactions.
That is designed-in give-up risk.

New model: **5 to 8 items per round, at most about 12 interactions, 2 to 3 minutes.**
The summary button says "Another 5". Both halves of the old shape fold into one short
round: first exposure is always a tap, typing becomes a graduation inside the same
round, and corrections happen inline instead of in a separate step.

An interaction is one learner input: a tile tap (a choice or a match pair), a whole
word-order, word-bank or listen-and-tap answer however many tiles it takes, a typed
answer submitted (a close-spelling retry is part of the same interaction, not a second
one), or a Continue tap after a flashcard or an inline correction. A 5-item round with
one flashcard and one correction lands around 7 interactions.

### Round shape

Every round draws 5 to 8 items — from one type, or from the 2 to 3 weakest types for
a mixed round — and shuffles them. When a type has fewer than 5 items, the round uses
all of them. Nothing is requeued inside a round; misses come back through
"Practise misses" in the summary, as today.

### Format ladder

Each item climbs a ladder as it is answered correctly. The round picks each item's
format from where the item sits on the ladder:

| Rung | Format | Who gets it |
|------|--------|-------------|
| 1 | Flashcard (see the answer, no question) | Brand-new items, shown before their first tap — which comes later in the same round or in the next one |
| 2 | Tap (multiple choice) | First exposure — always the first question format for an item |
| 3 | Match the columns · true-false · odd-one-out · word-bank tiles · listen-and-tap (TTS) | Items answered right once — variety rung |
| 4 | Typed | Graduation only: items answered correctly twice across rounds, max 3 typed per round |

Rules:

- First exposure is always tap. An item is never typed before it has been answered
  correctly twice across rounds. This replaces the per-type typed/choice-only flag:
  typing is per item, so every type can eventually type.
- At most 3 typed questions per round.
- No two consecutive questions of the same format. The round shuffles until this holds.
- Word order keeps its tap-to-arrange view as its tap rung.
- Listen-and-tap needs TTS, which stays parked in #532; it is in the ladder as the
  planned rung, not new content.
- A near-miss spelling still gets one retry (#622), unchanged (Close-spelling rule).
- Optional speed finale (timed tap) may follow the last question; it is not part of
  the first screen work.

### Inline corrections

A wrong answer does not leave the screen and has no separate correction step:

1. The chosen tile flashes red briefly.
2. A one-line rule (the item's existing `hint`) appears under the question.
3. One tap on "Continue" moves to the next question.

The miss is recorded at the moment of the wrong answer, so an abandoned round
(#624) already carries it, once at least 3 answers were given (per Ranking). No
requeue inside the round.

### Mixed rounds

"Practise weakest" (hub), the Today card and the drawer suggest the same mixed
round: sample the 2 to 3 weakest types, interleave their items, and label each
question with its type. Row taps start single-type rounds. Per-type scores keep
flowing through the review feed (#608) unchanged — an item answered in a mixed round
scores for its own type.

### Old mapping, superseded (#639)

The per-type typed/choice-only table is superseded by the ladder. Word order keeps
its tap-to-arrange view; all other types gain typed questions only through item
graduation. The old table, for reference:

| Type | Round shape (old) |
|------|-------------------|
| Prepositions | choice, then typed |
| Pronouns | choice, then typed |
| Articles | choice, then typed |
| Plurals | choice, then typed |
| Verb + preposition | choice, then typed |
| Separable verbs | choice, then typed |
| Case endings | choice only |
| Adjective endings | choice only |
| Word order | choice only (the tap-to-arrange view) |

## Close-spelling rule

An exact match is still the only way to be right. The check is
`checkTypedAnswer` in `app/src/main/java/app/olauncher/data/store/ConjugationQuiz.kt`.

A near miss is one of:

- one letter added, missing or wrong,
- one umlaut-equivalent difference (a and ae, o and oe, u and ue, ss and the sharp s).

On a near miss:

1. Show "Very close. Check the spelling and try once more."
2. Do not mark the item wrong. Do not reveal the answer.
3. The input stays so the learner can edit it.
4. A second miss on the same item counts as a miss and shows the answer as today.
5. A retry that is exact counts as "almost" in the summary, not as right.

## Summary screen

Decided: an adaptive screen. It replaces the single score line. The header keeps the
counts, for example "4 right · 1 almost · 1 wrong", and stays pinned.

Below it, a list of all items in the round (5 to 8). The misses
sit at the top, so they are visible without scrolling. When the list is long, the
whole screen stays readable: the header and the buttons stay in place and the list
scrolls between them.

Sort order, lowest score first:

- The data has no score per item. `DrillScores` keeps a score per drill type only, and
  `DrillItem` has no score field. So the sort uses each item's first-try result
  (`DrillQuiz.firstAttempts`), misses first: wrong, then almost, then right.
- Items with the same result keep round order.

Each row has:

- a mark: green tick for right, red cross for wrong, amber for almost (the
  `accentSage`, `accentRose` and `accentSand` colours below),
- the prompt, with the learner's answer when it was wrong or almost.

Under each red item: the correct answer and its one-line rule (the existing `hint`
field of `DrillItem`).

Buttons, in order:

- "Another 5" (primary): a new shuffled round of the same type, or the same mix
  for a mixed round.
- "Practise misses": a round of only the wrong and almost items.
- "Done": back to the hub (or Today if the round started there).

The review summary (`ReviewFragment`, `summaryContainer`) uses the same list design.
Today it shows only "Done · N knew · N almost · N again". It gets the same rows and
marks: knew is green, almost is amber, again is red.

## Ranking formula

Per type, keep decayed counts. After each round:

```
asked = asked_old * 0.8 + items_in_round
right = right_old * 0.8 + right_in_round
score = (right + 3) / (asked + 4)
```

- The +3 and +4 pull a new type toward 75%, so one lucky round does not top the list.
- Typed answers (graduated items) weigh more than tap or variety-rung answers.
  Flashcards are not scored. The exact weights are for the ranking issue to settle.
- Show a number only after about 2 rounds. Before that, show "1 round".
- An abandoned round counts its given answers, but only when at least 3 were given.
  Items not reached are not counted.

Worked example. A learner scores 3.5 of 5 every round (70% raw):

| Rounds | asked | right | score |
|--------|-------|-------|-------|
| 3 | 12.2 | 8.5 | 71% |
| 10 | 22.3 | 15.6 | 71% |
| 50 | 25.0 | 17.5 | 71% |

The decayed total tops out at 25 items (5 per round ÷ 0.2), so recent rounds always
carry the weight. The +3/+4 prior is twice as large against that 25-item cap as it
was against the old 50-item cap of 10-item rounds, so early scores stay pulled
toward 75% for longer.

Same learner, 5 of 5 every round:

| Rounds | asked | right | score |
|--------|-------|-------|-------|
| 3 | 12.2 | 12.2 | 94% |
| 10 | 22.3 | 22.3 | 96% |
| 50 | 25.0 | 25.0 | 97% |

Storage: the new fields live inside the existing `DRILL_SCORES` JSON
(`DrillScores` in `app/src/main/java/app/olauncher/data/store/DrillScores.kt`).
Keep the keys `asked` and `firstTryRight` as they are. Add new keys beside them, for
example `askedDecayed`, `rightDecayed` and `rounds`. Old data decodes unchanged, and a
type with only old keys seeds its decayed counts from them. No Prefs key is renamed.

## Repo design elements used

Colours (`app/src/main/res/values/colors.xml`, dark values in `values-night`):

- `accentSage` for right (green tick), `accentSand` for almost (amber), `accentRose`
  or `danger` for wrong. These match the review buttons: No is rose, Almost is sand,
  Knew is sage.
- `todayScrim` for the screen background.
- `todayPanel`, `todayTile`, `todayTileStroke`, `inkMuted` for surfaces and dividers.
- `readableCard` is the card colour over photo wallpaper (see `ReadableScrim.kt`).

Dimens (`dimens.xml`):

- `today_touch_min_height` (48dp) for every tap target.
- `quiz_choice_min_height` (56dp), `quiz_cell_padding`, `quiz_cell_margin`,
  `quiz_segment_gap` for choice tiles and the segment bar.
- `radiusSmall`, `radiusMedium`, `radiusPill`, `strokeHairline`, `strokeThin` for shapes.
- `today_label_size`, `today_body_size`, `today_stat_size` for text.

Styles (`styles.xml`):

- `TodayCardLabel`, `TodayCardStat`, `TodayCardBody`, `TodayCardBodyLight`,
  `TodayCardBodyBold` for text on the drill screens.
- `TodayButton` for the primary and suggestion buttons. `TodayButton.Danger` is not
  needed here.
- `TodayChip`, `TodayChip.Small` for small labels such as the "New" group tag.
- `TextLarge`, `TextSmall`, `TextSmallBold`, `TextSmallLight` for Words and review.

Drawables (`app/src/main/res/drawable`):

- `today_panel` for the hub rows panel and the summary list panel.
- `quiz_choice` and `quiz_choice_wrong` for choice tiles and the typed field.

Layouts and components:

- `fragment_drill.xml`: the question card, `TodayButton` footer, segment bar and
  `quizCard` summary panel. The hub and the new summary reuse its frame.
- `fragment_review.xml`: `summaryContainer`, `tvSummary`, `btnDone`.
- `fragment_words.xml`: the footer row with `tvTitle` where the Drills button goes.
- `card_words.xml` and `WordsCard.kt` (in `app/src/main/java/app/olauncher/ui/today/cards/`):
  the pattern for the Drills card (label, one line, `TodayCardBodyBold` open action,
  hairline divider).
- `TodayCard.kt`, `TodayOrder.kt`, `TodayFragment.kt` in `ui/today/`: where a card is
  registered and ordered.
- `DrillTables.available()`, `DrillTables.labelRes`, `DrillScores`, `DrillQuiz`,
  `DrillType` under `app/src/main/java/app/olauncher/data/store/`.

## What is new

Everything below is the smallest addition. Nothing here replaces an existing token.

- New: `card_drills.xml` and `DrillsCard.kt`, built from the `card_words.xml` pattern.
- New: hub layout `fragment_drill_hub.xml` and a fragment, in the Navigation graph
  next to `action_wordsFragment_to_drillFragment`.
- New: a hub row layout (name, score, count, thin bar). The bar is a small custom
  view like `LeitnerBoxBarView`, using `accentSage` on a `todayChip` track.
- New: a summary row layout with a mark. Marks are tinted vector drawables
  (tick, cross, amber dot) coloured by `accentSage`, `accentRose`, `accentSand`.
  No new colour is needed.
- New: strings for the card lines, hub labels, the close-spelling message and the
  summary buttons. They go in `values/strings.xml` only.
- New: a per-item maturity count (correct answers across rounds) so typing is earned,
  and a near-miss function next to `checkTypedAnswer` (Context-free Kotlin with unit
  tests). No per-type typed/choice-only flag any more.
- New: extra keys inside the `DRILL_SCORES` JSON. No new Prefs key is needed.
- Missing today: a shared list-row style for the summary. Propose one style,
  `DrillSummaryRow`, with `TodayCardBody` text and a 48dp minimum height.

## Open questions

1. Do the drill summary and the review summary share one component, or two that look
   alike? One component is less code but ties two screens together.

Decided, no longer open: the typed or choice-only mapping (Rounds — superseded in
#639 by the per-item format ladder), the adaptive summary with misses on top
(Summary screen), and the green, red and amber marks.

## HTML mock

`drills-round-mock.html` (next to this file) shows the redesigned round: the ladder
formats, an inline correction, the typed graduation and the "Another 5" summary. It
is self-contained (no remote fonts or scripts) and uses the repo tokens listed above.
Screen work starts only after the mock is approved.

## Out of scope

- New drill content or new drill types.
- Text to speech (parked in #532).
- Changing how drill answers feed the review schedule.
- Persistent drill rows in the app drawer. Only an optional search command is
  planned: typing "drill" shows one suggested row and an "All drills..." row. It is
  built last, and only if the rest ships.
