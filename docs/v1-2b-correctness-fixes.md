# V1.2B Correctness Fixes

Development baseline: feature `4eaf13317ec9a82d72f38869a62b43aee17483e3`; staging `77556cb7375b96e5146ca92deebcbf8f58284baa`. Main and production remain unchanged. Schema remains V15.

## Lifecycle

Word-bounded, contextual lifecycle statements replace the unsafe `ended` substring match. An extension, future closing date, or negated completion cannot establish completion. Withdrawal, termination, completion, extension, and conflicting statements remain distinct. Form C-W/C-TR semantics remain authoritative. Existing terms, extension deadlines, and evidence history are preserved.

Job 225 produced an incorrect `_secTerm.completed=true` marker on Sunpath Consulting LLC, offering 51, accession `0002145127-26-000002`. Its actual state stayed ACTIVE because completion is applied to C-U/C-U/A, not its C/A. The only other completion marker was Edison, correctly ENDED. No false terminal offering or notification from Sunpath was identified. Any staging metadata correction must be accession- and evidence-guarded; no cleanup of historical evidence is appropriate.

## Filed Amounts

Edison Interactive Holdings, offering 125, accession `0001872856-26-000331`, C-U filed October 2, 2026, primary document:

https://www.sec.gov/Archives/edgar/data/1733902/000187285626000331/primary_doc.xml

Filed sentence: "The Offering closed early on September 28, 2026 with a final raise amount of $62,562.45."

The generic `final raise amount` label now creates FINAL_COMPLETED_RAISE, distinct from interim commitments, target/maximum amounts, provisional values, and fee-inclusive gross proceeds. USD units, original excerpt, accession, document reference, semantic type and observation timestamp accompany the fact and effective packet terms. No company-specific parser exception was added.

Edison was not one of job 225's 25 fairness-selected offerings; native SEC intake had retained its evidence separately. It must not be forced into the next batch. Fixture and retained-evidence validation are separate from live selection.

## Minimum and Valuation Investigation

Only retained public term narratives and typed facts for job 225's 25 selected offerings were inspected; Edison was read separately. Missing terms were not inferred from inaccessible pages or unrelated numbers.

| Canonical field | Not disclosed in inspected retained evidence | Parser miss | Projection failure | Conditional/semantic ambiguity | Proven platform-only value | Different tranche |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Minimum | 24 | 1 | 0 | 0 | 0 | 0 |
| Valuation/cap | 24 | 0 | 0 | 1 | 0 | 0 |
| Final raised | 23 | 0 | 0 | 2 | 0 | 0 |

These are evidence-scope classifications, not proof that a missing term is absent from every full exhibit or blocked platform page. Complete document bodies are not all retained; unavailable sources cannot establish that they contain a value.

American Rebel, offering 41, sets the effective minimum to $100 and describes a future intention to increase it to $500. The proposal is now separately typed, not confused with the current minimum. Koios, offering 115, has conditional pre-money valuations; canonical valuation remains unset. Next Thing, offering 38, describes funds closed at a target milestone, not a final completed total. Rad Technologies, offering 119, reports a fee-inclusive final amount; it remains separately typed rather than invented as net securities sold.

## Campaign URL Safety

Name-derived Wefunder slugs and all targeted canonical probes are removed. The legacy adapter now reports EXPLICIT_LINK_REQUIRED with zero network requests. Normal background workflows use the authoritative SEC/issuer resolver instead. Republic and StartEngine directory paths retain only actually observed links; they do not generate paths from company names.

Candidate identity confirmation rejects unknown/guessed lineage. Explicit resolution records the source URL, observed URL, observation timestamp and identity reason. Campaign enrichment requires recorded explicit SEC/issuer/directory/user lineage or previously verified history. A platform/CIK/name alone cannot authorize a request. Guessed historical candidates are retained as evidence, not promoted as verified campaign data. Public 403 and issuer mismatch handling retain historical terms without bypass.

## Notification Audit Before Validation

All 13 events from job 225 were NEW_CONFIRMED_OFFERING for historical offerings, not MATERIAL_OFFERING_CHANGE. Filing dates span September 14 through October 5; first-seen dates precede the run. Issuance was driven by the existing CONFIRMED + ACTIVE eligibility rule without an accession/identity transition gate. None resulted from Sunpath's false marker. Material-change events added: zero. Duplicate fingerprints: zero.

Ten were sent. Events 216 (offering 49), 217 (108), and 219 (121) remained PENDING with attempt_count=0 because sendPending selects at most ten. The owner explicitly approved suppressing exactly those three. A staging-only transaction checked the database name, IDs, event/entity types, timestamp window, PENDING state, zero attempts and exactly three updated rows. They are SUPPRESSED, preserved, and cannot be selected for sending. No event was manually resent; sent history was not rewritten.

The corrected job captures offering state before intake/extraction. Already-confirmed offerings with the same accession cannot generate a new-confirmed notice solely from parser/lifecycle backfill. New records, actual identity resolution and new filing evidence retain the existing idempotent notification path. Comparable raw-source fingerprints remain required for material changes.

## Validation Rules

Complete Java and Docker-enabled PostgreSQL CI, frontend tests/lint/build, both audits, Fly strict validation and diff checks must pass before staging integration. Feature/staging application trees must match. Exactly one normal 25-offering autonomous-diligence run is permitted after health, authentication and notification checks. Do not manually choose Edison, rerun to improve counts, refresh packets separately, flush notifications, or promote production.

No migration, new provider, crawler, investment decision, auto-created Deal or auto-investing behavior is part of this correction.
