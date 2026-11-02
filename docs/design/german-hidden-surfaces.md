# German surfaces you cannot reach

Date: 2026-11-02

## Status

Informational, with a short assessment and the decisions at the end. It records what the app does today and what is
worth changing; each change is filed as its own issue.

## TL;DR

There are two different German gaps. The first is "no switch": the feature runs on fixed rules in the code and the
user cannot change it. The second is "no lens": the app already holds the content but only shows it at the moment it
was produced. The Deutsch section of Settings holds twelve rows and is the only place a user can change German
behaviour. Everything else — the review schedule, drill length, AI budgets, the news harvest, the context rewrite,
the Ask-AI chat, the grammar rules and the chat threads — is either fixed or reachable only through the one screen
that produced it.

## 1. What counts as reachable

A surface is reachable when the user can open it on purpose: a switch, a menu, a screen, or a row that starts an
action. A feature has no surface when the only thing that controls it is a preference value in `Prefs` or a constant
in the code, when the only way to see its content is the screen where the content was created, or when a background
job has no way to be started by hand. This document lists the German-side features in those groups.

## 2. The rows that do have a control

The Deutsch page is built from the `deutsch` list in `SettingsCatalog`. It defines twelve rows. Eleven always show
while German is on; `germanScreenPickScreenshot` appears only in the rare state where the screen-pick service cannot
take screenshots yet. The German-dependent rows stay hidden until the master switch is on, and `isRowAvailable`
keeps them out of Settings search in that state too.

| Row | What it changes |
|---|---|
| German | The master switch. Turning it on runs the three-step setup; turning it off stops the German services. |
| Clock | System clock, the German "de" form, or "in Worten". |
| Dictionary app | Which app opens a lookup; Wiktionary by default. |
| Offline dictionary | Use the bundled word list. |
| Look up online | Send a picked word to Wiktionary. |
| Spell suggestions | Offer German spellings for saved words. |
| Pick words from the screen | The accessibility overlay. |
| Screen-pick re-enable hint | Opens accessibility settings when the service lost screenshot ability. |
| Pause word | The German word on the pause screen. |
| German UI | German wording for nudges and pause. |
| AI practice | The bring-your-own-key generator. Its key, base URL and model sit behind the "Advanced" panel. |
| News sentences | Fill blank word contexts from German news. |

Two German surfaces sit outside the Deutsch section: the Words and Drills cards on Today are switched from the Today
card picker (`showTodayCardsDialog`). A user looking for a German control inside the Deutsch section will not find
those two there.

## 3. Behaviour with no control

### 3.1 The review schedule

A word moves through seven boxes with fixed day counts, `Leitner.INTERVALS = [1, 2, 4, 8, 16, 32, 64]`. "Knew it"
promotes the word one box and sets the next review from that table; "Almost" keeps the box and returns the word at
half the interval; "Again" drops it to box 1, due tomorrow. `ReviewPreview` shows each button's outcome before the
tap, so the user sees the schedule, but the box count and the day counts cannot be changed.

### 3.2 Drill rounds and ranking

- A round draws a random size between `DrillRoundPlanner.MIN_ROUND_SIZE` and `MAX_ROUND_SIZE`, which is 5 to 15
  items; a type with fewer items contributes all of them.
- Each item's format comes from a recognition ladder. First exposure is always a tap, the variety rung follows one
  right answer, and typing follows two (`TYPED_MATURITY`). A round allows at most three typed items and one built-in
  flashcard.
- AI practice items join a mixed round as flashcards only, at most two per round (`MAX_AI_PER_ROUND`).
- Ranking keeps decayed counts per type. Each round multiplies the old counts by `DECAY` (0.8), and a percentage
  shows only after about two rounds. The decay, the `RIGHT_PRIOR` and `ASKED_PRIOR` values, and the display rule are
  all constants in `DrillScores`.

### 3.3 AI practice budgets and cadence

Only the on/off row and the key dialog are visible. Behind them:

- A daily cap of 100 words (`AI_DAILY_WORD_CAP_DEFAULT`), kept as a guardrail against a runaway loop.
- A background top-up every six hours (`PracticeWorker.PERIODIC_INTERVAL_HOURS`).
- A target of three items per word (`PracticeContract`).
- A batched run two minutes after a capture (`PracticeWorker.CAPTURE_DELAY_MS`), and a fire-and-forget kick when
  Review opens with a thin word.
- A digest that runs weekly or earlier when a weak spot appears: two "again" marks on a word in fourteen days, or a
  drill type under 70 per cent first try (`PracticeDigest`).

Provider, base URL and model are editable in the key dialog's Advanced panel. The caps and the cadences are not
editable anywhere.

### 3.4 News sentence harvesting

The only control is the News sentences switch. The harvest runs every twelve hours, on unmetered network and while
charging, and caps itself at 10 articles per run and 30 a day (`Prefs.NEWS_DAILY_ARTICLE_CAP`). Which sentences match
a saved word is decided by the lemma matcher in `NewsHarvester`; no screen exposes the feeds or the matching.

### 3.5 Filling thin word contexts

After each harvest, `ContextRewriteRun` fills a blank or thin word context from a linked news sentence, or from an AI
rewrite (`SentenceRewriter`) when AI practice is on. The rewritten sentences live in `PracticeSentencesStore`. This
whole pass follows the News sentences switch and the AI practice switch; it has no row, no name in the interface and
no switch of its own.

### 3.6 The Ask-AI chat

The chat has no switch. It appears only while AI practice is on and a key and model are set
(`AskAiEntryPoints.environment`). Its entries sit on drill misses, the review summary and the word card, and the chat
screen opens only from one of those.

### 3.7 The conjugation quiz and the word lists

The conjugation quiz has no settings row. It opens from a word row (`WordsFragment`), a word list (`WordListFragment`)
or a drawer word lookup, and reads Wiktionary under CC BY-SA. The word lists have no behaviour settings either: the
built-in "From screen picks" list fills from captures, and the only control is adding or removing words.

### 3.8 The screen-pick hint

The "Tap or circle words" hint shows until the first pick and then sets `CAPTURE_HINT_SEEN`, after which it never
returns. Screenshot readiness is tracked by `CAPTURE_SCREENSHOT_OK`. Neither is a setting, so a user cannot bring the
hint back once it is gone.

## 4. Content and actions with no surface

These are cases where the data already exists but the app only shows it at the moment it was produced.

### 4.1 The grammar rules have no index

`DrillHelpContent.allRules()` builds a fixed set of help-card rules from the authored files in
`docs/design/drill-content/` and `DrillHelpContentLive`. Each rule carries a title, a plain explanation, a forms
table and an example. A user only ever sees one rule, and only inside a drill, after a miss or on a flashcard
(`DrillHelpCard`). No screen lists the rules or looks one up by name, and the rule subject the Ask-AI chat already
supports has no entry point of its own; a rule detail that carries an "Ask AI about this rule" row would give it one.
The row is an ordinary chat entry: it goes through `AskAiEntryPoints.openChat`, resumes the rule's latest thread, and
its sends obey the same automatic-compaction rule as every other thread.

### 4.2 The AI chats have no "all conversations" list

`AiChatStore.threads()` returns every stored thread, but the only list in the app is `AskAiThreadSheet`, which shows
`threadsFor(subject)` for one word, rule or drill type. A user reaches a chat only from the miss or word card that
started it, and there is no place that lists every conversation or reopens one out of context. The Ask-AI design
rejected a global list on purpose (`ask-ai-chat.md`, Out of scope: "history is reachable where the subject lives").
That holds while a practice session is the only moment you want the history, but it leaves no way to read or continue
a chat on its own.

### 4.3 Harvesting has no "fetch now"

`NewsHarvestWorker.runOnce()` exists and switching News sentences on already runs one harvest. After that the next
run waits for the twelve-hour schedule, and there is no on-demand trigger. AI practice does have one: "Generate now"
in its row menu (`generateAiPracticeNow`). The context rewrite and the weekly digest are the same — they have a run
path in code and no manual trigger.

### 4.4 Stores with no screen at all

Several files hold German data that no screen shows: `recent_sentences.json` (`RecentSentencesStore`, harvested
sentences), `practice_sentences.json` (`PracticeSentencesStore`, rewritten sentences), `practice_items.json`
(`PracticeItemsStore`, shown only inside rounds and review), and `review_events.json` (`ReviewEventsStore`, read for
counts and progress but never listed). These are machine state behind the features; the useful views, such as a
word's sentence, its progress and a drill item in a round, already exist.

## 5. Controls that exist but are hidden

These are not in the Deutsch section, but they reach German behaviour:

- Drawer commands `:w <word>` (look up) and `:sw <words>` (save words); both need German on (`DrawerCommand`).
- Typing "drill" in the drawer shows the suggested round and an "All drills" row (`DrawerDrills`).

Both are typed rather than tapped, so a user who does not already know the words will not find them.

## 6. What is worth adding

Sections 3 and 4 are different problems. Section 3 is "no switch": the app made a decision and hid it. Section 4 is
"no lens": the app knows something and shows it only where it was produced.

1. A rules index is the clearest win. The rules are a fixed, keyed registry with a title, an explanation, a table and
   an example; a read-only list that reuses the help-card rendering adds no content, so it stays inside the "no new
   drill content" boundary, and it gives the Ask-AI rule subject a way in.
2. An all-chats list is nearly as cheap. `threads()` already returns the data, and the list makes an existing feature
   findable instead of reachable only from a miss. It does override an explicit earlier decision, so the issue records
   the reasoning rather than quietly disagreeing.
3. A "fetch now" for news is a small mirror of AI practice's "Generate now". The run guard and the daily cap stay.
4. The raw stores are best left alone. They are intermediate data, and a JSON viewer is not a product.

The line to hold: a feature that generates, such as the context rewrite or the sentence rewriter, should stay
invisible; content the user owns, such as rules, chats and harvested sentences, is what deserves a surface.

## Decisions (2026-11-02)

Each item in section 6 is filed as its own issue. The placement questions above are settled:

- **Rules index (#792):** it lives inside the Drills hub, as a "Grammar rules" entry. No separate screen, and no link
  from the miss help card.
- **All-chats list (#793):** it opens from a button in the Words screen header, next to the Drills button. That is the
  on-demand entry the Ask-AI design lacks; the exact placement and shape are settled by a mock
  (`german-surfaces-mock.html`) before the code lands.
- **Fetch now (#794):** a normal row action, not a debug gesture — a visible trailing chip on the News sentences row,
  mirroring AI practice's "Generate now". The AI practice row takes the same shape: with no key it shows "Add API
  key"; with a key, "Generate now" is prominent and the key management moves behind the ⋮ menu.
- **Harvest network (#795):** the background run stays Wi-Fi only; a manual "Fetch now" is not gated by the network,
  because the user asked for it.
