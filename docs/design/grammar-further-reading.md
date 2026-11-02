# Further-reading links: hand-checked notes (#810)

Date checked: **2026-11-02**. Every URL below was opened and read in full on
that date by the implementer. This file is the source material for Task-07's
drafted content: the distillations record what each source actually says. Do
not add a claim to the app's copy that a source does not make here; anything
uncertain is marked.

The links live in `app/src/main/java/app/olauncher/data/store/GrammarFurtherReading.kt`,
keyed on the stable `GrammarRuleGroups` keys from #809. Nothing is fetched at
runtime — a tap hands the URL to the browser. A manual link-health script
(`scripts/check-further-reading-links.sh`) reports each URL's HTTP status; it
is never run by the build or the unit tests.

Source priority (owner decision): high-quality open sources first, Wikipedia
last. Every entry here is [Lingolia](https://deutsch.lingolia.com)'s free
German grammar reference — a professionally edited, freely readable grammar
reference with full tables and examples. Adding a second source per topic was
considered and rejected where the single page already covers the topic's
points; two links appear only where the topic genuinely has two separate
angles. Wikipedia was not needed: no topic lacked a good open source.

## articles — `https://deutsch.lingolia.com/en/grammar/nouns-and-articles`

- **Source:** Lingolia, "Nouns and Articles in German Grammar" (the gender page).
- **Angle:** gender patterns and exceptions — which the app's suffix rules
  assume but do not survey.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Every German noun has a grammatical gender (masculine, feminine, neuter);
  dictionaries mark it m/f/n. Often gender cannot be predicted from meaning —
  objects are not inherently "masculine" or "feminine" — so the page's advice
  is to learn the article with every new noun.
- There are no hard rules, but pattern tables: typically masculine are male
  people, seasons, days/months, precipitation types, nouns in -ling, -ich/-ig,
  and verbs without -en (der Lauf); typically feminine are female people,
  numbers as nouns, -ung, -schaft, -ion, -heit/-keit, -tät, -ik; typically
  neuter are letters of the alphabet, -lein/-chen, -um, -ment, foreign -ma,
  verb infinitives (das Laufen), English -ing words, and nominalised
  adjectives (das Neue).
- The page states there are exceptions to every pattern and gives some:
  das Mädchen (because -chen is always neuter), das Fräulein, das Frühjahr
  against der Frühling. Some nouns accept two articles: meaning-split
  (das/der Pony) or free variation (der/das Laptop, der/das Keks).
- The gender of a compound noun is always the gender of its final noun
  (der Tisch + das Bein = das Tischbein).
- The page also covers the generic masculine and the Gendersternchen
  (Lehrer*innen/Lehrer:innen) — modern usage debate, not a gender rule.

## plurals — `https://deutsch.lingolia.com/en/grammar/nouns-and-articles/plural`

- **Source:** Lingolia, "Plural Nouns in German Grammar".
- **Angle:** which noun endings take which plural ending, plus nouns that only
  exist in singular or plural.
- **Accessed:** 2026-11-02.

Key points the page makes:

- German plurals are formed with -n/-en, -e, -r/-er, -s, or no ending; the
  noun's ending gives a clue, and the page says plainly to expect many
  exceptions.
- -n/-en: masculine nouns in -e, -ent, -ant, -ist, -or; feminine nouns in -e,
  -in (n doubles: Lehrerinnen), -ion, -ik, -heit, -keit, -schaft, -tät, -ung;
  foreign -ma/-um/-us usually replaced by -en (Thema → Themen).
- -e: masculine nouns in -eur, -ich, -ier, -ig, -ling, -ör; many one-syllable
  feminine nouns, which then take an umlaut (Hand → Hände).
- -r/-er: many one-syllable neuter nouns, often with umlaut (Wort → Wörter);
  feminine nouns never form the plural with -r/-er.
- -s: nouns of any gender ending in -a, -i, -o, -u, -y (Auto → Autos) and
  family names (die Lehmanns).
- No ending: masculine nouns in -el, -en, -er (Löffel → Löffel); neuter
  -chen/-lein (Mädchen → Mädchen).
- Some nouns are used mostly in singular (das Geld, die Milch) — die Gelder
  exists but means something else — and some only in plural (die Eltern, die
  Leute, die Ferien).

## pronouns — two links

### `https://deutsch.lingolia.com/en/grammar/pronouns/personal-pronouns`

- **Source:** Lingolia, "Personal Pronouns in German Grammar".
- **Angle:** the personal pronoun case table and the three sie forms; covers
  the group's personal-pronoun rules, not the relative ones.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Personal pronouns replace nouns and are declined by case. The overview
  table: nominative ich/du/er/sie/es/wir/ihr/sie; accusative
  mich/dich/ihn/sie/es/uns/euch/sie; dative mir/dir/ihm/ihr/ihm/uns/euch/ihnen.
- Nominative forms are the subject pronouns; accusative the direct-object
  forms (English parallels: he → him); dative the indirect-object forms,
  often corresponding to English "to him/her".
- sie + singular verb = she; sie + plural verb = they; Sie (capital) +
  plural verb = formal you, used with strangers and in formal contexts. The
  verb form distinguishes them (sie arbeitet vs sie arbeiten vs Sie arbeiten).

### `https://deutsch.lingolia.com/en/grammar/pronouns/relative-pronouns`

- **Source:** Lingolia, "Relative Pronouns in German Grammar".
- **Angle:** the relative-pronoun full table including genitive dessen/deren —
  what the personal-pronoun page does not cover.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Relative pronouns introduce relative clauses and give extra information
  about something in the first part of the sentence.
- The table (with welcher as an alternative): der/die/das (nom.), den/die/das/
  die (acc.), dem/der/dem/denen (dat., denen or welchen for plural), dessen/
  deren/dessen/deren (gen.). Examples show each case: "der Mann, der …",
  "den wir gegrüßt haben", "dem wir begegnet sind", "dessen Hut weggeflogen
  ist".

## word_order — two links

### `https://deutsch.lingolia.com/en/grammar/sentence-structure/main-clauses`

- **Source:** Lingolia, "Main Clauses – Declarative Sentences in German
  Grammar".
- **Angle:** verb-second in the main clause and the middle-field order — the
  declarative half of the topic.
- **Accessed:** 2026-11-02.

Key points the page makes:

- The finite verb is always in the second position of a main-clause sentence;
  infinite verb forms (infinitives, past participles) go to the end.
- Position 1 need not be the subject: object, time or place can be fronted,
  and the subject then follows the finite verb (Den Ball fängt der Hund). The
  four cases make this flexible without changing meaning.
- The page's basic order for the rest of the sentence: subject — indirect
  object — time — place — direct object — infinite verb (its rendering of the
  TeKaMoLo idea), with the exception that a direct-object pronoun comes before
  the indirect object.
- Old information comes early, new information late; emphasis can push
  indirect object/time/place after the direct object, and time-after-place is
  possible for emphasis but in written German usually looks wrong.

### `https://deutsch.lingolia.com/en/grammar/sentence-structure/dependent-clauses`

- **Source:** Lingolia, "Dependent Clauses in German Grammar".
- **Angle:** the verb-last rule of subordinate clauses — the other half of the
  word-order topic.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Dependent clauses (Nebensätze) cannot stand alone and are separated by a
  comma; the finite (conjugated) verb comes at the end of the clause
  (Ich weiß nicht, ob er dir hilft).
- When the dependent clause comes first, the main clause begins with its
  finite verb (Ob er dir hilft, weiß ich nicht).
- The page is the hub for the clause types: conjunctions, indirect questions,
  infinitive clauses (zu / um … zu), participle clauses, relative clauses,
  conditional clauses, indirect speech.

## cases — `https://deutsch.lingolia.com/en/grammar/declension`

- **Source:** Lingolia, "Declension in German Grammar – the Four Cases".
- **Angle:** when each case is used — question words, verb and preposition
  signal words, object order — rather than the ending tables the app's rules
  already carry.
- **Accessed:** 2026-11-02.

Key points the page makes:

- The four cases are categories showing a noun's grammatical role:
  nominative the doer (wer?), accusative the receiver (wen?), dative the
  to-whom (wem?), genitive possession (wessen?).
- Signal words: nominative after sein, werden, bleiben; accusative after all
  transitive verbs and the prepositions durch, für, gegen, ohne, um (… entlang);
  dative after verbs like antworten, danken, folgen, gehören, helfen,
  glauben, gratulieren, zustimmen and the prepositions aus, bei, gegenüber,
  mit, nach, seit, von, zu; genitive after possession and prepositions such
  as wegen, trotz, während, aufgrund, innerhalb/außerhalb.
- Order of two objects: dative generally before accusative; a pronoun object
  comes first regardless of case; with two pronouns the accusative comes
  first. Some verbs take two accusatives (fragen, kosten, lehren, nennen).
- Time without a preposition is accusative (jeden Morgen), with a preposition
  dative (am Morgen). Two-way prepositions take accusative for direction
  (wohin?) and dative for position (wo?), except zu which is always dative;
  time uses of the two-way prepositions are always dative. liegen/stehen/
  sitzen (position, dative) pair with legen/stellen/setzen (direction,
  accusative).
- The cases are what make German word order flexible: the endings, not the
  position, show who does what.

## prepositions — `https://deutsch.lingolia.com/en/grammar/prepositions`

- **Source:** Lingolia, "Prepositions in German Grammar".
- **Angle:** the case-per-preposition survey, the two-way set, contractions,
  and verb+preposition units in one page.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Each preposition takes a specific case (accusative, dative or genitive) and
  there is no general rule — it must be learned. Common accusative:
  durch, für, gegen, ohne, um; common dative: aus, bei, gegenüber, mit,
  nach, seit, von, zu.
- The two-way prepositions (Wechselpräpositionen) are an, auf, hinter, in,
  neben, über, unter, vor, zwischen: dative for fixed location (wo?),
  accusative for movement/direction (wohin?).
- Contractions: an dem = am, bei dem = beim, in dem = im, in das = ins,
  von dem = vom, zu dem = zum, zu der = zur.
- Prepositions fall into types — time (um, nach, vor, seit, ab, bis), place
  (an, auf, in, nach, zu, aus, von, durch, über), manner (mit, ohne, aus,
  für), causal (wegen, dank, aufgrund, trotz) — and some belong to more
  than one.
- Verbs, adjectives and nouns can take a fixed preposition with no meaning of
  its own (gehören zu, stolz auf, ein Beweis für) — the page links its list
  of them; the reference prepositions (bezüglich, hinsichtlich + genitive;
  laut, zufolge, gemäß + dative) introduce according-to meanings.

## verbs — two links

### `https://deutsch.lingolia.com/en/grammar/verbs/modal-verbs`

- **Source:** Lingolia, "Modal Verbs in German Grammar".
- **Angle:** the six modals' full conjugation tables and the double-infinitive
  perfect; the group's werden, imperative and zu-infinitive rules are not
  covered here.
- **Accessed:** 2026-11-02.

Key points the page makes:

- The modal verbs are dürfen, können, mögen, müssen, sollen, wollen; they
  express ability, necessity, obligation, permission or possibility, and are
  generally used with the infinitive of the full verb.
- The full verb can be dropped when obvious from context (Kannst du Deutsch
  (sprechen)?).
- Present-tense forms are irregular in the singular (ich muss/kan…: muss,
  kann, darf, soll, will, mag) with identical 1st/3rd singular; plural equals
  the infinitive.
- Simple past: musste, konnte, durfte, sollte, wollte, mochte (möchten's past
  form uses wollte). In the perfect, modal + full verb use the double
  infinitive with haben (Er hat tanzen können); the participle (gemusst,
  gekonnt …) appears only when there is no full verb (Das habe ich nicht
  gewollt).
- The table also lists Subjunctive II stems (müsste, könnte, dürfte, sollte,
  wollte, möchte).

### `https://deutsch.lingolia.com/en/grammar/verbs/separable-verbs`

- **Source:** Lingolia, "Separable and Inseparable Verbs in German Grammar".
- **Angle:** which prefixes separate, participle ge- placement, and dual
  prefixes; the modal side is the other link's angle.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Separable prefixes: ab-, an-, auf-, aus-, bei-, ein-, los-, mit-, nach-,
  her-, hin-, vor-, weg-, zu-, zurück-. In the finite form the prefix
  separates and usually goes to the end of the sentence (ich stehe an); in
  the past participle ge- goes between prefix and verb (angestanden).
- Inseparable prefixes: be-, emp-, ent-, er-, ge-, miss-, ver-, zer-. They
  stay attached in every form and their participle has no ge- (bestanden).
- The prefixes durch-, hinter-, über-, um-, unter- can go both ways: always
  separable (umschauen), never separable (umarmen), or both with different
  meanings (umfahren: Er fährt das Schild um vs Er umfährt das Schild).
- Stress marks the difference: separable verbs stress the prefix, inseparable
  verbs the syllable after it.

## tenses — two links

### `https://deutsch.lingolia.com/en/grammar/tenses/simple-past`

- **Source:** Lingolia, "Präteritum – Past Tense in German Grammar".
- **Angle:** the Präteritum endings and the irregular stems — sein/haben and
  the strong verbs; Futur I is the other link's angle.
- **Accessed:** 2026-11-02.

Key points the page makes:

- The Präteritum is for completed past actions and past facts, mainly in
  written/storytelling German; spoken German prefers the Perfekt — but the
  simple past of sein/haben is common in speech for facts and conditions
  (war, hatte).
- Weak verbs: stem + -te + endings (ich lernte, du lerntest …); strong/mixed
  verbs change the stem (sehen → sah, gehen → ging, bringen → brachte) and
  take -st in the 2nd singular and no ending in 1st/3rd singular
  (ich/er sah, du sahst).
- sein and haben are irregular and especially important: war/warst/war/
  waren/wart/waren and hatte/hattest/hatte/hatten/hattet/hatten.
- Spelling adjustments: stems in s/ß/z drop the -s or add -e (du last/lasest);
  stems in -d/-t insert an -e before the ending (du landetest); weak stems in
  consonant+n (ordnen, zeichnen, öffnen) also insert -e; strong stems in -ie
  drop the final -e of the 1st/3rd plural (wir schrien, not schrieen).

### `https://deutsch.lingolia.com/en/grammar/tenses/future`

- **Source:** Lingolia, "Futur I – Future Tense in German Grammar".
- **Angle:** the werden + infinitive future and its assumption use — what the
  Präteritum page does not cover.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Futur I is mostly used for assumptions about the present or future; it can
  also express a future intention, though spoken German more often uses the
  present tense for that.
- Formation: finite werden (ich werde, du wirst, er wird, wir werden, ihr
  werdet, sie werden) + infinitive of the full verb at the end.
- Assumptions are often strengthened with wohl, sicher, bestimmt; with such a
  signal word the present tense can replace Futur I with the same meaning
  (Es wird wohl spät sein = Es ist wohl schon spät).

## adjectives — `https://deutsch.lingolia.com/en/grammar/adjectives/declension`

- **Source:** Lingolia, "Declension of Adjectives in German Grammar".
- **Angle:** the full ending tables for all three article situations; the
  app's rules cover the weak and mixed ones, the page adds the no-article
  type and the spelling exceptions.
- **Accessed:** 2026-11-02.

Key points the page makes:

- Only adjectives before a noun are declined; predicative adjectives never
  change (Das Haus ist groß).
- Three declension types by what introduces the noun: Type 1 after the
  definite article (and derselbe, dieser, jener, jeder, mancher, solcher,
  welcher, alle) — mostly -e in nominative singular and -en elsewhere; Type 2
  after ein/kein/possessives — distinctive -er/-es in nominative masculine
  and neuter, -en elsewhere; Type 3 with no article (also after numbers and
  viele/einige/wenige/andere) — the endings mirror the article itself
  (-er/-e/-es, -en, -em/-er/-em, -en).
- Spelling exceptions: adjectives in -e take no second -e (leiser); -el drops
  the e (dunkler); vowel+er usually drops the e (teures); hoch loses the -c
  (hohes).
- Comparative and superlative forms decline like the positive under the same
  type (ein besseres Büro, des besten Teams); mehr and weniger are not
  declined. German sometimes uses the comparative politely (ein älterer Mann).

## passive — `https://deutsch.lingolia.com/en/grammar/verbs/passive`

- **Source:** Lingolia, "Passive Voice in German Grammar".
- **Angle:** the full passive machinery — active-to-passive conversion, every
  tense, von/durch, verbs without a passive — in one page.
- **Accessed:** 2026-11-02.

Key points the page makes:

- German has two passives: the processual passive (Vorgangspassiv) with
  werden, emphasising the action, and the statal passive (Zustandspassiv)
  with sein, describing the state after the action (Er ist verletzt).
- The passive is used when the agent is unknown, obvious or unimportant. To
  name the agent: von + dative (the doer); durch + accusative introduces the
  means or method.
- Conversion steps: the accusative object becomes the nominative subject, the
  verb becomes werden (conjugated for the tense) + past participle. A dative
  object that becomes the "subject" stays dative (Dem Verletzten wurde ein
  Verband angelegt).
- Tense table: Präsens wird gefahren, Präteritum wurde gefahren; Futur I and
  Konjunktiv II append the infinitive werden (wird/würde … gefahren werden);
  the perfect tenses take sein + participle + worden (ist … gefahren worden —
  worden, not geworden).
- Only transitive verbs can passivise: haben, kennen, wissen, es gibt, all
  intransitive verbs and reflexive verbs cannot (though intransitive verbs
  have an impersonal passive: Es wird oft nicht aufgepasst).
- Active stand-ins for the passive: man + active verb; sich + infinitive +
  lassen; sein + zu + infinitive; -bar adjectives; zu + Partizip I;
  bekommen/kriegen + participle (informal).

## moods — `https://deutsch.lingolia.com/en/grammar/verbs/subjunctive`

- **Source:** Lingolia, "Konjunktiv – the Subjunctive Mood in German Grammar".
- **Angle:** both moods on one page — Konjunktiv II forms and uses, then
  Konjunktiv I for reported speech; no other link in the map covers either.
- **Accessed:** 2026-11-02.

Key points the page makes:

- The subjunctive marks the unreal: wishes, guesses, polite requests,
  hypotheticals. Konjunktiv II is the one learners must actively know;
  Konjunktiv I is mostly passive knowledge (news-report indirect speech).
- Present Konjunktiv II: würde + infinitive for most verbs; modal verbs (and
  sein/haben) add the umlaut to the simple-past stem instead (wäre, hätte,
  müsste, könnte, dürfte, bräuchte, wüsste); sollen and wollen take no
  umlaut, so their Konjunktiv II equals their past tense. One-form verbs
  (käme, ginge) exist but the würde form is more common.
- Past Konjunktiv II: hätte/wäre + past participle (Ich hätte dir geholfen);
  modals take hätte + infinitive + modal infinitive (Du hättest gehen
  können).
- Typical uses: wishes (Ich wünschte …), wenn-conditionals, polite requests
  (Würdest du …), suggestions (könntest), guesses (wäre), regrets about the
  past (Hätte ich nur …).
- Konjunktiv I marks reported speech and distances the speaker from the
  claim. Its present is the infinitive minus -n, almost always 3rd singular
  (er habe, er sehe, er könne); sein is unique (ich sei, du sei(e)st, er sei,
  wir seien …). Perfect and future use the helper verbs in Konjunktiv I (er
  sei gegangen, er werde gehen).
- Konjunktiv I is avoided (switching to Konjunktiv II) wherever its form is
  identical to the indicative — typically ich, wir and sie/Sie — and for 2nd
  person forms.

## Topics with no entry

None: all eleven groups have at least one verified link.

## Uncertainties

- The Lingolia pages are living web pages and can change after the check date;
  re-run `scripts/check-further-reading-links.sh` after any content edit and
  re-read a page before extending its distillation.
- The main-clauses page's middle-field order is Lingolia's own rendering of
  the TeKaMoLo convention (time before place before direct object); Task-07
  should attribute the order as stated on that page, not invoke the TeKaMoLo
  name, which the page itself does not use.
- Lingolia's free pages carry some Plus/paywalled exercises around the article
  body; the grammar text itself is free to read.
