# AI model decision (9.7, #600)

Benchmarked 2026-10-27/28 with the harness in
`app/src/test/java/app/olauncher/data/store/AiBenchmark.kt` (test source set only,
skipped without a key; the live run is an execution of that test, not a unit test).

- **Workload:** one real German news article — tagesschau.de, "EU-Pläne für
  Frontex: Mehr Personal und mehr Befugnisse",
  <https://www.tagesschau.de/ausland/europa/eu-will-fuer-frontex-mehr-befugnisse-und-personal-100.html>
  (stand 27.10.2026). Fixture: 20 saved words with their context sentences from
  that article (`app/src/test/resources/ai_bench/words.json`), same shape as app
  data (`Word` + `context`, ≤ 400 chars). Only the sentences, URL and date are
  stored, not the article.
- **Candidates:** `glm-5.3-flash`, `deepseek-v4-flash-0731`, `gemma-4-31b` on
  Melious — all EU-hosted, no-training policy (owner decision, 2026-10-27).
  3 runs per model, one call generating all 20 words × 3 items through the real
  `PracticeContract` prompt and the `OpenAiCompatibleProvider` adapter.
- **Judge / reviewer:** German correctness is a **proxy** score from
  `glm-5.3` (stronger model, same OpenAI-compatible endpoint), one call per
  candidate's output set, rubric 0–2 per item (2 = natural correct German,
  1 = minor problems, 0 = wrong/ungrammatical). **Final model pick pending owner
  sign-off (correctness scored by glm-5.3 proxy, not a native-speaker reviewer).**
- **Contract validity (objective):** the response must parse as JSON and yield,
  per requested word, 3 items with known `DrillType` ids that pass
  `DrillItem.validate()` (`PracticeContract.parse`); missing or rejected items
  count against the model, as do failed calls.
- **Grounding (objective, deterministic checker in `AiBenchLogic.isGrounded`):**
  an item is grounded when (a) its answer is the saved word or an inflected form
  (shared stem ≥ 4 chars covering ≥ half the word, umlauts folded ä→a, ö→o,
  ü→u, ß→ss), or (b) the item's prompt (gap filled with the answer), answer and
  choices share with the saved context sentence at least one content token
  (lowercased, punctuation stripped, length ≥ 4) that is itself ≥ 6 chars —
  short shared tokens such as "nicht" do not count on their own. The CSV has a
  manual `grounded_override` column for review.

## Results (3 runs × 20 words × 3 items per model, 2026-10-27/28)

| Model | Validity % | Grounding % | Correctness proxy (0–2) | €/item | p50 / p95 latency | Failures | Verdict |
|---|---|---|---|---|---|---|---|
| glm-5.3-flash | 77.2 | 97.8 | 1.98 (139/180 judged) | 0.000240 | 36.8 s / 72.0 s | 0/3 | FAIL |
| deepseek-v4-flash-0731 | 0.0 | 0.0 | n/a (0/180 judged) | — | 148.4 s / 152.6 s | 0/3 | FAIL |
| gemma-4-31b | 66.7 | 99.2 | 1.96 (120/180 judged) | 0.000288 | 126.5 s / 178.0 s | 0/3 | FAIL |

Raw per-item CSVs: `app/build/ai-bench/<model>.csv` and `summary.csv` (build
artifacts, not committed; no key or personal data in them).

## Thresholds and verdict

| Threshold | Value | glm-5.3-flash | deepseek-v4-flash-0731 | gemma-4-31b |
|---|---|---|---|---|
| Contract validity | ≥ 95 % | 77.2 ✗ | 0.0 ✗ | 66.7 ✗ |
| Mean German correctness (proxy) | ≥ 1.8 of 2 | 1.98 ✓ | n/a ✗ | 1.96 ✓ |
| Grounding | ≥ 90 % | 97.8 ✓ | 0.0 ✗ | 99.2 ✓ |

**No candidate passes; no recommended default is set.** The `AI_MODEL` pref
default stays empty pending either a fixed candidate or an owner decision.
By price to performance among the near-misses, **glm-5.3-flash** is the
strongest: highest validity (77.2 %), fastest (36.8 s p50), cheapest per valid
item (€0.000240), and its failures are single malformed items (omitted
`choices`), not whole-run losses.

Failure analysis:

- **glm-5.3-flash** — occasionally omits the `choices` array or duplicates a
  choice on single items; the contract fills those with cloze fallbacks, so the
  app degrades gracefully.
- **deepseek-v4-flash-0731** — every run emitted over 16,000 output tokens and
  never completed the JSON (verbosity/reasoning in the content stream). Not
  usable through this contract at this workload size.
- **gemma-4-31b** — one of three runs returned an unparseable/partial JSON set;
  slow (2+ minutes p95) and the most expensive per valid item.

## Budget

Hard cap €1 (`AI_BENCH_CAP_EUR`), estimated cost logged before every call;
no model run reached even 10 % of the cap. Recorded spend of the final live
run: **€0.082** (€0.033 + €0.014 + €0.035 per model incl. judge calls); all
three live runs together ≈ €0.2. Key read from the environment only
(`AI_BENCH_KEY` / `MELIOUS_API_KEY`), never printed, logged or committed.

## Decision

- Correctness is a glm-5.3 proxy score, pending native-speaker owner review —
  the per-item CSVs carry a blank `correctness` override column for that review.
- **No model is selected as the default**; the validity floor (≥ 95 %) is not
  met. Cheapest passing model would have won per the owner's rule; none passed.
- Final model pick pending owner sign-off (correctness scored by glm-5.3 proxy,
  not a native-speaker reviewer).

## Round 2 (2026-10-28): quality-only, mid-tier and Mistral candidates

Time constraints were **removed by owner decision 2026-10-28** — generation is a
background process, so latency is recorded informationally and never affects the
verdict; the read timeout went to 600 s per request and `max_tokens` to 16,384.
The verdict is quality-only on the unchanged thresholds: validity ≥ 95 %,
grounding ≥ 90 %, correctness proxy ≥ 1.8/2 (glm-5.3 judge). Budget cap €2
(recorded spend **€0.22** incl. judge calls; expected total was well under €1).

Harness changes: each candidate tries strict `response_format: json_schema`
first; a 400 `model_not_capable` (or a schema-induced
`context_length_exceeded`) falls back to `json_object` for the whole model and
the fallback is recorded in the summary's `schema_mode` column. A billing error
(429 `billing_error` / 402) marks a model "not available on the owner's current
plan (pay-as-you-go only)" and the benchmark moves on. Same fixture: 20 words ×
3 items, 3 runs per model, same tagesschau article as round 1.

### Results (3 runs × 20 words × 3 items per model, 2026-10-28)

| Model | Schema mode | Validity % | Grounding % | Correctness proxy (0–2) | €/item | p50 / p95 latency | Failures | Verdict |
|---|---|---|---|---|---|---|---|---|
| gemma-4-31b | json_schema | **95.0** | 99.4 | 1.91 (171/180 judged) | 0.000205 | 114.1 s / 176.9 s | 0/3 | **PASS** |
| glm-5.3-flash | json_schema | 92.8 | 98.8 | 1.98 (167/180) | 0.000175 | 16.2 s / 17.6 s | 0/3 | FAIL |
| glm-5.3 | json_schema | 82.2 | 98.6 | 1.87 (148/180) | 0.000640 | 42.7 s / 58.6 s | 0/3 | FAIL |
| mistral-small-4-119b-instruct | json_schema | 77.8 | 100.0 | n/a (judge call failed) | 0.000295 | 31.6 s / 39.1 s | 0/3 | FAIL |
| deepseek-v4.1-flash-1017 | json_schema | 33.3 | 98.3 | 2.00 (60/180) | 0.000395 | 27.3 s / 27.3 s | 2/3 calls failed | FAIL |
| deepseek-v4.1-flash | json_schema | 0.0 | 0.0 | n/a | — | — | 3/3 calls failed | FAIL |
| gpt-oss-120b | json_schema → json_object | 0.0 | 0.0 | n/a | — | — | 3/3 calls failed | FAIL |

Failure analysis:

- **gemma-4-31b — the only PASS**, exactly at the 95 % validity floor (171/180
  items; 9 invalid). Slow (≈ 2 minutes p95) but the owner removed time as a
  constraint. Its round-1 validity failure (66.7 %, json_object only) is fixed
  by the strict-schema request shape.
- **glm-5.3-flash** improved from 77.2 % to 92.8 % validity with the strict
  schema — still 12 items short over 180. Strongest near-miss by speed and
  price per item.
- **glm-5.3** (mid-tier, previously excluded by cost): 82.2 % — the stronger
  judge model does not generate more valid practice items; not worth €3.6× the
  per-item price of the flash variant.
- **mistral-small-4-119b-instruct** (owner-requested): ran on the owner's
  current plan (pay-as-you-go; no billing errors anywhere in round 2), best
  grounding (100.0 %), but 40/180 items missing or invalid — 77.8 % validity.
  Its proxy-judge call failed, so correctness is unscored (n/a, judged FAIL on
  validity alone).
- **deepseek-v4.1-flash** — repeats the round-1 DeepSeek failure mode: every
  call emitted 16k+ output tokens without completing the JSON. The -1017
  snapshot managed one good run (2.00 correctness on 60 items) but failed the
  other two calls.
- **gpt-oss-120b** — not usable on Melious: every call 400s with
  `context_length_exceeded`, in strict-schema *and* plain json_object mode
  (deployment exposes too small a context window for even a trivial prompt).
  The only schema fallback of the round.
- Melious schema note: no candidate 400s with `model_not_capable` — all seven
  accepted the strict envelope, but enforcement is loose (the schema shapes the
  output; it does not hard-guarantee it, e.g. mistral emitted wrong `word_id`s).

### Round-2 decision

- **gemma-4-31b passes all three quality thresholds** and is the cheapest
  passing model of round 2 → it becomes the recommended `AI_MODEL` default
  candidate. Cost: €0.000205/item; at 50 words/day (≈ 150 items, ~2.5
  20-word generation runs) that is ≈ **€0.007/day ≈ €0.21/month** (generation
  only, excluding the benchmark judge).
- Latency is informational by owner decision — gemma's ≈ 2 min p95 does not
  disqualify it; generation runs as a background process.
- Correctness is still a glm-5.3 proxy score pending native-speaker owner
  review; the per-item CSVs keep the blank `correctness` override column.
- Recorded round-2 spend: **€0.224** (cap €2).
