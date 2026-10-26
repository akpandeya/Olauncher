# Drills UX: menu, rounds and summary

Part of epic #532 (German drills). This is the agreed design. It changes how
drills are started, run, summarised and ranked. It does not add new drill content.

## Summary

- A Drills card on Today and a Drills button on Words give a one-tap way in.
- A full-screen hub lists the 9 drill types, weakest first.
- A round is 10 shuffled items: a choice round, then a typed round for types that keep one.
- A near-miss spelling gets one retry instead of a wrong mark.
- The summary lists every item with a tick, a cross or an amber mark.
- Ranking uses a smoothed score over decayed counts, so old rounds fade.
- The drawer gets an optional search command only. No permanent rows.

Today the entry point is a popup picker (`openDrillsPicker` in
`app/src/main/java/app/olauncher/ui/words/WordsFragment.kt`).

## Entry points

1. Today card, "Drills". Shown whenever German is on (same rule as the Words card,
   `WordsCardLogic.isAvailable`).
   - Default line: "Suggested: <type> · Start 10".
   - After a round: "Last round: <type> · <result>", for example "7 of 10".
   - Tap starts the suggested round. The card is a standing offer, not a to-do count.
2. Words page header. A visible "Drills" button next to the title. The title tap
   still opens the Drills hub (today it opens the popup picker). The long-press menu
   entry "Drills" also opens the hub.
3. Drawer search command. See the Out of scope and optional notes below.

The suggested type is the same one the hub shows as "Practise weakest", or the last
type when no type has a score yet.

## Hub

A full screen, not a dialog. It uses the same frame as the drill screen: scrim
background, 24dp side padding, a scrolling body.

Top of the screen, two suggestion buttons:

- "Continue: <last type>" starts a round of the last type played.
- "Practise weakest: <type>" starts a round of the lowest-ranked type that has a score.

Below, the 9 types ordered weakest first. Types never played go in a group labelled
"New", after the ranked ones. Each row shows:

- the type name (the existing `drill_type_*` strings),
- the score, shown as a percentage once the type has about 2 rounds, else "1 round",
- the count of rounds played,
- a thin bar for the score.

A row tap starts a 10-item round of that type. No sub-menu.

## Rounds

Every round has 10 items drawn from the type, shuffled. When a type has fewer than
10 items, the round uses all of them.

Flow: choice round over the 10 items, then a typed round over the same 10 items, for
types that keep a typed round. Choice-only types end after the choice round.

| Type | Round shape |
|------|-------------|
| Prepositions | choice, then typed |
| Pronouns | choice, then typed |
| Articles | choice, then typed |
| Plurals | choice, then typed |
| Verb + preposition | choice, then typed |
| Separable verbs | choice, then typed |
| Case endings | choice only |
| Adjective endings | choice only |
| Word order | choice only (the tap-to-arrange view) |

The agreed rule is "typed rounds stay for nouns, prepositions and verb forms". Articles
and plurals count as nouns. Pronouns, verb + preposition and separable verbs count as
verb forms. See Open questions if that mapping needs a check.

A per-type flag in the drill type table says typed or choice-only.

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

The drill summary replaces the single score line. The header keeps the counts, for
example "7 right · 1 almost · 2 wrong".

Below it, a list of all 10 items. Wrong items come first, then almost, then right.
Each row has:

- a mark: green tick for right, red cross for wrong, amber for almost,
- the prompt, with the learner's answer when it was wrong or almost.

Under each red item: the correct answer and its one-line rule (the existing `hint`
field of `DrillItem`).

Buttons, in order:

- "Another 10" (primary): a new shuffled round of the same type.
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
- The typed answer is the main evidence. A choice answer can be a guess, so for types
  with a typed round, typed results weigh most. The exact weights are for the ranking
  issue to settle. Choice-only types use choice results.
- Show a number only after about 2 rounds. Before that, show "1 round".
- An abandoned round counts its given answers, but only when at least 3 were given.
  Items not reached are not counted.

Worked example. A learner scores 7 of 10 every round (70% raw):

| Rounds | asked | right | score |
|--------|-------|-------|-------|
| 3 | 24.4 | 17.1 | 71% |
| 10 | 44.6 | 31.2 | 70% |
| 50 | 50.0 | 35.0 | 70% |

The decayed total tops out at 50 items, so recent rounds always carry the weight.

Same learner, 10 of 10 every round:

| Rounds | asked | right | score |
|--------|-------|-------|-------|
| 3 | 24.4 | 24.4 | 96% |
| 10 | 44.6 | 44.6 | 98% |
| 50 | 50.0 | 50.0 | 98% |

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
- New: a typed or choice-only flag per drill type, and a near-miss function next to
  `checkTypedAnswer` (Context-free Kotlin with unit tests).
- New: extra keys inside the `DRILL_SCORES` JSON. No new Prefs key is needed.
- Missing today: a shared list-row style for the summary. Propose one style,
  `DrillSummaryRow`, with `TodayCardBody` text and a 48dp minimum height.

## Open questions

These are not decided.

1. Does the summary list every item, or only the misses? Listing all is more
   complete. Listing misses is shorter on a phone.
2. Does "almost" keep its own amber colour, or join red? Amber is clearer. Red is
   simpler and matches "not first try".
3. Do the drill summary and the review summary share one component, or two that look
   alike? One component is less code but ties two screens together.
4. Is the type mapping for typed rounds right (pronouns, verb + preposition and
   separable verbs as verb forms)?

## Out of scope

- New drill content or new drill types.
- Text to speech (parked in #532).
- Changing how drill answers feed the review schedule.
- Persistent drill rows in the app drawer. Only an optional search command is
  planned: typing "drill" shows one suggested row and an "All drills..." row. It is
  built last, and only if the rest ships.
