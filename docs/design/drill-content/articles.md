# Articles help-card content: merged (A = GLM 5.3, B = agy)

17 rules, 72 items covered, 0 UNSURE.

| Rule ID | Version used | Why |
|---|---|---|
| `art-gender-suffix-ung` | A | Same content as B; A's miss lines are shorter and match design example A, and A's one-row table fits the nominative-only choices. |
| `art-gender-suffix-heit` | A | Same reasoning as -ung. |
| `art-gender-suffix-keit` | A | Same reasoning as -ung. |
| `art-gender-suffix-schaft` | A | Same reasoning as -ung. |
| `art-gender-suffix-ion` | A | Same reasoning as -ung. |
| `art-gender-suffix-taet` | merged | A's content, B's ASCII ID (the brief says lowercase, hyphens only; A used 'tät'). |
| `art-gender-suffix-ei` | A | Same reasoning as -ung. |
| `art-gender-suffix-e` | A | A keeps the design's 'der Junge' exception in the der line; B's content otherwise equal. |
| `art-gender-suffix-ismus` | A | Same reasoning as -ung. |
| `art-gender-suffix-ling` | A | Same reasoning as -ung. |
| `art-gender-suffix-ment` | merged | A's wording plus B's 'loan nouns' qualifier, which is accurate. |
| `art-gender-suffix-chen` | merged | A's wording plus B's 'diminutive' qualifier, which is accurate. |
| `art-gender-suffix-lein` | merged | A's wording plus B's 'diminutive' qualifier. |
| `art-gender-suffix-um` | merged | A's wording plus B's 'Latin loan' qualifier. |
| `art-gender-suffix-exception` | A (grouping), merged miss lines | A groups the three masculine look-alikes (Baum, Sprung, Moment) in one rule; B made three one-item rules plus an invalid 'beverage' rule. Miss lines are new: A's were generic, B's had invented detail. |
| `art-gender-natural` | B (grouping), merged ID and text | B found a real pattern (people take natural gender). Its IDs `-male`/`-female` named the answer, so merged into one ID; miss lines rewritten shorter. |
| `art-gender-fixed` | A (ID and shape), merged items | A's single catch-all avoids IDs that name the answer (B's `root-masculine/feminine/neuter`). B's `-en`/`-el` rules were dropped because the -en and -el endings do not give a reliable gender in the item set, so those four nouns land here. Kaffee and Tee (full-vowel -ee) and the plain words Tag and Monat also land here. |

## Disagreements

- **Forms table.** A: one row (Nom). B: two rows (Nom, Akk). Chose A. All items are nominative `___ Noun` with choices der/die/das, so the Akk row (den) is not a choice and only adds noise. Within the 3x4 limit either way.
- **Example bolding.** A bolds the whole article (**Die**); B bolds only the ending (D**ie**). Chose A: the whole article changes between genders, and design example A bolds **Die**.
- **Example sentences.** B's are longer and one per rule. Chose A's short frames. For multi-noun rules the example is rendered with the item's noun, so it never shows a different noun's answer.
- **Miss line wording.** A: "der: masculine. Nouns ending in -ung are feminine, so die." B: "der is masculine, but nouns ending in..." Chose A (design example A style, shorter). Added B's "diminutive", "loan" and "Latin loan" qualifiers for -chen, -lein, -ment and -um because they are accurate.
- **Rule grouping.** A: 16 rules, one catch-all `art-gender-fixed` for 37 nouns. B: 26 rules. Chose a middle: A's catch-all (32 nouns left), plus B's real pattern for people as `art-gender-natural`. B's calendar rule is dropped: the item set has no names of days or months, only the plain words Tag and Monat.
- **IDs naming the answer.** B's `natural-male`, `natural-female`, `root-masculine`, `root-feminine`, `root-neuter` break the ID rule. Merged or dropped.
- **B's `-en` / `-el` masculine rules** (`art-gender-ending-en`, `-el`): dropped. The -en and -el endings do not give a reliable gender in the item set. Wagen, Garten, Apfel, Schlüssel go to `art-gender-fixed`.
- **B's `art-gender-beverage`:** dropped. "Brewed hot drinks are masculine" is false as a rule (die Schokolade). Kaffee and Tee go to `art-gender-fixed`, because the -ee ending has no gender pattern.
- **Exceptions.** A: one rule of five (now three: Baum, Sprung, Moment). B: three one-item rules (`exception-um/-ung/-ment`) plus beverage. Chose A's grouping, renamed `art-gender-suffix-exception` (kept A's name), with per-item miss lines naming the misleading ending. B's "native Germanic" and "root noun from springen" claims dropped as extra grammar.
- **ID for -tät.** A: `art-gender-suffix-tät`. B: `...-taet`. Chose B (ASCII, lowercase, hyphens only).
- **Gender hint in the flashcard state.** The brief has no field for it. Both writers put the suffix hint only in miss lines. Left as is; the existing `ruleHintFor` hint can serve the flashcard.
- **A's note that Frühling-type and -e items have no `RULES` hint** (the -e rule is new, not in `RULES`): kept implicitly; `art-gender-suffix-e` needs a `RULES` entry in code if the hint is wanted.

## Checks

1. Coverage: pass. All 72 `TABLE_ENTRIES` map to exactly one rule (verified by script against DrillArticles.kt); none missing, none double.
2. Pre-answer leak: pass. Every rule uses the same plain sentence; it contains no der/die/das/den and no suffix or gender. Items 1-72.
3. Length: pass. Rule text is 113 characters (limit 170). Every table is 1 row x 4 columns (limit 3x4).
4. Miss lines: pass. Every rule gives a line for each of the two wrong choices of der/die/das. The three-gender rules (`natural`, `fixed`) give both lines per item gender; `exception` gives both per item.
5. Rule IDs: pass. All match `art-<point>[-<variant>]`, lowercase ASCII, no noun or answer in any ID. `art-gender-suffix-*` names a suffix, as the brief's own example does.

Passed: 5 of 5.

---

## art-gender-suffix-ung

**id**: `art-gender-suffix-ung`

**items**: ___ Zeitung · ___ Wohnung · ___ Hoffnung

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Wohnung ist hell.

**miss lines**:
- ✗ der: masculine. Nouns ending in -ung are feminine, so die.
- ✗ das: neuter. Nouns ending in -ung are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -ung are feminine.

**later_rung_cue**: Look at the -ung ending.

## art-gender-suffix-heit

**id**: `art-gender-suffix-heit`

**items**: ___ Freiheit · ___ Gesundheit

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Freiheit ist wichtig.

**miss lines**:
- ✗ der: masculine. Nouns ending in -heit are feminine, so die.
- ✗ das: neuter. Nouns ending in -heit are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -heit are feminine.

**later_rung_cue**: Look at the -heit ending.

## art-gender-suffix-keit

**id**: `art-gender-suffix-keit`

**items**: ___ Möglichkeit · ___ Schnelligkeit

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Möglichkeit ist da.

**miss lines**:
- ✗ der: masculine. Nouns ending in -keit are feminine, so die.
- ✗ das: neuter. Nouns ending in -keit are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -keit are feminine.

**later_rung_cue**: Look at the -keit ending.

## art-gender-suffix-schaft

**id**: `art-gender-suffix-schaft`

**items**: ___ Freundschaft · ___ Landschaft

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Landschaft ist schön.

**miss lines**:
- ✗ der: masculine. Nouns ending in -schaft are feminine, so die.
- ✗ das: neuter. Nouns ending in -schaft are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -schaft are feminine.

**later_rung_cue**: Look at the -schaft ending.

## art-gender-suffix-ion

**id**: `art-gender-suffix-ion`

**items**: ___ Station · ___ Situation

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Situation ist schwierig.

**miss lines**:
- ✗ der: masculine. Nouns ending in -ion are feminine, so die.
- ✗ das: neuter. Nouns ending in -ion are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -ion are feminine.

**later_rung_cue**: Look at the -ion ending.

## art-gender-suffix-taet

**id**: `art-gender-suffix-taet`

**items**: ___ Universität

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Universität ist alt.

**miss lines**:
- ✗ der: masculine. Nouns ending in -tät are feminine, so die.
- ✗ das: neuter. Nouns ending in -tät are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -tät are feminine.

**later_rung_cue**: Look at the -tät ending.

## art-gender-suffix-ei

**id**: `art-gender-suffix-ei`

**items**: ___ Bäckerei

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Bäckerei ist offen.

**miss lines**:
- ✗ der: masculine. Nouns ending in -ei are feminine, so die.
- ✗ das: neuter. Nouns ending in -ei are feminine, so die.

**flashcard_and_miss_hint**: Nouns ending in -ei are feminine.

**later_rung_cue**: Look at the -ei ending.

## art-gender-suffix-e

**id**: `art-gender-suffix-e`

**items**: ___ Sonne · ___ Straße · ___ Katze · ___ Schule · ___ Frage · ___ Reise

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Die** Sonne ist warm.

**miss lines**:
- ✗ der: masculine. Most nouns ending in -e are feminine (der Junge is an exception), so die.
- ✗ das: neuter. Most nouns ending in -e are feminine, so die.

**flashcard_and_miss_hint**: Most nouns ending in -e are feminine.

**later_rung_cue**: Look at the -e ending.

## art-gender-suffix-ismus

**id**: `art-gender-suffix-ismus`

**items**: ___ Optimismus · ___ Tourismus

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Der** Optimismus hilft.

**miss lines**:
- ✗ die: feminine. Nouns ending in -ismus are masculine, so der.
- ✗ das: neuter. Nouns ending in -ismus are masculine, so der.

**flashcard_and_miss_hint**: Nouns ending in -ismus are masculine.

**later_rung_cue**: Look at the -ismus ending.

## art-gender-suffix-ling

**id**: `art-gender-suffix-ling`

**items**: ___ Schmetterling · ___ Frühling

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Der** Frühling beginnt.

**miss lines**:
- ✗ die: feminine. Nouns ending in -ling are masculine, so der.
- ✗ das: neuter. Nouns ending in -ling are masculine, so der.

**flashcard_and_miss_hint**: Nouns ending in -ling are masculine.

**later_rung_cue**: Look at the -ling ending.

## art-gender-suffix-ment

**id**: `art-gender-suffix-ment`

**items**: ___ Dokument · ___ Instrument

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Das** Dokument ist lang.

**miss lines**:
- ✗ der: masculine. Most loan nouns ending in -ment are neuter, so das.
- ✗ die: feminine. Most loan nouns ending in -ment are neuter, so das.

**flashcard_and_miss_hint**: Most loan nouns ending in -ment are neuter.

**later_rung_cue**: Look at the -ment ending.

## art-gender-suffix-chen

**id**: `art-gender-suffix-chen`

**items**: ___ Mädchen · ___ Brötchen

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Das** Mädchen liest.

**miss lines**:
- ✗ der: masculine. Diminutive nouns ending in -chen are neuter, so das.
- ✗ die: feminine. Diminutive nouns ending in -chen are neuter, so das.

**flashcard_and_miss_hint**: Diminutive nouns ending in -chen are neuter.

**later_rung_cue**: Look at the -chen ending.

## art-gender-suffix-lein

**id**: `art-gender-suffix-lein`

**items**: ___ Fräulein

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Das** Fräulein wartet.

**miss lines**:
- ✗ der: masculine. Diminutive nouns ending in -lein are neuter, so das.
- ✗ die: feminine. Diminutive nouns ending in -lein are neuter, so das.

**flashcard_and_miss_hint**: Diminutive nouns ending in -lein are neuter.

**later_rung_cue**: Look at the -lein ending.

## art-gender-suffix-um

**id**: `art-gender-suffix-um`

**items**: ___ Museum · ___ Zentrum

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Das** Museum ist neu.

**miss lines**:
- ✗ der: masculine. Latin loan nouns ending in -um are neuter, so das.
- ✗ die: feminine. Latin loan nouns ending in -um are neuter, so das.

**flashcard_and_miss_hint**: Latin loan nouns ending in -um are neuter.

**later_rung_cue**: Look at the -um ending.

## art-gender-suffix-exception

**id**: `art-gender-suffix-exception`

**items**: ___ Baum · ___ Sprung · ___ Moment

Author note, not learner-facing: nouns whose ending suggests a different gender. `ruleHintFor` gives them no hint. The pre-answer text stays plain, because warning about the ending would point away from it.

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Der** Baum ist alt.

**miss lines**:
- ___ Baum: ✗ das: neuter. Nouns ending in -um usually are, but Baum is masculine, so der.
- ___ Baum: ✗ die: feminine. Baum is masculine, so der.
- ___ Sprung: ✗ die: feminine. Nouns ending in -ung usually are, but Sprung is masculine, so der.
- ___ Sprung: ✗ das: neuter. Sprung is masculine, so der.
- ___ Moment: ✗ das: neuter. Nouns ending in -ment usually are, but Moment is masculine, so der.
- ___ Moment: ✗ die: feminine. Moment is masculine, so der.

**flashcard_and_miss_hint**: The ending misleads here. Learn the article with the noun.

**later_rung_cue**: Learn the article together with the noun.

## art-gender-natural

**id**: `art-gender-natural`

**items**: ___ Mann · ___ Vater · ___ Bruder · ___ Freund · ___ Frau · ___ Mutter · ___ Schwester

Example is rendered with the item's noun and article: `<Art> <Noun> ist hier.`

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Der** Vater ist hier.

**miss lines**:
- Mann, Vater, Bruder, Freund: ✗ die: feminine. A male person usually takes the masculine article, so der.
- Mann, Vater, Bruder, Freund: ✗ das: neuter. A male person usually takes the masculine article, so der.
- Frau, Mutter, Schwester: ✗ der: masculine. A female person usually takes the feminine article, so die.
- Frau, Mutter, Schwester: ✗ das: neuter. A female person usually takes the feminine article, so die.

**flashcard_and_miss_hint**: A person usually takes the article that matches their sex.

**later_rung_cue**: Think about the person the noun names.

## art-gender-fixed

**id**: `art-gender-fixed`

**items**: ___ Tisch · ___ Hund · ___ Stuhl · ___ Wagen · ___ Garten · ___ Apfel · ___ Schlüssel · ___ Wald · ___ Bahnhof · ___ Platz · ___ Nacht · ___ Stadt · ___ Hand · ___ Zeit · ___ Kind · ___ Haus · ___ Auto · ___ Buch · ___ Bild · ___ Fenster · ___ Wasser · ___ Zimmer · ___ Jahr · ___ Geld · ___ Bett · ___ Brot · ___ Kleid · ___ Spiel · ___ Tag · ___ Monat · ___ Kaffee · ___ Tee

Nouns with no suffix or category pattern. Example is rendered with the item's noun and article: `<Art> <Noun> ist hier.`

**rule (pre-answer)**: Every German noun has one fixed gender, and the article must match it. Learn each noun together with its article.

**first_exposure_rule**: omitted. No case or gender is named before an answer; the gender hint is in `flashcard_and_miss_hint` only.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der | die | das | die |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the cell for the item's gender and bold its article.

**example (flashcard and after a miss)**: **Das** Haus ist groß.

**miss lines**:
- masculine items (for example Tisch): ✗ die: feminine. Tisch is masculine, so der. / ✗ das: neuter. Tisch is masculine, so der.
- feminine items (for example Nacht): ✗ der: masculine. Nacht is feminine, so die. / ✗ das: neuter. Nacht is feminine, so die.
- neuter items (for example Haus): ✗ der: masculine. Haus is neuter, so das. / ✗ die: feminine. Haus is neuter, so das.

**flashcard_and_miss_hint**: Learn the article with the noun.

**later_rung_cue**: Learn the article together with the noun.
