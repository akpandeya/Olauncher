# Word order (tile ordering) — help card content

**Count: 7 rules · 48 items covered · 0 UNSURE marks.** Every item in
`DrillWordOrder.kt` is placed under exactly one rule; no item is left over.

Notes for this topic:

- These are tile-ordering items with no gap and no article choice, so no rule needs a
  case or gender name. `first_exposure_rule` is therefore omitted for all seven rules.
- The tiles are fixed, so a miss is always a wrong order of the given tiles. Miss lines that
  described a tile the learner cannot produce (a conjugated main verb, a left-out prefix or
  participle) are replaced with orders the tiles allow.
- The answer here is the order itself, so each pre-answer rule states the position rule of its
  pattern; it names no item's sentence.
- The forms tables below are small position-pattern tables. Before an answer, no row or
  cell is marked; all rows carry equal weight. A matching cell is marked only in
  after-answer states.
- Each rule's `example` is rendered per item as that item's first accepted order, shown in the
  flashcard and after-miss states only, so a card never shows another item's answer. The verb
  parts whose position is the point are bold; the sentence given below is the rendering for one item.
- Several items accept more than one correct order; the example shows one accepted order.

---

## wo-verb-second

**id**: `wo-verb-second`

**items**: (all `VERB_SECOND` entries)

- v2_kino — Ich · gehe · morgen · ins · Kino
- v2_kaffee — Wir · trinken · heute · einen · Kaffee
- v2_buch — Er · kauft · am · Nachmittag · ein · Buch
- v2_garten — Sie · spielen · jetzt · im · Garten
- v2_deutsch — Ich · lerne · am · Wochenende · Deutsch
- v2_hause — Wir · fahren · bald · nach · Hause

**rule (pre-answer)**: In a main clause the conjugated verb is always the second element. If a time word or another element opens the clause, the subject slides in behind the verb.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**:

| | Subject first | Time first |
|---|---|---|
| Position 1 | subject | time word |
| Position 2 | verb | verb |
| Position 3 | rest | subject, then rest |

**example (flashcard and after a miss)**: rendered per item, for example: Morgen **gehe** ich ins Kino.

**miss lines**:

- ✗ *Morgen ich gehe …* — the subject stayed in front of the verb. After a fronted time word the verb comes next and the subject follows it: *Morgen gehe ich …*
- ✗ *Ich morgen gehe …* — an element slid between subject and verb. Nothing may stand between them here: *Ich gehe morgen …*
- ✗ *Gehe ich morgen …* — the verb in position 1 makes a question, not a statement. A statement keeps the verb in position 2.

**flashcard_and_miss_hint**: Either the subject or a time word may open the clause — the verb still lands in position 2.

**later_rung_cue**: Verb second; a fronted time word pushes the subject behind the verb.

---

## wo-verb-last-sub

**id**: `wo-verb-last-sub`

**items**: (all `VERB_LAST_SUBORDINATE` entries)

- sub_weil_krank — Er · bleibt · im · Bett · weil · er · krank · ist
- sub_weil_bus — Ich · komme · später · weil · der · Bus · Verspätung · hat
- sub_weil_regen — Wir · bleiben · drinnen · weil · es · heute · regnet
- sub_dass_kommen — Ich · weiß · dass · du · heute · kommst
- sub_dass_zeit — Er · sagt · dass · er · keine · Zeit · hat
- sub_dass_wetter — Wir · hoffen · dass · das · Wetter · morgen · schön · wird
- sub_wenn_sonne — Wir · fahren · an · den · See · wenn · die · Sonne · scheint
- sub_wenn_anrufen — Ich · rufe · dich · an · wenn · ich · fertig · bin
- sub_wenn_hilfe — Du · kannst · mich · fragen · wenn · du · Hilfe · brauchst
- sub_ob_kommen — Ich · weiß · nicht · ob · er · heute · kommt
- sub_ob_zeit — Sie · fragt · ob · wir · morgen · Zeit · haben
- sub_ob_tuer — Er · prüft · ob · die · Tür · geschlossen · ist

**rule (pre-answer)**: weil, dass, wenn and ob send the conjugated verb to the very end of their clause. The words in between keep normal order.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**:

| Clause type | Verb position |
|---|---|
| Main clause | position 2 |
| Clause after weil, dass, wenn, ob | the very end |

**example (flashcard and after a miss)**: rendered per item, for example: Er bleibt im Bett, weil er krank **ist**.

**miss lines**:

- ✗ *… weil er ist krank* — the verb stayed in second place. After the subjunction it goes to the very end of the clause: *… weil er krank ist.*
- ✗ *… weil ist er krank* — the subjunction already fills the first slot of its clause; the verb cannot take second place there. It closes the clause instead.
- ✗ *… weil krank er ist* — the subject left its place. Inside the weil clause the subject comes right after weil, and only the verb moves to the end: *… weil er krank ist.*

**flashcard_and_miss_hint**: The clause after the subjunction keeps normal order; only its verb moves to the end.

**later_rung_cue**: After weil, dass, wenn or ob the conjugated verb closes the clause.

---

## wo-separable-end

**id**: `wo-separable-end`

**items**: (all `SEPARABLE_VERB` entries)

- sep_aufstehen — Ich · stehe · jeden · Morgen · um · sieben · Uhr · auf
- sep_anfangen — Der · Film · fängt · um · zwanzig · Uhr · an
- sep_einkaufen — Wir · kaufen · am · Samstag · im · Supermarkt · ein
- sep_anrufen — Er · ruft · seine · Mutter · heute · Abend · an
- sep_abfahren — Der · Zug · fährt · pünktlich · in · München · ab
- sep_einschalten — Sie · schaltet · am · Abend · den · Fernseher · ein

**rule (pre-answer)**: A separable verb splits in a main clause: the conjugated part takes position 2, the prefix drops to the very end. In the infinitive the verb stays one word.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**: (example verbs, not the drill's)

| Infinitive | Position 2 | End of clause |
|---|---|---|
| mitkommen | kommt … | mit |
| fernsehen | sieht … | fern |

**example (flashcard and after a miss)**: rendered per item, for example: Ich **stehe** jeden Morgen um sieben Uhr **auf**.

**miss lines**:

- ✗ *Ich stehe auf jeden Morgen …* — the prefix stayed glued to the verb. It splits off and drops to the very end: *Ich stehe jeden Morgen … auf.*
- ✗ *Ich jeden Morgen um sieben Uhr auf stehe* — both verb parts went to the end. Only the prefix goes last; the conjugated part takes position 2: *Ich stehe jeden Morgen um sieben Uhr auf.*
- ✗ *Ich stehe jeden Morgen auf um sieben Uhr* — the prefix came before the rest of the clause. It goes after every other element: *… um sieben Uhr auf.*

**flashcard_and_miss_hint**: The verb splits: the conjugated part is early, the prefix tile belongs at the very end.

**later_rung_cue**: Conjugated verb part second, separable prefix at the very end.

---

## wo-modal-infinitive

**id**: `wo-modal-infinitive`

**items**: (all `MODAL_INFINITIVE` entries)

- mod_sprechen — Ich · kann · sehr · gut · Deutsch · sprechen
- mod_lernen — Wir · müssen · heute · für · die · Prüfung · lernen
- mod_fotografieren — Du · darfst · hier · im · Museum · nicht · fotografieren
- mod_besuchen — Er · will · morgen · seine · Großeltern · besuchen
- mod_nehmen — Sie · soll · dreimal · täglich · diese · Medizin · nehmen
- mod_ausflug — Wir · möchten · am · Wochenende · einen · Ausflug · machen

**rule (pre-answer)**: With a modal verb, the conjugated modal takes position 2 and the main verb's infinitive goes to the very end. The modal alone carries the ending.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**: (example verbs, not the drill's)

| Modal | Position 2 | End of clause |
|---|---|---|
| können | kann … | schwimmen |
| müssen | muss … | arbeiten |

**example (flashcard and after a miss)**: rendered per item, for example: Ich **kann** sehr gut Deutsch **sprechen**.

**miss lines**:

- ✗ *Ich kann sprechen sehr gut Deutsch* — the infinitive stood beside the modal. It moves to the very end: *Ich kann sehr gut Deutsch sprechen.*
- ✗ *Kann ich sehr gut Deutsch sprechen* — the modal opens the sentence, which makes a yes/no question. A statement keeps the modal in position 2.
- ✗ *Ich sehr gut Deutsch sprechen kann* — the modal went last. The conjugated modal is the position-2 verb.

**flashcard_and_miss_hint**: The conjugated modal is the position-2 verb; the infinitive tile goes to the far end.

**later_rung_cue**: Modal conjugated in position 2, infinitive at the end.

---

## wo-perfekt-bracket

**id**: `wo-perfekt-bracket`

**items**: (all `PERFEKT_BRACKET` entries)

- perf_buch — Ich · habe · gestern · ein · spannendes · Buch · gelesen
- perf_zug — Wir · sind · gestern · mit · dem · Zug · gefahren
- perf_aufgaben — Er · hat · seine · Hausaufgaben · schon · fertig · gemacht
- perf_aufgestanden — Sie · ist · heute · Morgen · sehr · früh · aufgestanden
- perf_gearbeitet — Ich · habe · den · ganzen · Tag · fleißig · gearbeitet
- perf_wohnung — Wir · haben · letzte · Woche · eine · neue · Wohnung · gefunden

**rule (pre-answer)**: In the Perfekt the helping verb takes position 2 and the participle closes the clause. The two verb parts form a bracket around the middle (Satzklammer).

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**: (example verbs, not the drill's)

| Helper | Position 2 | End of clause |
|---|---|---|
| haben | habe … | gespielt |
| sein | bin … | gekommen |

**example (flashcard and after a miss)**: rendered per item, for example: Ich **habe** gestern ein spannendes Buch **gelesen**.

**miss lines**:

- ✗ *Ich habe gelesen gestern …* — the participle stood beside the helper. It drops to the very end: *Ich habe gestern … gelesen.*
- ✗ *Ich gestern habe … gelesen* — an element stood in front of the helper. The helping verb is the conjugated verb, so it takes position 2.
- ✗ *Habe ich gestern … gelesen* — the helper opens the sentence, which makes a yes/no question. A statement keeps the helper in position 2.

**flashcard_and_miss_hint**: haben or sein is conjugated in position 2; the participle closes the clause.

**later_rung_cue**: Helping verb in position 2, participle at the end.

---

## wo-tekamolo

**id**: `wo-tekamolo`

**items**: (all `TEKAMOLO` entries)

- tekamolo_fahrrad — Ich · fahre · morgen · mit · dem · Fahrrad · zur · Arbeit
- tekamolo_hause — Er · fährt · heute · freiwillig · nach · Hause
- tekamolo_restaurant — Wir · gehen · am · Abend · gerne · ins · Restaurant
- tekamolo_italien — Sie · reist · im · Sommer · mit · dem · Zug · nach · Italien
- tekamolo_berlin — Ich · fliege · nächste · Woche · geschäftlich · nach · Berlin
- tekamolo_park — Er · joggt · jeden · Morgen · schnell · durch · den · Park

**rule (pre-answer)**: With several adverbials German prefers the order Te-Ka-Mo-Lo: time first, then cause, then manner, then place. Most sentences use only some of them.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**:

| | Te | Ka | Mo | Lo |
|---|---|---|---|---|
| asks | Wann? | Warum? | Wie? | Wo? Wohin? |

**example (flashcard and after a miss)**: rendered per item, for example: Ich fahre **morgen** mit dem Fahrrad **zur Arbeit**.

**miss lines**:

- ✗ *Ich fahre mit dem Fahrrad morgen zur Arbeit* — the manner came before the time. Wann? stands before Wie?: *… morgen mit dem Fahrrad zur Arbeit.*
- ✗ *Ich fahre morgen zur Arbeit mit dem Fahrrad* — the place came before the manner. Wohin? closes the chain: *… mit dem Fahrrad zur Arbeit.*
- ✗ *Ich fahre zur Arbeit morgen mit dem Fahrrad* — the place came first, as in English. German puts Wann? first and Wohin? last: *… morgen mit dem Fahrrad zur Arbeit.*

**flashcard_and_miss_hint**: Sort the adverbial tiles by the question they answer: Wann? before Wie? before Wohin?.

**later_rung_cue**: TeKaMoLo: time, cause, manner, place.

---

## wo-verb-first-question

**id**: `wo-verb-first-question`

**items**: (all `YES_NO_QUESTION` entries)

- q_party — Kommst · du · heute · Abend · zur · Party
- q_hausaufgaben — Hast · du · deine · Hausaufgaben · schon · gemacht
- q_kino — Gehen · wir · morgen · zusammen · ins · Kino
- q_helfen — Kannst · du · mir · bitte · helfen
- q_kaffee — Trinkst · du · morgens · gerne · Kaffee
- q_hamburg — Fährst · du · am · Wochenende · nach · Hamburg

**rule (pre-answer)**: A yes/no question opens with the conjugated verb, followed directly by the subject. The rest of the clause keeps its normal order.

**first_exposure_rule**: omitted. Word order needs no case or gender name.

**forms table**:

| | Position 1 | Position 2 |
|---|---|---|
| Statement | subject | verb |
| Yes/no question | verb | subject |

**example (flashcard and after a miss)**: rendered per item, for example: **Kommst** du heute Abend zur Party?

**miss lines**:

- ✗ *Du kommst heute Abend zur Party* — statement order. As a yes/no question the verb moves in front of the subject: *Kommst du heute Abend zur Party?*
- ✗ *Heute Abend kommst du …* — the verb in position 2. In a yes/no question the conjugated verb opens the sentence; nothing stands before it.
- ✗ *Du heute Abend kommst zur Party* — the verb stood after other elements. With no question word, the conjugated verb itself takes position 1.

**flashcard_and_miss_hint**: There is no question word here, so the conjugated verb itself opens the question.

**later_rung_cue**: Yes/no question: conjugated verb first, subject right after it.
