# V1.2B Production Read-Only Inventory

Observed 2026-10-07T12:45:27.857342+00:00. Development baseline: main `9ab8886b99f213708ac4b4ec12ac3695db023e58`.
Production branch: `e5fada6ac4458231632a9ccf2852d0d5d385287c`; schema V15.

## Scope and Safety

Database-enforced `BEGIN; SET TRANSACTION READ ONLY;`; `transaction_read_only=on` verified.
All reads ended with `ROLLBACK`; connection closed.
102 distinct public offering records inspected, below the authorized 150 maximum.
1167 retained SEC_FILED_FACT evidence rows evaluated server-side for the requested public term phrases.

Read only: relevant column definitions for radar_offerings, radar_offering_filings, radar_diligence_evidence, radar_diligence_packets and radar_platform_campaigns; successful Flyway version; aggregate counts; public issuer, filing, intermediary, canonical terms, campaign metadata and packet status/completeness; relevant retained public SEC excerpts.
Excluded credentials, auth/session data, private Deal Scout material, personal data and email contents.
External fetches/crawls: 0. Production database mutations: 0. Jobs: 0. Emails: 0.

## Coverage

- Offerings: 102; actionable packets: 64.
- Missing raw platform label: 17. Other labels include legal names, not necessarily a canonical platform family.
- SEC-confirmed platform identity in existing packet projection: 0 (previously coupled to verified campaign).
- Missing campaign URL: 102; verified campaigns: 0.
- Missing minimum investment: 102.
- Missing valuation/cap: 102.
- Missing raised amount: 102.
- Official issuer websites: 88.
- Offerings with explicit external HTTP(S) URLs in retained official metadata: 71.
- Twelve co-issuer links are Wefunder roots; these do not establish campaign URLs.

## Intermediary Legal Names

| Filed name | Count |
| --- | --- |
| UNKNOWN | 17 |
| Vicinity LLC | 1 |
| WeVidIt, Inc. | 1 |
| Fundify Portal, LLC | 1 |
| GigaStar Portal LLC | 1 |
| OpenDeal Portal LLC | 3 |
| Wefunder Portal LLC | 20 |
| Honeycomb Portal LLC | 4 |
| ChainRaise Portal LLC | 1 |
| PicMii Crowdfunding LLC | 3 |
| DEALMAKER SECURITIES LLC | 28 |
| StartEngine Capital, LLC | 1 |
| StartEngine Primary, LLC | 13 |
| CULTIVATE CAPITAL GROUP, LLC | 1 |
| NETCAPITAL FUNDING PORTAL INC | 2 |
| CLIMATIZE EARTH SECURITIES LLC | 1 |
| NetCapital Funding Portal Inc. | 1 |
| Silicon Prairie Capital Partners, LLC | 2 |
| Jumpstart Micro, Inc. d/b/a Issuance Express | 1 |

## Intermediary CIKs

| Filed CIK | Count |
| --- | --- |
| UNKNOWN | 17 |
| 0001640943 | 2 |
| 0001664804 | 1 |
| 0001665160 | 1 |
| 0001669191 | 3 |
| 0001670254 | 20 |
| 0001705726 | 4 |
| 0001725012 | 13 |
| 0001751525 | 3 |
| 0001768367 | 1 |
| 0001788777 | 1 |
| 0001798542 | 1 |
| 0001817013 | 3 |
| 0001870874 | 1 |
| 0001872856 | 28 |
| 0001883789 | 1 |
| 0001923174 | 1 |
| 0001935609 | 1 |

## CRD and Commission File Numbers

CRD distribution (leading zeros retained as filed):
```json
{
  "226591": 2,
  "282912": 1,
  "283503": 20,
  "283596": 2,
  "283874": 1,
  "289015": 2,
  "307772": 1,
  "310171": 2,
  "Not stored": 43,
  "000315324": 27,
  "000300634": 1
}
```

Commission file numbers:
```json
{
  "007-00033": 20,
  "008-70060": 12,
  "Not stored": 29,
  "008-70756": 27,
  "007-00246": 2,
  "008-69625": 2,
  "007-00035": 2,
  "007-00007": 1,
  "008-70293": 1,
  "007-00119": 2,
  "007-00314": 1,
  "007-00167": 1,
  "007-00008": 1,
  "007-00223": 1
}
```

## Filing Forms

| Form | Current | Retained history |
| --- | --- | --- |
| C | 13 | 43 |
| C-U | 25 | 31 |
| C-W | 1 | 1 |
| C/A | 60 | 91 |
| C-TR | 3 | 4 |
| C-U/A | 0 | 0 |

## Candidate Phrase Availability

Counts are offerings with matching retained text, not verified canonical extractions.
Structured facts, retained fact values and raw excerpts were checked; complete document bodies are not separately cached.

| Phrase | Offerings |
| --- | --- |
| maturity | 0 |
| amount raised | 16 |
| discount rate | 0 |
| interest rate | 0 |
| valuation cap | 0 |
| price per share | 1 |
| securities sold | 20 |
| company valuation | 5 |
| minimum investment | 2 |
| pre-money valuation | 5 |
| post-money valuation | 0 |
| investment commitments | 1 |
| minimum subscription / purchase | 1 |

Five pre-money descriptions include conditional/early-bird pricing. They must not all become single canonical valuations.
Retained amendments include explicit changes to minimum investment/purchase amounts.
C-U text contains completed sales, interim commitments, provisional accounting and fee-inclusive proceeds; these require separate semantic types.
Zero matches do not prove the complete SEC document lacks a term. New index-first bounded SEC retrieval is a STAGING validation operation, not part of this production inventory.

## Implementation Constraints

No material contradiction with the approved design was found.
Use exact CIK, CRD/file and normalized legal names; preserve conflicts as ambiguous.
Never treat a platform root or intermediary identity as a campaign URL.
Retain conditional values as evidence and block ambiguous effective terms.
Keep READY requirements, company identity rules, refresh preservation and notification baselines unchanged.
Existing JSON evidence/provenance metadata is sufficient; no migration is planned.

