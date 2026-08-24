# Router Live Staging Benchmark

Date: 2026-08-23/24 UTC

## Scope and safety

- Integration branch: `codex/router-live-staging-v1`
- Integration baseline SHA: `e3568f0001cf50e01751470e3152caaeb32c240b`
- Staging integration SHA: `afdf3e2058c16b6ea5311e5821d4a0feaddd3085`
- Original Router implementation: `c3e2f04ff97c609e6d7a8e553771496c031e4a8b`
- Router Data Use/content recording: OFF
- Cost-efficient routing: ON
- Benchmark routing: ON
- Shadow Models: OFF
- NeMo Switchyard was not explicitly selected by the application.
- Only `PublicCompanyAnalysisInput` snapshots were sent. No Deal Scout workspace, private diligence, notes, documents, authentication data, or credentials were included.
- Router provider calls were capped at 28. The experiment made 27 Router calls and used zero application retries.

The `radar-routine` alias used these account weights:

- Intelligence Index: 45%
- IFBench: 30%
- Cost per Task: 25%

## Models

- Pinned routine: `accounts/fireworks/models/gpt-oss-20b`
- Pinned Deep Dive: `accounts/fireworks/models/gpt-oss-120b`
- Benchmark alias: `radar-routine`
- Alias-selected model: `accounts/fireworks/models/minimax-m3` for all 13 alias calls (one smoke plus 12 benchmark calls)

The authenticated Router catalog reported structured-output support for the two pinned models and Minimax M3. Catalog prices used for estimates below were, per million tokens:

| Model | Input | Output |
| --- | ---: | ---: |
| `gpt-oss-20b` | $0.07 | $0.30 |
| `gpt-oss-120b` | $0.15 | $0.60 |
| `minimax-m3` | $0.30 | $1.20 |

## Smoke tests

| Path | Requested model | Actual model | Result | Latency | Tokens in/out | Retries |
| --- | --- | --- | --- | ---: | ---: | ---: |
| Direct pinned routine | `gpt-oss-20b` | `gpt-oss-20b` | Valid schema | 4,499 ms | 1,040 / 532 | 0 |
| Application routine | `gpt-oss-20b` | `gpt-oss-20b` | Valid schema and persisted telemetry | 2,918 ms | 1,040 / 459 | 0 |
| Alias routine | `radar-routine` | `minimax-m3` | Valid schema | 12,442 ms | 618 / 1,182 | 0 |
| Browser Deep Dive | `gpt-oss-120b` | `gpt-oss-120b` | Valid schema and rendered in profile | 10,062 ms | 1,041 / 1,980 | 0 |

Wardin was used for the smoke tests. The application routine persisted requested/actual model, latency, token counts, retry count, and safe request/trace IDs. Its deterministic Radar Score remained 41 before and after AI enrichment. Router did not return inline cost, so cost is estimated from catalog prices and reported token usage.

## Benchmark sample

The benchmark captured each public input once and reused that exact snapshot for all three providers. It bypassed normal cached analysis retrieval and did not write benchmark outputs, alter Radar Scores, or change watchlist state.

1. Discovered Materials (YC P26)
2. ESS - Environmental Stability Snapshot
3. Wardin
4. Binadox
5. Rillet
6. Thrive Holdings
7. Moove
8. Ekubo Wallet
9. RamboCard
10. Postern
11. Aerostic Automation
12. Construct Computer

The set covers AI/materials, safety analytics, developer tooling/security, SaaS/FinOps, accounting, enterprise AI, transportation/autonomy, fintech/crypto, payments, open-source agent infrastructure, marketing automation, and productivity.

Wardin's pinned result was reused from the pinned preflight, so the batch itself made 11 pinned calls. Metrics below describe the fresh 12-company comparison: 12 Groq calls, 12 alias calls, and 11 batch pinned calls plus the one fresh pinned Wardin preflight.

## Results

| Metric | Groq | Router Pinned | `radar-routine` |
| --- | ---: | ---: | ---: |
| Fresh calls | 12 | 12 | 12 |
| Successful calls | 4 | 12 | 12 |
| Schema success | 33.3% | 100% | 100% |
| Total retries | 0 | 0 | 0 |
| Malformed client responses | 0 | 0 | 0 |
| Provider schema rejections | 8 | 0 | 0 |
| Average latency | 664 ms | 2,832 ms | 16,127 ms |
| Median latency | 682 ms | 2,911 ms | 13,857 ms |
| P95 latency | 883 ms | 3,881 ms | 21,729 ms |
| Input tokens | 3,518 reported on successes | 11,225 | 6,236 |
| Output tokens | 1,450 reported on successes | 6,614 | 19,785 |
| Estimated known cost | Provider control, not Router billed | $0.0028 | $0.0256 |
| Estimated average cost/call | Not comparable | $0.00023 | $0.00213 |
| Unsupported factual claims | 1 | 3 | 3 |
| Fact/inference confusion | 0 | 2 | 0 |
| Missed material evidence | 8 (failed outputs) | 0 | 1 |
| Generic/unhelpful outputs | 0 | 1 | 1 |
| Other material issues | 8 schema failures | 2 | 2 |

Router pinned totals include the reused Wardin preflight. Its aggregate latency and tokens are therefore calculated from 11 batch calls plus that preflight. `radar-routine` selected `accounts/fireworks/models/minimax-m3` 12/12 times in the benchmark.

Estimated Router spend for all 27 Router calls, including smoke tests, the application-path check, Deep Dive, and benchmark, was approximately **$0.0315**. Router did not return inline cost, so the account balance could not be used as a precise experiment ledger; the starting balance was approximately $25.97 and the ending balance was not independently available through the Responses or model-catalog APIs.

## Qualitative review

No synthetic quality score was assigned. Every output was inspected against its stored public snapshot.

Groq was fastest when it succeeded, and its four accepted outputs were concise and mostly grounded. With retries disabled for a fair single-attempt comparison, however, eight of twelve generations were rejected by Groq's strict structured-output validation for missing fields or an invalid confidence enum. This reliability result dominates its latency advantage for the tested prompt/schema combination.

The pinned Router model was fast enough for interactive routine enrichment and consistently produced valid structured data. It was substantially more concise than the alias. Its material weaknesses were occasional unsupported specificity or inference: treating `P26` as `S26`, temporal confusion in the ESS version description, an unsupported Thrive positioning inference, and one marketing description classified as traction. One output also contained stray punctuation, but remained schema-valid.

The `radar-routine` alias showed the best evidence-discipline overall. It frequently labeled uncertainty and separated facts from inferences clearly. Its weaknesses were high latency, roughly three times the pinned model's output tokens, a semantically empty RamboCard summary with an incomplete inference, and a few speculative risk statements (notably Thrive concentration and Aerostic pre-revenue implications). It was reliable but too slow and verbose to justify becoming the routine interactive default on this sample.

Meaningful traction extraction was limited by sparse source data. Both Router paths correctly extracted Rillet's ARR growth; neither treated most marketing descriptions as verified traction consistently. The alias produced the most useful evidence-limit and missing-data risks, while pinned output was more economical.

## Conclusion

Router is technically sound as an optional provider: privacy boundaries held, schemas were reliable, telemetry worked, the Deep Dive rendered correctly, and cost was negligible. For routine enrichment, the pinned `gpt-oss-20b` path offers the strongest operational balance of reliability, latency, and cost. The benchmark alias produced richer evidence-aware prose but was too slow and verbose for the default path. Groq should remain the active staging default until the Router results are reviewed and a provider change is explicitly approved.

Recommendation: `ROUTER_OPTIONAL_PROVIDER_ONLY`
