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
