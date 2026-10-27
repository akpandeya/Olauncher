# Genitive help-card content (family `gen`)

7 rules · 10 of 10 items covered, each exactly once · 0 `UNSURE:` marks.

Conventions (author notes, not learner-facing):

- Layout follows `articles.md`: id, items, rule, first_exposure_rule, forms table, example,
  miss lines, flashcard_and_miss_hint, later_rung_cue.
- No existing ID fits: no item uses a preposition (so `prep-fixed-gen` does not apply).
- The genitive is the answer's case for every item, so no pre-answer text names it; the rules
  call it the "owner" form. `first_exposure_rule` is omitted everywhere: the design names a
  case before an answer only for one-case prepositions. Case names appear only in
  `flashcard_and_miss_hint` and miss lines.
- Forms tables are plain before an answer; the matching cell is marked only in the flashcard
  and after-a-miss states. Tables use nouns that are not drill answers.
- Data fixed in review (`items/genitive.md`): item 4 distractor Kinds (a valid rare genitive) →
  Kinde. „Vaters altes Fahrrad" is correct, if dated or colloquial, German.

---

## gen-article

**id**: `gen-article`

**items**:

- Das Auto ___ Vaters ist neu.
- Das ist die Tasche ___ Mutter.
- Die Farbe ___ Blumen ist rot.

**rule (pre-answer)**: For possession, the owner's article takes a special form that depends on the owner's gender. The owner noun often adds an ending too.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | m | f | n | pl |
|---|---|---|---|---|
| Wessen? | des | der | des | der |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its article.

**example (flashcard and after a miss)**: Das ist die Farbe **des** Meeres.

**miss lines**:

- Das Auto ___ Vaters ist neu. (des)
  - ✗ *der*: feminine or plural owner. Vater is masculine, so des.
  - ✗ *dem*: dative. The owner here is genitive, so des.
  - ✗ *den*: accusative masculine. The owner here is genitive, so des.
- Das ist die Tasche ___ Mutter. (der)
  - ✗ *des*: masculine or neuter owner. Mutter is feminine, so der.
  - ✗ *dem*: dative. The owner here is genitive, so der.
  - ✗ *die*: nominative or accusative. The owner here is genitive, so der.
- Die Farbe ___ Blumen ist rot. (der)
  - ✗ *des*: masculine or neuter singular owner. Blumen is plural, so der.
  - ✗ *die*: nominative or accusative plural. The owner here is genitive, so der.
  - ✗ *dem*: dative. The owner here is genitive, so der.

**flashcard_and_miss_hint**: In the genitive, des goes with masculine and neuter owners, der with feminine and plural ones.

**later_rung_cue**: Wessen? The owner's article follows its gender.

---

## gen-noun-s

**id**: `gen-noun-s`

**items**:

- Ich lese das Buch des ___.
- Das Ende des ___ war spannend.

**rule (pre-answer)**: After des, the owner noun adds -s or -es. Many one-syllable nouns take -es (des Mannes); longer nouns take plain -s (des Lehrers).

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | longer nouns | many one-syllable nouns |
|---|---|---|
| with des | des Lehrers | des Mannes |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: Ich lese den Anfang des Buch**es**.

**miss lines**:

- Ich lese das Buch des ___. (Kindes)
  - ✗ *Kinde*: an old dative form (dem Kinde). After des the noun takes -es: Kindes.
  - ✗ *Kind*: no ending. After des this noun adds -es, so Kindes.
  - ✗ *Kindern*: dative plural. One child owns the book, so Kindes.
- Das Ende des ___ war spannend. (Films)
  - ✗ *Filme*: that is the plural. One film owns the ending, so Films.
  - ✗ *Filmen*: dative plural. One film takes the owner -s, so Films.
  - ✗ *Film*: no ending. After des this noun adds -s, so Films.

**flashcard_and_miss_hint**: In the genitive, masculine and neuter nouns take -(e)s; many short nouns like Mann take the fuller -es.

**later_rung_cue**: After des the noun adds -s — or the fuller -es when the word is short.

---

## gen-noun-weak

**id**: `gen-noun-weak`

**items**:

- Das ist der Ball des ___.

**rule (pre-answer)**: Some nouns never take the -s owner ending. These "weak" nouns — many are words for people or animals — add -n or -en instead after des.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | most nouns | weak nouns |
|---|---|---|
| with des | des Films | des Menschen |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its ending.

**example (flashcard and after a miss)**: Das ist das Auto des Nachbar**n**.

**miss lines**:

- Das ist der Ball des ___. (Jungen)
  - ✗ *Junges*: weak nouns never take -s. Junge is weak and adds -n, so Jungen.
  - ✗ *Junge*: no ending. After des, weak nouns add -n, so Jungen.
  - ✗ *Jungens*: weak nouns never take -s. Junge adds just -n, so Jungen.

**flashcard_and_miss_hint**: Junge is a weak noun: in the genitive it takes -n/-en (des Jungen), never -s.

**later_rung_cue**: Is this a weak noun? Weak nouns never take the -s owner ending.

---

## gen-time-phrase

**id**: `gen-time-phrase`

**items**:

- ___ Tages erzähle ich dir alles.

**rule (pre-answer)**: Some time phrases stand in a fixed case without any preposition. Learn each one as a whole phrase, article form included.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | Nom | Gen | Dat |
|---|---|---|---|
| m · n | ein | eines | einem |
| f | eine | einer | einer |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell and bold its form.

**example (flashcard and after a miss)**: **Eines** Tages erzähle ich dir alles.

**miss lines**:

- ___ Tages erzähle ich dir alles. (Eines)
  - ✗ *Einem*: dative. The set phrase stands in the genitive, so Eines.
  - ✗ *Einer*: feminine genitive or dative. Tag is masculine, so Eines.
  - ✗ *Ein*: nominative or accusative. The set phrase is genitive, so Eines.

**flashcard_and_miss_hint**: Eines Tages is a fixed genitive phrase meaning "one day"; both words stand in the genitive.

**later_rung_cue**: A fixed time phrase: recall it as a whole, article included.

---

## gen-question-whose

**id**: `gen-question-whose`

**items**:

- ___ Schlüssel ist das?

**rule (pre-answer)**: German has a separate question word for "whose". The other who-words ask who (wer), whom (wen) and to whom (wem); the whose-word asks for the owner.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | wer | wen | wem | wessen |
|---|---|---|---|---|
| asks | who (subject) | whom (object) | to whom | whose |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell.

**example (flashcard and after a miss)**: **Wessen** Schlüssel ist das?

**miss lines**:

- ___ Schlüssel ist das? (Wessen)
  - ✗ *Wer*: asks for the subject. For the owner, German uses Wessen.
  - ✗ *Wen*: asks for a direct object. For the owner, German uses Wessen.
  - ✗ *Wem*: asks for an indirect object. For the owner, German uses Wessen.

**flashcard_and_miss_hint**: Wessen is the genitive question word "whose" — the owner form of wer.

**later_rung_cue**: "Whose" has its own question word — not wer, wen or wem.

---

## gen-kinship-s

**id**: `gen-kinship-s`

**items**:

- ___ altes Fahrrad steht in der Garage.

**rule (pre-answer)**: Kinship nouns and names can mark the owner the short way: no article, just an added -s — like an English apostrophe-s. The rest of the noun stays as is.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**: (example noun, not the drill's)

| | with article | without article |
|---|---|---|
| owner | des Opas | Opas |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell.

**example (flashcard and after a miss)**: Vater**s** altes Fahrrad steht in der Garage.

**miss lines**:

- ___ altes Fahrrad steht in der Garage. (Vaters)
  - ✗ *Vater*: no ending. Without an article, the kinship owner adds -s, so Vaters.
  - ✗ *Väter*: that is the plural. One owner takes Vaters.
  - ✗ *Vatern*: not a German form. Kinship owners take bare -s, so Vaters.

**flashcard_and_miss_hint**: Male kinship nouns can show the genitive with no article and bare -s: Vaters, like English "father's".

**later_rung_cue**: No article here: this owner works like a name and takes bare -s.

---

## gen-noun-zero

**id**: `gen-noun-zero`

**items**:

- Das sind die Bücher der ___.

**rule (pre-answer)**: After the owner article, a noun's ending depends on its group: many add -(e)s, weak ones add -n or -en, and plural nouns stay unchanged.

**first_exposure_rule**: omitted. The case is the answer's case, and the design names a case before an answer only for one-case prepositions.

**forms table**:

| | -(e)s nouns | weak nouns | plurals |
|---|---|---|---|
| owner form | des Films | des Menschen | der Bücher |

Equal weight before an answer. After the answer only (flashcard, after a miss): highlight the item's cell.

**example (flashcard and after a miss)**: Das sind die Bücher der **Schüler**.

**miss lines**:

- Das sind die Bücher der ___. (Schüler)
  - ✗ *Schülern*: dative plural. Genitive plural nouns stay unchanged, so der Schüler.
  - ✗ *Schülers*: genitive singular (des Schülers). The owners here are plural, so der Schüler.
  - ✗ *Schüleres*: not a German form. Plural owners stay unchanged, so der Schüler.

**flashcard_and_miss_hint**: Plural nouns take no ending in the genitive; only the article becomes der.

**later_rung_cue**: Plural owners stay unchanged; only the article changes.

---

## Coverage check

| Rule | Items |
|---|---|
| gen-article | 3 (items 1, 2, 3) |
| gen-noun-s | 2 (items 4, 5) |
| gen-noun-weak | 1 (item 6) |
| gen-time-phrase | 1 (item 7) |
| gen-question-whose | 1 (item 8) |
| gen-kinship-s | 1 (item 9) |
| gen-noun-zero | 1 (item 10) |

Total: 10 items, each placed exactly once.
