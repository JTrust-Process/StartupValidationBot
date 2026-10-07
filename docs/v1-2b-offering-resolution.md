# V1.2B Offering Terms and Platform Resolution

Development baseline is main `9ab8886b99f213708ac4b4ec12ac3695db023e58`.
The bounded [production inventory](v1-2b-production-inventory.md) is read-only and complete.
Implementation and live validation are staging-only. Production promotion is not authorized.

## Authority and Identity

`IntermediaryRegistry` uses exact CIK, exact CRD/commission file, exact normalized legal name, then explicit official domain. Entries cover the actually observed production intermediaries, not only the three fetchable campaign platforms.
Conflicting registered identifiers are ambiguous. Unknown names remain in filed evidence without inventing a family.
Company/issuer match, intermediary family, campaign link confidence and HTTP availability are independent.
SEC-confirmed intermediary identity does not establish campaign availability or make a packet READY.

## SEC Terms

Deterministic XML/label-aware HTML extraction retains term values, semantic types, source excerpts, accession, source document and observation time in existing JSON/evidence fields. Schema remains V15; no migration or new dependency is needed.

- Explicit amended minimums supersede the old amount in `from X to Y` language.
- Statutory investor limits are not offering minimums.
- Valuation cap, pre-money valuation, post-money valuation and company valuation stay distinct in provenance.
- Multiple/conditional amounts stay as evidence; effective canonical projection is unset and requires review.
- Filed `Other` with an explicit SAFE or other supported subtype becomes precise without discarding the raw category/description.
- Final sales/raised, interim raised, investment commitments and fee-inclusive proceeds remain separately typed.
- Provisional/fee-inclusive amounts do not silently become a final net raised figure.
- C-U closes lifecycle only with explicit completion evidence, not filing recency.
- Missing retrieval data never deletes known values; ambiguous terms block effective projection without deleting historical canonical/evidence rows.

Index-first SEC retrieval keeps its two-document budget and 8 MB document limit. It may inspect a primary XML/HTML and one explicitly relevant small terms/security HTML exhibit. It does not fetch whole submissions, decks or arbitrary attachment lists. Existing SEC pacing/retries are unchanged.

## Explicit Campaign Links

An exact supported campaign path observed in SEC evidence may establish a link; platform roots do not. No slug/path is generated from a company name.
For independently matched issuers, a bounded homepage-only inspection can capture explicit outbound anchors. Robots policy, HTTPS/public-address validation, same-host bounded redirects, response size, pacing and browser-verification refusal apply. No internal path is guessed or followed.
Campaign fetch uses the existing enrichers only after a URL exists. Issuer mismatch is rejected. 403/404/verification failures retain established links/SEC terms and are displayed separately from prior verification.
Only three existing platform fetchers are used; other registered families remain SEC metadata without a new crawler.

## Packets and Handoff

Completeness separately counts platform identity and raised coverage (five points each); its base is fifteen and it is capped at 100. READY rules are unchanged.
The packet displays semantic term provenance, original filed excerpts and campaign fetch status independently of intermediary identity.
The existing Deal Scout action opens an unsaved draft using effective terms. Revenue Share and ambiguous/multiple securities are not incorrectly mapped to Equity/SAFE. No deal or investment decision is created automatically.
SEC parser-only extraction does not emit a material offering-change event: the existing material notification path compares previously observed campaign baselines, not parser projection changes.

## Verification and Live Gate

Regression tests cover exact/conflicting intermediary IDs, root/explicit URLs, issuer link access failures, conditional terms, amended minimums, C-U semantics, effective authority, persistence, lifecycle and preservation. The persistence cases also run in the existing PostgreSQL/Testcontainers suite.
Frontend tests cover exact subtype mapping, escaped excerpts, semantic provenance and independent campaign messaging.
Full Java, PostgreSQL CI, frontend tests/lint/build/audits, Fly strict validation and diff checks must pass before a normal staging merge.
Capture staging coverage first, then run exactly one normal fairness-selected autonomous-diligence cycle. No hand-picked packet refreshes or extra standalone discovery jobs.
Readiness requires meaningful real-data improvement in at least two approved dimensions with all safety invariants preserved. Zero READY packets is acceptable; missing live validation must not be reported as success.
