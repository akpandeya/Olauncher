# Ask AI chat threads (design)

Issue #699 · Mock: `ask-ai-chat-mock.html` (8 screens) · Follows `TEMPLATE.md`.

## Problem

When a drill marks an answer wrong, the correction is one line — "das · Wohin? →
movement". It names the rule but can't answer "why?" (why *stellen* takes the
accusative while *stehen* takes the dative). The learner has nowhere to ask. The
same gap exists on a saved word: its one-line context can't hold a conversation.

We want the learner to ask the AI about a specific miss or word, ask follow-ups,
keep the conversation as long as they want, and revisit it later.

## Design

Built from the existing drill-screen skin (`today_panel`, `TodayChip`, existing
tokens — no new colours). AI calls ride the existing provider config
(`AI_PROVIDER` / `AI_BASE_URL` / `AI_MODEL`, BYOK). No model is hardcoded.

### Entry points

An entry is **visible** iff `AiPracticeGate.canRun(aiPracticeEnabled, hasKey,
model)` — the same gate in front of every AI call — **and** the provider config
is valid (`validate()` returns null). `DISABLED`, `NO_KEY`, `NO_MODEL` and
invalid config mean hidden, never greyed. A revoked key, dead network or empty
account can't be known in advance; those are runtime errors (see Reliability).

| Surface | Entry | Subject it opens |
|---|---|---|
| Drill summary, wrong row | Chip under the correction line: "Ask AI" (no delivered turns) or "Chat · n". Chip long-press → thread sheet | Routed (below) |
| Drill summary, almost row | Same chip, same behaviour | Routed |
| Review summary, again/almost row | Same chip | The reviewed word |
| Word card, revealed | Full-width row under the review buttons: "Ask AI about this word", with "n turns" when history exists. Same action in the long-press popup | The word |
| Word card, practice prompt — pre-reveal | Nothing — the item's answer has not been shown yet, and chat would leak it | — |
| Word card, practice prompt — revealed | Same row as the revealed card. The grounding additionally carries the practice prompt and its answer; subject is the practice item's word — the item's `wordId` resolves to the word's normalized citation text (one candidate, no proposal) | The word |
| Word long-press popup, hidden card | Nothing — Edit/Delete only. The popup *is* reachable pre-reveal today, so this is a **deliberate suppression**; the card-row rule does not cover the popup | — |
| Right / knew rows | Nothing | — |

One long-press grammar everywhere: long-press on the entry (chip or word-card
row) → thread sheet. Nothing else uses long-press.

**Counts, one shape per surface.** A chip reads "Ask AI" or "Chat · n"; a
full-width row reads "Ask AI about this word" and appends "n turns". There is no
"Ask AI · n" form. n is always delivered turns. A **turn** is one user question
plus the reply that answered it; a failed or in-flight question is not a turn.

### Chat screen

```
 header (pinned)      subject line · ✕
 ┌ grounding card ┐   what the AI sees; collapsible
 └────────────────┘
 messages (scroll)    assistant panels · user tiles right
 ─────────────────
 usage line           "≈ 1.8k tokens · 4 turns" (muted, tap for detail)
 composer (pinned)    [Ask a follow-up…        ] [Send]
```

- **Header**: word → the saved word as written; rule → the rule's `title`
  ("Two-way prepositions"); topic → the drill type's label; a provisional
  both-candidates thread → "New conversation" until the first reply names the
  subject.
- **Grounding card**: the miss (prompt, wrong pick, correct answer, rule chips),
  the word (word, meaning, saved context) and/or a practice prompt with its
  answer — shown to the learner so they always see what the AI sees. Expanded on
  a fresh thread or a resume carrying a new miss; collapsed otherwise.
- **New conversation** starts a fresh thread for the same subject; the old one
  stays in the sheet.
- **Cancel** on the in-flight "Thinking…" placeholder removes the tile and
  restores the draft. The question was persisted when Send fired (see
  Reliability), so Cancel also deletes that stored turn — nothing of a cancelled
  turn remains. If the cancelled turn was the thread's first, the thread is
  deleted.
- Chat is **post-answer only** — it never appears while a question is on screen,
  never scores, advances or mutates a round, and back returns the summary exactly
  as it was.

### Threads

- **Subjects, not items.** A thread belongs to a word, a grammar rule, or a topic:
  - **word** — ref is the normalized citation text (trim + lowercase + NFC).
    Deleting a word leaves its threads as an inert archive; re-saving the same
    noun re-links them. Two saved words sharing a citation form (`Bank`, `See`)
    share one thread — accepted trade-off.
  - **rule** — the help-card rule id (#651, `DrillHelpContent`), e.g.
    `prep-two-way`, shown by the rule's `title` ("Two-way prepositions"). A miss
    on *neben* today and on *auf* next week join one conversation.
  - **topic** — the drill type, for items without help-card content (null
    `ruleId`: conjugation, cloze, AI practice items), so every wrong row still
    gets a chip.
- **Prerequisite: every authored rule is wired.** Today only the gap topics set
  `ruleId`. The live topics (prepositions, case endings, articles, plurals,
  adjective endings, pronouns, verb prepositions, separable verbs, word order)
  already have authored help content in `docs/design/drill-content/`, but their
  items carry no rule id, so their misses would land in a topic thread. Wiring
  that content (items → rule ids, `DrillHelpContent` entries) comes before chat,
  and it also switches on those topics' pre-answer "Why?" card as #651 intended.
  `DrillHelpRule` gains a short `title` for every rule, gap and live.
- **Routing** (deterministic, in order):
  1. Word-linked by construction, read from the item id (`DrillItem` has no
     `wordId` field): AI practice items `ai:<wordId>:<itemId>`; case- and
     adjective-ending items built from a saved word `<wordId>:<key>`; articles
     and plurals built from a saved word use the bare word id; review misses
     are the word itself.
  2. Built-in items that carry the noun's citation form in their id — articles
     `art:<noun>` and plurals `plur:<noun>` — whose noun exactly matches a saved
     word. Exact match only, never the inflected prompt token. Other types carry
     only inflected forms and skip this step.
  3. Otherwise: the rule thread, or the topic thread when there is no rule.
- **When a fresh thread could be word or rule** (both candidates plausible —
  the item matched a saved word by its citation noun (routing step 2) and has a `ruleId`; **fresh** means neither the word
  thread nor the rule thread exists yet; if exactly one exists it opens, and if
  both exist the word thread opens): the
  grounding card shows both blocks and the AI's first reply names the subject —
  first non-empty line `SUBJECT: word` or `SUBJECT: rule`, trimmed, emphasis
  markers stripped, case-insensitive, then removed before render. Chat is prose,
  so every chat request passes `jsonMode = false` explicitly. Malformed or
  missing → rule fallback. Once both threads exist, the deterministic router
  decides; the proposal never runs again.
- **A failed first reply is still the first reply.** If the first send fails
  before any reply arrives, the retry's reply is parsed for `SUBJECT:` as usual —
  it is still the thread's first assistant message, and the thread is still
  provisional until it lands. A thread that never gets a reply falls back to the
  rule thread on the next send.
- **Thread existence starts at first send.** "Ask AI" on a first-ever miss opens
  an empty, unpersisted chat; a thread is created only when something is sent.
- **Multiple threads per subject**: the latest opens by default; the sheet lists
  all (first-question snippet, date, turn count). No global thread list — history
  lives where the subject lives.
- **Context budget: 10k input tokens.** A named **cost** threshold, not a model
  limit: BYOK models vary, the provider carries no context-size metadata, and
  every turn re-bills the history it resends. Crossing it starts an automatic
  **compaction**: older turns are summarised into one block, and the next request
  carries that summary instead of them. Compaction runs **after the answer
  lands**, never on the critical path of a send; its token cost shows in the
  usage line (the line always reports the latest request). A send is never
  blocked: if compaction is unavailable or fails, the request goes out with the
  fallback below.
- **Compaction is lossy for the model, lossless for the user.** The full
  transcript is always stored; the summary replaces old turns *in the request*
  only. In the transcript the summary sits where its turns were, as the muted
  marker "Earlier turns summarised · see what it kept"; the marker also appears
  at the moment the compaction happens, not only on the next open. Tapping
  expands the summary and the original turns beneath it, read-only. It is the
  same marker shape as "Earlier turns omitted" — one pattern, two meanings: not
  sent, still stored / summarised, still stored. A later successful compaction
  replaces an earlier "Earlier turns omitted" marker.
- **How much is summarised:** the oldest turns, up to the point where the
  projected next request (summary + remaining turns + groundings) fits under
  7k — headroom below the 10k trigger, so compaction doesn't fire again
  immediately.
- **Re-summarise from the stored originals, never from the previous summary**, so
  drift is capped at one pass however long a thread lives. Only the newest
  summary is active — an older summary whose turns it covered are re-summarised
  into the new one. Failed turns are never summarised — they were never in the
  context to begin with.
- **Fallback when compaction cannot run:** the oldest prose turns leave the
  request and "Earlier turns omitted" appears in the visible history. Truncation
  is the degraded path, not the plan.
- **Groundings are bounded too.** The newest **3** groundings are re-injected as
  a leading block on every request; older ones stay in the store and remain
  visible on the card, but the model is not told about them — a dropped
  grounding silently leaves its miss context. One per miss would otherwise grow
  without bound — groundings accumulate where prose at least gets summarised —
  and could take the whole budget, leaving prose no room. Bounded request,
  unbounded store, same rule as everywhere else.
- **Counts never regress:** the chip's n, the usage line and the sheet's turn
  count all count delivered turns, summarised ones included. A count that drops
  would read as deleted history.

### Reliability

| State | Behaviour |
|---|---|
| waiting | User tile shown, "Thinking…" placeholder, Send disabled (one request at a time), draft kept, Cancel available |
| success | Reply replaces the placeholder; usage line updates; turn persisted |
| failed | Inline rose line per error kind below; the turn is persisted as failed **before the request fires**, stays visible as "Not sent · Retry", and is excluded from AI context and chip counts until a retry succeeds |
| cancelled | Tile removed, draft restored; first-turn cancel deletes the thread |

Each error kind has its own string. These are the copy for `strings.xml`, and the
mock uses the same wording — no slash-joined variants.

| Error | Message | Recovery |
|---|---|---|
| RATE_LIMIT | "The AI is busy. Try again in a moment." | Retry |
| NETWORK | "Couldn't reach the AI. Check your connection." | Retry |
| SERVER | "The AI service had a problem. Try again." | Retry |
| BAD_RESPONSE | "The AI sent something unreadable. Try again." | Retry |
| AUTH | "Your AI key was rejected. Check your AI settings." | "Open AI settings" (primary) + Retry |
| BILLING | "The AI account is out of credits. Top up to continue." | Retry after top-up |

- Only the **newest** failed turn is retryable. Once a newer turn is delivered,
  an older failed turn converts to "Ask again" (a new turn quoting the question),
  so history never orders a retry after later messages.
- A thread whose only turn failed still resumes as the latest thread — the
  "Not sent · Retry" tail reads correctly on arrival.
- Request scope is `viewModelScope`: rotation cancels nothing; process death
  leaves the persisted "Not sent · Retry" turn.
- Storage failure shows an inline "Couldn't save this conversation" — a visible
  message is never silently lost. A corrupt store is renamed `ai_chats.json.bak`
  and chat starts fresh.
- Compaction failure is not a user-facing error: the send proceeds with the
  truncation fallback and the marker says which of the two happened.
- Accessibility: 48dp targets; chips and sheet rows announce their content
  ("Chat, 4 turns"); Thinking/error lines are `liveRegion="polite"`.

### Data

`AiChatStore` (`filesDir/ai_chats.json`), same codec and atomic-write pattern as
`WordsStore`:

```
threads[{
  id                                          // stable random id; sheet and resume open by it
  subject {kind: word|rule|topic, ref}
  createdAt, updatedAt
  groundings [{id, kind, payload}]            // newest 3 re-injected per request
  summaries  [{text, coversUpTo, createdAt}]  // newest replaces the turns it covers
  messages   [{role, text, ts, usage, failed?, groundingId?}]
}]
```

- `payload` is typed per kind, each with a round-trip decode test — an
  unshapeable payload in a hand-rolled codec silently decodes to a blank card:
  - `word`: text, meaning, context
  - `miss`: prompt, picked, answer, ruleId, chips
  - `practice`: prompt, answer (+ the practice item's `wordId`, resolved to the
    word's citation text as the thread subject)
- `groundingId` sits on the user turn that introduced the grounding.
- `coversUpTo` is the **index of the last message the summary covers**, so the
  request split is reconstructable and the expander knows which original turns
  to show under the summary. Only the newest summary is active; older entries
  stay in the store for the audit trail.

## Rejected ideas

- **Hardcoding a model** (GLM Flash, DeepSeek, …) — cheapest-model rankings change
  constantly and the codebase already picks provider + model via prefs; chat rides
  the same config.
- **Streaming for v1** — needs new provider-interface infrastructure; one-shot +
  Cancel gives most of the value. Revisit only if waiting proves painful.
- **Per-item threads** — a thread per drill item fragments history nobody
  revisits; subjects are word/rule/topic.
- **Chat during a question** — leaks answers and inflates the round's interaction
  budget; the pre-answer "Why?" stays the static help card (#651).
- **AI picks the thread on every miss** — flip-flopping scatters a topic across
  threads; the proposal only names a *new* thread, the router handles resumes.
- **LRU eviction of old threads** — deletes exactly the archive revisiting wants;
  every thread is kept and the request is budgeted instead.
- **Dropping summarised turns from the store** — the model may forget them, the
  learner must not; the transcript keeps every turn and expands under the marker.
- **Summarising the previous summary** — drift compounds every pass; compaction
  always re-summarises the stored originals.
- **A manual "Summarise earlier turns" button** — the learner should not have to
  manage tokens, and the marker keeps the result visible; automatic keeps the
  request bounded without a control nobody presses. *Revisit* if summaries start
  poisoning answers, or if learners are surprised by a line they did not trigger.
- **Stem/inflection matching for saved words** — a wrong match silently misroutes
  into an unrelated conversation; exact citation-form match under-matches
  gracefully to a rule or topic thread.
- **Dropping failed turns from the transcript** — loses the typed question;
  failed turns persist as "Not sent · Retry".
- **Excluding failed-only threads from latest-thread resolution** — strands the
  question unretryable behind a new empty chat; the fix, if ever needed, is
  carrying the orphaned turn into the new draft.
- **Chat writes to `Word.context`** ("save this explanation") — that is #693's
  territory, with its own format and dedup rules.
- **A global thread-list screen** — history is reachable where the subject lives;
  a list would duplicate navigation for little gain.
- **Gender colouring on German text in chat** — colour is a help-card rule (#651);
  chat prose stays ink.
- **Pre-send offline check** — an extra state with no benefit over the inline
  NETWORK error.
- **Chip on wrong rows only, almost via long-press** — an invisible path is a
  feature nobody discovers; almost rows get the same chip.

## Open questions

None for the design stage. Watch items (check in device testing):

- Does the `SUBJECT:` proposal misroute in practice? If yes, the fallback flips
  to the deterministic router.
- Does resuming a failed-only thread feel like a bug? If yes, carry the orphaned
  turn into the new draft.
- Is the 10k trigger comfortable on real threads? It is deliberately
  conservative, and it is a cost threshold, not a model limit.
- Are 3 re-injected groundings enough context on a long-lived rule thread, or
  does the card need to say which older misses are no longer sent?
- Does a summary ever contradict the turns it covers? A poisoned summary is the
  first suspect when answers drift; re-summarising always runs from the stored
  originals, so the recovery is delete the summary and let compaction redo it.
- Do learners notice and understand the "Earlier turns summarised" marker, or
  does it read as lost history?

## Flows

**First-ever miss → chat**

```
 drill summary
 └─ ✗ Ich lege das Buch auf ___ Tisch.  (dem → den)
     ( Ask AI )  ◄── tap
         │ router: no word candidate → rule thread
         ▼
 ┌ chat: "Two-way prepositions" ────────┐
 │ grounding (expanded): ✗ miss line    │
 │ (no messages yet)  [ ask… ] [Send]   │
 └──────────────────────────────────────┘
     │ Send → thread created → Thinking… → reply
     ▼
 chip now "Chat · 1"; back returns the summary intact
```

**Failed send, then retry**

```
 [Send] ─► user tile + Thinking…
           │ RATE_LIMIT
           ▼
 user tile (kept) + "The AI is busy. Try again in a moment." (Retry)
 turn persisted as failed — excluded from context & counts,
 survives process death
     │ Retry
     ▼
 Thinking… ─► reply filed; turn enters history; count includes it
```

**Both candidates on a fresh thread**

```
 ✗ ___ Straße  (der → die)   articles item art:straße; "die Straße" is saved
 └─ ( Ask AI ) ─► empty thread, header "New conversation"
     grounding: [word block] [rule block] + ✗ miss line
         │ Send
         ▼
 first reply, first non-empty line:
   SUBJECT: word  → word thread   (header: die Straße)
   SUBJECT: rule  → rule thread   (header: Nouns ending in -e)
   missing/broken → rule fallback (reply still renders normally)
```

**Thread crosses 10k → automatic compaction**

```
 turn #21 send: request would exceed 10k input tokens
     │ reply lands (never delayed by compaction)
     ▼
 compaction summarises turns #1…#14 — enough that the projected
 next request (summary + turns #15…#21 + groundings) fits under 7k
     │ (from the STORED originals, one pass)
     ▼
 transcript: "Earlier turns summarised · see what it kept"   ← appears now
 turn #22 request: summary block + turns #15…#21 + newest 3 groundings
 chip still "Chat · 21"  (counts never regress); usage = turn #22's request
     │ tap the marker
     ▼
 summary text + the 14 original turns, read-only
     │ compaction failed instead?
     ▼
 same send goes out minus the oldest prose, "Earlier turns omitted"
```
