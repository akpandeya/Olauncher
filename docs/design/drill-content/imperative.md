# Imperative help-card content (family `imp`)

4 rules · 10 of 10 items covered, each exactly once · 0 `UNSURE:` marks.

Conventions (author notes, not learner-facing):

- Layout follows `articles.md`: id, items, rule, first_exposure_rule, forms table, example,
  miss lines, flashcard_and_miss_hint, later_rung_cue.
- No existing ID fits. Separable prefixes (zu, an, aus) already sit at the end of the prompts,
  so they are not a rule of their own.
- Regrouped in review: the former `imp-du` and `imp-ihr` rules each named the person their
  items need (a pre-answer leak), so they are merged into `imp-person`, which names du, ihr
  and Sie with equal weight. The two sein items moved to `imp-sein` (formerly
  `imp-du-exception`).
- Forms tables use verbs that are not drill items, so no table names an item's answer.
- `first_exposure_rule` is omitted except in `imp-sie-infinitive`, where it states the Sie
  pattern the table already shows; no rule names a case or gender.
- Data fixed in review (`items/imperative.md`): five prompts now end with the person addressed
  (…, Paul! · …, Anna! · …, Kinder, … · …, Max! · …, Kinder, …), so only one person's form fits.

---

## imp-person

**id**: `imp-person`

**items**:

- ___ bitte die Tür zu, Paul!
- ___ eure Schuhe an!
- ___ nicht so schnell, Kinder, wir haben Zeit!

**rule (pre-answer)**: German has one imperative per way of addressing: du drops the -st and keeps the stem, ihr takes stem + t, Sie uses the infinitive. Decide whom it addresses.

**first_exposure_rule**: omitted. No case or gender is named, and the rule already gives all three forms with equal weight.

**forms table**: (example verbs, not the drill's)

| | du | ihr | Sie |
|---|---|---|---|
| kommen | Komm! | Kommt! | Kommen Sie! |
| helfen | Hilf! | Helft! | Helfen Sie! |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the column of the person addressed.

**example (flashcard and after a miss)**: rendered per item with its own sentence, verb bold, for example: **Zieht** eure Schuhe an!

**miss lines**:

- ___ bitte die Tür zu, Paul! (Mach)
  - ✗ *Macht*: the ihr form, for several people. Paul is one person you know, so Mach.
  - ✗ *Machst*: the du present tense, not a command. The du imperative drops the -st: Mach.
  - ✗ *Machen*: the formal form, which always needs Sie after it. Paul is addressed informally, so Mach.
- ___ eure Schuhe an! (Zieht)
  - ✗ *Zieh*: the du form. eure shows several people are addressed, so Zieht.
  - ✗ *Ziehen*: the formal form, which needs Sie after it. eure shows informal plural address, so Zieht.
  - ✗ *Ziehst*: the du present tense, not a command. The ihr imperative is stem + t: Zieht.
- ___ nicht so schnell, Kinder, wir haben Zeit! (Esst)
  - ✗ *Iss*: the du imperative, with its vowel change. Kinder are several people, so Esst.
  - ✗ *Isst*: the present tense. The ihr imperative keeps the stem and adds -t: Esst.
  - ✗ *Ess*: not a form. du changes the vowel (Iss), and ihr adds -t to the plain stem: Esst.

**flashcard_and_miss_hint**: du: bare stem. ihr: stem + t. Sie: infinitive followed by Sie. Words like eure, euch or a name show who is addressed.

**later_rung_cue**: Whom are you addressing: one person (du), several (ihr) or formally (Sie)?

---

## imp-du-vowel-change

**id**: `imp-du-vowel-change`

**items**:

- ___ mir bitte das Salz!
- ___ die E-Mail noch einmal, Anna!

**rule (pre-answer)**: Imperatives match the person you address. In the du imperative, many strong verbs change the stem vowel (e → i or ie); ihr and Sie keep the normal stem.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**: (example verbs, not the drill's)

| | du | ihr | Sie |
|---|---|---|---|
| sprechen | Sprich! | Sprecht! | Sprechen Sie! |
| sehen | Sieh! | Seht! | Sehen Sie! |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the du column.

**example (flashcard and after a miss)**: rendered per item with its own sentence, verb bold, for example: **Gib** mir bitte das Salz!

**miss lines**:

- ___ mir bitte das Salz! (Gib)
  - ✗ *Gibst*: the du present tense, not a command. The du imperative drops the -st: Gib.
  - ✗ *Geb*: geben is a strong verb, so its stem vowel changes in the du imperative: Gib.
  - ✗ *Gibt*: the er/sie/es present tense (er gibt), not a command. The du imperative is Gib.
- ___ die E-Mail noch einmal, Anna! (Lies)
  - ✗ *Lest*: the ihr form. Anna is one person, so Lies.
  - ✗ *Les*: lesen changes e to ie in the du imperative, so the stem is not Les: Lies.
  - ✗ *Liest*: the du present tense. The imperative drops the -st: Lies.

**flashcard_and_miss_hint**: Strong verbs with e change it for du only: sprechen → sprich, sehen → sieh.

**later_rung_cue**: Whom are you addressing? Strong verbs change the stem vowel only for du.

---

## imp-sein

**id**: `imp-sein`

**items**:

- ___ bitte leise, Kinder, das Baby schläft!
- ___ bitte vorsichtig mit dem Glas, Max!

**rule (pre-answer)**: The verb sein has its own imperative forms, one for each way of addressing. Decide whom the sentence addresses.

**first_exposure_rule**: omitted. No case or gender is named before an answer.

**forms table**:

| | du | ihr | Sie |
|---|---|---|---|
| Present | bist | seid | sind |
| Imperative | Sei! | Seid! | Seien Sie! |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell.

**example (flashcard and after a miss)**: rendered per item with its own sentence, verb bold, for example: **Sei** bitte vorsichtig mit dem Glas!

**miss lines**:

- ___ bitte leise, Kinder, das Baby schläft! (Seid)
  - ✗ *Sei*: the du imperative of sein. Kinder are several people, so Seid.
  - ✗ *Sind*: the present tense, not an imperative. The ihr imperative of sein is Seid.
  - ✗ *Sein*: the infinitive. The ihr imperative of sein is Seid.
- ___ bitte vorsichtig mit dem Glas, Max! (Sei)
  - ✗ *Bist*: the du present tense. The du imperative of sein drops the ending: Sei.
  - ✗ *Seid*: the ihr form. Max is one person, so Sei.
  - ✗ *Seist*: not a German form. The du imperative of sein is Sei.

**flashcard_and_miss_hint**: sein is irregular: Sei for du, Seid for ihr, Seien Sie for formal address.

**later_rung_cue**: sein has its own imperative forms — check whom you are addressing.

---

## imp-sie-infinitive

**id**: `imp-sie-infinitive`

**items**:

- ___ Sie bitte Platz!
- ___ Sie bitte etwas Geduld!
- ___ Sie das Formular bitte ganz aus!

**rule (pre-answer)**: German has an imperative form for each person addressed: du, ihr, or formal Sie. This sentence addresses Sie formally, so recall the form that goes with it.

**first_exposure_rule**: (flashcard and first tap only) The formal Sie imperative is the infinitive, the dictionary form of the verb: Kommen Sie bitte!

**forms table**: (example verbs, not the drill's)

| | du | ihr | Sie |
|---|---|---|---|
| kommen | Komm! | Kommt! | Kommen Sie! |
| warten | Warte! | Wartet! | Warten Sie! |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the Sie column.

**example (flashcard and after a miss)**: rendered per item with its own sentence, verb bold, for example: **Nehmen** Sie bitte Platz!

**miss lines**:

- ___ Sie bitte Platz! (Nehmen)
  - ✗ *Nimm*: the du imperative. Sie marks formal address, which uses the infinitive: Nehmen.
  - ✗ *Nehmt*: the ihr form. Formal address uses the infinitive: Nehmen.
  - ✗ *Nimmt*: the present tense (er/sie/es). The Sie imperative is the infinitive: Nehmen.
- ___ Sie bitte etwas Geduld! (Haben)
  - ✗ *Hab*: the du imperative of haben. Sie is formal, so the infinitive: Haben.
  - ✗ *Habt*: the ihr form. Formal address uses the infinitive: Haben.
  - ✗ *Hast*: the du present tense. The Sie imperative is the infinitive: Haben.
- ___ Sie das Formular bitte ganz aus! (Füllen)
  - ✗ *Füll*: the du imperative. Sie is formal, so the infinitive: Füllen.
  - ✗ *Füllt*: the ihr form. Formal address uses the infinitive: Füllen.
  - ✗ *Füllst*: the du present tense. The Sie imperative is the infinitive: Füllen.

**flashcard_and_miss_hint**: Sie marks formal address, and its imperative is the infinitive.

**later_rung_cue**: This sentence uses formal address; recall its imperative form.

---

## Coverage check

| Rule | Items |
|---|---|
| imp-person | 3 (items 1, 4, 10) |
| imp-du-vowel-change | 2 (items 2, 3) |
| imp-sein | 2 (items 5, 6) |
| imp-sie-infinitive | 3 (items 7, 8, 9) |

Total: 10 items, each placed exactly once.
