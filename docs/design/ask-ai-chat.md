# Ask AI chat threads (design)

Issue #699 · Mock: `ask-ai-chat-mock.html` (6 screens) · Follows `TEMPLATE.md`.

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
model)` — the same gate in front of every AI call. `DISABLED`, `NO_KEY`,
`NO_MODEL` and invalid config mean hidden, never greyed. A revoked key, dead
network or empty account can't be known in advance; those are runtime errors
(see Reliability).

| Surface | Entry | Subject it opens |
|---|---|---|
| Drill summary, wrong row | Chip under the correction line: "Ask AI" (no history) or "Chat · n" (n = delivered turns). Chip long-press → thread sheet | Routed (below) |
| Drill summary, almost row | Same chip, same behaviour | Routed |
| Review summary, again/almost row | Same chip | The reviewed word |
| Word card, revealed | "Ask AI · n" row under the review buttons; same action in the long-press popup | The word |
| Word card hidden, or practice prompt pre-reveal | Nothing — chat could leak the answer | — |
| Right / knew rows | Nothing | — |

One long-press grammar everywhere: chip long-press → thread sheet. No
row-level long-press.

### Chat screen

```
 header (pinned)      subject line · ✕
 ┌ grounding card ┐   what the AI sees; collapsible
 └────────────────┘
 messages (scroll)    assistant panels · user tiles right
 ─────────────────
 usage line           "· 1.8k tokens · 4 turns" (muted, tap for detail)
 composer (pinned)    [Ask a follow-up…        ] [Send]
```

- **Grounding card**: the miss (prompt, wrong pick, correct answer, rule chips)
  and/or the word (word, meaning, saved context) — shown to the learner so they
  always see what the AI sees. Expanded on a fresh thread or a resume carrying a
  new miss; collapsed otherwise.
- **New conversation** starts a fresh thread for the same subject; the old one
  stays in the sheet.
- **Cancel** on the in-flight "Thinking…" placeholder removes the tile and
  restores the draft. If the cancelled turn was the thread's first, the thread is
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
  - **rule** — the help-card rule id (#651), e.g. "two-way prepositions". A miss
    on *neben* today and on *auf* next week join one conversation.
  - **topic** — the drill type, for items without help-card content (null
    `ruleId`), so every wrong row still gets a chip.
- **Routing** (deterministic, in order):
  1. Word-linked by construction: case-endings items embed the matched word;
     AI practice items carry `wordId`; review misses are the word itself.
  2. Noun types (articles, plurals, n-declension, adjective-endings,
     case-endings) whose citation-form noun exactly matches a saved word —
     exact match only, noun taken from item content, never the inflected prompt
     token.
  3. Otherwise: the rule thread, or the topic thread when there is no rule.
- **When a fresh thread could be word or rule** (both candidates plausible): the
  grounding card shows both blocks and the AI's first reply names the subject —
  first non-empty line `SUBJECT: word` or `SUBJECT: rule`, trimmed, emphasis
  markers stripped, case-insensitive, then removed before render. Chat is prose,
  so every chat request passes `jsonMode = false` explicitly. Malformed or
  missing → rule fallback. Once both threads exist, the deterministic router
  decides; the proposal never runs again.
- **Thread existence starts at first send.** "Ask AI" on a first-ever miss opens
  an empty, unpersisted chat; a thread is created only when something is sent.
- **Multiple threads per subject**: the latest opens by default; the sheet lists
  all (first-question snippet, date, turn count). No global thread list — history
  lives where the subject lives.
- **Context budget: 6k input tokens**, a named conservative constant (BYOK models
  vary and the provider carries no context-size metadata). All of a thread's
  groundings are re-injected as a leading block on **every** request, so
  truncation only ever drops prose: when a send would exceed the budget, the
  oldest prose turns leave the request and a muted "earlier turns omitted"
  marker appears in the visible history. The full transcript is always stored.

### Reliability

| State | Behaviour |
|---|---|
| waiting | User tile shown, "Thinking…" placeholder, Send disabled (one request at a time), draft kept, Cancel available |
| success | Reply replaces the placeholder; usage line updates; turn persisted |
| failed | Inline rose line per error kind below; the turn is persisted as failed **before the request fires**, stays visible as "Not sent · Retry", and is excluded from AI context and chip counts until a retry succeeds |
| cancelled | Tile removed, draft restored; first-turn cancel deletes the thread |

| Error | Message shape | Recovery |
|---|---|---|
| RATE_LIMIT / NETWORK / SERVER / BAD_RESPONSE | "The AI is busy / unreachable / had a problem / sent something unreadable. Try again." | Retry |
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
- Accessibility: 48dp targets; chips and sheet rows announce their content
  ("Chat, 4 turns"); Thinking/error lines are `liveRegion="polite"`.

### Data

`AiChatStore` (`filesDir/ai_chats.json`), same codec and atomic-write pattern as
`WordsStore`:

```
threads[{
  subject {kind: word|rule|topic, ref}
  createdAt, updatedAt
  groundings [{id, kind, payload}]   // re-injected on every request
  messages  [{role, text, ts, usage, failed?, groundingId?}]
}]
```

- `payload` is typed per kind — `word`: text/meaning/context; `miss`:
  prompt/picked/answer/ruleId/chips — with a round-trip decode test, because an
  unshapeable payload in a hand-rolled codec silently decodes to a blank card.
- `groundingId` sits on the user turn that introduced the grounding.

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
- Is the 6k budget comfortable on real threads? It is deliberately conservative.

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
 user tile (kept) + "The AI is busy." (Retry)
 turn persisted as failed — excluded from context & counts,
 survives process death
     │ Retry
     ▼
 Thinking… ─► reply filed; turn enters history; count includes it
```

**Both candidates on a fresh thread**

```
 ✗ Ich fahre über ___ Brücke.  (dem → die)   "die Brücke" is saved
 └─ ( Ask AI ) ─► empty thread, header "New conversation"
     grounding: [word block] [rule block] + ✗ miss line
         │ Send
         ▼
 first reply, first non-empty line:
   SUBJECT: word  → word thread   (header: die Brücke)
   SUBJECT: rule  → rule thread   (header: Two-way prepositions)
   missing/broken → rule fallback (reply still renders normally)
```
