# Drill question card and help card: merged design (follow-up to #639, issue #651)

Merges `design-opus.md`, `design.md` (GLM) and `design-agy.md` (agy), with fixes from the
Opus review. Design stage only: no code, no scoring change. The mock is `drill-help-card-mock.html`
next to this file.

## Summary

- The question card sits at the bottom of the body, just above the pinned footer. Its frame
  never moves: only its contents change, with the existing #640 slide.
- Help opens in one slot above the prompt. It grows upward, so the prompt and the answer tiles
  stay put whether help is open or closed.
- Before an answer, help teaches the general rule and shows the whole contrast set. It never
  says which form this item needs. After a miss, the card names the right form and why.
- Gender has three fixed colours, on the German text only. Case has no colour: it is a labelled
  row and an underlined ending. Plural is labelled "pl", not coloured.

## 1. Placement

Current problem: `questionCard` is the first child of the scrolling body, so with short content
it sits at the top, over the photo, far from the thumb and the footer.

In `fragment_drill.xml`:

- The ScrollView's inner LinearLayout gets `android:gravity="bottom"` (it already has
  `fillViewport="true"`). Short content sits next to the footer. Long content (8-tile word bank,
  large font scale) still scrolls, and the screen scrolls to the bottom on each new question.
- Order inside the card, top to bottom: help slot, prompt, answer view (choice grid, typed field
  or word order).
- The card frame stays fixed; only the interior re-binds with the #640 slide/fade.
- The help slot is capped at about 40% of the body height. Taller help scrolls inside its own
  slot, so the prompt and tiles stay on screen.
- The inline correction adds no line under the tiles. It opens the help slot in its "after a
  miss" state, and the footer button becomes "Continue". The tiles never move after a wrong tap.
- The card keeps `today_panel` over a `readableCard` background, so it stays legible on photo
  wallpaper (`ReadableScrim.kt`).

```
 header (pinned)      title · progress · segments
 ─────────────────
                      (empty space; help grows into it, capped)
 ┌ help slot ─────┐
 │ chips          │
 │ rule           │
 │ forms table    │
 │ ✗ miss line    │  ← only after a miss, directly above the prompt
 └────────────────┘
 prompt               Ich lege das Buch auf ___ Tisch.
 tiles  [den][dem]    ← fixed position
        [der][das]
 ─────────────────
 footer (pinned)      Check / Continue
```

## 2. When help shows, and what it may say

| Moment | Help slot | Item-specific answer shown? |
|---|---|---|
| Rung 1, flashcard "take a look" (new item) | Open, full card | Yes: it is a reading step, so the correct form is bold and the row is highlighted |
| Rung 2, first tap exposure | Open, full card | No |
| Later tap, variety or typed rungs | Collapsed: one cue line (the existing `hint`) plus a "Why?" button | No |
| "Why?" tapped before answering | Open, full card | No |
| Right answer | Unchanged; auto-advance as #640 | — |
| Wrong answer | Open; the ✗ miss line is the last line, directly above the prompt | Yes, in the miss line only |

**The rule before an answer.** Pre-answer help teaches the general rule of the pattern and shows
the whole contrast set with equal weight. It never names the form this item needs, and its chips
and cue lines name no item-specific gender or case. The learner must still decide which case the
situation calls for, and which gender the noun has.

**The rule after a miss.** The ✗ line names the wrong choice, says why it is wrong, and gives
the right form. This follows Duolingo's post-answer feedback on the specific mistake.

"Why?" adds no interaction to the round budget. A learner who does not need it sees one line.

**Scoring.** A "Why?" tap before answering counts toward the score: a right answer after "Why?" is
recorded as a weaker right answer, so the drill still gets a signal from it. The exact weighting
belongs to the scoring issue; this design only requires that the tap is recorded.

## 3. Help card anatomy

1. **Rule chips** (`TodayChip.Small`): the trigger word and the case, for example `Wohin?` and
   `Wo?` shown together, or the gender label `der · masculine` in the table header. Pre-answer
   chips show the pattern, never this item's answer.
2. **Rule**: 1–2 sentences of plain English, at most about 170 characters, `TodayCardBody`.
3. **Forms table**: at most 3 rows × 4 columns. Columns are the genders (m, f, n, pl) or the
   question words. It shows the forms of the contrast set. Nothing is hidden.
4. **Example sentence**: one German sentence with the changing ending bold and underlined.
   Shown in the flashcard state and after a miss, never before a tap answer.
5. **Miss line** (after a miss only): rose ✗ mark, then the wrong choice and why it is wrong, in
   ink. The German words in this line carry no gender colour, so the rose and the red for *die*
   never sit side by side on the same word.

Content model (from the Opus design): the rule and table belong to a **rule**, not to each item
(for example `two-way-auf`, `noun-e-feminine`, `city-nach-in-aus`). Items point to a rule id, and
the articles table is generated from gender and case in Context-free Kotlin. Out of scope here;
noted for the implementation issue.

## 4. Colour rules

### Gender (fixed hue, three colours)

| Gender | Light | Dark | Label |
|---|---|---|---|
| masculine | `#2F5D8A` blue | `#8FB3DC` | m |
| feminine | `#A23B3B` red | `#EC948A` | f |
| neuter | `#2F6B45` green | `#8CC79A` | n |
| plural | none (ink) | none (ink) | pl |

- **Colour follows the noun's gender column, not the article word.** The feminine dative *der* is
  red, and the masculine accusative *den* is blue. The colour tells the learner which noun
  gender a form belongs to; the article written next to it tells the case.
- Applied to German text only: the article and the changed ending. Never to tile fills, borders
  or the card frame.
- The article is always written, so colour is never the only signal. The table headers also
  carry the labels m, f, n, pl.
- Plural is not coloured. Its column header says "pl". This drops the violet from the Opus design,
  which had no source.
- New tokens: `genderMasc`, `genderFem`, `genderNeut` in `colors.xml` and `values-night`.

### Case (no hue)

- Case rows are labelled `Akk` and `Dat` in tables and chips, with the question word beside
  them. In prose, write "accusative" and "dative" in full.
- The active case row gets the `todayChip` background only when the answer is shown: the
  flashcard and the after-a-miss state. Before an answer, all rows have equal weight.
- The changing ending is bold and underlined (de**n**, de**m**) in example sentences and when
  the answer is shown. Before an answer, the forms in the table are plain, not bold.

### Correctness (unchanged)

`accentSage` for right, `accentRose` for wrong, `accentSand` for almost, on tile fill, border and
glyph. Rose is used for frames and the ✗ mark only. The German words in the miss line stay ink.

## 5. Worked examples

Three examples cover the issue's criteria: a gender-driven item (A), a case change with gender
inside it (B), and a preposition without gender (C). Each shows the pre-answer help and the
states after an answer.

### A. Gender-driven: Articles, new item

Prompt: `___ Brücke ist alt.` Answer **die**. Choices: der · die · das · den.

Pre-answer rule (flashcard): *Every German noun has one fixed gender, and the article has to
match it. Learn the article with the noun. Most nouns ending in -e are feminine (exceptions such
as der Junge).*

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |
| Akk | den | die | das | die |

The accusative row is shown so *den* has a place in the table. The item is a nominative subject,
so the nominative row is the one that applies.

Flashcard example: **Die** Brücke ist alt. (The answer is shown, since this is the reading step.)

Miss lines (one per wrong choice):
- ✗ *der*: masculine. Brücke is feminine, so die.
- ✗ *das*: neuter. Brücke is feminine, so die.
- ✗ *den*: accusative masculine. Brücke is feminine and the subject is nominative, so die.

### B. Case change with gender: two-way *auf* (Prepositions)

Prompt: `Ich lege das Buch auf ___ Tisch.` Answer **den**. Choices: den · dem · der · das.

Pre-answer rule: *auf is a two-way preposition. Movement to a place (Wohin?) takes the
accusative. A fixed position (Wo?) takes the dative. Decide which one the sentence describes.*

| | m | f | n | pl |
|---|---|---|---|---|
| Wohin? → Akk | den | die | das | die |
| Wo? → Dat | dem | der | dem | den |

Both rows have equal weight before an answer. The flashcard state highlights the row for the
answer and bolds **den**.

Example after the answer: *Ich lege das Buch auf den Tisch.* (movement, Wohin?) and
*Das Buch liegt auf dem Tisch.* (position, Wo?).

Cue line for later rungs: `auf + Wohin? → accusative`.

Miss lines:
- ✗ *dem*: the dative is for a fixed position (Wo?). Putting a book down is movement, so Wohin?
  → accusative.
- ✗ *der*: masculine nominative or feminine dative, neither of which fits here. Tisch is
  masculine and takes the accusative den.
- ✗ *das*: neuter. Tisch is masculine, so den.

### C. Preposition without gender: a city (Prepositions)

Prompt: `Ich fahre morgen ___ Berlin.` Answer **nach**. Choices: nach · in · aus.

Pre-answer rule: *Cities and most countries have no article. The preposition shows the direction
of the trip: nach for going there (Wohin?), in for being there (Wo?) and aus for coming from there
(Woher?).*

| | Wohin? | Wo? | Woher? |
|---|---|---|---|
| | nach Berlin | in Berlin | aus Berlin |

No gender colour and no article row: nothing in the answer depends on them, so the card stays
neutral.

Flashcard example: *Ich fahre nach Berlin. Ich wohne in Berlin.*

Miss lines:
- ✗ *in*: in marks being in a place (Wo?). Going there is nach.
- ✗ *aus*: aus marks coming from a place (Woher?). Going there is nach.

## 6. Implementation notes (for the implementation issue, not this stage)

- New tokens: `genderMasc`, `genderFem`, `genderNeut` (+ dark) and an underline style for case. No
  existing token changes.
- New per-rule content: `ruleId`, the rule text, the forms block, and the miss lines per distractor.
  The existing `hint` and `DrillHints` checks are unchanged. Pre-answer content must pass a
  check that it names no item-specific answer.
- Verify on device: two consecutive questions with different help heights keep the prompt and
  tiles at the same y, and the help slot caps at about 40% of the body.

## Decided

- A "Why?" tap before answering counts toward the score, as a weaker right answer (see §2).
- Plural is labelled "pl" in ink, with no fourth colour.
- Three examples (A, B, C) are shown in the mock.

## Open questions

None left for the design stage. Weighting of a "Why?" answer goes to the scoring issue.
