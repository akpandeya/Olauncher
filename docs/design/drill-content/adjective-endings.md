# Adjective endings drill: help-card content

**Count:** 8 rules · 64 items covered (32 weak, 32 mixed), each exactly once · 0 `UNSURE:` marks.

## Conventions in this file

- Items are listed as the learner sees them (the article resolved from `{Art}`), with the template key from `DrillAdjectiveEndings.kt` in brackets.
- The answer tiles are endings. The case and gender are the reasoning step, so nothing before an answer names this item's case. The four weak rules share one pre-answer rule, table and cue; so do the four mixed rules. The card before an answer therefore differs only by the article type, which the prompt already shows.
- `first_exposure_rule` is omitted: the design allows a case name before an answer only for one-case prepositions.
- Table cells show `article …-ending`. Plural is labelled pl, in ink. The genitive shares a row with the dative because both take -en throughout, which keeps the table at 3 rows.
- The existing `hint` in `DrillAdjectiveEndings.kt` names the case ("Schwache Deklination: Dativ"). On later rungs the implementation should show `later_rung_cue` instead.
- Miss lines: ✗, then the wrong choice, why it is wrong and the right form. German words stay in ink.
- Data fixed in review: the prompts now print the stems hoh- and dunkl-, so the answer ending gives hohen and dunklen.

---

## adj-weak-nom

**id**: `adj-weak-nom`

**items**: Der alt___ Mann geht im Park spazieren. (nom_weak_m_mann) · Der klein___ Hund schläft im Garten. (nom_weak_m_hund) · Die jung___ Frau liest ein Buch. (nom_weak_f_frau) · Die weiß___ Katze sitzt auf dem Dach. (nom_weak_f_katze) · Das klein___ Kind spielt mit dem Ball. (nom_weak_n_kind) · Das neu___ Haus hat einen großen Garten. (nom_weak_n_haus) · Die fleißig___ Kinder lernen Deutsch. (nom_weak_pl_kinder) · Die hoh___ Bäume stehen am Fluss. (nom_weak_pl_baeume)

**rule (pre-answer)**: The definite article already shows case and gender, so the adjective adds only -e or -en, never -er, -es or -em. Find the article in the table.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der …-e | die …-e | das …-e | die …-en |
| Akk | den …-en | die …-e | das …-e | die …-en |
| Dat · Gen | dem · des …-en | der …-en | dem · des …-en | den · der …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Die weiß**e** Katze sitzt auf dem Dach.

**miss lines**:
- ✗ *-en*: -en follows plural die (and accusative den). Your noun here is singular, so the ending is -e.
- ✗ *-e*: -e is the singular ending after der, die and das. The noun here is plural, so -en.
- ✗ *-er*: -er follows ein-words; it never follows der, die or das. Singular takes -e, plural die takes -en.
- ✗ *-es*: -es follows neuter ein. After der, die and das the singular ending is -e.
- ✗ *-em*: -em is a dative ending on the article itself (dem, einem), never on an adjective after der, die or das. Plural die takes -en.

**flashcard_and_miss_hint**: Schwache Deklination, Nominativ: singular takes -e, plural takes -en.

**later_rung_cue**: Find the article in the table: which row and column is it in?

---

## adj-weak-akk

**id**: `adj-weak-akk`

**items**: Ich kenne den nett___ Lehrer gut. (akk_weak_m_lehrer) · Er isst den lecker___ Kuchen ganz auf. (akk_weak_m_kuchen) · Wir besuchen die alt___ Stadt jedes Jahr. (akk_weak_f_stadt) · Sie gießt die schön___ Blume auf dem Balkon. (akk_weak_f_blume) · Er kauft das spannend___ Buch im Laden. (akk_weak_n_buch) · Sie verkaufen das rot___ Auto bald. (akk_weak_n_auto) · Wir lesen die wichtig___ Briefe sorgfältig. (akk_weak_pl_briefe) · Sie begrüßt die neu___ Gäste herzlich. (akk_weak_pl_gaeste)

**rule (pre-answer)**: The definite article already shows case and gender, so the adjective adds only -e or -en, never -er, -es or -em. Find the article in the table.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der …-e | die …-e | das …-e | die …-en |
| Akk | den …-en | die …-e | das …-e | die …-en |
| Dat · Gen | dem · des …-en | der …-en | dem · des …-en | den · der …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Ich kenne den nett**en** Lehrer gut.

**miss lines**:
- ✗ *-e*: -e follows die and das when the noun is singular. den and plural die take -en.
- ✗ *-en*: -en follows den and plural die. Singular die and das take -e.
- ✗ *-er*: -er only follows ein. den and plural die take -en; singular die and das take -e.
- ✗ *-es*: -es follows neuter ein. Singular die and das take -e.
- ✗ *-em*: -em is a dative ending (einem). den and plural die take -en.

**flashcard_and_miss_hint**: Schwache Deklination, Akkusativ: den and plural die take -en; singular die and das take -e.

**later_rung_cue**: Find the article in the table: which row and column is it in?

---

## adj-weak-dat

**id**: `adj-weak-dat`

**items**: Ich helfe dem groß___ Bruder beim Umzug. (dat_weak_m_bruder) · Die Lampe steht auf dem rund___ Tisch. (dat_weak_m_tisch) · Er dankt der lieb___ Mutter für alles. (dat_weak_f_mutter) · Wir wohnen in der ruhig___ Straße. (dat_weak_f_strasse) · Sie gibt dem krank___ Kind warme Milch. (dat_weak_n_kind) · Er wohnt in dem klein___ Dorf in den Bergen. (dat_weak_n_dorf) · Der Professor antwortet den interessiert___ Studenten. (dat_weak_pl_studenten) · Zwischen den bunt___ Häusern liegt ein Spielplatz. (dat_weak_pl_haeusern)

**rule (pre-answer)**: The definite article already shows case and gender, so the adjective adds only -e or -en, never -er, -es or -em. Find the article in the table.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der …-e | die …-e | das …-e | die …-en |
| Akk | den …-en | die …-e | das …-e | die …-en |
| Dat · Gen | dem · des …-en | der …-en | dem · des …-en | den · der …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Ich helfe dem groß**en** Bruder beim Umzug.

**miss lines**:
- ✗ *-e*: -e follows the subject articles der, die and das. dem, der and den here signal the dative, so the ending is -en.
- ✗ *-er*: -er follows ein; the definite article never takes it. After dem, der and den the adjective ending is -en.
- ✗ *-em*: -em belongs to the article itself (dem, einem). The adjective after it takes -en.

**flashcard_and_miss_hint**: Schwache Deklination, Dativ: dem, der and den — the adjective always takes -en.

**later_rung_cue**: Find the article in the table: which row and column is it in?

---

## adj-weak-gen

**id**: `adj-weak-gen`

**items**: Das ist das Fahrrad des alt___ Vaters. (gen_weak_m_vaters) · Am Rande des dunkl___ Waldes steht eine Hütte. (gen_weak_m_waldes) · Er repariert das Auto der gut___ Freundin. (gen_weak_f_freundin) · Der Lehrer lobt die Noten der fleißig___ Schülerin. (gen_weak_f_schuelerin) · Der Schornstein des modern___ Hauses raucht. (gen_weak_n_hauses) · Wir bewundern das Lächeln des klein___ Mädchens. (gen_weak_n_maedchens) · Der Garten der nett___ Eltern grenzt an den See. (gen_weak_pl_eltern) · Die Stimmen der fröhlich___ Kinder hallen im Flur. (gen_weak_pl_kinder)

**rule (pre-answer)**: The definite article already shows case and gender, so the adjective adds only -e or -en, never -er, -es or -em. Find the article in the table.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | der …-e | die …-e | das …-e | die …-en |
| Akk | den …-en | die …-e | das …-e | die …-en |
| Dat · Gen | dem · des …-en | der …-en | dem · des …-en | den · der …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Das ist das Fahrrad des alt**en** Vaters.

**miss lines**:
- ✗ *-e*: -e follows the subject articles der, die and das. des and der here signal the genitive, so the ending is -en.
- ✗ *-er*: -er follows ein; the definite article never takes it. After des and der the genitive adjective is -en.
- ✗ *-em*: -em is dative (dem, einem). des signals the genitive, and the adjective takes -en.

**flashcard_and_miss_hint**: Schwache Deklination, Genitiv: des and der — the adjective always takes -en.

**later_rung_cue**: Find the article in the table: which row and column is it in?

---

## adj-mixed-nom

**id**: `adj-mixed-nom`

**items**: Dort läuft ein treu___ Hund über die Wiese. (nom_mixed_m_hund) · Hier liegt ein rot___ Apfel auf dem Tisch. (nom_mixed_m_apfel) · Dort schläft eine schwarz___ Katze auf dem Sofa. (nom_mixed_f_katze) · Dort hängt eine teur___ Uhr aus der Schweiz. (nom_mixed_f_uhr) · Auf dem Tisch liegt ein spannend___ Buch über Geschichte. (nom_mixed_n_buch) · Dort lacht ein fröhlich___ Kind. (nom_mixed_n_kind) · Heute stehen keine dunkl___ Wolken am Himmel. (nom_mixed_pl_wolken) · Im Text stehen keine schwer___ Fehler. (nom_mixed_pl_fehler)

**rule (pre-answer)**: ein, eine and keine show the case only partly. Where the article has an ending, the adjective adds -e or -en; where it has none, the adjective carries the signal.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | ein …-er | eine …-e | ein …-es | keine …-en |
| Akk | einen …-en | eine …-e | ein …-es | keine …-en |
| Dat · Gen | einem · eines …-en | einer …-en | einem · eines …-en | keinen · keiner …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Dort läuft ein treu**er** Hund über die Wiese.

**miss lines**:
- ✗ *-es*: -es follows neuter ein. Masculine ein takes -er, eine takes -e, plural keine takes -en.
- ✗ *-en*: -en follows plural keine. ein takes -er or -es, and eine takes -e.
- ✗ *-e*: -e follows eine. ein takes -er or -es; plural keine takes -en.
- ✗ *-er*: -er follows masculine ein. eine takes -e, neuter ein takes -es, keine takes -en.
- ✗ *-em*: -em is dative (einem). Plural keine takes -en.

**flashcard_and_miss_hint**: Gemischte Deklination, Nominativ: ein (m) → -er, eine → -e, ein (n) → -es, keine → -en.

**later_rung_cue**: Does the article have an ending of its own? If not, the adjective carries the signal.

---

## adj-mixed-akk

**id**: `adj-mixed-akk`

**items**: Ich habe einen treu___ Freund gefunden. (akk_mixed_m_freund) · Er trinkt einen heiß___ Tee mit Zitrone. (akk_mixed_m_tee) · Sie kocht eine lecker___ Suppe zum Abendessen. (akk_mixed_f_suppe) · Er stellt eine schwierig___ Frage im Unterricht. (akk_mixed_f_frage) · Wir kaufen ein schnell___ Auto für die Reise. (akk_mixed_n_auto) · Sie trägt ein blau___ Kleid zur Feier. (akk_mixed_n_kleid) · Wir sehen keine groß___ Probleme bei diesem Plan. (akk_mixed_pl_probleme) · Er schreibt keine lang___ Briefe mehr. (akk_mixed_pl_briefe)

**rule (pre-answer)**: ein, eine and keine show the case only partly. Where the article has an ending, the adjective adds -e or -en; where it has none, the adjective carries the signal.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | ein …-er | eine …-e | ein …-es | keine …-en |
| Akk | einen …-en | eine …-e | ein …-es | keine …-en |
| Dat · Gen | einem · eines …-en | einer …-en | einem · eines …-en | keinen · keiner …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Er trinkt einen heiß**en** Tee mit Zitrone.

**miss lines**:
- ✗ *-e*: -e follows eine. einen and plural keine take -en; neuter ein takes -es.
- ✗ *-en*: -en follows einen and keine. eine takes -e; neuter ein takes -es.
- ✗ *-er*: -er is the subject ending of masculine ein. eine takes -e, neuter ein takes -es, keine takes -en.
- ✗ *-em*: -em is dative (einem). einen and keine take -en.
- ✗ *-es*: -es follows neuter ein. eine is feminine, so the ending is -e.

**flashcard_and_miss_hint**: Gemischte Deklination, Akkusativ: einen → -en, eine → -e, ein → -es, keine → -en.

**later_rung_cue**: Does the article have an ending of its own? If not, the adjective carries the signal.

---

## adj-mixed-dat

**id**: `adj-mixed-dat`

**items**: mit einem klein___ Hund (dat_mixed_m_hund) · Sie spricht mit einem gut___ Freund über alles. (dat_mixed_m_freund) · Er trinkt aus einer weiß___ Tasse seinen Kaffee. (dat_mixed_f_tasse) · Ich begegne einer alt___ Freundin am Bahnhof. (dat_mixed_f_freundin) · Sie wohnen in einem ruhig___ Dorf im Süden. (dat_mixed_n_dorf) · Wir übernachten in einem schön___ Hotel in Berlin. (dat_mixed_n_hotel) · Er vertraut keinen fremd___ Leuten so schnell. (dat_mixed_pl_leuten) · Sie schadet keinen wild___ Tieren im Wald. (dat_mixed_pl_tieren)

**rule (pre-answer)**: ein, eine and keine show the case only partly. Where the article has an ending, the adjective adds -e or -en; where it has none, the adjective carries the signal.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | ein …-er | eine …-e | ein …-es | keine …-en |
| Akk | einen …-en | eine …-e | ein …-es | keine …-en |
| Dat · Gen | einem · eines …-en | einer …-en | einem · eines …-en | keinen · keiner …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Sie spricht mit einem gut**en** Freund über alles.

**miss lines**:
- ✗ *-e*: -e follows eine and the nominative or accusative die. einem, einer and keinen here show the dative, so the adjective takes -en.
- ✗ *-er*: -er follows masculine ein as a subject. After einem, einer and keinen the adjective takes -en.
- ✗ *-em*: -em is the ending of the article itself (einem). The adjective after it takes -en.

**flashcard_and_miss_hint**: Gemischte Deklination, Dativ: einem, einer and keinen — the adjective always takes -en.

**later_rung_cue**: Does the article have an ending of its own? If not, the adjective carries the signal.

---

## adj-mixed-gen

**id**: `adj-mixed-gen`

**items**: Eines schön___ Tages reisen wir nach Rom. (gen_mixed_m_tages) · Das ist das Buch eines klug___ Lehrers. (gen_mixed_m_lehrers) · Der Duft einer frisch___ Blume erfüllt den Raum. (gen_mixed_f_blume) · Innerhalb einer kurz___ Woche war alles erledigt. (gen_mixed_f_woche) · Wegen eines alt___ Hauses gab es viel Streit. (gen_mixed_n_hauses) · Die Zukunft eines krank___ Kindes liegt uns am Herzen. (gen_mixed_n_kindes) · Trotz keiner sicher___ Zeugen wurde der Fall gelöst. (gen_mixed_pl_zeugen) · Trotz keiner groß___ Vorkenntnisse bestand sie die Prüfung. (gen_mixed_pl_vorkenntnisse)

**rule (pre-answer)**: ein, eine and keine show the case only partly. Where the article has an ending, the adjective adds -e or -en; where it has none, the adjective carries the signal.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Nom | ein …-er | eine …-e | ein …-es | keine …-en |
| Akk | einen …-en | eine …-e | ein …-es | keine …-en |
| Dat · Gen | einem · eines …-en | einer …-en | einem · eines …-en | keinen · keiner …-en |

Equal weight before an answer; the same table is shown for every item of this declension, whatever its case. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Das ist das Buch eines klug**en** Lehrers.

**miss lines**:
- ✗ *-e*: -e follows eine and the nominative or accusative die. eines, einer and keiner here show the genitive, so the adjective takes -en.
- ✗ *-er*: -er follows masculine ein as a subject. After the genitive eines, einer and keiner the adjective takes -en.
- ✗ *-em*: -em is dative (einem). The genitive eines and keiner take the adjective -en.

**flashcard_and_miss_hint**: Gemischte Deklination, Genitiv: eines, einer and keiner — the adjective always takes -en.

**later_rung_cue**: Does the article have an ending of its own? If not, the adjective carries the signal.
