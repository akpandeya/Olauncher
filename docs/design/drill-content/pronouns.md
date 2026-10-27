# Pronouns drill: help-card content

Source: `app/src/main/java/app/olauncher/data/store/DrillPronouns.kt` (personal, reflexive,
possessive). Family: `pron`.

**Count: 7 rules · 98 items covered · 0 `UNSURE:` marks (both resolved; the data was fixed in review) · 0 items unplaced.**

Existing IDs reused: none — every ID above (`art-*`, `prep-*`) belongs to the articles or
prepositions topics; none describes a pronoun rule, so all IDs here are new.

Conventions used below (from `docs/design/drill-help-card.md`):
- Wer? / Wen? / Wem? / Wessen? label the jobs (doer / directly affected / receiver / owner)
  in pre-answer text, so no case name is revealed before an answer. Miss lines name cases
  freely, because they appear only after a miss.
- All forms tables are shown with equal weight before an answer; no cell is marked. The
  matching cell is marked only in flashcard and after-a-miss states.
- `first_exposure_rule` is omitted in every rule: the design allows a case name before an
  answer only for one-case prepositions, and no pronoun rule needs one.

---

## pron-first-second

Personal pronouns of the people talking: ich, du, wir, ihr — nominative, accusative and
dative items (the case name is the recall target, so it is never named before the answer).

**id**: `pron-first-second`

**items**:

- Heute habe ___ leider keine Zeit für Sport. (ich)
- Mein bester Freund besucht ___ heute Nachmittag. (mich)
- Kannst du ___ bitte bei der Aufgabe helfen? (mir)
- Wann kommst ___ heute Abend nach Hause? (du)
- Ich habe ___ gestern auf dem Markt gesehen. (dich)
- Wie schmeckt ___ das Essen im Restaurant? (dir)
- Meine Frau und ich haben Urlaub. Im Sommer fahren ___ gemeinsam nach Italien. (wir)
- Der Lehrer lobt ___ für unsere gute Arbeit. (uns)
- Wir sind zum ersten Mal in Prag. Der Reiseleiter zeigt ___ die historische Altstadt. (uns)
- Kinder, warum macht ___ so viel Lärm? (ihr)
- Kinder, wo wart ihr? Ich habe ___ überall im Park gesucht. (euch)
- Kinder, Oma schenkt ___ ein spannendes Brettspiel. (euch)

**rule (pre-answer)**: A pronoun's form follows its job: the doer (Wer?) keeps the base form; the person
directly affected (Wen?) and the receiver (Wem?) have their own forms.

**first_exposure_rule**: omitted — for these items the case name is what the learner must
recall, so no case is named before the answer.

**forms table**:

| | ich | du | wir | ihr |
|---|---|---|---|---|
| Wer? | ich | du | wir | ihr |
| Wen? | mich | dich | uns | euch |
| Wem? | mir | dir | uns | euch |

**example (flashcard and after a miss)**: rendered per item with its own sentence, pronoun bold, for example: Der Lehrer lobt **uns** für unsere gute Arbeit.

**miss lines**:

- „Heute habe ___ …" (ich)
  - ✗ *mich*: the accusative form of ich — the pronoun is the doer (nominative), so ich.
  - ✗ *mir*: the dative form of ich — again the doer, not the receiver, so ich.
  - ✗ *du*: a different person — „habe" with no ending goes with ich.
- „… besucht ___ heute Nachmittag." (mich)
  - ✗ *mir*: the dative form — besuchen acts directly on someone (Wen?), so mich.
  - ✗ *ich*: the doer form — the doer is mein bester Freund; the one visited is mich.
  - ✗ *dich*: the du form — the sentence is about me, so mich.
- „Kannst du ___ … helfen?" (mir)
  - ✗ *mich*: the accusative form — helfen gives help TO someone (Wem?), so mir.
  - ✗ *ich*: the doer form — the doer is du; the pronoun receives the help, so mir.
  - ✗ *dir*: the dative form of du — the help comes to me, so mir.
- „Wann kommst ___ …?" (du)
  - ✗ *dich*: the accusative form of du — the pronoun does the verb (kommst), so du.
  - ✗ *dir*: the dative form of du — still the doer, so du.
  - ✗ *ihr*: you-all — „kommst" is the du form, so du.
- „Ich habe ___ … gesehen." (dich)
  - ✗ *dir*: the dative form — sehen acts directly on someone, so dich.
  - ✗ *du*: the doer form — the doer is ich; the person seen is dich.
  - ✗ *mich*: the accusative form of ich — you were seen, not me, so dich.
- „Wie schmeckt ___ das Essen …?" (dir)
  - ✗ *dich*: the accusative form — schmecken tells how food tastes TO someone (Wem?), so dir.
  - ✗ *du*: the doer form — the food does the tasting (schmeckt); you receive, so dir.
  - ✗ *mir*: the dative form of ich — the question is about you, so dir.
- „Meine Frau und ich … Im Sommer fahren ___ gemeinsam …" (wir)
  - ✗ *uns*: the object form of wir — the pronoun does the verb (fahren), so wir.
  - ✗ *ihr*: you-all — ihr takes fahrt, not fahren, so wir.
  - ✗ *sie*: they — the travellers are meine Frau und ich, a group that includes the speaker, so wir.
- „Der Lehrer lobt ___ …" (uns)
  - ✗ *wir*: the doer form — the teacher is the doer; we are praised, so uns.
  - ✗ *euch*: the object form of ihr — „unsere" says the group includes the speaker, so uns.
  - ✗ *sie*: the object form of they — unsere points to wir, so uns.
- „Wir sind … in Prag. Der Reiseleiter zeigt ___ … Altstadt." (uns)
  - ✗ *wir*: the doer form — the guide is the doer; the showing goes to us, so uns.
  - ✗ *euch*: to you-all — the visitors are wir (Wir sind in Prag), so uns.
  - ✗ *ihnen*: to them — the visitors are wir, so the receiver form uns.
- „Kinder, warum macht ___ so viel Lärm?" (ihr)
  - ✗ *euch*: the object form of ihr — the children do the verb (macht), so ihr.
  - ✗ *wir*: we — the children are addressed, so ihr.
  - ✗ *sie*: they — „macht" goes with ihr (ihr macht), so ihr.
- „Kinder, wo wart ihr? Ich habe ___ überall im Park gesucht." (euch)
  - ✗ *ihr*: the doer form of you-all — the doer is ich; the ones sought are euch.
  - ✗ *uns*: us — the speaker addresses the children (wo wart ihr?), so euch.
  - ✗ *sie*: them — the children are addressed as ihr, so euch.
- „Kinder, Oma schenkt ___ ein … Brettspiel." (euch)
  - ✗ *ihr*: the doer form of you-all — Oma is the doer; the gift goes to euch.
  - ✗ *uns*: to us — Kinder addresses the children, so the gift goes to euch.
  - ✗ *ihnen*: to them — the children are addressed directly, so euch.

**flashcard_and_miss_hint**: Ask who does the verb. If the pronoun is affected, ask Wen? or
Wem? after that verb.

**later_rung_cue**: Find the doer. If the pronoun is not the doer, ask Wen? or Wem? after this
verb.

---

## pron-third

Third-person pronouns er, sie, es, sie (pl) — nominative, accusative and dative items. The
pronoun copies the replaced noun's gender, so the rule may speak of gender in general, never
of this item's answer.

**id**: `pron-third`

**items**:

- Thomas ist müde, deshalb schläft ___ schon. (er)
- Herr Weber ist nicht da, wir rufen ___ später an. (ihn)
- Paul ist zufrieden, der neue Pullover passt ___ wie angegossen. (ihm)
- Maria lernt viel, denn ___ möchte Ärztin werden. (sie)
- Ich habe Anna lange nicht gesehen und besuche ___ morgen. (sie)
- Lisa ist stolz, das schöne Fahrrad gehört ___ ganz allein. (ihr)
- Wo ist das Buch? ___ liegt auf dem Schreibtisch. (Es)
- Das Bild ist wunderschön, ich kaufe ___ sofort. (es)
- Dem Baby ist kalt, gib ___ bitte eine Decke! (ihm)
- Die Nachbarn sind freundlich, ___ helfen uns oft. (sie)
- Ich mag die Kollegen und lade ___ zum Essen ein. (sie)
- Die Gäste kommen an, wir bieten ___ Kaffee an. (ihnen)

**rule (pre-answer)**: A third-person pronoun copies the gender of the noun it replaces; sie also stands for plurals. Its form follows the job: Wer?, Wen? or Wem?

**first_exposure_rule**: omitted — the case name is the recall target for these items.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Wer? | er | sie | es | sie |
| Wen? | ihn | sie | es | sie |
| Wem? | ihm | ihr | ihm | ihnen |

**example (flashcard and after a miss)**: rendered per item with its own sentence, pronoun bold, for example: Herr Weber ist nicht da, wir rufen **ihn** später an.

**miss lines**:

- „Thomas ist müde, … schläft ___ schon." (er)
  - ✗ *ihn*: the accusative form — Thomas does the sleeping (nominative), so er.
  - ✗ *ihm*: the dative form — still the doer, so er.
  - ✗ *sie*: she/they — Thomas is one man, so er.
- „… wir rufen ___ später an." (ihn)
  - ✗ *ihm*: the dative form — anrufen acts directly on someone, so ihn.
  - ✗ *er*: the doer form — the doer is wir; the one called is ihn.
  - ✗ *sie*: her/they — Herr Weber is masculine, so ihn.
- „Paul ist zufrieden, der neue Pullover passt ___ wie angegossen." (ihm)
  - ✗ *ihn*: the accusative form — passen tells how something fits someone (Wem?), so ihm.
  - ✗ *er*: the doer form — the pullover does the fitting; the wearer receives, so ihm.
  - ✗ *ihr*: to her — the wearer is Paul, a man, so ihm.
- „Maria lernt viel, denn ___ möchte Ärztin werden." (sie)
  - ✗ *ihr*: the dative form — Maria does the wanting (nominative), so sie.
  - ✗ *ihnen*: to them — Maria is one person, so sie.
  - ✗ *er*: he — Maria is feminine, so sie.
- „… und besuche ___ morgen." (sie)
  - ✗ *ihr*: the dative form — besuchen acts directly on someone, so sie.
  - ✗ *ihnen*: to them — Anna is one person, so sie.
  - ✗ *ihn*: the masculine object form — Anna is feminine, so sie.
- „Lisa ist stolz, das schöne Fahrrad gehört ___ ganz allein." (ihr)
  - ✗ *sie*: the doer / direct-object form — gehören says something belongs TO someone
    (Wem?), so ihr.
  - ✗ *ihm*: to him — the owner is Lisa, a woman, so ihr.
  - ✗ *ihnen*: to them — Lisa is one person, so ihr.
- „Wo ist das Buch? ___ liegt auf dem Schreibtisch." (Es)
  - ✗ *Er*: masculine — das Buch is neuter, so es.
  - ✗ *Sie*: feminine/plural — das Buch is neuter, so es.
  - ✗ *Ihm*: the dative form — the book does the verb (liegt), so the doer form es.
- „… ich kaufe ___ sofort." (es)
  - ✗ *ihn*: the masculine object form — das Bild is neuter, so es.
  - ✗ *ihm*: the dative form — kaufen acts directly on it, so es.
  - ✗ *sie*: the feminine/plural object form — das Bild is neuter, so es.
- „Dem Baby ist kalt, gib ___ bitte eine Decke!" (ihm)
  - ✗ *es*: the doer form — the pronoun receives the blanket (Wem?), so ihm.
  - ✗ *ihn*: the accusative form — the blanket is the direct object; the baby receives, so
    ihm.
  - ✗ *ihr*: to her — das Baby is neuter, so ihm.
- „Die Nachbarn sind freundlich, ___ helfen uns oft." (sie)
  - ✗ *ihnen*: the dative form — the neighbours do the helping (nominative), so sie.
  - ✗ *ihr*: to her — the neighbours are many, so sie.
  - ✗ *wir*: we — the speakers are the receivers (uns); the helpers are the neighbours, so
    sie.
- „… lade ___ zum Essen ein." (sie)
  - ✗ *ihnen*: the dative form — einladen acts directly on someone, so sie.
  - ✗ *ihn*: him — die Kollegen are plural, so sie.
  - ✗ *ihr*: to her — plural is meant, so sie.
- „… wir bieten ___ Kaffee an." (ihnen)
  - ✗ *sie*: the doer / direct-object form — anbieten gives coffee TO the guests (Wem?), so
    ihnen.
  - ✗ *ihm*: to him — the guests are plural, so ihnen.
  - ✗ *ihr*: to her — plural is meant, so ihnen.

**flashcard_and_miss_hint**: The pronoun copies the noun's gender; the verb decides the row:
doing, affected or receiving.

**later_rung_cue**: Take the pronoun for the noun's gender, then pick the row for its job.

---

## pron-formal

The formal Sie — nominative, accusative and dative items. The doer and the directly-affected
form are the same word; only the receiver form differs.

**id**: `pron-formal`

**items**:

- Frau Müller, kommen ___ morgen zum Treffen? (Sie)
- Herr Schneider, ich habe ___ gestern im Büro gesucht. (Sie)
- Darf ich ___ behilflich sein, Herr Direktor? (Ihnen)

**rule (pre-answer)**: The formal word for you barely changes: doer and directly affected are the same
word; only the receiver form is different. Decide its job first.

**first_exposure_rule**: omitted — the case name is the recall target for these items.

**forms table**:

| | Wer? | Wen? | Wem? |
|---|---|---|---|
| Sie | Sie | Sie | Ihnen |

**example (flashcard and after a miss)**: rendered per item with its own sentence, pronoun bold, for example: Darf ich **Ihnen** behilflich sein, Herr Direktor?

**miss lines**:

- „Frau Müller, kommen ___ morgen zum Treffen?" (Sie)
  - ✗ *Ihnen*: the dative form — the person asked does the coming (nominative), so Sie.
  - ✗ *du*: the informal word — Frau Müller is addressed formally, so Sie.
  - ✗ *ihr*: you-all — ihr takes kommt, not kommen, and Frau Müller is addressed formally, so Sie.
- „Herr Schneider, ich habe ___ gestern … gesucht." (Sie)
  - ✗ *Ihnen*: the dative form — suchen acts directly on someone, so Sie.
  - ✗ *ihn*: the masculine object form — the formal pronoun stays Sie.
  - ✗ *dich*: the informal object form — formal address uses Sie.
- „Darf ich ___ behilflich sein, Herr Direktor?" (Ihnen)
  - ✗ *Sie*: the doer form — behilflich sein helps TO someone (Wem?), so Ihnen.
  - ✗ *ihm*: to him — the formal receiver form is Ihnen.
  - ✗ *dir*: the informal receiver form — formal address uses Ihnen.

**flashcard_and_miss_hint**: Formal you: the doer and Wen? word are both Sie; only the Wem?
job changes to Ihnen.

**later_rung_cue**: Two of the three jobs share one word; only the receiver form is
different.

---

## pron-reflex-shift

Reflexive pronouns of ich and du where the choice is between the accusative and the dative
form (mich/mir, dich/dir). The case name is the recall target, so the rule states the
criterion and the learner applies it.

**id**: `pron-reflex-shift`

**items**:

- Ich gehe ins Bad und wasche ___. (mich)
- Vor dem Essen wasche ich ___ gründlich die Hände. (mir)
- Wäschst du ___ morgens immer kalt? (dich)
- Du wäschst ___ das Gesicht mit kaltem Wasser. (dir)
- Ich habe es eilig, aber ich kämme ___ noch schnell. (mich)
- Jeden Morgen kämme ich ___ die Haare vor dem Spiegel. (mir)
- Zieh ___ bitte schnell an, wir müssen los! (dich)
- Zieh ___ lieber eine warme Jacke an! (dir)
- Ich putze ___ für die große Party heraus. (mich)
- Nach jeder Mahlzeit putze ich ___ die Zähne. (mir)
- Ich freue ___ schon riesig auf das Wochenende. (mich)
- Kannst du ___ vorstellen, wie spannend das war? (dir)
- Darf ich ___ kurz vorstellen? Mein Name ist Felix. (mich)
- Ich mache ___ große Sorgen um deine Zukunft. (mir)
- Mach ___ keine Sorgen, alles wird gut gehen! (dir)
- Wie fühlst du ___ nach dem langen Lauf? (dich)
- Zieh die Mütze auf, sonst erkältest du ___ noch! (dich)
- Bei dem lauten Straßenlärm kann ich ___ kaum konzentrieren. (mich)
- Nach der anstrengenden Schicht ruhe ich ___ auf dem Sofa aus. (mich)
- Was wünschst du ___ eigentlich zum Geburtstag? (dir)
- Diese wichtige PIN muss ich ___ gut merken. (mir)

**rule (pre-answer)**: A reflexive verb acts back on its doer. If nothing else is affected, the pronoun
takes the Wen? form; if a thing is already affected, the Wem? form.

**first_exposure_rule**: omitted — the case name is the recall target for these items.

**forms table**:

| | Wen? | Wem? |
|---|---|---|
| ich | mich | mir |
| du | dich | dir |
| er · sie · es | sich | sich |

**example (flashcard and after a miss)**: rendered per item with its own sentence, pronoun bold, for example: Vor dem Essen wasche ich **mir** gründlich die Hände.

**miss lines**:

- „… und wasche ___." (mich)
  - ✗ *mir*: the dative form — nothing else is affected, the washing lands on me directly,
    so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „… wasche ich ___ gründlich die Hände." (mir)
  - ✗ *mich*: the accusative form — die Hände already fill that role, so the pronoun shifts
    to the dative mir.
  - ✗ *dir*: the du form — the doer is ich, so mir.
  - ✗ *sich*: the third-person form — ich takes mir.
- „Wäschst du ___ morgens immer kalt?" (dich)
  - ✗ *dir*: the dative form — nothing else is affected here, so dich.
  - ✗ *mich*: the ich form — the doer is du, so dich.
  - ✗ *sich*: the third-person form — du takes dich.
- „Du wäschst ___ das Gesicht …" (dir)
  - ✗ *dich*: the accusative form — das Gesicht already fills that role, so dir.
  - ✗ *mir*: the ich form — the doer is du, so dir.
  - ✗ *sich*: the third-person form — du takes dir.
- „… ich kämme ___ noch schnell." (mich)
  - ✗ *mir*: the dative form — nothing else is affected, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „Jeden Morgen kämme ich ___ die Haare …" (mir)
  - ✗ *mich*: the accusative form — die Haare already fill that role, so mir.
  - ✗ *dir*: the du form — the doer is ich, so mir.
  - ✗ *sich*: the third-person form — ich takes mir.
- „Zieh ___ bitte schnell an, …" (dich)
  - ✗ *dir*: the dative form — nothing else is affected, so dich.
  - ✗ *mich*: the ich form — the doer is du, so dich.
  - ✗ *sich*: the third-person form — du takes dich.
- „Zieh ___ lieber eine warme Jacke an!" (dir)
  - ✗ *dich*: the accusative form — eine Jacke already fills that role, so dir.
  - ✗ *mir*: the ich form — the doer is du, so dir.
  - ✗ *sich*: the third-person form — du takes dir.
- „Ich putze ___ für die große Party heraus." (mich)
  - ✗ *mir*: the dative form — nothing else is affected, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „… putze ich ___ die Zähne." (mir)
  - ✗ *mich*: the accusative form — die Zähne already fill that role, so mir.
  - ✗ *dir*: the du form — the doer is ich, so mir.
  - ✗ *sich*: the third-person form — ich takes mir.
- „Ich freue ___ schon riesig …" (mich)
  - ✗ *mir*: the dative form — freuen acts directly back onto the doer, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „Kannst du ___ vorstellen, wie spannend das war?" (dir)
  - ✗ *dich*: the accusative form — the imagined thing already fills that role, so dir.
  - ✗ *mir*: the ich form — the doer is du, so dir.
  - ✗ *sich*: the third-person form — du takes dir.
- „Darf ich ___ kurz vorstellen? …" (mich)
  - ✗ *mir*: the dative form — nothing else is affected, I present myself directly, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „Ich mache ___ große Sorgen …" (mir)
  - ✗ *mich*: the accusative form — die Sorgen already fill that role, so mir.
  - ✗ *dir*: the du form — the doer is ich, so mir.
  - ✗ *sich*: the third-person form — ich takes mir.
- „Mach ___ keine Sorgen, …" (dir)
  - ✗ *dich*: the accusative form — keine Sorgen already fill that role, so dir.
  - ✗ *mir*: the ich form — the doer is du, so dir.
  - ✗ *sich*: the third-person form — du takes dir.
- „Wie fühlst du ___ nach dem langen Lauf?" (dich)
  - ✗ *dir*: the dative form — nothing else is affected, so dich.
  - ✗ *mich*: the ich form — the doer is du, so dich.
  - ✗ *sich*: the third-person form — du takes dich.
- „… erkältest du ___ noch!" (dich)
  - ✗ *dir*: the dative form — nothing else is affected, so dich.
  - ✗ *mich*: the ich form — the doer is du, so dich.
  - ✗ *sich*: the third-person form — du takes dich.
- „… kann ich ___ kaum konzentrieren." (mich)
  - ✗ *mir*: the dative form — nothing else is affected, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „… ruhe ich ___ auf dem Sofa aus." (mich)
  - ✗ *mir*: the dative form — ausruhen acts directly back onto the doer, so mich.
  - ✗ *dich*: the du form — the doer is ich, so mich.
  - ✗ *sich*: the third-person form — ich takes mich.
- „Was wünschst du ___ eigentlich zum Geburtstag?" (dir)
  - ✗ *dich*: the accusative form — the wished-for thing already fills that role, so dir.
  - ✗ *mir*: the ich form — the doer is du, so dir.
  - ✗ *sich*: the third-person form — du takes dir.
- „Diese wichtige PIN muss ich ___ gut merken." (mir)
  - ✗ *mich*: the accusative form — die PIN already fills that role, so mir.
  - ✗ *dir*: the du form — the doer is ich, so mir.
  - ✗ *sich*: the third-person form — ich takes mir.

**flashcard_and_miss_hint**: No other affected thing → the Wen? form. An affected thing
already in the sentence → the Wem? form.

**later_rung_cue**: Look for a second affected thing: none → Wen? form, one → Wem? form.

---

## pron-reflex-person

Reflexive verbs whose choice set contains only object forms of different persons (uns, euch,
sich, mich, dich). Person is the discriminator; no case name is needed.

**id**: `pron-reflex-person`

**items**:

- Er erinnert ___ gern an die Zeit in Wien. (sich)
- Wir müssen ___ beeilen, der Zug fährt gleich ab. (uns)
- Lisa interessiert ___ sehr für moderne Kunst. (sich)
- Setzt ___ bitte auf eure Plätze, der Film beginnt! (euch)
- Mein Kollege ärgert ___ über den verspäteten Zug. (sich)
- Wir haben ___ für das kleinere Apartment entschieden. (uns)
- Wann trefft ihr ___ heute mit euren Freunden? (euch)
- Wir bedanken ___ herzlich bei allen Helfern. (uns)
- Die Kinder langweilen ___ bei der langen Autofahrt. (sich)
- Er hat ___ beim Fußballspielen am Knie verletzt. (sich)
- Wir haben ___ schnell an das neue Klima gewöhnt. (uns)

**rule (pre-answer)**: Some verbs act back on their doer. The reflexive pronoun matches the subject: it is
the doer's own object form. Find the person in the verb ending.

**first_exposure_rule**: omitted. The design allows a case name before an answer only for one-case prepositions, and the rule needs none: the person decides.

**forms table**:

| | ich | du | er · sie · es · sie (pl) | wir · ihr |
|---|---|---|---|---|
| Wer? | ich | du | er · sie · es · sie | wir · ihr |
| reflexive | mich | dich | sich | uns · euch |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the column of the subject's person and bold the pronoun.

**example (flashcard and after a miss)**: rendered per item with its own sentence, pronoun bold, for example: Er erinnert **sich** gern an die Zeit in Wien.

**miss lines**:

- „Er erinnert ___ gern an die Zeit in Wien." (sich)
  - ✗ *mich*: me — the doer is er, so the reflexive sich.
  - ✗ *dich*: you — the doer is er, so sich.
  - ✗ *uns*: us — the doer is er, so sich.
- „Wir müssen ___ beeilen, …" (uns)
  - ✗ *euch*: you-all — the doer is wir (müssen wir), so uns.
  - ✗ *sich*: the third-person form — the doer is wir, so uns.
  - ✗ *mich*: me — the doer is wir, so uns.
- „Lisa interessiert ___ sehr für moderne Kunst." (sich)
  - ✗ *mich*: me — the doer is Lisa (sie), so sich.
  - ✗ *dich*: you — the doer is sie, so sich.
  - ✗ *uns*: us — the doer is sie, so sich.
- „Setzt ___ bitte auf eure Plätze, …" (euch)
  - ✗ *uns*: us — the doer is ihr (eure Plätze), so euch.
  - ✗ *sich*: the third-person form — the doer is ihr, so euch.
  - ✗ *dich*: the du form — the doer is ihr, so euch.
- „Mein Kollege ärgert ___ über den verspäteten Zug." (sich)
  - ✗ *mich*: me — the doer is der Kollege (er), so sich.
  - ✗ *uns*: us — the doer is er, so sich.
  - ✗ *euch*: you-all — the doer is er, so sich.
- „Wir haben ___ für das kleinere Apartment entschieden." (uns)
  - ✗ *euch*: you-all — the doer is wir, so uns.
  - ✗ *sich*: the third-person form — the doer is wir, so uns.
  - ✗ *mich*: me — the doer is wir, so uns.
- „Wann trefft ihr ___ heute mit euren Freunden?" (euch)
  - ✗ *uns*: us — the doer is ihr (trefft ihr), so euch.
  - ✗ *sich*: the third-person form — the doer is ihr, so euch.
  - ✗ *dich*: the du form — the doer is ihr, so euch.
- „Wir bedanken ___ herzlich bei allen Helfern." (uns)
  - ✗ *euch*: you-all — the doer is wir, so uns.
  - ✗ *sich*: the third-person form — the doer is wir, so uns.
  - ✗ *mich*: me — the doer is wir, so uns.
- „Die Kinder langweilen ___ bei der langen Autofahrt." (sich)
  - ✗ *mich*: me — the doer is die Kinder (sie), so sich.
  - ✗ *uns*: us — the doer is sie, so sich.
  - ✗ *euch*: you-all — the doer is sie, so sich.
- „Er hat ___ beim Fußballspielen am Knie verletzt." (sich)
  - ✗ *mich*: me — the doer is er, so sich.
  - ✗ *dich*: you — the doer is er, so sich.
  - ✗ *uns*: us — the doer is er, so sich.
- „Wir haben ___ schnell an das neue Klima gewöhnt." (uns)
  - ✗ *euch*: you-all — the doer is wir, so uns.
  - ✗ *sich*: the third-person form — the doer is wir, so uns.
  - ✗ *mich*: me — the doer is wir, so uns.

**flashcard_and_miss_hint**: Match the reflexive pronoun to the subject: wir → uns, ihr →
euch, er/sie/es → sich.

**later_rung_cue**: Find the doer in the verb ending; the reflexive pronoun takes that
person's object form.

---

## pron-poss-ending

**id**: `pron-poss-ending`

Possessives mein, dein, sein, ihr, unser, Ihr before a noun of any gender or number, in all
four cases. Merged from the former `pron-poss-masc`, `-fem`, `-neut` and `-pl`: those IDs named
the noun's gender, which is part of the answer, and the plural rule showed a different table
before the answer. One rule, one table: the card before an answer is the same for every item.

**items**:

- Das ist ___ neuer Kollege aus Hamburg. (mein)
- Ich habe ___ Schlüsselbund überall gesucht. (meinen)
- Ich helfe ___ Vater bei der Gartenarbeit. (meinem)
- Das Fahrrad ___ Bruders ist leider gestohlen worden. (meines)
- Ist das ___ Koffer dort an der Rezeption? (dein)
- Vergiss ___ Regenschirm nicht, es regnet! (deinen)
- Kommst du gut mit ___ neuen Chef zurecht? (deinem)
- Paul sucht ___ verlorenen Ausweis. (seinen)
- Anna geht jeden Tag mit ___ Hund im Wald spazieren. (ihrem)
- Das ist ___ neuer Deutschlehrer. (unser)
- Wir haben ___ alten Wagen endlich verkauft. (unseren)
- Wegen des Umzugs ___ Nachbarn war es gestern laut. (unseres)
- Herr Meyer, ist das ___ Koffer? (Ihr)
- Guten Tag Herr Schmidt, haben Sie ___ Pass dabei? (Ihren)
- Das ist ___ Tante aus Berlin. (meine)
- Ich besuche ___ Schwester am Wochenende. (meine)
- Ich schenke ___ Mutter einen schönen Blumenstrauß. (meiner)
- Wegen der Krankheit ___ Oma bin ich zu Hause geblieben. (meiner)
- Erzähl mir bitte mehr von ___ Urlaubsreise! (deiner)
- Sie telefoniert gerade mit ___ besten Freundin. (ihrer)
- Hier ist ___ neue Wohnung. (unsere)
- In ___ Stadt gibt es viele Grünanlagen. (unserer)
- Frau Wagner, wie lautet ___ neue Adresse? (Ihre)
- Das ist ___ neues Fahrrad. (mein)
- Ich wasche ___ Auto jeden Samstag. (mein)
- In ___ neuen Zimmer gibt es viel Licht. (meinem)
- Während ___ Urlaubs war das Wetter herrlich. (meines)
- Lukas zeigt uns stolz ___ neues Smartphone. (sein)
- Wir fühlen uns in ___ neuen Haus sehr wohl. (unserem)
- Gefällt es Ihnen in ___ neuen Büro, Frau Klein? (Ihrem)
- Hier wohnen ___ besten Freunde. (meine)
- Ich danke ___ Eltern für ihre große Unterstützung. (meinen)

**rule (pre-answer)**: A possessive takes the ending ein or kein would take: the ending marks the noun's gender and its job in the sentence. The stem changes, the endings never do.

**first_exposure_rule**: omitted — gender and case are both recall targets for these items.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Wer? · Wen? | mein · meinen | meine | mein | meine |
| Wem? | meinem | meiner | meinem | meinen |
| Wessen? | meines | meiner | meines | meiner |

Equal weight before an answer. The same endings go on every possessor stem (dein, sein, ihr, unser, Ihr). After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Ich habe mein**en** Schlüsselbund überall gesucht.

**miss lines**:

- „Das ist ___ neuer Kollege aus Hamburg." (mein)
  - ✗ *meinen*: the accusative form — Kollege is the doer (nominative masculine), so mein.
  - ✗ *meinem*: the dative form — no giving-to role here, so mein.
  - ✗ *meines*: the genitive form — no owner role here, so mein.
- „Ich habe ___ Schlüsselbund überall gesucht." (meinen)
  - ✗ *meinem*: the dative form — suchen acts directly on the bundle, so meinen.
  - ✗ *mein*: the doer form — the doer is ich, the bundle is acted on, so meinen.
  - ✗ *meines*: the genitive form — no owner role, so meinen.
- „Ich helfe ___ Vater bei der Gartenarbeit." (meinem)
  - ✗ *meinen*: the accusative form — helfen gives help TO someone (dative), so meinem.
  - ✗ *mein*: the doer form — the father receives the help, so meinem.
  - ✗ *meines*: the genitive form — helping takes the to-form, so meinem.
- „Das Fahrrad ___ Bruders ist leider gestohlen worden." (meines)
  - ✗ *meinem*: the dative form — Bruders already shows the owner (genitive), so meines.
  - ✗ *meinen*: the accusative form — ownership needs the genitive, so meines.
  - ✗ *mein*: the doer form — the brother owns the bike, so meines.
- „Ist das ___ Koffer dort an der Rezeption?" (dein)
  - ✗ *deinen*: the accusative form — Koffer is the doer (nominative masculine), so dein.
  - ✗ *deinem*: the dative form — no giving-to role, so dein.
  - ✗ *deines*: the genitive form — no owner role, so dein.
- „Vergiss ___ Regenschirm nicht, …" (deinen)
  - ✗ *deinem*: the dative form — vergessen acts directly on the umbrella, so deinen.
  - ✗ *dein*: the doer form — the doer is du; the umbrella is acted on, so deinen.
  - ✗ *deines*: the genitive form — no owner role, so deinen.
- „Kommst du gut mit ___ neuen Chef zurecht?" (deinem)
  - ✗ *deinen*: the accusative form — mit always takes the dative, so deinem.
  - ✗ *dein*: the doer form — the chef follows the preposition, so deinem.
  - ✗ *deines*: the genitive form — mit takes the to-form, so deinem.
- „Paul sucht ___ verlorenen Ausweis." (seinen)
  - ✗ *seinem*: the dative form — suchen acts directly on the card, so seinen.
  - ✗ *sein*: the doer form — the doer is Paul; the card is acted on, so seinen.
  - ✗ *seines*: the genitive form — no owner role, so seinen.
- „Anna geht jeden Tag mit ___ Hund im Wald spazieren." (ihrem)
  - ✗ *ihren*: the accusative form — mit takes the dative, so ihrem.
  - ✗ *ihr*: the doer form — the dog follows the preposition, so ihrem.
  - ✗ *ihres*: the genitive form — mit takes the to-form, so ihrem.
- „Das ist ___ neuer Deutschlehrer." (unser)
  - ✗ *unsere*: the feminine/plural form — Deutschlehrer is masculine and the doer, so
    unser.
  - ✗ *unserem*: the dative form — no giving-to role, so unser.
  - ✗ *unseren*: the accusative form — the teacher is the doer, so unser.
- „Wir haben ___ alten Wagen endlich verkauft." (unseren)
  - ✗ *unserem*: the dative form — verkaufen acts directly on the car, so unseren.
  - ✗ *unser*: the doer form — the doer is wir; the car is acted on, so unseren.
  - ✗ *unseres*: the genitive form — no owner role, so unseren.
- „Wegen des Umzugs ___ Nachbarn war es gestern laut." (unseres)
  - ✗ *unserem*: the dative form — wegen takes the genitive, so unseres.
  - ✗ *unseren*: the accusative form — the neighbour is the owner of the move, so unseres.
  - ✗ *unser*: the doer form — ownership needs the genitive, so unseres.
- „Herr Meyer, ist das ___ Koffer?" (Ihr)
  - ✗ *Ihren*: the accusative form — Koffer is the doer (nominative masculine), so Ihr.
  - ✗ *Ihrem*: the dative form — no giving-to role, so Ihr.
  - ✗ *Ihres*: the genitive form — no owner role, so Ihr.
- „… haben Sie ___ Pass dabei?" (Ihren)
  - ✗ *Ihrem*: the dative form — dabei haben carries the pass directly, so Ihren.
  - ✗ *Ihr*: the doer form — the doer is Sie; the pass is carried, so Ihren.
  - ✗ *Ihres*: the genitive form — no owner role, so Ihren.
- „Das ist ___ Tante aus Berlin." (meine)
  - ✗ *meiner*: the dative/genitive form — Tante is the doer (feminine nominative), so
    meine.
  - ✗ *mein*: the masculine/neuter form — Tante is feminine, so meine.
  - ✗ *meinen*: the masculine accusative / plural dative form — feminine takes -e, so
    meine.
- „Ich besuche ___ Schwester am Wochenende." (meine)
  - ✗ *meiner*: the dative/genitive form — besuchen acts directly on her (Wen?), so meine.
  - ✗ *mein*: the masculine/neuter form — Schwester is feminine, so meine.
  - ✗ *meinen*: the masculine accusative form — feminine takes -e, so meine.
- „Ich schenke ___ Mutter einen … Blumenstrauß." (meiner)
  - ✗ *meine*: the nominative/accusative form — schenken gives TO the mother (dative), so
    meiner.
  - ✗ *meinem*: the masculine/neuter dative form — Mutter is feminine, so meiner.
  - ✗ *meinen*: the masculine accusative form — the bouquet is the direct object, the
    mother receives, so meiner.
- „Wegen der Krankheit ___ Oma bin ich …" (meiner)
  - ✗ *meine*: the nominative/accusative form — wegen takes the genitive, so meiner.
  - ✗ *meines*: the masculine/neuter genitive form — Oma is feminine, so meiner.
  - ✗ *meinem*: the dative form — ownership needs the genitive, so meiner.
- „Erzähl mir bitte mehr von ___ Urlaubsreise!" (deiner)
  - ✗ *deine*: the nominative/accusative form — von takes the dative, so deiner.
  - ✗ *deinem*: the masculine/neuter dative form — Urlaubsreise is feminine, so deiner.
  - ✗ *deinen*: the masculine accusative form — feminine takes -er in the dative, so
    deiner.
- „Sie telefoniert gerade mit ___ besten Freundin." (ihrer)
  - ✗ *ihre*: the nominative/accusative form — mit takes the dative, so ihrer.
  - ✗ *ihrem*: the masculine/neuter dative form — Freundin is feminine, so ihrer.
  - ✗ *ihren*: the masculine accusative form — feminine takes -er in the dative, so ihrer.
- „Hier ist ___ neue Wohnung." (unsere)
  - ✗ *unserer*: the dative/genitive form — Wohnung is the doer (nominative), so unsere.
  - ✗ *unser*: the masculine/neuter form — Wohnung is feminine, so unsere.
  - ✗ *unserem*: the dative form — no giving-to role here, so unsere.
- „In ___ Stadt gibt es viele Grünanlagen." (unserer)
  - ✗ *unsere*: the nominative/accusative form — a fixed position takes the dative, so
    unserer.
  - ✗ *unserem*: the masculine/neuter dative form — Stadt is feminine, so unserer.
  - ✗ *unseren*: the masculine accusative / plural dative form — feminine takes -er, so
    unserer.
- „Frau Wagner, wie lautet ___ neue Adresse?" (Ihre)
  - ✗ *Ihrer*: the dative/genitive form — Adresse is the doer (nominative), so Ihre.
  - ✗ *Ihr*: the masculine/neuter form — Adresse is feminine, so Ihre.
  - ✗ *Ihrem*: the dative form — no giving-to role here, so Ihre.
- „Das ist ___ neues Fahrrad." (mein)
  - ✗ *meine*: the feminine/plural form — Fahrrad is neuter and the doer, so mein.
  - ✗ *meinem*: the dative form — no giving-to role here, so mein.
  - ✗ *meines*: the genitive form — no owner role here, so mein.
- „Ich wasche ___ Auto jeden Samstag." (mein)
  - ✗ *meine*: the feminine/plural form — Auto is neuter; waschen acts on it directly, so
    mein.
  - ✗ *meinem*: the dative form — the car is the direct object, so mein.
  - ✗ *meines*: the genitive form — no owner role, so mein.
- „In ___ neuen Zimmer gibt es viel Licht." (meinem)
  - ✗ *meinen*: the masculine accusative / plural dative form — a fixed position takes the
    dative, so meinem.
  - ✗ *mein*: the doer form — in for a position (Wo?) takes the dative, so meinem.
  - ✗ *meines*: the genitive form — no owner role, so meinem.
- „Während ___ Urlaubs war das Wetter herrlich." (meines)
  - ✗ *meinem*: the dative form — während takes the genitive, so meines.
  - ✗ *mein*: the doer form — während takes the genitive, so meines.
  - ✗ *meiner*: the feminine genitive form — Urlaub is masculine (der Urlaub), and the
    masculine genitive is meines.
- „Lukas zeigt uns stolz ___ neues Smartphone." (sein)
  - ✗ *seine*: the feminine/plural form — Smartphone is neuter; zeigt acts on it directly,
    so sein.
  - ✗ *seinem*: the dative form — the phone is the direct object, so sein.
  - ✗ *seines*: the genitive form — no owner role, so sein.
- „Wir fühlen uns in ___ neuen Haus sehr wohl." (unserem)
  - ✗ *unseren*: the masculine accusative / plural dative form — a fixed position takes the
    dative, so unserem.
  - ✗ *unser*: the doer form — in for a position (Wo?) takes the dative, so unserem.
  - ✗ *unseres*: the genitive form — no owner role, so unserem.
- „Gefällt es Ihnen in ___ neuen Büro, Frau Klein?" (Ihrem)
  - ✗ *Ihren*: the masculine accusative form — a fixed position takes the dative, so
    Ihrem.
  - ✗ *Ihr*: the doer form — in for a position (Wo?) takes the dative, so Ihrem.
  - ✗ *Ihres*: the genitive form — no owner role, so Ihrem.
- „Hier wohnen ___ besten Freunde." (meine)
  - ✗ *meinen*: the masculine accusative / plural dative form — the friends are plural and
    the doers, so meine.
  - ✗ *meiner*: the feminine dative/genitive form — plural takes -e here, so meine.
  - ✗ *mein*: the masculine/neuter doer form — plural takes -e, so meine.
- „Ich danke ___ Eltern für ihre große Unterstützung." (meinen)
  - ✗ *meinem*: the masculine/neuter dative form — Eltern are plural, so meinen.
  - ✗ *meine*: the nominative/accusative form — danken thanks TO someone (dative plural),
    so meinen.
  - ✗ *meiner*: the feminine dative / genitive form — plural dative is meinen, so meinen.

**flashcard_and_miss_hint**: The noun's gender and its job in the sentence pick the ending, the same one ein or kein would take.

**later_rung_cue**: Which ending would ein or kein take here? The possessive takes the same one.

---

## pron-poss-edrop

The possessor euer, whose stem drops its e whenever an ending is added. No answer form is
named before the answer; the stem is visible in every choice tile.

**id**: `pron-poss-edrop`

**items**:

- Ist das ___ Wagen da drüben? (euer)
- Wo arbeitet ___ Mutter eigentlich? (eure)
- Ich habe gestern ___ Einladung erhalten. (eure)
- Sind das ___ Fahrräder vor der Tür? (eure)
- Habt ihr mit ___ neuen Lehrer schon gesprochen? (eurem)
- Passt auf ___ kleinen Bruder auf! (euren)
- Wie viele Schüler sind in ___ Klasse? (eurer)

Data fixed in review: „Passt auf ___ kleinen Bruder auf!" now answers euren (aufpassen auf + accusative). To keep every euer form drilled, „Habt ihr ___ neuen Stundenplan schon bekommen?" became the dative item „Habt ihr mit ___ neuen Lehrer schon gesprochen?" (eurem).

**rule (pre-answer)**: euer drops the e of its stem as soon as an ending is added; the endings follow the ein pattern. Without an ending, the stem stays whole.

**first_exposure_rule**: omitted — gender and case are both recall targets for these items.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Wer? · Wen? | euer · euren | eure | euer | eure |
| Wem? | eurem | eurer | eurem | euren |
| Wessen? | eures | eurer | eures | eurer |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: rendered per item with its own sentence, possessive bold, for example: Wo arbeitet **eure** Mutter eigentlich?

**miss lines**:

- „Ist das ___ Wagen da drüben?" (euer)
  - ✗ *eure*: the feminine/plural form — Wagen is masculine and the doer, and no ending
    belongs there, so euer.
  - ✗ *eurem*: the dative form — no giving-to role here, so euer.
  - ✗ *euren*: the accusative form — the car is the doer, so euer.
- „Wo arbeitet ___ Mutter eigentlich?" (eure)
  - ✗ *euer*: the masculine/neuter form — Mutter is feminine and the doer, so eure.
  - ✗ *eurer*: the dative/genitive form — the doer question takes -e, so eure.
  - ✗ *eurem*: the masculine/neuter dative form — Mutter is feminine, so eure.
- „Ich habe gestern ___ Einladung erhalten." (eure)
  - ✗ *euer*: the masculine/neuter form — Einladung is feminine, so eure.
  - ✗ *eurer*: the dative/genitive form — erhalten acts directly on it (Wen?), so eure.
  - ✗ *eurem*: the masculine/neuter dative form — Einladung is feminine, so eure.
- „Sind das ___ Fahrräder vor der Tür?" (eure)
  - ✗ *euer*: the masculine/neuter form — plural takes -e, so eure.
  - ✗ *euren*: the masculine accusative / plural dative form — the bikes are the doers, so
    eure.
  - ✗ *eurer*: the feminine dative / genitive form — plural nominative is eure, so eure.
- „Habt ihr mit ___ neuen Lehrer schon gesprochen?" (eurem)
  - ✗ *euren*: the masculine accusative form — mit always takes the dative, so eurem.
  - ✗ *euer*: the no-ending form — a masculine dative needs the ending -em, so eurem.
  - ✗ *eures*: the genitive form — mit takes the dative, so eurem.
- „Passt auf ___ kleinen Bruder auf!" (euren)
  - ✗ *eurem*: the dative form — aufpassen auf takes the accusative (Wen?), so euren.
  - ✗ *euer*: the no-ending form — a masculine accusative needs the ending -en, so euren.
  - ✗ *eures*: the genitive form — no owner role, so euren.
- „Wie viele Schüler sind in ___ Klasse?" (eurer)
  - ✗ *eure*: the nominative/accusative form — a fixed position takes the dative, so
    eurer.
  - ✗ *eurem*: the masculine/neuter dative form — Klasse is feminine, so eurer.
  - ✗ *euren*: the masculine accusative / plural dative form — feminine dative is eurer, so
    eurer.

**flashcard_and_miss_hint**: The noun's gender and job pick the ending; any ending costs
euer its stem e.

**later_rung_cue**: Does an ending belong here? If yes, drop the stem e first, then add it
like ein.

---

## Coverage and data notes

- Every one of the 98 items in `DrillPronouns.kt` appears in exactly one items list above
  (27 personal + 32 reflexive + 39 possessive). No item is unplaced; no item is listed twice.
- Rule texts were checked item by item: none names the answer form, the answer's case, or
  the answer's gender before the answer. Tables show the full contrast set with equal weight
  pre-answer, per the design doc.
- Data fixed in review: six items that had two grammatical answers now carry a context cue
  (Meine Frau und ich …, Wir sind … in Prag, Kinder, wo wart ihr?, Kinder, Paul ist zufrieden,
  Lisa ist stolz). „Während ___ Urlaubs" now has the hint „Maskulinum Genitiv". „Passt auf ___
  kleinen Bruder auf!" now answers euren; see `pron-poss-edrop`.
