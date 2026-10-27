# AI practice from saved words

Part of epic #561 (AI-generated practice from saved words). This is the agreed
design. It turns the words the user saves into grounded practice material, using
a cheap, batched AI pipeline. Local-first, opt-in, bring your own key (BYOK).

The principle behind every section: **the user is the filter; the AI is a
generator and never a tutor.** The user marks the words they don't know and saves
the sentence they met them in; the AI only turns that material into drill items.
It never infers the user's level, never scores free-form answers and never
explains — the Why? scoring in the drills (#532) stays on-device.

This doc is the reference for tasks 9.3–9.11. Sub-task 9.2 (#595) confirms the
Melious API shape; until then the provider section below is **unverified** and
the adapter (#598, 9.5) must not be built on assumptions.

## Pipeline

```
saved word + context          Word (text, context) — the user's saved sentence
        |
   generation queue           words without items, FIFO oldest-first + jitter,
        |                     daily cap; also synchronous on-demand when thin
        v
   AiProvider (BYOK)          Melious first, OpenAI-compatible adapter
        |
   PracticeContract.parse     validate JSON, completeness check,
        |                     cloze fallback for bad/unknown items
        v
   practice_items.json        validated items, frozen at creation
        |
   PracticeRotation           due word serves a rotating item from its pool
        |
   digest                     weekly or threshold-triggered weak-spot batch
```

Stages:

1. **Saved word and context.** The only input is `Word` + `context` — the saved
   sentence grounds every generated example. Phrases (multi-word saves) are
   legitimate entries. The capture front door (#523) is decoupled; this is the
   only contract between them.
2. **Generation queue.** Words without items, FIFO oldest-first with jitter,
   capped per day (see Budgets). The queue is derived from words and items and
   has no file (#601, 9.8).
3. **Provider.** One small `AiProvider` interface, an OpenAI-compatible adapter,
   and provider, base URL and model as config (#597, 9.4).
4. **Validated item store.** `PracticeContract.parse` validates every item
   (#596, 9.3); only valid items reach `practice_items.json`. Content is frozen
   at creation.
5. **Rotation.** Scheduling stays on the word (existing Leitner, #452); due
   words are served a rotating item from their pool (#603, 9.10).
6. **Digest.** One batched call reads the review event log and returns
   weak-spot exercises (#604, 9.11).

This stays **in the launcher** (resolves the "revisit after 5.1" note in #400):
no companion app, no sync backend, no server of our own.

## The AI contract

**Request** (#596, 9.3 — `PracticeContract.buildPrompt`): each requested word
with its saved `context`, plus the app's own drill-section list — the `DrillType`
ids from `app/src/main/java/app/olauncher/data/store/Drill.kt`
(`prepositions`, `pronouns`, `articles`, `plurals`, `case_endings`,
`verb_prepositions`, `separable_verbs`, `adjective_endings`, `word_order`,
`conjugation`, `cloze`, `passive`, `konjunktiv_ii`, `relative_pronouns`,
`genitive`, `n_declension`, `imperative`, `werden`, `praeteritum`,
`infinitiv_zu`, `modal_verbs`, `konjunktiv_i`) — and the JSON schema below.

**Per word, 3 items** (owner decision, 2026-10-27): one `CLOZE` plus two other
types from the allowed ids. Completeness is checked independently of the count:
**every requested word id has at least 3 valid items**. A word is never dropped.

**Response** — one JSON object:

```json
{
  "items": [
    {
      "word_id": "insistieren",
      "type": "cloze",
      "prompt": "Die Arbeit ___ auf seiner Unterschrift.",
      "answer": "insistiert",
      "choices": ["insistiert", "insistieren", "insistiertest"],
      "hint": "Präsens, 3. Person Singular"
    }
  ]
}
```

Fields: `word_id`, `type`, `prompt`, `answer`, `choices` (choice items),
optional `hint`. `hint` must not reveal the answer (the drill hint rule, #643).

**Validation and fallback:** an unknown `type` or an item that fails
`DrillItem.validate()` becomes a cloze built from the saved `context` with the
word blanked (#576, 12.1). Truncated or malformed JSON is rejected before any
item is produced. The count of responses is never treated as proof of
completeness; the check is per word id.

## Budgets

**Two-layer cost model** (owner decision, 2026-10-27):

- **Prepaid Melious credits are the financial ceiling.** The user's wallet,
  account-level billing: generation stops when credits run out, no matter what
  the app does. A `402 Payment Required` (or the provider's out-of-credits
  error — exact code to be confirmed in 9.2) maps to a new `BILLING` kind in
  `AiResult.Error`, **distinct from `AUTH`**, so the settings UI can say "top up
  your Melious credits" instead of "key rejected".
- **The in-app daily word cap is the behavioral guardrail.** `AI_DAILY_WORD_CAP`,
  default **50 words/day** (raised from 20 on 2026-10-27), counted from today's
  items. It is never removed, even though cost stopped being the binding
  constraint: it bounds a runaway loop, a misconfigured base URL pointed at an
  expensive endpoint, or a bug that regenerates in circles.

Cost rationale for the relaxation: at 20 words/day on GLM 5.3 Flash pricing
(€0.10/M input tokens, €0.40/M output tokens) a month of daily generation costs
roughly €0.15. Even at the 50-word cap the monthly cost stays in cents, so the
original tight throttles were relaxed — the cap stays purely as a guardrail.

**Hybrid generation trigger** (owner decision, 2026-10-27):

- **Synchronous on demand.** When a practice round starts and the due words'
  pool is thin (fewer than 2 items per word), generate synchronously at
  practice start — a flash-class model is expected to answer in 1–2 s — with a
  **~10 s timeout**. On failure or timeout: fall back to serving the existing
  bare words as today, and enqueue the words for the background queue.
- **Background top-up.** The WorkManager batched queue (unique periodic work
  `ai_practice`, 24 h, network-constrained) restores all pools to **3 items**
  per word, FIFO oldest-first with jitter.

Review serving stays free — it is rotation over the local store, no network.

**Digest cadence** (owner decision, 2026-10-27): the original fortnightly
digest is now **weekly OR threshold-triggered, whichever first**. The threshold
is a weak-spot pattern in the review event log as defined in #604's spec
(words with repeated AGAIN, drill types below threshold on first try).
`AI_LAST_DIGEST_AT` semantics are unchanged: it updates only on success.

## Provider

**BYOK, opt-in** (owner decision, 2026-10-26). Off by default; off works fine.
The settings line says so plainly: the feature sends saved words and their
sentences to the provider with the user's own key.

- **Provider for now: Melious** (https://melious.ai/), the first provider.
- **Provider-agnostic code:** a small `AiProvider` interface, an
  OpenAI-compatible adapter, and provider, base URL and model as config
  (`AI_PROVIDER`, `AI_BASE_URL`, `AI_MODEL`), so Anthropic (for example Haiku)
  can be added later with one adapter class and one map entry.
- **Melious base URL:** `https://api.melious.ai/v1` (provisional — see below).

**The Melious API shape is unverified until #595 (9.2) records it from the
public docs.** Nothing below this line is confirmed: the chat endpoint path,
the exact request/response JSON, JSON-mode support, model ids, per-token
prices, rate limits, error codes (including the exact out-of-credits status
and code for the `BILLING` mapping), and the data retention and training
policy. #595 (9.2) fills in the "Provider: Melious" details; the adapter
(#598, 9.5) is built only after that.

## Key storage

Owner decision, 2026-10-26 — platform APIs only, no new dependency (no
security-crypto):

- An **AES-256-GCM key in `AndroidKeyStore`** encrypts the API key.
- Ciphertext and IV go to `noBackupFilesDir/ai_key.bin` (#599, 9.6).
- The key file is excluded from **every** section of `backup_rules.xml` and
  `data_extraction_rules.xml` (`full-backup-content`, `cloud-backup`,
  `device-transfer`); `BackupCoverageTest` asserts it is never exported.
- The key is **never** in SharedPreferences, never in code, never in logs or
  error messages, never in a backup. There is no key-related pref key.

## Backup decisions

- `practice_items.json` is user data that costs money to regenerate: exported
  and in **every** auto-backup section (#596, 9.3). `BackupCoverageTest` goes
  from six to seven files.
- The generation queue has no file (#601, 9.8), so nothing to back up.
- The key file `no_backup/ai_key.bin` is excluded everywhere (#599, 9.6).
- Pre-existing: `intention_history.json` and `review_events.json` are in
  `Backup.EXPORTED_FILES` but missing from both backup XMLs. Fixed separately
  in #593, which lands before 9.3 and 9.6.

## Benchmark (9.7)

Model choice is deferred to a benchmark (#600, 9.7) on a real German news
article: 20 saved words with context sentences, 3 runs per model.

**Candidates** (owner decision, 2026-10-27, all on Melious, all EU-hosted with
a no-training policy): **GLM 5.3 Flash**, **DeepSeek V4 Flash**, **Gemma 4
31B**. Haiku is no longer the primary candidate.

**Rubric:** grounding, German correctness (owner or native-level reviewer),
contract validity (JSON and a known type), cost per item, latency and failure
rate. Pass thresholds:

| Threshold | Value |
|---|---|
| Contract validity | ≥ 95 % |
| Mean German correctness | ≥ 1.8 of 2 |
| Grounding | ≥ 90 % |

**Pick the cheapest model that passes**; price to performance is a stated
consideration. The result is recorded in `docs/design/ai-model-decision.md`
and sets the `AI_MODEL` default in the same PR.

## Out of scope

- The capture front door (#523) — decoupled; the only contract is `Word` +
  `context`.
- OCR.
- A sync backend or companion app.
- New third-party runtime dependencies.
- **Any tutor or free-form scoring feature** — Why?-explanations, AI grading of
  typed answers, conversational practice. The AI is a generator, never a tutor.
