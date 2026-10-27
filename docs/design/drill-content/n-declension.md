# n-declension help-card content (family `ndecl`)

1 rule · 10 of 10 items covered, each exactly once · 0 `UNSURE:` marks.

Conventions (author notes, not learner-facing):

- Layout follows `articles.md`: id, items, rule, first_exposure_rule, forms table, example,
  miss lines, flashcard_and_miss_hint, later_rung_cue.
- All ten items test two sides of one pattern (weak nouns: bare subject singular vs -(e)n
  everywhere else, plural included), so they share one rule. No existing ID fits.
- `first_exposure_rule` is omitted: the case is the reasoning step, and the design names a
  case or gender before an answer only for one-case prepositions. The gender fact (weak nouns
  are masculine) sits in `flashcard_and_miss_hint`.
- The table uses der Bär, which is not a drill item, so it names no item's answer.
- German check: der Junge / den Jungen / dem Nachbarn / des Kollegen / den Herrn (plural
  Herren) / dem Kunden / die Menschen / den Affen / dem Löwen / den Namen (genitive des
  Namens) are standard forms.

---

## ndecl-weak-en

**id**: `ndecl-weak-en`

**items**:

- Der ___ spielt nach der Schule Fußball.
- Ich kenne den ___ aus dem Sprachkurs.
- Wir helfen dem ___ am Wochenende im Garten.
- Das ist das Auto des ___.
- Grüßen Sie bitte den ___ von mir.
- Der Kellner bringt dem ___ das Essen.
- Die ___ warten an der Haltestelle auf den Bus.
- Im Zoo fotografiert sie den ___ hinter dem Baum.
- Der Pfleger gibt dem ___ jeden Tag frisches Fleisch.
- Ich habe den ___ der neuen Kollegin wieder vergessen.

**rule (pre-answer)**: Some German nouns, called weak, add -(e)n in every form except the bare singular used for the sentence's subject. Learn these nouns with their endings.

**first_exposure_rule**: omitted. The case is the reasoning step, and the design names a case or gender before an answer only for one-case prepositions.

**forms table**: (example noun, not the drill's)

| | Nom | Akk | Dat | Gen |
|---|---|---|---|---|
| Singular | der Bär | den Bären | dem Bären | des Bären |
| Plural | die Bären | die Bären | den Bären | der Bären |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending. The learner still has to decide subject vs non-subject, and singular vs plural.

**example (flashcard and after a miss)**: rendered per item with its own sentence, ending bold, for example: Wir helfen dem Nachbar**n** am Wochenende im Garten.

**miss lines**:

- Der ___ spielt nach der Schule Fußball. (Junge)
  - ✗ *Jungen*: the -n form is for every use except the subject; the boy is the one playing, so Junge.
  - ✗ *Jung*: that is not the noun; the noun is der Junge.
  - ✗ *Jungs*: that is the informal plural; the sentence is about one boy, so Junge.
- Ich kenne den ___ aus dem Sprachkurs. (Studenten)
  - ✗ *Student*: the bare form is only for the subject; after den the weak noun adds -en: Studenten.
  - ✗ *Studente*: weak nouns add -en, not -e: Studenten.
  - ✗ *Studentes*: weak nouns never take -s: Studenten.
- Wir helfen dem ___ am Wochenende im Garten. (Nachbarn)
  - ✗ *Nachbar*: the bare form is only for the subject; after dem the weak noun takes -n: Nachbarn.
  - ✗ *Nachbars*: weak nouns take -n, not -s: Nachbarn.
  - ✗ *Nachbaren*: Nachbar adds just -n, not -en: Nachbarn.
- Das ist das Auto des ___. (Kollegen)
  - ✗ *Kollege*: the bare form is only for the subject; after des the weak noun adds -n: Kollegen.
  - ✗ *Kolleges*: weak nouns add -n, not -s, even here: Kollegen.
  - ✗ *Kolleg*: that is not the noun; the noun is der Kollege, here des Kollegen.
- Grüßen Sie bitte den ___ von mir. (Herrn)
  - ✗ *Herr*: after den, Herr adds -n: Herrn.
  - ✗ *Herren*: that is the plural; one person is meant, so Herrn.
  - ✗ *Herrs*: weak nouns take no -s: Herrn.
- Der Kellner bringt dem ___ das Essen. (Kunden)
  - ✗ *Kunde*: the bare form is only for the subject; after dem the weak noun adds -n: Kunden.
  - ✗ *Kunds*: weak nouns take -n, not -s: Kunden.
  - ✗ *Kundes*: there is no -s form here; it is dem Kunden.
- Die ___ warten an der Haltestelle auf den Bus. (Menschen)
  - ✗ *Mensch*: singular; the verb warten shows several people, so Menschen.
  - ✗ *Menschens*: the plural of a weak noun never takes -s: Menschen.
  - ✗ *Menscher*: not a form of Mensch; the plural is Menschen.
- Im Zoo fotografiert sie den ___ hinter dem Baum. (Affen)
  - ✗ *Affe*: the bare form is only for the subject; after den the weak noun adds -n: Affen.
  - ✗ *Aff*: that is not the noun; the noun is der Affe, here den Affen.
  - ✗ *Affes*: weak nouns never take -s: Affen.
- Der Pfleger gibt dem ___ jeden Tag frisches Fleisch. (Löwen)
  - ✗ *Löwe*: the bare form is only for the subject; after dem the weak noun adds -n: Löwen.
  - ✗ *Löwes*: weak nouns take -n, not -s: Löwen.
  - ✗ *Löwin*: the female lion is feminine (der Löwin after a dative). dem shows a masculine noun, der Löwe, so dem Löwen.
- Ich habe den ___ der neuen Kollegin wieder vergessen. (Namen)
  - ✗ *Name*: the bare form is only for the subject; after den the weak noun adds -n: Namen.
  - ✗ *Namens*: that -s form belongs after des; after den it is Namen.
  - ✗ *Names*: not a form of Name; here den Namen.

**flashcard_and_miss_hint**: These nouns are masculine. Every case adds -(e)n except the nominative singular — the plural keeps it too.

**later_rung_cue**: Weak noun: every form takes -(e)n except the bare subject singular — the plural too.

---

## Coverage check

All 10 items in `items/n-declension.md` appear exactly once, in `ndecl-weak-en`.
