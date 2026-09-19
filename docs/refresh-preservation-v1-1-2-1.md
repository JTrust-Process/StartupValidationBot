# V1.1.2.1 refresh preservation and projection repair

## Invariant and audited write paths

Absence is not deletion. `Candidate.retrievalQuality` explicitly distinguishes
`DETAIL_COMPLETE`, `PARTIAL_DETAIL`, and `INDEX_ONLY`. Native SEC conversion
preserves that signal. SEC detail failures/bounds remain index-only; persistence
does not infer completeness from URL or error text.

Audited canonical writers:

- SEC `OfferingDiscoveryService` -> `OfferingStore.upsert` (recent filings,
  baseline, amendments and lifecycle). Equal accession updates merge fields;
  historical accessions still resolve to the existing offering and cannot undo
  newer filings.
- `NativeOfferingIntakeService` -> the same SEC upsert. Known terminal filings
  update lifecycle even though they are excluded from actionable intake.
- `OfferingStore.updateMatch`, including stored identity revalidation and native
  reconciliation conflicts. Missing corroboration preserves CONFIRMED; observed
  domain/issuer conflicts or concrete ambiguity can downgrade it.
- `NativeOfferingStore.upsertPlatformOffering`: missing platform fields preserve
  canonical values and reconciliation. SEC-backed fields retain higher authority.
- `DiligenceStore.upsertCampaign`, used by native intake, campaign discovery, and
  autonomous campaign refresh. Partial results merge per field; unavailable
  results retain established terms. HTTP exceptions preserve the old campaign
  while the existing platform source-health record reports failure.
- `markResolution` only updates current resolution diagnostics, not terms or
  historical proof. Candidate-cache rows describe the latest retrieval attempt;
  they are not the canonical offering projection.
- `attachCampaignUrl` fills only an absent URL and unknown platform after bounded
  campaign identity resolution; it cannot erase an established URL.

`OfferingRefreshMerge` accepts explicit valid observations, preserves missing or
rejected values, and validates numeric bounds/security normalization. A newer
amendment may change valid terms. Withdrawal/termination/explicit expiry still
make an offering non-actionable; historical terms are not erased.

Per-field `_observedAt`, `_sourceUrl`, and `_accession` metadata lives in existing
JSON columns. Carry-forward does not refresh these timestamps. `_lastRefreshAt`
and existing source diagnostics describe the current attempt. The packet's
existing provenance display includes a last-observed date when known. Legacy
records without a defensible timestamp do not invent one. No migration beyond
V14 is required. Evidence-history insertion/deduplication is unchanged.
Raw financial aliases are carried within the same accession only, avoiding
attribution of an older fiscal period to a newer filing. Prior financial/evidence
rows remain retained; carried canonical terms keep their original filing source.

## Repair is explicit maintenance, not an automatic fallback

`OfferingProjectionRepair.dryRun(1..100)` automatically selects SEC-backed rows
with missing canonical terms. It returns a zero-mutation plan with each proposed
field, current/proposed value, evidence ID, classification, source, timestamp,
and accession. It only accepts valid, unambiguous `SEC_FILED_FACT` evidence from
the current accession and the exact official SEC filing URL. Conflicting proof,
older accessions, and unsupported fields remain unresolved. Non-null canonical
fields are never overwritten.

The canonical offering is repaired once rather than adding a competing evidence
fallback to packet reads. SEC terms remain higher authority than platform claims.
Evidence remains visible in packet evidence tables while a projection repair is
awaiting approval. No email text or UI output is used as repair evidence.

Current resolution metadata is mutable and does not prove a prior stronger
company match. This repair therefore reports weaker identity states as unresolved
instead of guessing CONFIRMED. A future identity repair requires actual durable
resolution proof and separate review.

`apply(reviewedPlan, approvedOfferingIds)` is a deliberately unexposed service
method for later explicit maintenance. It requires a 1-25 ID allowlist, locks each
row, rechecks evidence, rejects stale plans, updates only still-null whitelisted
columns, and preserves original evidence observation time. Replays are no-ops.
It is exercised only in transactional test databases for this release. There is
no API, scheduler, startup hook, or command-line apply switch.

## Offline production analysis (read-only)

After staging passes, export a bounded `OfferingProjectionRepair.Snapshot` using
a read-only PostgreSQL transaction. Only offering identity, the seven canonical
term fields and retained SEC evidence are needed, not secrets or Deal workspaces.
Pass the JSON snapshot on standard input to `OfferingProjectionRepairTool` with
the backend runtime classpath. It rejects arguments and input above 5 MB, prints
only a dry-run plan, and does not connect to any database or external service.

Snapshot shape: `{ "rows": [...] }`. Each row contains `offeringId`, `company`,
`accession`, `sourceUrl`, `matchStatus`, `values` (database-column names), and
`evidence`. Each evidence item contains `id`, `field`, `value`, `classification`,
`sourceUrl`, `observedAt`, and `accession` from retained evidence metadata.

Production apply is not authorized by this release. Review every proposed field
and unresolved identity before considering a separate bounded repair approval.

## Verification and staging gate

`RefreshPreservationIntegrationTest` covers both incident-equivalent fixtures,
same-accession partial/index refresh, explicit updates, amendments, lifecycle,
identity conflicts, invalid terms, campaign failures, packet projections,
notification deduplication, and bounded repair. Its Testcontainers subclass runs
the same invariants against PostgreSQL 16, with Flyway and `ddl-auto=validate`.
Local Docker skips are not passes; Docker-enabled CI must execute the suite.

After all verification passes, merge the fix normally into staging only. Deploy
with the explicit staging Fly app/config (the root `fly.toml` targets production).
Snapshot populated staging offerings and campaigns, trigger exactly one bounded
autonomous-diligence job, wait for its durable completion, and compare fields,
identity, observation metadata, packets, source failures, and unsaved Deal Scout
handoff. Do not save a Deal or make an investment decision. Production analysis
remains read-only and occurs only after the staging gate passes.
