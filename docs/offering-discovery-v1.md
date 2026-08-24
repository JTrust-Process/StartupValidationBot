# Offering Discovery V1

## Purpose

Startup Radar discovers companies. Offering Discovery determines whether an actual public securities offering exists. Deal Scout evaluates an offering only after the user explicitly chooses to hand it off. An offering being found is not an investment recommendation and does not affect Radar Score, Personal Relevance, Quick Screen, Deep Diligence, or a Deal Scout decision.

## Architecture

The separate `com.startupvalidationbot.offering` domain contains the official SEC client, source adapter, deterministic matcher, JDBC store, discovery service, and authenticated API. Flyway V11 adds offering, filing-history, and source-state tables. Unmatched SEC issuers are evaluated transiently and are not retained; confirmed, likely, and ambiguous matches are retained for user review.

The existing worker runs `offering-discovery` daily through the same persisted lease and idempotency mechanism as other Radar jobs. The Admin page can run the same job manually and displays sanitized source health and match counts. No AI provider is called.

## Official SEC Sources

- Baseline: the official quarterly [Crowdfunding Offerings Data Sets](https://www.sec.gov/data-research/sec-markets-data/crowdfunding-offerings-data-sets), bounded to 2025-current by default and refreshed every 30 days.
- Current quarter: the official EDGAR current-quarter `full-index` `master.idx`, filtered to `C`, `C/A`, `C-U`, `C-U/A`, `C-W`, and `C-TR`.
- Verification: the complete submission text and primary Form C XML facts are fetched only for recent issuers that first match a tracked Radar identity.
- Evidence links: canonical SEC filing index URLs under `www.sec.gov/Archives/edgar/data`.

Normal tests use local fixtures and never require SEC connectivity. A live staging smoke run requires `SEC_EDGAR_USER_AGENT` and remains bounded.

## Fair Access And URL Safety

`SEC_EDGAR_USER_AGENT` must identify the application and provide a monitored contact. Requests are restricted to official HTTPS SEC hosts, default to two requests per second, use bounded retries/backoff, reject redirects, and enforce response-size limits. This is intentionally below the SEC's published ten-request-per-second ceiling.

Platform offering URLs are retained only when they pass the existing public HTTP URL policy, including DNS checks against private, loopback, link-local, multicast, and metadata-style destinations. The application does not scrape platform pages or bypass login, bot, or rate-limit controls.

## Data And Lifecycle

`radar_offerings` stores issuer identity, CIK, current accession/file number, intermediary and normalized platform, offering and SEC URLs, Form C type/date, exemption, security and amount fields, deadline, lifecycle, match evidence, and bounded structured facts. `radar_offering_filings` preserves accession-level history without storing complete filing documents. `radar_offering_source_state` records sanitized source health.

Accession numbers are unique. Amendments and updates sharing issuer CIK plus SEC file number update one logical offering and append filing history. Older baseline data cannot replace a newer filing. Lifecycle handling is conservative:

- `C` and `C/A`: possibly active unless a future deadline supports active or a past deadline supports ended.
- `C-U` and `C-U/A`: progress/update evidence, not automatic proof of availability.
- `C-W`: withdrawn.
- `C-TR`: terminated.
- Missing evidence: unknown rather than active.

An SEC-filed offering statement is strong evidence that the filing exists. It is not SEC verification of every issuer claim.

## Deterministic Matching

Matching never calls Groq or Router. Rules are deliberately conservative:

- Previously confirmed issuer CIK: confirmed, 100.
- Exact normalized legal name or known alias plus exact issuer domain: confirmed, 100.
- Unique exact normalized legal name/alias without a conflicting domain: confirmed, 90.
- Exact domain without exact name: likely, 75.
- One prefix-style close name only: likely, 60.
- Multiple exact/close identities: ambiguous.
- Exact name with a conflicting issuer domain: rejected.
- No deterministic identity evidence: unmatched and not persisted.

Only confirmed, active or possibly-active matches receive the strong `Offering Found` badge. Likely and ambiguous records remain review items.

## Intermediary Normalization

The legal intermediary name is always stored. A small explicit map normalizes recognizable public names for Wefunder, Republic/OpenDeal Portal, StartEngine, DealMaker, Honeycomb Credit, Netcapital, MicroVentures/First Democracy, Equifund, and PicMii. The initial legal-name markers were checked against the SEC's 2026 Q2 structured dataset. Unknown legal names remain visible instead of being discarded. The map should be updated only when a legal-name relationship is confirmed from current public records.

## API And UI

Authenticated APIs:

- `GET /api/radar/offerings`
- `GET /api/radar/offerings/{id}`
- `GET /api/radar/companies/{id}/offerings`
- `GET /api/radar/admin/offering-discovery`
- `POST /api/radar/jobs/offering-discovery`

Offering reads require the HttpOnly browser session. Unsafe browser calls require the exact configured origin. The existing worker token may invoke the leased Radar job endpoint, but cannot read offering data or access Deal Scout.

The VentureLens UI adds Offerings under Diligence, confirmed/possible/historical views, confirmed Radar badges, company-profile filing evidence, and explicit Deal Scout handoff. Handoff prefills only public facts and creates nothing until the user submits the existing New Deal form. `offeringDiscoveryId` and the offering URL support duplicate warnings; an SEC filing evidence claim is created only with the user-submitted workspace.

## Configuration

```env
SEC_EDGAR_USER_AGENT=StartupValidationBot/1.0 monitored-contact@example.com
SEC_EDGAR_REQUESTS_PER_SECOND=2
SEC_EDGAR_RECENT_LIMIT=40
SEC_CF_BASELINE_START_YEAR=2025
SEC_CF_BASELINE_MAX_DATASETS=8
SEC_CF_BASELINE_REFRESH_DAYS=30
OFFERING_DISCOVERY_CRON=0 15 7 * * *
```

## Known Limitations

- Regulation Crowdfunding/Form C is the only fully automated offering source in V1.
- Regulation A support is deferred to Stage 8B.
- Platform-native adapters are deferred until an official API or stable, permitted public feed is available.
- Form C does not consistently provide minimum investment, live amount raised, valuation/cap, or a direct campaign URL; unknown values stay unknown.
- Ambiguous legal identities require manual review and never receive a confirmed badge.
- No offering email alert is added in V1; material lifecycle changes use existing Radar company-change records.
