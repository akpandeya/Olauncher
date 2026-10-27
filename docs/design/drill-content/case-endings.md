# Case endings drill: help-card content

**Count:** 7 rules: 4 in the `case` family and 3 shared with `prepositions.md` (`prep-fixed-acc`, `prep-fixed-dat`, `prep-fixed-gen`). **Items covered:** 52 of 52, each exactly once. Data fixed in review: Bahnhof (`gen_n_unweit_bahnhofs`) is now masculine in `DrillCaseEndings.kt`; the key keeps its old name so saved progress stays linked. **UNSURE marks:** 0.

## Conventions in this file

- The case is the reasoning step for every item, so nothing shown before an answer names this item's case. The four `case-*` rules share one pre-answer rule text and one table, and three of them share one later-rung cue, so the card before an answer is the same whether the noun is a subject, an object or a receiver.
- `first_exposure_rule` is omitted for the `case-*` rules: the design allows a case name before an answer only for one-case prepositions. The three shared `prep-fixed-*` rules keep their first-exposure text, which names the preposition's case, as the design allows.
- The `case-*` table puts the cases in columns so all four cases and every choice fit in 3 rows. The shared `prep-fixed-*` table (Akk, Dat, Gen rows) is copied unchanged from `prepositions.md`; it also contains every choice of those items.
- The existing `hint` in `DrillCaseEndings.kt` names the case ("Subjekt: Nominativ", "helfen + Dativ"). On later rungs the implementation should show `later_rung_cue` instead.
- Miss lines are grouped by the noun's gender; each wrong choice of each item has one line.

---

## case-subject-nom

**id**: `case-subject-nom`

Author note, not learner-facing: the doer of the action (drill trigger "Subjekt").

**items**: Dort steht ___ Lehrer. (nom_m_lehrer) · Auf dem Tisch liegt ___ Roman von Paul. (nom_m_tisch) · Hier schläft ___ Hund. (nom_m_hund) · Dort wartet ___ Frau. (nom_f_frau) · Hier arbeitet ___ Ärztin. (nom_f_aerztin) · Hier liegt ___ Buch. (nom_n_buch) · ___ neue Auto steht in der Garage. (nom_n_auto) · Dort spielt ___ Kind. (nom_n_kind) · Dort stehen ___ Kinder. (nom_pl_kinder) · Hier treffen sich ___ besten Freunde. (nom_pl_freunde) · Hier wohnen ___ Studenten. (nom_pl_studenten)

**rule (pre-answer)**: The article shows the noun's job: the doer (Wer?), the thing acted on (Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | Wer? → Nom | Wen? → Akk | Wem? → Dat | Wessen? → Gen |
|---|---|---|---|---|
| m · n | der · das | den · das | dem | des |
| f | die | die | der | der |
| pl | die | die | den | der |

Equal weight before an answer; every choice in these items (der, die, das, den, dem, des) is in the table. After the answer only (flashcard, after a miss): highlight the item's case column and gender row and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Hier schläft de**r** Hund.

**miss lines**:
- Masculine — Lehrer, Roman, Hund; right form der:
  - ✗ den: den is the accusative form for a masculine noun — it marks the thing acted on.
    This noun does the action, so der.
  - ✗ dem: dem is the dative form for a masculine or neuter noun — it marks the receiver.
    This noun is the doer, so der.
  - ✗ des: des is the genitive masculine form — the possessor's form. Nothing is possessed
    here; the noun is the doer, so der.
- Feminine — Frau, Ärztin; right form die:
  - ✗ der: der is the dative feminine form (or the masculine doer's form). This noun is
    feminine and does the action, so die.
  - ✗ den: den is the accusative masculine form. This noun is feminine, so die.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. This noun is feminine,
    so die.
- Neuter — Buch, Auto, Kind; right form das:
  - ✗ den: den is the accusative masculine form. This noun is neuter, so das.
  - ✗ dem: dem is the dative form for masculine or neuter nouns — the receiver's form.
    This noun is the doer, so das.
  - ✗ des: des is the genitive form for masculine or neuter nouns — the possessor's form.
    The noun is the doer, so das.
- Plural — Kinder, Freunde, Studenten; right form die:
  - ✗ den: den is the dative plural form — it marks plural receivers. This plural noun is
    the doer, so die.
  - ✗ der: der is the dative feminine or the genitive form. This noun is a plural doer,
    so die.
  - ✗ dem: dem is the dative singular form for masculine or neuter nouns. This noun is
    plural, so die.

**flashcard_and_miss_hint**: The one doing the action is the subject, and the subject takes the nominative: der, die, das, die.

**later_rung_cue**: Ask Wer?, Wen? or Wem? for this noun, then recall its gender.

---

## case-sein-nom

**id**: `case-sein-nom`

Author note, not learner-facing: the noun after sein (drill trigger "sein").

**items**: Das ist ___ beste Lösung. (nom_f_loesung)

**rule (pre-answer)**: The article shows the noun's job: the doer (Wer?), the thing acted on (Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | Wer? → Nom | Wen? → Akk | Wem? → Dat | Wessen? → Gen |
|---|---|---|---|---|
| m · n | der · das | den · das | dem | des |
| f | die | die | der | der |
| pl | die | die | den | der |

Equal weight before an answer; every choice in these items (der, die, das, den, dem, des) is in the table. After the answer only (flashcard, after a miss): highlight the item's case column and gender row and bold its article.

**example (flashcard and after a miss)**: Das ist di**e** beste Lösung.

**miss lines**:
- Feminine — Lösung; right form die:
  - ✗ der: der is the dative feminine form (or the masculine doer's form). Lösung is
    feminine and follows sein, so die.
  - ✗ den: den is the accusative masculine form. Lösung is feminine, so die.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. Lösung is feminine,
    so die.

**flashcard_and_miss_hint**: sein links two names for the same thing, so the noun after sein takes the nominative, like the subject.

**later_rung_cue**: Ask what the noun after sein refers to, then recall its gender.

---

## case-verb-acc

**id**: `case-verb-acc`

Author note, not learner-facing: the verb's direct object (drill triggers sehen, kaufen, besuchen, kennen, lesen, öffnen, trinken, zählen).

**items**: Ich sehe ___ Hund. (akk_m_sehen_hund) · Er kauft ___ Wagen. (akk_m_kaufen_wagen) · Wir besuchen ___ Großvater. (akk_m_besuchen_grossvater) · Ich kenne ___ Frau schon lange. (akk_f_kennen_frau) · Er liest ___ Zeitung jeden Morgen. (akk_f_lesen_zeitung) · Sie öffnet ___ Fenster. (akk_n_oeffnen_fenster) · Ich trinke ___ Wasser gern. (akk_n_trinken_wasser) · Er zählt ___ Tage bis zum Urlaub. (akk_pl_zaehlen_tage)

**rule (pre-answer)**: The article shows the noun's job: the doer (Wer?), the thing acted on (Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | Wer? → Nom | Wen? → Akk | Wem? → Dat | Wessen? → Gen |
|---|---|---|---|---|
| m · n | der · das | den · das | dem | des |
| f | die | die | der | der |
| pl | die | die | den | der |

Equal weight before an answer; every choice in these items (der, die, das, den, dem, des) is in the table. After the answer only (flashcard, after a miss): highlight the item's case column and gender row and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Wir besuchen de**n** Großvater.

**miss lines**:
- Masculine — Hund, Wagen, Großvater; right form den:
  - ✗ der: der is the nominative masculine form — the doer's form. This noun is the thing
    being seen, bought or visited, so den.
  - ✗ dem: dem is the dative form for a masculine or neuter noun — the receiver's form.
    These verbs act straight on the noun, so den.
  - ✗ des: des is the genitive masculine form — the possessor's form. Nothing is possessed
    here, so den.
- Feminine — Frau, Zeitung; right form die:
  - ✗ der: der is the dative feminine form (or the masculine doer's form). kennen and
    lesen act straight on the noun, so die.
  - ✗ den: den is the accusative masculine form. This noun is feminine, so die.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. This noun is feminine,
    so die.
- Neuter — Fenster, Wasser; right form das:
  - ✗ den: den is the accusative masculine form. This noun is neuter, so das.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. öffnen and trinken act
    straight on the noun, so das.
  - ✗ des: des is the genitive form for masculine or neuter nouns — the possessor's form.
    No possession here, so das.
- Plural — Tage; right form die:
  - ✗ den: den is the dative plural form. zählt takes the days as a direct object, so die.
  - ✗ der: der is the dative or genitive feminine form. The days are a plural direct
    object, so die.
  - ✗ dem: dem is the dative singular form for masculine or neuter nouns. This noun is
    plural, so die.

**flashcard_and_miss_hint**: The thing the verb acts on is the direct object, and it takes the accusative (Wen?).

**later_rung_cue**: Ask Wer?, Wen? or Wem? for this noun, then recall its gender.

---

## case-verb-dat

**id**: `case-verb-dat`

Author note, not learner-facing: the dative verbs (drill triggers helfen, danken, schmecken, gefallen, gehören, antworten).

**items**: Ich helfe ___ Mann gerne. (dat_m_helfen_mann) · Wir danken ___ Arzt für die Hilfe. (dat_m_danken_arzt) · Die Suppe schmeckt ___ Mutter gut. (dat_f_schmecken_mutter) · Das Kleid gefällt ___ Frau sehr. (dat_f_gefallen_frau) · Das Buch gehört ___ Mädchen. (dat_n_gehoeren_maedchen) · Der Lehrer antwortet ___ Schülern. (dat_pl_antworten_schuelern)

**rule (pre-answer)**: The article shows the noun's job: the doer (Wer?), the thing acted on (Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | Wer? → Nom | Wen? → Akk | Wem? → Dat | Wessen? → Gen |
|---|---|---|---|---|
| m · n | der · das | den · das | dem | des |
| f | die | die | der | der |
| pl | die | die | den | der |

Equal weight before an answer; every choice in these items (der, die, das, den, dem, des) is in the table. After the answer only (flashcard, after a miss): highlight the item's case column and gender row and bold its article.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Ich helfe de**m** Mann gerne.

**miss lines**:
- Masculine — Mann, Arzt; right form dem:
  - ✗ der: der is the nominative masculine form — the doer's form. The doer is ich or
    wir; the man or the doctor receives the help or the thanks, so dem.
  - ✗ den: den is the accusative masculine form — the direct object's form. These verbs
    do not act on the person, so dem.
  - ✗ des: des is the genitive masculine form — the possessor's form. No possession is
    marked on the article here, so dem.
- Feminine — Mutter, Frau; right form der:
  - ✗ die: die is the nominative or accusative feminine form. schmeckt and gefällt name
    the person who enjoys or likes, so the dative feminine der.
  - ✗ den: den is the accusative masculine (or dative plural) form. This person is
    feminine, so der.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. This person is feminine
    — the dative feminine form is der.
- Neuter — Mädchen; right form dem:
  - ✗ das: das is the nominative or accusative neuter form. gehören does not make the
    girl a doer or an object — she is the owner, so dem.
  - ✗ den: den is the accusative masculine (or dative plural) form. This receiver is a
    neuter singular, so dem.
  - ✗ des: des is the genitive neuter form. gehören is about possession, but German
    names the owner with dem, not des.
- Plural — Schülern; right form den:
  - ✗ die: die is the nominative or accusative plural form. antwortet names the pupils
    as receivers, so the dative plural den.
  - ✗ der: der is the dative or genitive feminine form. The pupils are plural receivers,
    so den.
  - ✗ dem: dem is the dative singular form. Schülern is plural — its -n ending already
    shows the dative plural, so den.

**flashcard_and_miss_hint**: helfen, danken, schmecken, gefallen, gehören and antworten take the dative: the person who benefits or receives (Wem?).

**later_rung_cue**: Ask Wer?, Wen? or Wem? for this noun, then recall its gender.

---

## prep-fixed-acc

**id**: `prep-fixed-acc`

Shared with `prepositions.md`: every field except the items and the miss lines is copied from there unchanged.

**items**: Das Geschenk ist für ___ Vater. (akk_m_fuer_vater) · Er geht ohne ___ Schirm aus dem Haus. (akk_m_ohne_schirm) · Wir gehen durch ___ Tunnel. (akk_m_durch_tunnel) · Wir kämpfen gegen ___ Grippe. (akk_f_gegen_grippe) · Wir fahren um ___ Schloss. (akk_n_um_schloss) · Diese Blumen sind für ___ Großeltern. (akk_pl_fuer_grosseltern)

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

**miss lines**:
- Masculine — Vater, Schirm, Tunnel; right form den:
  - ✗ der: der is the nominative masculine form — the doer's form. The noun follows für,
    ohne or durch, which take the accusative, so den.
  - ✗ dem: dem is the dative form for a masculine or neuter noun. These prepositions take
    the accusative, so den.
  - ✗ des: des is the genitive masculine form. These prepositions take the accusative,
    so den.
- Feminine — Grippe; right form die:
  - ✗ der: der is the dative feminine form (or the masculine doer's form). gegen takes
    the accusative, so die.
  - ✗ den: den is the accusative masculine form. Grippe is feminine, so die.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. Grippe is feminine,
    so die.
- Neuter — Schloss; right form das:
  - ✗ den: den is the accusative masculine form. Schloss is neuter, so das.
  - ✗ dem: dem is the dative form for masculine and neuter nouns. um takes the
    accusative, so das.
  - ✗ des: des is the genitive form for masculine and neuter nouns. um takes the
    accusative, so das.
- Plural — Großeltern; right form die:
  - ✗ den: den is the dative plural form. für takes the accusative, so die.
  - ✗ der: der is the dative or genitive feminine form. Großeltern is a plural
    accusative, so die.
  - ✗ dem: dem is the dative singular form for masculine or neuter nouns. Großeltern is
    plural, so die.

**flashcard_and_miss_hint**: These prepositions always take the accusative: den, die, das, die.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## prep-fixed-dat

**id**: `prep-fixed-dat`

Shared with `prepositions.md`: every field except the items and the miss lines is copied from there unchanged.

**items**: Ich fahre mit ___ Bus zur Arbeit. (dat_m_mit_bus) · Nach ___ Unterricht gehen wir essen. (dat_m_nach_unterricht) · Er wohnt bei ___ Onkel. (dat_m_bei_onkel) · Sie kommt aus ___ Schule. (dat_f_aus_schule) · Er geht zu ___ Post. (dat_f_zu_post) · Ich spreche mit ___ Kind. (dat_n_mit_kind) · Sie kommt von ___ Konzert. (dat_n_von_konzert) · Er spricht mit ___ Kollegen. (dat_pl_mit_kollegen)

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
- Masculine — Bus, Unterricht, Onkel; right form dem:
  - ✗ der: der is the nominative masculine form — the doer's form. The noun follows mit,
    nach or bei, which take the dative, so dem.
  - ✗ den: den is the accusative masculine (or dative plural) form. These prepositions
    take the dative, so dem.
  - ✗ des: des is the genitive masculine form. These prepositions take the dative,
    so dem.
- Feminine — Schule, Post; right form der:
  - ✗ die: die is the nominative or accusative feminine form. aus and zu take the dative,
    so the dative feminine form der.
  - ✗ den: den is the accusative masculine (or dative plural) form. This noun is feminine
    dative, so der.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. Schule and Post are
    feminine, so der.
- Neuter — Kind, Konzert; right form dem:
  - ✗ das: das is the nominative or accusative neuter form. mit and von take the dative,
    so dem.
  - ✗ den: den is the accusative masculine (or dative plural) form. This noun is neuter
    dative, so dem.
  - ✗ des: des is the genitive form for masculine and neuter nouns. mit and von take the
    dative, so dem.
- Plural — Kollegen; right form den:
  - ✗ die: die is the nominative or accusative plural form. mit takes the dative, so the
    dative plural den.
  - ✗ der: der is the dative or genitive feminine form. Kollegen is a plural receiver,
    so den.
  - ✗ dem: dem is the dative singular form. Kollegen is plural, so den.

**flashcard_and_miss_hint**: These prepositions always take the dative: dem for masculine and neuter, der for feminine, den for plural.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.

---

## prep-fixed-gen

**id**: `prep-fixed-gen`

Shared with `prepositions.md`: every field except the items and the miss lines is copied from there unchanged.

**items**: Trotz ___ Regens gehen wir spazieren. (gen_m_trotz_regens) · Wegen ___ Streiks fährt heute kein Zug. (gen_m_wegen_streiks) · Während ___ Konzerts war es ruhig. (gen_n_waehrend_konzerts) · Anstatt ___ Kaffees trinkt er Tee. (gen_m_anstatt_kaffees) · Außerhalb ___ Stadt gibt es kaum Parkplätze. (gen_f_ausserhalb_stadt) · Innerhalb ___ Woche war alles erledigt. (gen_f_innerhalb_woche) · Anlässlich ___ Feier backen wir Kuchen. (gen_f_anlaesslich_feier) · Unweit ___ Bahnhofs liegt ein kleines Café. (gen_n_unweit_bahnhofs) · Jenseits ___ Dorfes liegt eine kleine Kirche. (gen_n_jenseits_dorfes) · Infolge ___ Unfalls wurde die Straße gesperrt. (gen_m_infolge_unfalls) · Trotz ___ Probleme geben wir nicht auf. (gen_pl_trotz_probleme) · Wegen ___ Bauarbeiten ist die Bahn langsam. (gen_pl_wegen_bauarbeiten)

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

**miss lines**:
- Masculine — Regens, Streiks, Kaffees, Unfalls, Bahnhofs (the noun's -s already shows the
  genitive); right form des:
  - ✗ der: der is the nominative masculine form — the doer's form. The noun follows
    trotz, wegen, anstatt, infolge or unweit, which take the genitive, so des.
  - ✗ den: den is the accusative masculine (or dative plural) form. These prepositions
    take the genitive, so des.
  - ✗ dem: dem is the dative form for a masculine or neuter noun. These prepositions take
    the genitive, so des.
- Feminine — Stadt, Woche, Feier; right form der:
  - ✗ die: die is the nominative or accusative feminine form. außerhalb, innerhalb and
    anlässlich take the genitive, so the genitive feminine form der.
  - ✗ den: den is the accusative masculine (or dative plural) form. These prepositions
    take the genitive, so der.
  - ✗ dem: dem is the dative form for masculine or neuter nouns. These nouns are
    feminine, so der.
- Neuter — Konzerts, Dorfes (the noun's -s already shows the genitive); right form des:
  - ✗ das: das is the nominative or accusative neuter form. während and jenseits
    take the genitive, so des.
  - ✗ den: den is the accusative masculine (or dative plural) form. These prepositions
    take the genitive, so des.
  - ✗ dem: dem is the dative form for masculine and neuter nouns. These prepositions take
    the genitive, so des.
- Plural — Probleme, Bauarbeiten; right form der:
  - ✗ die: die is the nominative or accusative plural form. trotz and wegen take the
    genitive here, so der.
  - ✗ den: den is the accusative masculine (or dative plural) form. These nouns are
    genitive plural, so der.
  - ✗ dem: dem is the dative singular form. These nouns are plural, so der.

**flashcard_and_miss_hint**: These prepositions take the genitive: des for masculine and neuter nouns, der for feminine and plural nouns.

**later_rung_cue**: This preposition keeps one case. Recall it, then the noun's gender.
