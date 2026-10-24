# German word screens

Mock-ups are in `mockups/`. Epic: #457.

## Setup (three steps)
Mock-ups: `LangA`, `LangB`, `LangC`.
1. **Welcome** (`LangA`): "Learn German words as you read", three short points (offline meanings, review on Today, optional screen picks). Buttons: "Set up German", "Not now".
2. **Choose what to turn on** (`LangB`): four switches, each with a privacy note.
   - Offline dictionary: works without internet, nothing leaves the phone (on by default).
   - Look up online: sends the word you pick to Wiktionary (off).
   - Pick words from the screen: needs one permission, reads the screen only when pressed (off).
   - Nudges and pause in German: only changes wording (off).
   Buttons: "Continue", "Back". Note: "You can change this any time in German settings."
3. **Screen picks permission, optional** (`LangC`): explains the accessibility access and how it works in three lines, and says nothing is stored or sent. A box says what stays off if skipped ("You can still save words by typing them in the drawer"). Buttons: "Open accessibility settings", "Skip for now".

A progress bar of three segments is shown on every step.

## Review
Mock-ups: `ReviewFront`, `ReviewAnswer`.
- Top: back link, "3 left · 13 today", thin progress bar.
- Card: question first (`ReviewFront`). After reveal (`ReviewAnswer`): article and meaning label, the word large, an example sentence with its translation.
- Three buttons: **No**, **Almost**, **Knew it**. Each shows the next interval under its name ("Again tomorrow", "Same box", "Box 3 · in 4 days").
- Footer: "Add to list".
- Empty state: nothing due: "All done" with a link to the Words page.

## Add words to list
Mock-up: `AddToList`.
- Header: Cancel, "Add to 'Kitchen'". Title "Add words", line "Due words first · 7 not in this list".
- Search field, then rows with a tick box, the word and its meaning ("plate · due now"). Due words come first.
- The Add button is sticky at the bottom and shows the count ("Add 1 word"). It stays visible while the list scrolls.
- Empty state: all words already in the list: "Every word is in this list".

## Word lists
Mock-up: `Lists`.
- Title "Word lists" with a count line ("10 words · 3 lists") and "+ New".
- "All words" row: total and due today.
- A card per list: name, word count, "1 due" or "all done".
- Built-in card "From screen picks": grouped by where the words were saved.
- Empty state: "Lists keep your words in groups. A word can sit in more than one list." with "+ Add".

## Edit word
Mock-up: `EditWord`.
- Header: Cancel, "Edit word", Save.
- Fields: Word, Meaning, Example.
- Lists: chips, ticked when the word is in the list ("Small talk"), "+ name" to add.
- Progress line: box, next review date, review count ("Box 2 · next review Mon 26 Oct · 6 reviews").
- "Delete word" is a separate outlined button at the bottom, away from Save.

## Conjugation
Mock-ups: `Conjugation`, `ConjugationSummary`.

**Quiz**
- Header: back link with the verb ("machen"), tense and position ("Präsens · 4 of 6").
- Progress: one segment per form. Green = right, red = wrong, grey = current, dark = not yet asked.
- Prompt: "Conjugate 'machen'", the pronoun large ("wir"), the meaning.
- Four options in a 2x2 grid. The user picks one, then checks. A wrong pick is marked red.
- Correct form line below the options: "Not quite. The answer is machen." (a right answer shows a tick and a short line).
- "Continue" moves to the next form. Source line: Wiktionary, CC BY-SA.

**Summary**
- "First try" score ("5 of 6") and the note "Missed forms come back until you get them right."
- Grid of all forms (ich, du, er/sie/es, wir, ihr, sie/Sie) with the form and a tick, or a cross with the miss count.
- Buttons: "Practise er/sie/es again" (retry the misses), "Done".
