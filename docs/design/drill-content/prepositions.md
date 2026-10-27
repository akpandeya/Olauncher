# Prepositions help-card content (writer: GLM 5.3; reviewed by Opus)

5 rules, 55 items covered (every item in `DrillPrepositions.kt`: 19 which-case, 18 fixed-case gaps, 18 two-way), 0 `UNSURE:` marks.

Shared conventions (author notes, not learner-facing):

- The pre-answer `rule` names no case. Where the design doc (example B) puts a case name in the pre-answer rule, that wording is moved to `first_exposure_rule` (flashcard and first tap only, per design section 2), because this brief forbids case names in the pre-answer rule. Same for the design's later-rung cue `auf + Wohin? → accusative`: the cue here uses question words only.
- Forms tables show the whole contrast set with equal weight before an answer. Cells/rows are marked only in after-answer states (flashcard reading step, after a miss).
- Miss lines are post-answer, so they may name cases. The German words stay in ink (no gender colour on miss-line words, design section 3.5).
- Example sentences are rendered with the item's own sentence and noun, bold article, so a shared rule never shows a different item's answer.
- `prep-which-fixed` is shared with `verb-prepositions.md`; `prep-fixed-acc`, `prep-fixed-dat` and `prep-fixed-gen` are shared with `case-endings.md`. The rule, first-exposure rule, table, hint and cue texts are identical in each file; only the items, the table note and the miss lines differ.

---

## prep-which-fixed

**id**: `prep-which-fixed`

**items**: (the case name is the answer, so no field below names a case before the answer)

- Welcher Fall folgt auf „durch“?
- Welcher Fall folgt auf „für“?
- Welcher Fall folgt auf „gegen“?
- Welcher Fall folgt auf „ohne“?
- Welcher Fall folgt auf „um“?
- Welcher Fall folgt auf „bis“?
- Welcher Fall folgt auf „entlang“ (nachgestellt)?
- Welcher Fall folgt auf „aus“?
- Welcher Fall folgt auf „bei“?
- Welcher Fall folgt auf „mit“?
- Welcher Fall folgt auf „nach“?
- Welcher Fall folgt auf „seit“?
- Welcher Fall folgt auf „von“?
- Welcher Fall folgt auf „zu“?
- Welcher Fall folgt auf „gegenüber“?
- Welcher Fall folgt auf „wegen“?
- Welcher Fall folgt auf „trotz“?
- Welcher Fall folgt auf „während“?
- Welcher Fall folgt auf „statt“?

**rule (pre-answer)**: This preposition is one of those that always take the same case, whatever the sentence says. Learn which one it takes.

**first_exposure_rule**: omitted. For these items the case name is the answer, so it may never be named before the answer in any field (design section 2; same reason the items carry no hint in `DrillPrepositions.kt`).

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Akk | den | die | das | die |
| Dat | dem | der | dem | den |
| Gen | des | der | des | der |

The row labels include every case the item offers as a choice, shown with equal weight; nothing marks the answer. After the answer (flashcard, after a miss) the row of the item's case is highlighted; no single cell is marked, because the answer is a case, not an article.

**example (flashcard and after a miss)**: rendered per item with its preposition, article bold, for example: Ich fahre mit **dem** Bus. · Wir bleiben bis **nächsten** Montag. (bis has no article form; the adjective carries the ending.) The bold form is the only answer display; no field text names the case.

**miss lines**: (after a miss only; grouped by the case each preposition takes)

- durch, für, gegen, ohne, um, bis, entlang: ✗ Dativ: this preposition never takes the Dativ; it always takes the Akkusativ. ✗ Genitiv: this preposition never takes the Genitiv; it always takes the Akkusativ.
- aus, bei, mit, nach, seit, von, zu, gegenüber: ✗ Akkusativ: this preposition never takes the Akkusativ; it always takes the Dativ. ✗ Genitiv: this preposition never takes the Genitiv; it always takes the Dativ.
- wegen, trotz, während, statt: ✗ Akkusativ: this preposition never takes the Akkusativ; it always takes the Genitiv. ✗ Dativ: this preposition takes the Genitiv in standard written German (the Dativ is colloquial), so Genitiv.

**flashcard_and_miss_hint**: These prepositions never change their case. Learn each one with its case.

**later_rung_cue**: Fixed case. Recall which one this preposition takes.

---

## prep-fixed-acc

**id**: `prep-fixed-acc`

**items**:

- Ich gehe durch ___ Park.
- Das Geschenk ist für ___ Vater.
- Das Auto fährt gegen ___ Baum.
- Wir gehen ohne ___ Hund spazieren.
- Die Kinder laufen um ___ Tisch.
- Wir spazieren ___ Fluss entlang.

**rule (pre-answer)**: This preposition is one of those that always take the same case, whatever the sentence says. Learn which one it takes.

**first_exposure_rule**: (flashcard and first tap only) durch, für, gegen, ohne, um and entlang always take the accusative.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Akk | den | die | das | die |
| Dat | dem | der | dem | den |
| Gen | des | der | des | der |

Equal weight before an answer. After the answer (flashcard, after a miss), highlight the Akk row and the column of the item's noun, and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, for example: Ich gehe durch **den** Park.

**miss lines**: (after a miss only; all six items answer den)

- ✗ dem (all six items): dative. These prepositions take the accusative, so den.
- ✗ der (für, gegen, um, entlang): feminine dative or masculine nominative. The noun is masculine, so den.
- ✗ das (durch, ohne): neuter. The noun is masculine, so den.
- ✗ des (durch, ohne, für, gegen, um, entlang): genitive. These prepositions take the accusative, so den.

**flashcard_and_miss_hint**: These prepositions always take the accusative: den, die, das, die.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## prep-fixed-dat

**id**: `prep-fixed-dat`

**items**:

- Er kommt aus ___ Haus.
- Sie wohnt bei ___ Eltern.
- Ich fahre mit ___ Bus.
- Nach ___ Unterricht gehen wir essen.
- Seit ___ Unfall ist er vorsichtig.
- Das Paket ist von ___ Großeltern.
- Ich gehe zu ___ Arzt.
- Das Hotel liegt gegenüber ___ Park.

**rule (pre-answer)**: This preposition is one of those that always take the same case, whatever the sentence says. Learn which one it takes.

**first_exposure_rule**: (flashcard and first tap only) aus, bei, mit, nach, seit, von, zu and gegenüber always take the dative.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Akk | den | die | das | die |
| Dat | dem | der | dem | den |
| Gen | des | der | des | der |

Equal weight before an answer. After the answer (flashcard, after a miss), highlight the Dat row and the column of the item's noun, and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, for example: Ich fahre mit **dem** Bus.

**miss lines**: (after a miss only)

Singular items (aus Haus, mit Bus, nach Unterricht, seit Unfall, zu Arzt, gegenüber Park — answer dem):
- ✗ den (all six): masculine accusative. These prepositions take the dative, so dem.
- ✗ das (aus, mit, nach, seit, zu, gegenüber): nominative or accusative. These prepositions take the dative, so dem.
- ✗ der (mit, nach, seit, zu, gegenüber): feminine dative. The noun is masculine or neuter, so dem.
- ✗ des (aus): genitive. aus takes the dative, so dem.

Plural items (bei Eltern, von Großeltern — answer den):
- ✗ dem (bei, von): singular dative. The noun is plural, so den.
- ✗ der (bei, von): singular dative feminine. The noun is plural, so den.
- ✗ die (bei): nominative or accusative plural. bei takes the dative, so den.
- ✗ des (von): genitive. von takes the dative, so den.

**flashcard_and_miss_hint**: These prepositions always take the dative: dem for masculine and neuter, der for feminine, den for plural.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## prep-fixed-gen

**id**: `prep-fixed-gen`

**items**:

- Wegen ___ Wetters bleiben wir hier.
- Trotz ___ Regens geht sie raus.
- Während ___ Fahrt lesen wir.
- Statt ___ Kaffees trinkt er Tee.

**rule (pre-answer)**: This preposition is one of those that always take the same case, whatever the sentence says. Learn which one it takes.

**first_exposure_rule**: (flashcard and first tap only) wegen, trotz, während, (an)statt, innerhalb, außerhalb, anlässlich, unweit, jenseits and infolge take the genitive.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Akk | den | die | das | die |
| Dat | dem | der | dem | den |
| Gen | des | der | des | der |

Equal weight before an answer. After the answer (flashcard, after a miss), highlight the Gen row and the column of the item's noun, and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, for example: Wegen **des** Wetters bleiben wir hier.

**miss lines**: (after a miss only)

des items (wegen Wetter, trotz Regen, statt Kaffee — answer des):
- ✗ dem: dative. These prepositions take the genitive, so des.
- ✗ den: accusative. The noun is masculine or neuter; its genitive is des.
- ✗ das: nominative or accusative. These prepositions take the genitive, so des.

während item (während Fahrt — answer der):
- ✗ die: nominative or accusative feminine. während takes the genitive, so der.
- ✗ den: accusative masculine. Fahrt is feminine, so der.
- ✗ dem: dative. während takes the genitive, so der.

**flashcard_and_miss_hint**: These prepositions take the genitive: des for masculine and neuter nouns, der for feminine and plural nouns.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## prep-two-way

**id**: `prep-two-way`

**items**: (each preposition has one position sentence and one movement sentence)

- Das Bild hängt an ___ Wand.
- Ich hänge das Bild an ___ Wand.
- Das Buch liegt auf ___ Tisch.
- Ich lege das Buch auf ___ Tisch.
- Der Hund schläft hinter ___ Haus.
- Der Hund rennt hinter ___ Haus.
- Wir sitzen in ___ Garten.
- Wir gehen in ___ Garten.
- Er steht neben ___ Fenster.
- Er stellt sich neben ___ Fenster.
- Die Lampe hängt über ___ Sofa.
- Er hängt die Lampe über ___ Sofa.
- Die Katze schläft unter ___ Bett.
- Die Katze kriecht unter ___ Bett.
- Das Auto steht vor ___ Garage.
- Das Auto fährt vor ___ Garage.
- Das Kind sitzt zwischen ___ Stühlen.
- Das Kind setzt sich zwischen ___ Stühle.

**rule (pre-answer)**: This is a two-way preposition. Wohin? means movement to a place; Wo? means a fixed position. The verb tells you which one the sentence describes.

**first_exposure_rule**: (flashcard and first tap only) an, auf, hinter, in, neben, über, unter, vor and zwischen take the accusative after Wohin? and the dative after Wo?.

**forms table** (design example B):

| | m | f | n | pl |
|---|---|---|---|---|
| Wohin? → Akk | den | die | das | die |
| Wo? → Dat | dem | der | dem | den |

Both rows have equal weight before an answer. After the answer (flashcard, after a miss), highlight the row the sentence calls for and the column of the item's noun, and bold the article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, for example: Ich lege das Buch auf **den** Tisch. (movement, Wohin?) · Das Buch liegt auf **dem** Tisch. (position, Wo?)

**miss lines**: (after a miss only, one per wrong choice of each item)

- Das Bild hängt an ___ Wand. (der)
  - ✗ die: the movement (Wohin?) form. hängen describes a fixed position (Wo?), so der.
  - ✗ den: accusative masculine. Wand is feminine, so der.
  - ✗ dem: dative masculine or neuter. Wand is feminine, so der.
- Ich hänge das Bild an ___ Wand. (die)
  - ✗ der: the fixed-position (Wo?) form. Hanging the picture up is movement (Wohin?), so die.
  - ✗ den: accusative masculine. Wand is feminine, so die.
  - ✗ dem: dative masculine or neuter. Wand is feminine and this is movement, so die.
- Das Buch liegt auf ___ Tisch. (dem)
  - ✗ den: the movement (Wohin?) form. liegen is a fixed position (Wo?), so dem.
  - ✗ der: dative feminine. Tisch is masculine, so dem.
  - ✗ das: neuter. Tisch is masculine, so dem.
- Ich lege das Buch auf ___ Tisch. (den)
  - ✗ dem: the fixed-position (Wo?) form. legen is movement (Wohin?), so den.
  - ✗ der: dative feminine. Tisch is masculine, so den.
  - ✗ das: neuter. Tisch is masculine, so den.
- Der Hund schläft hinter ___ Haus. (dem)
  - ✗ das: the movement (Wohin?) form. schlafen is a fixed position (Wo?), so dem.
  - ✗ den: accusative masculine. Haus is neuter, so dem.
  - ✗ des: genitive. A two-way preposition takes the movement or the position form; a fixed position needs dem.
- Der Hund rennt hinter ___ Haus. (das)
  - ✗ dem: the fixed-position (Wo?) form. rennen is movement (Wohin?), so das.
  - ✗ den: accusative masculine. Haus is neuter, so das.
  - ✗ des: genitive. Movement (Wohin?) with a neuter noun needs das.
- Wir sitzen in ___ Garten. (dem)
  - ✗ den: the movement (Wohin?) form. sitzen is a fixed position (Wo?), so dem.
  - ✗ der: dative feminine. Garten is masculine, so dem.
  - ✗ das: neuter. Garten is masculine, so dem.
- Wir gehen in ___ Garten. (den)
  - ✗ dem: the fixed-position (Wo?) form. gehen is movement (Wohin?), so den.
  - ✗ der: dative feminine. Garten is masculine, so den.
  - ✗ das: neuter. Garten is masculine, so den.
- Er steht neben ___ Fenster. (dem)
  - ✗ das: the movement (Wohin?) form. stehen is a fixed position (Wo?), so dem.
  - ✗ den: accusative masculine. Fenster is neuter, so dem.
  - ✗ des: genitive. A fixed position (Wo?) with a neuter noun needs dem.
- Er stellt sich neben ___ Fenster. (das)
  - ✗ dem: the fixed-position (Wo?) form. sich stellen is movement (Wohin?), so das.
  - ✗ den: accusative masculine. Fenster is neuter, so das.
  - ✗ des: genitive. Movement (Wohin?) with a neuter noun needs das.
- Die Lampe hängt über ___ Sofa. (dem)
  - ✗ das: the movement (Wohin?) form. The lamp hangs in a fixed position (Wo?), so dem.
  - ✗ den: accusative masculine. Sofa is neuter, so dem.
  - ✗ der: dative feminine. Sofa is neuter, so dem.
- Er hängt die Lampe über ___ Sofa. (das)
  - ✗ dem: the fixed-position (Wo?) form. Hanging the lamp up is movement (Wohin?), so das.
  - ✗ den: accusative masculine. Sofa is neuter, so das.
  - ✗ der: dative feminine. Sofa is neuter, so das.
- Die Katze schläft unter ___ Bett. (dem)
  - ✗ das: the movement (Wohin?) form. schlafen is a fixed position (Wo?), so dem.
  - ✗ den: accusative masculine. Bett is neuter, so dem.
  - ✗ des: genitive. A fixed position (Wo?) with a neuter noun needs dem.
- Die Katze kriecht unter ___ Bett. (das)
  - ✗ dem: the fixed-position (Wo?) form. kriechen unter is movement (Wohin?), so das.
  - ✗ den: accusative masculine. Bett is neuter, so das.
  - ✗ des: genitive. Movement (Wohin?) with a neuter noun needs das.
- Das Auto steht vor ___ Garage. (der)
  - ✗ die: the movement (Wohin?) form. stehen is a fixed position (Wo?), so der.
  - ✗ den: accusative masculine. Garage is feminine, so der.
  - ✗ dem: dative masculine or neuter. Garage is feminine, so der.
- Das Auto fährt vor ___ Garage. (die)
  - ✗ der: the fixed-position (Wo?) form. fahren is movement (Wohin?), so die.
  - ✗ den: accusative masculine. Garage is feminine, so die.
  - ✗ dem: dative masculine or neuter. Garage is feminine and this is movement, so die.
- Das Kind sitzt zwischen ___ Stühlen. (den)
  - ✗ die: the movement (Wohin?) form. sitzen is a fixed position (Wo?), so den.
  - ✗ dem: dative singular. Stühle is plural, so den.
  - ✗ der: dative singular feminine. Stühle is plural, so den.
- Das Kind setzt sich zwischen ___ Stühle. (die)
  - ✗ den: the fixed-position (Wo?) form. sich setzen is movement (Wohin?), so die.
  - ✗ der: dative singular. Stühle is plural, so die.
  - ✗ dem: dative singular. Stühle is plural and this is movement, so die.

**flashcard_and_miss_hint**: Movement (Wohin?) takes the accusative; a fixed position (Wo?) takes the dative.

**later_rung_cue**: Ask Wohin? (movement) or Wo? (fixed position) — the verb decides.
