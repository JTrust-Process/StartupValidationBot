# Native Offering Intake V1

Native Offering Intake starts with current public investment opportunities instead of requiring a company to already exist in Radar:

```text
public Reg CF source -> normalized candidate -> SEC reconciliation -> Radar identity -> offering -> diligence packet
```

It runs once at the beginning of the existing autonomous-diligence job. The existing durable `autonomous-diligence` lease therefore protects both intake and packet generation without adding an overlapping scheduler.

## Public source mechanisms

- Republic: one unauthenticated `https://republic.com/companies` request. The adapter accepts canonical top-level campaign links, records live/reservation/closing evidence, and separates Reg D, Reg A+, registered funds, and SPVs from the actionable Reg CF feed.
- StartEngine: one unauthenticated `https://www.startengine.com/explore` request followed by bounded canonical `/offering/{slug}` detail requests. Explicit closed text always wins over active-looking terms. A JavaScript, CAPTCHA, cookie, or robot-verification response is `UNAVAILABLE`; the adapter does not bypass it.
- SEC EDGAR: the existing current-quarter Form C index and filing parser. The latest filing per CIK/file number controls lifecycle: `C-W` is withdrawn, `C-TR` is terminated, an elapsed deadline is closed, and a future deadline is active or closing soon. A `C` or `C/A` filed within 30 days without a parsed deadline may enter `NEEDS_REVIEW`; it is never counted as confirmed active.
- Wefunder: no native directory request is added. The system uses SEC intermediary classification, an SEC-filed or already-known canonical URL, issuer-linked evidence already captured by the application, then records direct discovery as unavailable.

The SEC publishes structured Regulation Crowdfunding data quarterly at [Crowdfunding Offerings Data Sets](https://www.sec.gov/data-research/sec-markets-data/crowdfunding-offerings-data-sets). Republic exposes its current public opportunities at [Republic Companies](https://republic.com/companies). StartEngine identifies its public offering directory at [StartEngine Explore](https://www.startengine.com/explore).

## Identity and reconciliation

Candidate identity is evaluated in this order:

1. SEC CIK
2. SEC accession or file number
3. exact canonical campaign URL
4. verified issuer domain
5. exact normalized legal/company name plus matching platform/intermediary evidence

Fuzzy-only names do not merge. Multiple plausible identities remain `NEEDS_REVIEW`. A platform candidate may be `PLATFORM_CONFIRMED` without being SEC-confirmed. Only a deterministic SEC match becomes `SEC_RECONCILED`.

Every normalized candidate is stored without full HTML. Current actionable candidates may create an offering and a Radar company with `PLATFORM_OFFERING` provenance. Closed or excluded candidates remain audit records only. Deal Scout creation remains an explicit browser action.

## Bounds and failure behavior

Defaults:

```env
NATIVE_OFFERING_INTAKE_MAX_PER_PLATFORM=25
NATIVE_OFFERING_INTAKE_MAX_DETAIL_PAGES_PER_PLATFORM=10
NATIVE_OFFERING_INTAKE_MAX_NEW_COMPANIES_PER_RUN=25
```

Platform HTTP remains HTTPS-only, host-allowlisted, redirect-rejecting, response-size limited, timeout bounded, retried at most once, and limited to one request per second per platform. One source failure degrades that source but does not discard SEC or another platform's valid results.

## Admin diagnostics

The authenticated Admin page reports, per source, directory availability, requests, detail requests, candidates, actionable candidates, inserted offerings, matched companies, rejected candidates, and sanitized errors. Overall counters include new/matched companies, new/updated offerings, deduplicated records, SEC platform classifications/reconciliations, and Review Queue counts before and after the run.

## Staging validation

Run exactly one worker-authorized `autonomous-diligence` job after deploying the additive `V14` migration. Inspect every actionable candidate and packet. Confirm canonical URLs, real issuer identity, current status, Reg CF exemption, SEC lifecycle, terms, provenance, queue status, and absence of Deal Scout writes. Do not manually add campaign URLs to satisfy a target count, and do not run the job against production during V1.1.2 review.
