# Passive help-card content (family `passiv`)

6 rules (5 in the `passiv` family, 1 reused `prep-fixed-dat`) · 10 of 10 items covered, each
exactly once · 0 `UNSURE:` marks.

Conventions (author notes, not learner-facing):

- Layout follows `articles.md`: id, items, rule, first_exposure_rule, forms table, example,
  miss lines, flashcard_and_miss_hint, later_rung_cue.
- `passiv-aux-form` and `passiv-perfekt-partizip` are shared with `werden.md` (its passive
  items). The rule, first-exposure rule, table, hint and cue texts are identical in both files;
  only the items and the miss lines differ.
- `prep-fixed-dat` is shared with `prepositions.md` and `case-endings.md`; every field except
  the items and the miss lines is copied from there unchanged.
- `first_exposure_rule` is omitted in the `passiv` rules: no answer here is a case or gender,
  and the design allows a case name before an answer only for one-case prepositions.
- Forms tables are plain before an answer; the matching cell is marked only in the flashcard
  and after-a-miss states.
- In `passiv-aux-form` the pre-answer rule and captions avoid the helper's infinitive token,
  because item 2's answer is that token; the table carries the forms.
- Data fixed in review (`items/passive.md`): item 1 distractor war → wurden; item 3 prompt adds
  „von meiner Schwester" (rules out the state passive war); item 5 distractor war → sind
  (war … worden is a correct pluperfect).

---

## passiv-aux-form

**id**: `passiv-aux-form`

**items**:

- Das Auto ___ gerade repariert.
- Die Bücher ___ oft gelesen.
- Der Brief ___ gestern von meiner Schwester geschrieben.
- Die Tiere ___ letzte Woche vom Tierarzt untersucht.

**rule (pre-answer)**: A passive says the action happens to the subject. Its helper matches the subject in number and carries the tense. sein with a participle only describes a state.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**:

| | ich | er/sie/es | plural |
|---|---|---|---|
| Present | werde | wird | werden |
| Past | wurde | wurde | wurden |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its form.

**example (flashcard and after a miss)**: rendered per item with its own sentence, helper bold, for example: Die Bücher **werden** oft gelesen.

**miss lines**:

- Das Auto ___ gerade repariert. (wird)
  - ✗ *werde*: the ich form. Das Auto is a third-person subject, so wird.
  - ✗ *werden*: the plural present form. Das Auto is one single thing, so wird.
  - ✗ *wurden*: the past plural form. Das Auto is one thing, and gerade shows the repair is going on now, so wird.
- Die Bücher ___ oft gelesen. (werden)
  - ✗ *wird*: the singular present form. Die Bücher is plural, so werden.
  - ✗ *wurde*: the singular past form. oft is a habit in the present, so werden.
  - ✗ *worden*: a participle that stands beside a form of sein, not a finite verb. The present helper werden is needed.
- Der Brief ___ gestern von meiner Schwester geschrieben. (wurde)
  - ✗ *wird*: the present form. gestern points to the past, so wurde.
  - ✗ *war*: sein in the past only describes a finished state. von meiner Schwester names who did the writing, so this is the action: the process passive wurde.
  - ✗ *worden*: a participle for the perfect passive. A finite past helper is needed, so wurde.
- Die Tiere ___ letzte Woche vom Tierarzt untersucht. (wurden)
  - ✗ *wurde*: the singular past form. Die Tiere is plural, so wurden.
  - ✗ *werden*: the present form. letzte Woche points to the past, so wurden.
  - ✗ *worden*: a participle, not a finite verb. The past plural form wurden fits.

**flashcard_and_miss_hint**: One subject takes wird or wurde; several take werden or wurden. sein + participle describes a state, not the action.

**later_rung_cue**: The helper matches the subject in number and the time word in tense, like any verb.

---

## passiv-perfekt-sein

**id**: `passiv-perfekt-sein`

**items**:

- Die Wohnung ___ schon verkauft worden.
- Die Blumen ___ bereits gegossen worden.

**rule (pre-answer)**: The perfect passive is built with a form of sein, never haben, plus Partizip II and worden. The sein form matches its subject: one thing or several.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**:

| | singular | plural |
|---|---|---|
| sein, present | ist | sind |
| haben, present | hat | haben |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its form.

**example (flashcard and after a miss)**: rendered per item with its own sentence, helper bold, for example: Die Wohnung **ist** schon verkauft worden.

**miss lines**:

- Die Wohnung ___ schon verkauft worden. (ist)
  - ✗ *hat*: haben never builds a passive. The perfect passive stands on sein, so ist.
  - ✗ *sind*: the plural form. Die Wohnung is one thing, so ist.
  - ✗ *wird*: the present passive helper. With schon … worden the perfect is needed, so ist.
- Die Blumen ___ bereits gegossen worden. (sind)
  - ✗ *haben*: the plural of haben, which never builds a passive. Die Blumen take sind.
  - ✗ *wurden*: a finite past helper. worden needs a form of sein beside it, so sind.
  - ✗ *werden*: the present plural helper. With bereits … worden the perfect needs sind.

**flashcard_and_miss_hint**: The perfect passive always uses sein: ist for one subject, sind for several.

**later_rung_cue**: Perfect passive: a form of sein + Partizip II + worden. haben never appears.

---

## passiv-perfekt-partizip

**id**: `passiv-perfekt-partizip`

**items**:

- Die Suppe ist schon gekocht ___.

**rule (pre-answer)**: In the perfect passive the helper verb has its own special participle. The participle it uses as an ordinary full verb is a different word. The table shows both.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**:

| | as the passive helper | as a full verb |
|---|---|---|
| Perfect | ist repariert worden | ist müde geworden |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its participle.

**example (flashcard and after a miss)**: rendered per item with its own sentence, participle bold, for example: Die Suppe ist schon gekocht **worden**.

**miss lines**:

- Die Suppe ist schon gekocht ___. (worden)
  - ✗ *geworden*: the participle werden uses as a full verb (Er ist müde geworden). Inside the passive it is worden.
  - ✗ *werden*: an infinitive. After ist + gekocht the passive participle worden completes the perfect.
  - ✗ *war*: a finite past of sein. The sentence already has ist; the missing word is the participle worden.

**flashcard_and_miss_hint**: Inside the perfect passive the participle is worden; geworden belongs to the full verb.

**later_rung_cue**: Perfect passive: sein + Partizip II + the helper's special participle, not its full-verb form.

---

## passiv-agent

**id**: `passiv-agent`

**items**:

- Der Text wird ___ dem Lehrer korrigiert.

**rule (pre-answer)**: A passive can tell who did the action and how. The doer, the means or cause and the tool each take their own preposition. Match each noun to its role.

**first_exposure_rule**: omitted. The answer is a preposition, and its role, not a case, decides it.

**forms table**:

| | who did it | means or cause | tool | place |
|---|---|---|---|---|
| | von | durch | mit | bei |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell. The case each preposition takes is not shown here, so the card names no case on later rungs.

**example (flashcard and after a miss)**: Der Text wird **von** dem Lehrer korrigiert.

**miss lines**:

- Der Text wird ___ dem Lehrer korrigiert. (von)
  - ✗ *mit*: names the tool the doer used, not the doer. Der Lehrer is the person doing it, so von dem Lehrer.
  - ✗ *durch*: names the means or the cause, and takes the accusative. A person doing the action takes von dem.
  - ✗ *bei*: marks being at a person or place, not doing the action. The doer takes von dem.

**flashcard_and_miss_hint**: The doer takes von + dative; dem Lehrer already shows the dative.

**later_rung_cue**: Is the noun the doer, the means or the tool? Each role takes its own preposition.

---

## prep-fixed-dat

**id**: `prep-fixed-dat`

Shared with `prepositions.md`: every field except the items and the miss lines is copied from there unchanged.

**items**:

- Das Lied wird von ___ Sängerin gesungen.

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

**miss lines**:

- Feminine — Sängerin; right form der:
  - ✗ *die*: die is the nominative or accusative feminine form. von takes the dative, so the dative feminine form der.
  - ✗ *dem*: dem is the dative form for masculine or neuter nouns. Sängerin is feminine, so der.
  - ✗ *den*: den is the accusative masculine (or dative plural) form. Sängerin is feminine dative, so der.

**flashcard_and_miss_hint**: These prepositions always take the dative: dem for masculine and neuter, der for feminine, den for plural.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## passiv-partizip-ii

**id**: `passiv-partizip-ii`

**items**:

- Das Geschirr wird jetzt ___.

**rule (pre-answer)**: The helper of a passive carries the person and the tense. The action word after it never conjugates: it stands as the Partizip II, the form the perfect also uses.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**: (example verbs, not the drill's)

| | conjugated | infinitive | Partizip II |
|---|---|---|---|
| trinken | trinkt | trinken | getrunken |
| spülen | spült | spülen | gespült |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the Partizip II column.

**example (flashcard and after a miss)**: Das Geschirr wird jetzt **gewaschen**.

**miss lines**:

- Das Geschirr wird jetzt ___. (gewaschen)
  - ✗ *wäscht*: the conjugated present form. After the helper the action word is not conjugated, so gewaschen.
  - ✗ *waschen*: the infinitive. The helper already carries the tense, so the Partizip II gewaschen.
  - ✗ *gewaschte*: not a German form. waschen is a strong verb, so its participle ends in -en, never -t: gewaschen.

**flashcard_and_miss_hint**: After the helper the action word is the Partizip II, the same form the perfect uses: hat gewaschen, wird gewaschen.

**later_rung_cue**: The helper carries person and tense; the action word after it stays the Partizip II.

---

## Coverage check

| Rule | Items | Item prompts |
|---|---|---|
| passiv-aux-form | 4 | Das Auto ___ gerade repariert. · Die Bücher ___ oft gelesen. · Der Brief ___ gestern von meiner Schwester geschrieben. · Die Tiere ___ letzte Woche vom Tierarzt untersucht. |
| passiv-perfekt-sein | 2 | Die Wohnung ___ schon verkauft worden. · Die Blumen ___ bereits gegossen worden. |
| passiv-perfekt-partizip | 1 | Die Suppe ist schon gekocht ___. |
| passiv-agent | 1 | Der Text wird ___ dem Lehrer korrigiert. |
| prep-fixed-dat (shared) | 1 | Das Lied wird von ___ Sängerin gesungen. |
| passiv-partizip-ii | 1 | Das Geschirr wird jetzt ___. |

Total: 10 items, each placed exactly once.
