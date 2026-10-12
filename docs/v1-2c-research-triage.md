# V1.2C Research Priority and Triage

## Baseline Audit

Development baseline: main `56582716e19573189e02742998abc99f268a549d` (V1.2B).
Production branch: `d4d71837e07036f53e8bb0ea93d99ef62c3eb40f`; schema V15.

Production inventory captured 2026-10-09T00:20:21.552883Z (October 8 in America/New_York).
All 72 stored packets inspected, below the explicit 150-record bound. Database-enforced repeatable-read,
read-only transaction verified `transaction_read_only=on`, then ROLLBACK; connection closed.
An initial terminal-wrapped result was discarded and the same bounded read repeated in clean output.

- 63 non-terminal packets: 37 PARTIAL, 26 NEEDS_REVIEW; 9 historical/ended packets.
- 61 non-terminal packets have source-backed financial periods; 2 do not.
- 37 confirmed issuer identities; 26 likely identities requiring further investigation.
- Two recorded campaign HTTP 403s. No recorded material discrepancies in this snapshot.
- Two deadlines within 14 days at inventory time (ShredPay and Musaffa, October 11).
- No filing-history rows captured after their packet's latest refresh in this snapshot.
- Every packet's `reviewed_at` is unset. It is not a user-review completion signal yet.
- Generic monitoring milestones are free text, sometimes AI-generated, without authoritative dates.
  They are not converted into deadline urgency. The dated, source-backed offering deadline is the
  meaningful approaching milestone available now.

Only public research fields read: issuer/platform/lifecycle, identity, canonical terms, filing history,
packet readiness and public research questions/discrepancies, source evidence classifications/timestamps,
and financial periods/source references. No credentials, auth/session data, private deal notes or email
contents read. Production SQL mutations 0, triggered jobs 0, sent emails 0, external searches 0.

## Deterministic Rules

Protected browser-session GET `/api/radar/diligence/triage`, evaluated on read in a read-only transaction.
No stored score, migration, background job, AI call, external fetch or notification is introduced.
UTC refresh/evidence timestamps use the existing server convention; deadline dates use `radar.time-zone`
(America/New_York by default), not the browser timezone.

Category precedence: time-sensitive, substantive research, investigation, blocked, historical.
Within a category: research weights descending; urgent deadline ascending; oldest refresh; stable packet ID.
The attention list has at most five distinct Radar companies. All packets remain in the full queue.
The priority is research-workflow ordering, not investment attractiveness or a 0-100 investment score.

| Reason | Condition | Research weight |
| --- | --- | --- |
| APPROACHING_SOURCE_DEADLINE | Active/possibly active, source-backed deadline within 30 days | 100 within 7, 80 within 14, 60 within 30 |
| RECENT_SEC_AMENDMENT | C/A or C-U/A filed within 14 days, no later recorded review | 35 |
| SOURCE_BACKED_TERM_CHANGE | Comparable explicit SEC values across dated accessions, same field/period/type/units | 25, at most once |
| FINANCIAL_EVIDENCE_AVAILABLE | At least one substantive source-linked financial period; zero values count | 20 |
| OFFERING_EVIDENCE_AVAILABLE | At least two distinct source-backed offering fact keys | 15 |
| ISSUER_IDENTITY_UNRESOLVED | Current canonical identity not CONFIRMED | 25 with substantive evidence, otherwise 10 |
| RECORDED_DISCREPANCIES | Persisted discrepancies | 30 with substantive evidence, otherwise 10 |
| MISSING_RESEARCH_TERMS | Missing security/minimum/deadline or applicable valuation | Up to 12, only with substantive evidence |
| STALE_DILIGENCE / REFRESH_NOT_ESTABLISHED | Refresh missing or at least 7 days old | 12, only with substantive evidence |
| EVIDENCE_FRESHNESS_UNCERTAIN | Source observations missing or at least 30 days old | 6, only with substantive evidence |
| RECOVERABLE_SOURCE_FAILURE | Explicit transient status, at least 7-day cooldown, substantive evidence | 8 |
| SOURCE_ACCESS_BLOCKED / SOURCE_FAILURE_UNCLASSIFIED | 403/404/verification or unspecified failure | 0 |
| OFFERING_ENDED / ISSUER_LINK_REJECTED | Terminal/expired offering or rejected issuer link | Excluded from attention |

Existing failure capture often records only UNAVAILABLE, not a specific transient cause. That does not
receive a speculative retry boost. A blocked campaign with useful SEC evidence can still deserve
substantive research for that evidence; the failure itself adds zero. A packet with only inaccessible
sources and no independent urgency/substantive evidence is placed in Blocked research.
Recent amendments use filed dates, not import/update times. Numerical formatting alone is not a change.
Comparison uses the most recent applicable prior filing, not an arbitrary older different value;
contradictory values within that prior accession do not generate a confident term-change signal.
Differing semantic types, units or financial periods are not comparable changes. All reasons expose
codes, weights, supporting field values, source URLs, accession references and evidence IDs where available.

## Queue and Preservation

Five filters/sort controls: packet status, lifecycle, completeness, platform and queue ordering.
The attention list follows research priority within the current filters; other packets can be sorted
by research attention, deadline or most recent successful refresh within the existing status sections.
Historical packets are separately disclosed, not deleted. Status totals include attention and historical
packets matching the filters. Packet links and the existing unsaved Deal Scout handoff are unchanged.

READY/PARTIAL/NEEDS_REVIEW definitions, identity/evidence thresholds, effective terms, worker fairness,
authentication, schedules, email gating and investment decisions remain unchanged. No automatic deal
creation or purchase recommendation. Missing information is explicitly neutral, not a negative
investment signal.

## Verification and Staging

Focused deterministic ordering and security tests plus inherited PostgreSQL/Testcontainers coverage.
Frontend tests execute filtering/sorting/rendering, safe links and escaping. Full Maven verification,
frontend tests/lint/build/audits, strict Fly config validation and diff review gate staging promotion.
Live staging validation reads existing packets only; it does not refresh packets or run discovery,
diligence, digest or other jobs. Production promotion requires separate approval.
