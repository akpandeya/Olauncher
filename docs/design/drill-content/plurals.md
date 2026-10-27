# Plurals drill: help-card content

**Count:** 8 rules. **Items covered:** 63 of 63 table entries, each exactly once. **UNSURE marks:** 0.

## Conventions in this file

- The plural form is the recall target. All eight rules share one pre-answer rule text and one forms table, so the card shown before an answer is the same for every plural item and cannot point at the item's pattern. The states after an answer (example, miss lines, hint) differ per rule.
- `first_exposure_rule` is omitted everywhere: a plural answer has no case or gender to name.
- `later_rung_cue` names no ending. The existing `hint` in `DrillPlurals.kt` is the pattern tag ("-e", "¨-er"), which names the answer pattern; the implementation should use `later_rung_cue` instead on later rungs.
- Choices are generated per noun by `buildChoices`, so each rule's miss lines are keyed by the wrong **pattern**. Every wrong pattern that `buildChoices` produces for an item of the rule has one line (checked against the generated choices); the form in brackets illustrates the pattern, and the drilled noun's forms replace it at render time.
- The plural article is always *die* and is already in the prompt, so no rule text or miss line teaches it.
- Saved dictionary words can also yield plural items, with no pattern tag. Each would join the rule whose pattern its dictionary plural matches.

---

## plural-e

**id**: `plural-e`

Author note, not learner-facing: one-syllable and other nouns that add -e (pattern tag "-e").

**items**: der Tag → die ___ (plur:tag) · der Hund → die ___ (plur:hund) · der Tisch → die ___ (plur:tisch) · der Monat → die ___ (plur:monat) · der Freund → die ___ (plur:freund) · das Jahr → die ___ (plur:jahr) · der Schuh → die ___ (plur:schuh) · das Boot → die ___ (plur:boot)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Es gibt viele Weg**e** nach Rom.

**miss lines**:
- ✗ the umlaut form (Täge for Tag): this noun keeps its vowel and adds plain -e: die Tage.
- ✗ the -er form (Tager): the added ending is -e here, not -er: die Tage.
- ✗ the umlaut-plus-er form (Täger): no -er and no vowel change, just -e: die Tage.
- ✗ the -n form (Tischn for Tisch): -n goes on nouns that already end in -e; this noun adds -e: die Tische.
- ✗ the -en form (Tischen): -en belongs to other noun groups; this noun adds -e: die Tische.

**flashcard_and_miss_hint**: Many masculine and neuter nouns add -e and keep the vowel (der Tag → die Tage).

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.

---

## plural-umlaut-e

**id**: `plural-umlaut-e`

Author note, not learner-facing: nouns that change the vowel and add -e (pattern tag "¨-e").

**items**: die Hand → die ___ (plur:hand) · die Stadt → die ___ (plur:stadt) · die Nacht → die ___ (plur:nacht) · der Stuhl → die ___ (plur:stuhl) · der Baum → die ___ (plur:baum) · der Ball → die ___ (plur:ball) · die Wand → die ___ (plur:wand) · der Zug → die ___ (plur:zug)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Meine F**ü**ß**e** sind müde.

**miss lines**:
- ✗ the plain -e form (Hande for Hand): the vowel changes too, a becomes ä: die Hände.
- ✗ the -er form (Hander): the ending is -e, and the vowel changes: die Hände.
- ✗ the umlaut-plus-er form (Händer): no -er, just the vowel change plus -e: die Hände.

**flashcard_and_miss_hint**: The vowel changes too: a→ä, o→ö, u→ü (die Hand → die Hände). Many one-syllable feminines do this.

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.

---

## plural-er

**id**: `plural-er`

Author note, not learner-facing: nouns that add -er (pattern tag "-er").

**items**: das Kind → die ___ (plur:kind) · das Bild → die ___ (plur:bild) · das Kleid → die ___ (plur:kleid) · das Licht → die ___ (plur:licht) · das Ei → die ___ (plur:ei) · das Feld → die ___ (plur:feld) · das Lied → die ___ (plur:lied)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Die Vögel bauen neue Nest**er**.

**miss lines**:
- ✗ the -e form (Kinde for Kind): this noun takes the fuller ending -er: die Kinder.
- ✗ the -n form (Kindn): -n goes on nouns that already end in -e; this noun takes -er: die Kinder.
- ✗ the -en form (Kinden): -en belongs to other noun groups; this noun takes -er: die Kinder.

**flashcard_and_miss_hint**: Many neuter one-syllable nouns add -er and keep the vowel (das Kind → die Kinder).

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.

---

## plural-umlaut-er

**id**: `plural-umlaut-er`

Author note, not learner-facing: nouns that change the vowel and add -er (pattern tag "¨-er").

**items**: das Haus → die ___ (plur:haus) · das Buch → die ___ (plur:buch) · der Mann → die ___ (plur:mann) · das Rad → die ___ (plur:rad) · der Wald → die ___ (plur:wald) · das Glas → die ___ (plur:glas) · das Dorf → die ___ (plur:dorf) · das Blatt → die ___ (plur:blatt)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Auf der Wiese springen viele L**ä**mm**er**.

**miss lines**:
- ✗ the -e form (Hause for Haus): this noun takes -er, and the vowel changes: die Häuser.
- ✗ the umlaut-plus-e form (Häuse): the ending is -er, not -e: die Häuser.
- ✗ the plain -er form (Hauser): the vowel changes too, au becomes äu: die Häuser.

**flashcard_and_miss_hint**: Add -er and change the vowel: das Haus → die Häuser, das Buch → die Bücher.

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.

---

## plural-n

**id**: `plural-n`

Author note, not learner-facing: nouns ending in -e that add -n (pattern tag "-n").

**items**: die Katze → die ___ (plur:katze) · die Straße → die ___ (plur:straße) · die Schule → die ___ (plur:schule) · die Frage → die ___ (plur:frage) · die Reise → die ___ (plur:reise) · die Lampe → die ___ (plur:lampe) · die Tasche → die ___ (plur:tasche) · das Auge → die ___ (plur:auge)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Im Garten blühen viele Blume**n**.

**miss lines**:
- ✗ the unchanged form (Katze): nouns ending in -e add -n in the plural: die Katzen.
- ✗ the umlaut form (Kätze): the vowel stays as it is; -n is added: die Katzen.
- ✗ the -er form (Katzeer): not -er; nouns ending in -e add -n: die Katzen.
- ✗ the -s form (Reises for Reise): -s is mostly for borrowed words; nouns ending in -e add -n: die Reisen.

**flashcard_and_miss_hint**: Nouns ending in -e usually add -n (die Katze → die Katzen); das Gebäude is an exception.

**later_rung_cue**: Look at how the singular ends.

---

## plural-en

**id**: `plural-en`

Author note, not learner-facing: nouns that add -en (pattern tag "-en").

**items**: die Frau → die ___ (plur:frau) · die Zeit → die ___ (plur:zeit) · die Zeitung → die ___ (plur:zeitung) · die Wohnung → die ___ (plur:wohnung) · das Bett → die ___ (plur:bett) · das Hemd → die ___ (plur:hemd) · das Ohr → die ___ (plur:ohr) · der Mensch → die ___ (plur:mensch)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Die Schüler schreiben heute viele Übung**en**.

**miss lines**:
- ✗ the -e form (Zeite for Zeit): this noun adds -en: die Zeiten.
- ✗ the -n form (Zeitn): -n goes on nouns that already end in -e; this noun adds -en: die Zeiten.
- ✗ the -er form (Zeiter): the ending is -en, not -er: die Zeiten.
- ✗ the umlaut form (Fräue for Frau): the vowel stays; the ending is -en: die Frauen.

**flashcard_and_miss_hint**: Many feminine nouns and all -ung nouns take -en; so do das Bett, das Hemd, das Ohr and der Mensch.

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.

---

## plural-s

**id**: `plural-s`

Author note, not learner-facing: borrowed words that add -s (pattern tag "-s").

**items**: das Auto → die ___ (plur:auto) · das Foto → die ___ (plur:foto) · das Radio → die ___ (plur:radio) · das Sofa → die ___ (plur:sofa) · der Chef → die ___ (plur:chef) · das Hotel → die ___ (plur:hotel) · der Park → die ___ (plur:park) · das Kino → die ___ (plur:kino)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Am Bahnhof warten viele Taxi**s**.

**miss lines**:
- ✗ the -e form (Autoe for Auto): borrowed words add -s: die Autos.
- ✗ the umlaut form (Autöe): no vowel change, just -s: die Autos.
- ✗ the -er form (Autoer): this group adds -s, not -er: die Autos.
- ✗ the -n form (Chefn for Chef): -n goes on nouns that already end in -e; borrowed words add -s: die Chefs.

**flashcard_and_miss_hint**: Borrowed words add -s: das Auto → die Autos, der Chef → die Chefs, das Hotel → die Hotels.

**later_rung_cue**: Think about where this word comes from.

---

## plural-no-change

**id**: `plural-no-change`

Author note, not learner-facing: nouns whose plural matches the singular (pattern tag "—").

**items**: das Fenster → die ___ (plur:fenster) · das Zimmer → die ___ (plur:zimmer) · das Mädchen → die ___ (plur:mädchen) · das Brötchen → die ___ (plur:brötchen) · der Wagen → die ___ (plur:wagen) · der Schlüssel → die ___ (plur:schlüssel) · das Gebäude → die ___ (plur:gebäude) · der Lehrer → die ___ (plur:lehrer)

**rule (pre-answer)**: Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), sometimes with a vowel change (¨). Learn the plural with the noun.

**first_exposure_rule**: omitted. A plural has no case or gender to name.

**forms table**:

| | -e | -er | -(e)n |
|---|---|---|---|
| vowel kept | der Weg → die Wege | das Nest → die Nester | die Blume → die Blumen · die Übung → die Übungen |
| vowel ¨ | der Fuß → die Füße | das Lamm → die Lämmer | — |
| other | -s: das Taxi → die Taxis | no ending: das Messer → die Messer | ¨ only: der Apfel → die Äpfel |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell of the item's pattern and bold its ending. The example nouns are not drill items.

**example (flashcard and after a miss)**: Im Büro stehen viele Computer. (No bold ending: the plural looks exactly like the singular.)

**miss lines**:
- ✗ the -e form (Fenstere for Fenster): this noun adds no ending: die Fenster.
- ✗ the -er form (Fensterer): no -er; the plural matches the singular: die Fenster.
- ✗ the -n form (Fenstern): -n goes on nouns that end in -e; this noun does not change: die Fenster.
- ✗ the umlaut form (Wägene for Wagen, Gebäüde for Gebäude): no vowel change and no ending: die Wagen, die Gebäude.
- ✗ the umlaut-plus-er form (Gebäüdeer for Gebäude): no vowel change and no ending: die Gebäude.

**flashcard_and_miss_hint**: Masculine and neuter nouns ending in -er, -el, -en or -chen often stay the same; so does das Gebäude.

**later_rung_cue**: Recall this noun's plural pattern: the ending, and any vowel change.
