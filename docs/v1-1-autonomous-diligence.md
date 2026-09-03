# V1.1 Autonomous Diligence

V1.1 turns a confirmed or strong possible public offering into a first-pass evidence packet. It does
not create a Deal Scout workspace, make an investment decision, or submit a transaction.

## Pipeline

The worker runs `autonomous-diligence` daily at 7:45 AM `America/New_York`, after Radar and offering
discovery. The existing durable job lease prevents overlapping runs. A bounded set of confirmed,
strong likely, watched, new, or changed offerings is processed:

1. Revisit stored likely/ambiguous SEC matches and selectively enrich plausible candidates.
2. Preserve the strict identity rule: exact name plus independently matching filed domain confirms;
   exact name alone remains likely; a domain conflict is rejected.
3. Extract structured Form C terms and current/prior fiscal-period financials.
4. Resolve a known official Wefunder, Republic, or StartEngine campaign URL and extract public terms.
5. Store filed facts and issuer/platform claims as different evidence classes.
6. Reconcile like-for-like metrics and terms, create or update one packet, and queue idempotent alerts.

Repeated runs use packet, campaign, financial-period, evidence, and notification uniqueness keys.
They never create private Deal Scout data and do not require a fresh AI call. Existing public Radar
analysis may be used for synthesis; deterministic risks/questions remain available when AI is off.

## Platform Access

Platform requests accept HTTPS only and use explicit official-host allowlists. Private, loopback,
link-local, credential-bearing, fragmented, redirected, oversized, or unexpected-host URLs are
rejected. Requests default to one per second per platform, two bounded attempts, a 20-second request
timeout, and a 2 MB response limit.

Wefunder, Republic, and StartEngine support targeted official campaign URLs. Their public browse pages
do not provide a versioned offering-discovery API contract, so broad HTML crawling remains disabled.
No adapter uses login sessions, CAPTCHA bypass, private APIs, or aggressive crawling. When a
safe public discovery mechanism is unavailable, SEC-known or official campaign URLs are used and the
coverage result honestly reports `COULD NOT ESTABLISH`.

## Evidence Semantics

Evidence classifications are `SEC_FILED_FACT`, `PLATFORM_ISSUER_CLAIM`,
`ISSUER_WEBSITE_CLAIM`, `PUBLIC_REPORTING`, `DETERMINISTIC_INFERENCE`, and `AI_SYNTHESIS`.
The Review Queue never turns an issuer claim into a filed fact. Its taxonomy also keeps GMV,
bookings, users, paying customers, LOIs, pilots, amount raised, revenue, valuation, and valuation cap
distinct. Only the same metric and period with different values is a direct metric conflict.

Packet states are `PENDING`, `RESOLVING_IDENTITY`, `GATHERING_EVIDENCE`, `READY`, `PARTIAL`,
`NEEDS_REVIEW`, and `FAILED`. A platform outage produces `PARTIAL` when usable SEC evidence remains.
Unavailable facts are listed under Data Not Found with the sources checked and unresolved questions.

## Review and Deal Scout

`#/review` groups READY, NEEDS REVIEW, and PARTIAL packets. Packet detail shows offering terms,
multi-period financials, filed facts, platform claims, discrepancies, risks, bull/bear synthesis,
questions, monitoring milestones, source coverage, and freshness.

`Evaluate in Deal Scout` is an explicit handoff. It opens `#/deals/new` and prefills public facts,
financial context, risks, milestones, and evidence references. It does not save, create, overwrite, or
choose Pass, Watch, or Invest Small. Existing duplicate-workspace protections remain active.

## Resend

The Fly worker writes durable notification events before attempting delivery. The canonical server
configuration is:

```text
EMAIL_PROVIDER=resend
RESEND_API_KEY=...
RESEND_FROM=Startup Intelligence <verified-sender@example.com>
STARTUP_INTELLIGENCE_EMAIL_RECIPIENT=recipient@example.com
```

Never prefix `RESEND_API_KEY` with `VITE_`. The sender uses a 20-second timeout, at most two attempts
per send call, safe error messages, persisted Resend message IDs, and unique fingerprints. A Resend
failure leaves the packet intact and the event retryable. Events cover new confirmed offerings,
first READY packets, and material status/term changes. Existing digest endpoint variables remain
supported for compatibility.

## Configuration

- `AUTONOMOUS_DILIGENCE_CRON` (default `0 45 7 * * *`)
- `DILIGENCE_MAX_OFFERINGS_PER_RUN` (default 25, hard maximum 100)
- `DILIGENCE_PLATFORM_REQUESTS_PER_SECOND` (maximum 1)
- `DILIGENCE_PLATFORM_MAX_RESPONSE_BYTES` (default 2 MB, hard maximum 3 MB)

All collected evidence is public. Private Deal Scout notes, reviews, decisions, documents, and user
information are never sent to Groq, Router, campaign platforms, or Resend.
