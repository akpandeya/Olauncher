# Ask AI about a miss: saved chat threads (design pass for #699)

Design stage only: no code. The mock is `ask-ai-chat-mock.html` next to this file.
This document merges the original design pass with the exhaustive scenario/state
pass (2026-10-29, GLM 5.3 agent + owner decisions): every surface state, the full
thread-state matrix, the in-chat error state machine, and five flows.

The feature: from a missed drill item or from a saved word, open a conversation with
the AI that starts grounded in that miss or word, supports follow-ups, and is
persisted so the learner can revisit it later — and keep chatting as long as they
want (#699).

## Summary

- One chat screen, full screen over the scrim (the drill-hub frame), opened from two
  surfaces: miss rows in the drill/review summaries, and the revealed word card.
- Threads belong to a **word** or a **grammar rule** — never a single drill item. A
  thread is created on first *send*, never on entry.
- When a miss could belong to both a word and a rule, the AI proposes the subject on
  the first reply of a fresh thread (with a deterministic fallback); on resume, the
  deterministic router decides.
- Chat is a post-answer activity only. While a question is on screen the entry does
  not exist; the static help card (#651) stays the pre-answer teacher.

## 1. Visibility rule and surfaces

**The one visibility rule (rev 2, review A1):** an entry point is visible iff
`AiPracticeGate.canRun(aiPracticeEnabled, hasKey, model)` returns true — the same
gate in front of every AI practice network call (`AiPracticeGate`'s contract: "the
one guard in front of every AI practice network call … with the switch off no call
is made, ever"). `DISABLED`, `NO_KEY` and `NO_MODEL` are **statically knowable**
(`SettingsGerman` already computes `hasKey` for the settings subtitle), so they are
visibility states, not runtime errors: hidden, never disabled or greyed. Only
invalid config (`validate() != null`) joins them in hidden. What remains
runtime-only: revoked key, dead network, exhausted credits — inline errors per §5.
Without this gate, chat would be a second AI path that bypasses the guard the code
declares universal.

| # | Surface | State | Entry? | Form | Subject |
|---|---------|-------|--------|------|---------|
| A1 | Drill summary row | wrong (✗) | Yes | Quiet chip under the correction line: "Ask AI" (no delivered turns) / "Chat · n" (n = delivered **turns** of the subject's latest thread). Long-press on the chip → thread sheet. | Router §4 |
| A2 | Drill summary row | almost (amber) | Yes | Same-shaped chip as A1 (rev 2, review D: a long-press-only entry is a feature nobody discovers; the spelling-slip rationale does not justify an invisible path). | Router §4 |
| A3 | Drill summary row | almost → second miss | Yes | Close-spelling rule counts it wrong; same as A1. | Router §4 |
| A4 | Drill summary row | right (✓) | No | Nothing. | — |
| A5 | Review summary row | again (✗) | Yes | Same chip as A1. Review misses are **word-shaped**: subject = the reviewed word, grounding = the word's saved context. No matching, no proposal. | Word |
| A6 | Review summary row | almost | Yes | Same chip as A2. | Word |
| A7 | Review summary row | knew (✓) | No | Nothing. | — |
| A8 | Word card (`WordReveal`) | revealed | Yes | Quiet row under the review buttons, hairline divider: "Ask AI" / "Ask AI · n". Long-press → thread sheet. | Word |
| A9 | Word card | hidden (meaning not shown) | No | Nothing — post-answer only; chat would leak the meaning. | — |
| A10 | Word card | practice prompt (#603) pre-reveal | No | Same as A9 — the practice answer is not yet seen. | — |
| A11 | Word card | practice prompt, revealed | Yes | Same as A8; grounding additionally carries the practice prompt and its answer. Subject = the item's `wordId` (single candidate, no proposal). | Word |
| A12 | Today word card, two-button variant | revealed | Yes | Same row as A8; independent of button count. Chat never grades. | Word |
| A13 | Word long-press popup | revealed card | Yes | Third item "Ask AI" after Edit/Delete; same subject and thread as A8. | Word |
| A14 | Word long-press popup | hidden card | No | Edit/Delete only — A9's rule suppresses the item (the popup *is* reachable pre-reveal today; this is a deliberate suppression). | — |

One long-press grammar everywhere (rev 2, review D): **chip long-press → thread
sheet**, on wrong and almost rows alike; there is no row-level long-press.

## 2. The chat screen

```
 header (pinned)      subject line · ✕
 ┌ grounding card ┐   collapsible, starts expanded on a fresh thread or new miss
 │ chips          │
 │ ✗ miss line / word context (both blocks when both candidates apply)
 └────────────────┘
 messages (scroll)    assistant panels · user tiles right
 ─────────────────
 usage line           "· 1.8k tokens · 4 turns" (muted, tap for detail)
 composer (pinned)    [Ask a follow-up…        ] [Send]
```

- **Grounding card.** The first request's context, shown to the learner so they
  always see what the AI sees. For a drill miss: prompt with the learner's answer,
  correct answer, rule chips. For a word: word, meaning, saved context. When both a
  word and a rule are candidates, both blocks show. Collapsible to one line; starts
  expanded when the thread is new **or the resume carries a new miss**, collapsed
  otherwise.
- **Messages.** Assistant messages are full-width `today_panel` blocks; user messages
  are right-aligned tiles. German in chat prose carries no gender colour — colour is
  a help-card rule (#651).
- **New conversation.** A quiet "New conversation" action (header area and thread
  sheet) starts a fresh thread for the same subject; the previous one stays in the
  sheet.
- **Usage line.** Muted cumulative tokens/turns under the newest assistant message;
  tap shows the per-turn breakdown. No cap, no nag (the #699 "unbounded by design"
  stance).
- Runtime states (waiting, errors, retry) are specified in §5.

## 3. When chat is available

- Only **after the answer**: summary screens and the revealed word card. Never during
  a question — no answer leak, no impact on the round's interaction budget; the
  pre-answer "Why?" keeps pointing at the static rule card (#651).
- Opening chat from a summary is a side path: back returns the summary in its exact
  state. Chat never scores, advances or mutates a round.
- From the word card, chat sits beside grading; it does not grade or schedule.

## 4. Thread model and routing

- **Subjects are words, rules and topics — not items.** The help-card content model
  (#651 §3) assigns every drill item a rule id (`two-way-auf`, `noun-e-feminine`);
  word refs are the word's **citation text**, normalized (trim + lowercase + NFC) in
  one place, original kept for display. A miss on *neben* today and on *auf* next
  week share one "two-way prepositions" thread.
- **Live topics (rev 2, review B1).** `DrillItem.ruleId` is nullable — items without
  help-card content (the live topics) have no rule to thread to, and even the "Why?"
  button is hidden for them today. They get a third subject kind **`topic`** (ref =
  the `DrillType` id, title = the type's label), so the "every wrong row gets a
  chip" decision survives: a conjugation miss opens the "Conjugation" topic thread.
- **Deterministic router, in order:**
  1. Word-linked by construction: case-endings items embed the matched word in their
     key (`DrillCaseEndings.items()`); AI practice items carry `wordId`; review
     misses are the word itself.
  2. Noun types (articles, plurals, n-declension, adjective-endings, case-endings)
     whose citation-form noun **exactly matches** a saved word (case-insensitive,
     noun from item content data — never the inflected prompt token).
  3. Items with a `ruleId` → the rule's thread; items without → the drill type's
     `topic` thread.
- **AI proposes the subject only when a *new* thread has both candidates** (§6). A
  proposal's only job is to name a thread that doesn't exist yet; when both threads
  already exist, the deterministic router picks (Decided).
- **Context budget (rev 2, reviews B3 + E2).** "Unbounded" applies to what the user
  may *say*, not to what is resent: each thread has a per-thread token budget. When
  a send would exceed it, the oldest delivered turns are dropped from the request
  and a muted "earlier turns omitted" marker is inserted at the top of the visible
  history. The full transcript stays stored; only the request window shrinks. This
  is also the eviction policy (§9): every thread is kept, nothing is deleted.
- **Retry ordering (rev 2, review B2).** Only the **newest** failed turn is
  retryable. Once a newer turn is delivered, an older failed turn converts to
  "Ask again" — a new turn quoting the original question — never a retry, so
  history never orders `Q2, A2, Q1, A1`.
- **Thread-state matrix for a miss** (delivered turns = exchanges that entered
  history; failed turns excluded — one delivered turn = user message + assistant
  reply, the unit for chip counts, the usage line and TalkBack alike; rev 2, review
  C2):

| State | Chip | Tap opens | Grounding | On send |
|---|---|---|---|---|
| First-ever encounter, one candidate | "Ask AI" | Empty chat, grounding expanded, composer focused | The one candidate block | Thread **created on first send**; nothing persisted before |
| First-ever, both candidates | "Ask AI" | Empty chat, header provisional ("New conversation"), both blocks shown | Both blocks + miss line | Created on send with provisional subject; first reply's proposal files it (§6) |
| Rule thread exists (same rule, different item) | "Chat · n" | Rule subject's latest thread, scrolled to bottom | Re-expanded: new miss line on top, previous turns below | Appended; request context = history + new miss grounding |
| Word thread exists (noun saved) | "Chat · n" | Word's latest thread | Re-expanded: word block + miss line, rule rides along | Appended to word thread |
| Both threads exist | "Chat · n" | The thread the deterministic router picks (word-by-construction > word-exact > rule); no re-proposal | Both blocks, resumed thread's block first | Appended to the opened thread; the other subject's thread is reachable from its own surface |
| Latest thread stale (weeks old) | "Chat · n" | Latest thread, unchanged | Re-expanded with the new miss line | Appended; `updatedAt` moves it to the sheet front; sheet dates show staleness; no nag |
| Thread with pending failed turn | "Chat · n" (failed not counted) | Latest thread; failed turn visible at tail: muted tile + "Not sent · Retry" | Re-expanded with the new miss line | **Retry** (newest failed turn only) re-sends with current full context; a new message may also be sent normally — once a newer turn is delivered, the older failed turn converts to "Ask again" (§4) |

- Repeated misses of the same item land in the same subject thread; the chip count
  grows and never regresses to "Ask AI" once delivered messages exist.

## 5. In-chat state machine and errors

| State | What the user sees | Composer | Exit |
|---|---|---|---|
| idle | Messages, grounding card, usage line | Empty, enabled | → composing |
| composing | Draft in field (survives rotation — request scope is `viewModelScope`, never an Activity; rev 2, review D) | Enabled; Send enabled when non-blank | → waiting on Send |
| waiting | User tile appended; "Thinking…" placeholder; usage unchanged. A quiet **Cancel** control on the placeholder (rev 2, review E1: most of streaming's value, none of its interface work — the user-triggered twin of cancel-on-destroy) | **Send disabled** (single outbox — a second request would race subject filing on fresh threads); draft kept | → success, error, or cancelled (placeholder removed, draft restored, nothing persisted) |
| success | Placeholder replaced by reply; usage updates; thread persisted | Cleared, enabled | → idle |

Errors render as an inline `accentRose` line under the user tile (replacing the
placeholder); the turn persists with a `failed` flag **before the request fires**;
failed turns are excluded from model context and chip counts until a Retry succeeds,
but stay visible in the transcript marked "Not sent · Retry" (Decided: keep + retry —
nothing is lost, and the model never sees an unanswered question).

| `ErrorKind` | Inline message shape | Recovery |
|---|---|---|
| RATE_LIMIT | "The AI is busy. Try again in a moment." | Retry |
| NETWORK | "Couldn't reach the AI. Check your connection." | Retry |
| SERVER | "The AI service had a problem. Try again." | Retry |
| BAD_RESPONSE | "The AI sent something unreadable. Try again." | Retry (chat is prose, `jsonMode=false`; malformed *proposals* are a fallback case, never this error) |
| AUTH | "Your AI key was rejected. Check your AI settings." | **"Open AI settings"** (primary) + Retry |
| BILLING | "The AI account is out of credits. Top up to continue." | Retry (after top-up); no settings detour — config is fine, the account is empty |

- **First-message error:** the thread persists on first send with the failed turn, so
  process death loses nothing. The chip still reads "Ask AI" (no delivered turns);
  the next entry resumes this thread with the "Not sent · Retry" tail (no emptiness
  special-casing). Watch item (rev 2, review C1 — narrowed): if device testing finds
  this confusing, the fix is *not* excluding failed-only threads from latest-thread
  resolution — that would strand the typed question unretryable behind a new empty
  chat. The correct fix is carrying the orphaned failed turn into the new draft.
- **Storage full (rev 2, review B4):** a write failure shows an inline muted line
  "Couldn't save this conversation" — a visible message must never be silently lost,
  and a failed first write must not silently produce a fresh empty chat on the next
  entry.
- **Process death while waiting:** the turn is already persisted as failed; waiting
  is memory-only, so no phantom "Thinking…" survives a restart.
- **Corrupt `ai_chats.json` (rev 2, review D):** decode failure renames the file to
  `ai_chats.json.bak` and starts an empty store — the codec pattern's fresh-start
  behaviour, kept, but this store is rewritten on every message and grows without
  bound, so the archive is preserved at the cost of one rename.

## 6. Subject-proposal flow (word | rule)

Requested once, on the first send of a fresh both-candidate thread. Later turns
never carry or act on one (parsing scoped to the thread's first assistant message).

**Wire format (rev 2, review A2).** Chat is prose — every chat request passes
`jsonMode = false` explicitly (`AiRequest.jsonMode` defaults to `true` for the
practice contract, so the default is wrong for chat). With JSON off, a JSON object
would arrive fenced or phrased unpredictably, so the proposal is a plain sentinel:
the reply's **first line** is exactly `SUBJECT: word` or `SUBJECT: rule`, parsed on
a literal prefix, case-sensitive. The sentinel line is **stripped before render** —
the learner never sees scaffolding. No proposal on the line → treated as missing →
rule fallback.

| Case | Filed to | Header | Grounding |
|---|---|---|---|
| Proposal `word` | Word thread (created on this send) | The word (citation form) | Collapses to the word block |
| Proposal `rule` | Rule thread | The rule's title (help-card source) | Collapses to the rule block + miss line |
| Malformed (bad shape, unknown id) | **Rule thread** (deterministic fallback) | Rule title | Rule block; reply text renders normally, no error shown |
| Missing | Rule thread (fallback) | Rule title | Rule block |
| Both threads already exist | Deterministic router — no proposal (Decided) | — | Both blocks |
| Crash before filing | Provisional-subject thread + failed turn; the retry's reply re-carries the proposal (still a "first reply") | Provisional "New conversation" | Both blocks |

## 7. Lifecycle edge cases

| Edge case | Behavior |
|---|---|
| Thread sheet, empty | "No conversations yet" (defensive; a visible chip implies ≥1) |
| Thread sheet, 1 / many | One / newest-first rows (snippet, date, count) + "New conversation" action in the sheet |
| "New conversation", back before sending | Never persisted — a thread exists only from its first send; next entry resumes the previous latest |
| Word deleted with threads | Threads kept as an inert archive (chat never writes `WordsStore`, word deletion never writes the chat store). **Ref = normalized citation text**, so re-saving the same noun re-links the threads automatically. Known trade-off (rev 2, review D, written down): two saved words sharing a citation form (`Bank`, `See`, `Band`) share one thread, and delete-then-save of a *different* word with the same form resurrects an unrelated archive — accepted for v1 |
| Storage full | Write fails, session stays usable; last successful write wins (same as every prefs-backed store) |
| Rotation while composing / waiting | Draft survives (ViewModel); waiting cancels on destroy — the persisted failed turn shows "Not sent · Retry" on return |
| TalkBack | Chip descriptions: "Ask AI about this mistake" / "Chat, 4 turns"; sheet rows read as one unit; assistant/user messages announce "AI replied:" / "You said:"; Thinking, Cancel and error lines are `liveRegion="polite"`; 48dp targets from `today_touch_min_height` |
| No-network device | Entries stay visible (config exists); no pre-send offline check — sending fails NETWORK with Retry |

## 8. Flows

**(1) First-ever miss → chat**

```
 drill summary (misses on top)
 ┌──────────────────────────────────────┐
 │ ✗  Ich lege das Buch auf ___ Tisch.  │
 │    dem  →  den · rule line           │
 │                    ( Ask AI )  ◄──── tap
 └──────────────────────────────────────┘
              │
 router: item → rule  (no word candidate, no threads)
              │
 ┌ chat screen ─────────────────────────┐
 │ header: Two-way prepositions     ✕   │
 │ ┌ grounding ─────────────────────┐   │
 │ │ chips · ✗ miss line   (open)   │   │
 │ └────────────────────────────────┘   │
 │ (no messages yet)                    │
 │ [ Ask a follow-up…        ] [Send]   │
 └──────────────────────────────────────┘
              │ Send
 thread created on send ──► Thinking… ──► reply filed
              │                           (chip now "Chat · 1")
              ▼
        back → summary, state intact
```

**(2) Miss with an existing rule thread (same rule, different item)**

```
 drill summary
 ┌──────────────────────────────────────┐
 │ ✗  Ich stelle die Lampe in ___ Ecke. │
 │    der → die · rule line             │
 │              ( Chat · 4 )  ◄───────── tap ── (long-press: thread sheet)
 └──────────────────────────────────────┘           │
              │                                     ▼
 resume rule subject's LATEST thread     ┌ sheet: subject threads ┐
              │                          │ · "Why auf takes…" 4   │
              ▼                          │ · older…               │
 ┌ chat screen ─────────────────────────┐└────────────────────────┘
 │ grounding RE-EXPANDED:               │
 │   ✗ new miss line (this item) on top │
 │ previous turns below, scrolled to end│
 └──────────────────────────────────────┘
              │ Send
 appended to same thread ──► count grows ("Chat · 5" — one new turn)
```

**(3) No provider configured**

```
 any surface (summary row / word card / popup)
              │
 check: AiProviders prefs ──► empty ──────► entry NOT RENDERED
              │                             (chip, row, popup item
 valid?  ──► validate() fails ──────────►     all absent, nothing
              │                               disabled or greyed)
       (only when config valid:)
 entry visible ──► send ──► key missing/revoked
                              │
                     inline AUTH error:
                     "Your AI key was rejected.
                      Check your AI settings."
                     [ Open AI settings ] [ Retry ]
```

**(4) Send fails RATE_LIMIT, then succeeds**

```
 composer: "Warum den und nicht dem?"  [Send]
              │
 user tile kept ──► Thinking…
              │
 AiResult.Error(RATE_LIMIT)
 ┌──────────────────────────────────────┐
 │ ✗ user tile (kept)                   │
 │   "The AI is busy. Try again in a    │
 │    moment."            ( Retry )     │
 └──────────────────────────────────────┘
   · turn persisted as failed, not counted
   · thread survives app kill here
              │ Retry
 Thinking… ──► Ok ──► reply filed, usage line updates
              │        failed flag cleared — turn enters history
              ▼
        chip count now includes the delivered turn
        (Cancel is available on any in-flight Thinking…)
```

**(5) AI proposal flow, both candidates**

```
 miss: noun-type item, citation noun = saved word  (word + rule both apply)
 drill summary
 └── ( Ask AI ) ──► fresh thread, subject UNRESOLVED
      ┌ grounding ──────────────────────┐
      │ [word block]  [rule block]      │   both shown, expanded
      │ ✗ miss line                     │
      └─────────────────────────────────┘
      header: "New conversation" (muted, provisional)
              │ Send
      Thinking… ──► first reply + proposal {kind}
              │
   ┌──────────┴─────────────┬──────────────────┐
   ▼                        ▼                  ▼
 proposal "word"       proposal "rule"    malformed / missing
   │                        │                  │
   ▼                        ▼                  ▼
 filed to WORD          filed to RULE      filed to RULE
 thread                 thread             (deterministic
 header: die Brücke     header: Two-way    fallback, decided)
 grounding collapses    prepositions       reply text still
 to word block          (reply shown       renders normally;
                        either way)        no error surfaced
```

## 9. Store and config

- `AiChatStore` (`filesDir/ai_chats.json`, codec pattern of `WordsStore`, atomic
  temp-file rename): `threads[{ subject{kind: word|rule|topic, ref}, createdAt,
  updatedAt, groundings[{id, kind, payload}], messages[{role, text, ts, usage,
  failed?, groundingId?}] }]`.
  - **Grounding is persisted (rev 2, review A3).** One `grounding` record per miss
    (or word block), referenced by the turn that introduced it. Without it, a
    resumed thread's "history + new miss grounding" request loses miss #2 from the
    *following* turn's context, and the grounding card could not re-render from
    stored data when a thread is reopened from the sheet days later.
  - **Topic is a third subject kind** (§4, review B1): live-topic items without a
    `ruleId` thread per drill type.
- Generation rides the existing `AiProvider` config (`AI_PROVIDER`, `AI_BASE_URL`,
  `AI_MODEL` prefs). No hardcoded model anywhere.
- **Eviction (rev 2, review E2): decided — keep every thread, cap the request, not
  the store.** Thread count is naturally one per word/rule the learner has asked
  about, so an LRU cap loses exactly the archive §7 wants to keep. The per-thread
  token budget with the "earlier turns omitted" marker (§4) does the real work and
  doubles as the context-window policy.

## 10. Repo design elements used

Same skin as the drill screens (`docs/design/drills-ux.md` has the full token list):
`todayScrim`/`readableCard` background, `today_panel` surfaces, `TodayChip.Small`
for resume chips and rule chips, `TodayCardBody` text, `accentRose` only for the
miss line, error lines and failed-turn marks, `radiusMedium` panels,
`today_touch_min_height` targets. The thread sheet reuses the hub-row pattern.
New: a message layout pair (assistant panel, user tile) and a composer row — built
from `quiz_choice` tile and `TodayButton` primitives, no new colours.

## Decided

Original pass (owner, 2026-10-29): post-answer only; word-or-rule subjects, no
per-item threads; AI proposes the subject on fresh both-candidate threads, accepted
with a rule-thread fallback; multiple threads per subject, latest by default, older
behind a long-press sheet; chip on wrong rows, long-press on almost rows; exact
citation-form matching, stem matching deferred; relative pronouns → rule thread in
v1; no provider configured → hidden, not disabled; chat never writes `WordsStore`.

Scenario pass (owner, 2026-10-29):
- **One visibility rule everywhere**: config present + `validate()` passes; key and
  network problems are runtime errors, not visibility states.
- **Both-candidate resume**: deterministic router (word-by-construction > word-exact
  > rule); the AI proposal is scoped to thread creation only.
- **Failed turns: keep + Retry** — visible as "Not sent · Retry", excluded from
  model context and chip counts until a retry succeeds; persisted before the request
  fires.
- **Word thread ref = citation text**, so delete + re-save re-links; id refs would
  orphan threads permanently.
- **Pre-reveal suppression**: word-card row and popup item hidden until the word is
  revealed (practice prompts included).
- **Send disabled while waiting** (single outbox; draft kept); request cancels on
  destroy; thread created on first send only.
- **Review summary misses route to the word thread** by construction; no matching,
  no proposal.

Review round (rev 2, owner review on #699, applied 2026-10-29 — supersedes where
noted):
- **Visibility is `AiPracticeGate.canRun`**, not raw prefs + `validate()` — chat
  respects the AI-practice switch; `DISABLED`/`NO_KEY`/`NO_MODEL` are hidden states
  (supersedes the scenario-pass visibility rule).
- **Chip on wrong *and* almost rows**, same shape, sheet on chip long-press only
  (supersedes "wrong rows only / long-press on almost rows" — an invisible path is
  a feature nobody discovers).
- **Subject proposal wire format**: first line `SUBJECT: word|rule`, literal-prefix
  parse, stripped before render; every chat request passes `jsonMode = false`
  explicitly.
- **Grounding is persisted** per miss and referenced by its turn; request context
  reconstruction and card re-render depend on it.
- **Live topics get a third subject kind `topic`** (per `DrillType`) when `ruleId`
  is null — every wrong row still gets a chip.
- **Retry ordering**: only the newest failed turn is retryable; older failed turns
  convert to "Ask again".
- **Per-thread token budget**: oldest turns drop out of the *request* with a muted
  "earlier turns omitted" marker; the transcript stays complete. This is also the
  eviction policy: **every thread is kept** (supersedes "LRU cap or keep everything"
  as an open question — decided: keep everything).
- **Streaming: decided no for v1**; a Cancel control on the in-flight turn instead
  (supersedes the streaming open question).
- **Storage-full shows an inline "Couldn't save this conversation" line.**
- **Corrupt store → `ai_chats.json.bak`, then fresh** (archive preserved).
- **Request scope is `viewModelScope`** — rotation must not manufacture a fake
  "Not sent".
- **Turns everywhere**: chip counts, usage line and TalkBack all count turns
  (user + assistant), not messages.
- **Ref normalization in one place** (trim + lowercase + NFC); duplicate citation
  forms (`Bank`, `See`, `Band`) sharing a thread is a written-down v1 trade-off.

## Open questions

None left for the design stage. Watch items only:

1. Does the AI's `SUBJECT:` proposal misroute in practice? If yes, the fallback
   flips to the deterministic router.
2. Does resuming a failed-only thread ("Not sent" tail on arrival) feel like a bug
   in device testing? If yes, the fix is carrying the orphaned failed turn into the
   new draft — *not* excluding failed-only threads from latest-thread resolution,
   which would strand the typed question unretryable (review C1).

## HTML mock

`ask-ai-chat-mock.html` (next to this file) shows three states: the drill summary
with resume chips, the chat over a drill miss, and the revealed word card with its
Ask AI row. Self-contained, no remote fonts or scripts, repo tokens only, light/dark
toggle. Screen work starts only after the mock is approved.
