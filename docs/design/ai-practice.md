# AI practice from saved words

Part of epic #561 (AI-generated practice from saved words). This is the agreed
design. It turns the words the user saves into grounded practice material, using
a cheap, batched AI pipeline. Local-first, opt-in, bring your own key (BYOK).

The principle behind every section: **the user is the filter; the AI is a
generator and never a tutor.** The user marks the words they don't know and saves
the sentence they met them in; the AI only turns that material into drill items.
It never infers the user's level, never scores free-form answers and never
explains — the Why? scoring in the drills (#532) stays on-device.

This doc is the reference for tasks 9.3–9.11. The Melious API shape was
unverified until #595 (9.2) recorded it from the public docs; the
"Provider: Melious" section below now carries the confirmed facts (read
2026-10-27).

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
  the app does. The provider's out-of-credits response (confirmed in 9.2: on
  chat completions it is **429 with `type: "billing_error"`, not the 402 we
  expected** — see Provider: Melious below) maps to a new `BILLING` kind in
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
  Amendment (2026-10-28, epic review round 1): the 1–2 s assumption was wrong —
  the benchmark (#600) measured glm-5.3-flash at **p50 36.8 s / p95 72 s** for
  20 words. Generation is therefore chunked into small per-request batches
  (~5 words), each with its own token budget. Amendment (2026-10-28, round 2):
  the ~10 s on-demand ceiling is bounded by the per-request read timeout on
  the HTTP connection — connect time (≤15 s) plus the read timeout. Coroutine
  cancellation interrupts the worker thread, but a socket read already blocked
  in the kernel only ends at the read timeout; `disconnect()` runs after the
  call returns. The first card waits for the inline generation at most that
  long; the round then starts once, on whatever the pools hold.
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

### Provider: Melious

Confirmed from Melious's public docs on **2026-10-27** (#595, 9.2). Until then
the shape was unverified; the adapter (#598, 9.5) is built on what follows.

- **Base URL:** `https://api.melious.ai` — the OpenAI SDK's `base_url` is
  `https://api.melious.ai/v1`. The OpenAI-compatible endpoints live under
  `/v1/…`; Anthropic-compatible `/v1/messages` also exists.
  [API reference](https://melious.ai/docs/reference), read 2026-10-27.
- **Auth header:** `Authorization: Bearer <KEY>` (or `x-api-key: <KEY>` — both
  work on every endpoint). Keys are `sk-mel-…`, shown once. Scopes gate
  endpoints; the chat endpoint needs the `inference.chat` scope.
  [Authentication](https://melious.ai/docs/concepts/authentication), read
  2026-10-27.
- **Chat endpoint:** `POST /v1/chat/completions`.
  [Chat completions](https://melious.ai/docs/reference/chat-completions), read
  2026-10-27.
- **Request JSON** is the OpenAI shape: `model`, `messages`, `max_tokens`,
  `temperature`, `tools`, `response_format`, plus Melious-specific extras
  (`request_id`, routing flavors like `:eco`). **Response JSON** is the OpenAI
  shape — `choices[].message.content`, `finish_reason`, `usage` (OpenAI-shaped
  plus `cached_tokens` and `reasoning_tokens`) — with two Melious extensions
  that SDKs ignore: `environment_impact` and `billing_cost` (EUR cost as
  strings). Same reference, read 2026-10-27.
- **JSON mode:** supported. `response_format: {"type": "json_object"}` works on
  almost every chat model; strict `{"type": "json_schema", …}` only on models
  with `_meta.capabilities.json_schema` (others get a 400
  `model_not_capable`). Melious repairs near-valid JSON on non-streaming
  responses. [Structured outputs](https://melious.ai/docs/guides/structured-outputs),
  read 2026-10-27.
- **Model ids and per-token prices** ([hub](https://melious.ai/hub/models), read
  2026-10-27):

  | Model id | Input €/M | Output €/M | Cache hit €/M |
  |---|---|---|---|
  | `glm-5.3-flash` | 0.10 | 0.40 | 0.02 |
  | `deepseek-v4-flash-0731` (DeepSeek V4 Flash) | 0.10 | 0.25 | 0.02 |
  | `gemma-4-31b` | 0.10 | 0.30 | 0.02 |

  These are the three 9.7 benchmark candidates, all EU-hosted.
- **Rate limits** ([Rate limits](https://melious.ai/docs/concepts/rate-limits),
  read 2026-10-27): counted **per account, per endpoint path** in a sliding
  60-second window — extra keys share the bucket. Base ceiling 120
  requests/min; 240 when the credit balance is at or above a threshold (10
  credits by default). A key created with its own per-minute limit uses that
  instead of the account's. Responses carry `X-RateLimit-Limit/Remaining/Reset`;
  a 429 carries `Retry-After` (seconds). The account limiter returns a flat 429
  body `{"code": "RATE_LIMIT_EXCEEDED", …}`.
- **Error codes** ([Errors](https://melious.ai/docs/concepts/errors), read
  2026-10-27), on `/v1/chat/completions`, OpenAI envelope
  `{"error": {"message", "type", "param", "code"}}`:

  | Status | `type` | `code` | Maps to |
  |---|---|---|---|
  | 401 | `authentication_error` | `invalid_api_key` | `AUTH` |
  | 403 | `authentication_error` | `auth_insufficient_scope` | `AUTH` |
  | 404 | `invalid_request_error` | `model_not_found` | `BAD_RESPONSE` |
  | 400 | `invalid_request_error` | `model_not_capable`, `context_length_exceeded`, … | `BAD_RESPONSE` |
  | 429 | `rate_limit_exceeded` | `rate_limit_exceeded` | `RATE_LIMIT` (honor `Retry-After`) |
  | 429 | `billing_error` | `billing_insufficient_balance`, `insufficient_quota` | `BILLING` |
  | 500/503 | `server_error` | `system_internal_error`, `server_error`, `provider_error`, `timeout` | `SERVER` |

- **Out-of-credits error — confirmed, and it is not 402.** On
  `/v1/chat/completions` an exhausted balance returns **429 with
  `type: "billing_error"`** (`code` `billing_insufficient_balance` or
  `insufficient_quota`); the same refusal is **403** on `/v1/responses` and
  `/v1/messages`. It is not a throttle: backing off cannot fix a balance. The
  adapter must classify on `type`/`code`, not on an expected 402 status, and
  map it to the `BILLING` kind (owner decision, #561). Same reference, read
  2026-10-27.
- **Per-key spend limit: none found.** The public docs offer no per-key
  spend/budget cap. What exists: a **per-key request-rate limit** (per-minute,
  set at key creation — [Rate limits](https://melious.ai/docs/concepts/rate-limits)),
  a per-key payment-order override (energy first / credits first —
  [Pricing](https://melious.ai/docs/concepts/pricing)), and
  **account-level** credits with optional auto top-up (threshold €5–€10,000,
  charges a saved card or emails a warning —
  [Auto top-up](https://melious.ai/docs/concepts/auto-top-up)). The
  "spending or purchase limit" that pauses auto top-up is a card limit, not a
  key limit. Read 2026-10-27. So the financial ceiling stays the account credit
  balance, as the two-layer cost model assumes; the in-app daily cap is the
  only spend throttle inside the app.
- **Data retention and training policy**
  ([Privacy](https://melious.ai/docs/concepts/privacy), read 2026-10-27):
  EU-only inference (no non-EU fallback region), **no provider trains on your
  traffic** (a condition of being on Melious's provider list), and **Melious
  does not log prompt or response content** — only operational metadata
  (request id, token counts, model/provider, cost). Not a DPA; the legal
  policies live at melious.ai. This matches the 2026-10-27 owner decision's
  "EU-hosted, no-training policy".
- **Live call** (2026-10-27, key read from the `MELIOUS_API_KEY` environment
  variable only — never printed, logged or committed): `POST
  /v1/chat/completions` with `glm-5.3-flash` and a one-sentence prompt returned
  **HTTP 200**, echoed `model: glm-5.3-flash`, and the OpenAI-compatible shape
  held (`choices[0].message.content`, `finish_reason: "stop"`, OpenAI-shaped
  `usage`). Recorded here: status code and model id only.
- **Differences the adapter must still know** (none break the verdict): the
  out-of-credits refusal is a 429 `billing_error` here (402 was the assumption,
  403 on the other two endpoint shapes); `cache_control` blocks are dropped —
  prefix caching is transparent via `usage.cached_tokens`; reasoning models can
  return `reasoning_content` alongside `content`.

**OpenAI-compatible adapter fits: yes.**

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

### Benchmark results (2026-10-27)

Ran for real (#600) with the harness in
`app/src/test/java/app/olauncher/data/store/AiBenchmark.kt` — 3 runs per model
over 20 saved words with context sentences from one real tagesschau article
([Frontex-Ausbau](https://www.tagesschau.de/ausland/europa/eu-will-fuer-frontex-mehr-befugnisse-und-personal-100.html),
27.10.2026). Validity and grounding scored by the harness's deterministic
checkers; correctness by a glm-5.3 proxy judge. Full record and failure
analysis: [ai-model-decision.md](ai-model-decision.md).

| Model | Validity % | Grounding % | Correctness proxy | €/item | p50/p95 latency | Failures | Verdict |
|---|---|---|---|---|---|---|---|
| glm-5.3-flash | 77.2 | 97.8 | 1.98/2 | 0.000240 | 36.8 s / 72.0 s | 0/3 | FAIL |
| deepseek-v4-flash-0731 | 0.0 | 0.0 | n/a | — | 148.4 s / 152.6 s | 0/3 | FAIL |
| gemma-4-31b | 66.7 | 99.2 | 1.96/2 | 0.000288 | 126.5 s / 178.0 s | 0/3 | FAIL |

Thresholds (unchanged): validity ≥ 95 %, correctness ≥ 1.8/2, grounding ≥ 90 %.
**No candidate passes the validity floor, so no `AI_MODEL` default is set.**
Correctness is close for both working models (1.96–1.98/2, grounding ~98–99 %);
the miss is contract validity (dropped or malformed items). Best price to
performance among the near-misses: **glm-5.3-flash** — highest validity,
fastest, cheapest per valid item (DeepSeek never completes the JSON even at
16k output tokens).

Final model pick pending owner sign-off (correctness scored by glm-5.3 proxy,
not a native-speaker reviewer). Recorded benchmark spend: €0.082 for the final
run (cap €1; ~€0.2 across all three live runs incl. earlier harness shakedown).

## Out of scope

- The capture front door (#523) — decoupled; the only contract is `Word` +
  `context`.
- OCR.
- A sync backend or companion app.
- New third-party runtime dependencies.
- **Any tutor or free-form scoring feature** — Why?-explanations, AI grading of
  typed answers, conversational practice. The AI is a generator, never a tutor.
