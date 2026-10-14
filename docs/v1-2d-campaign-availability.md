# V1.2D Campaign Accessibility and Public Status

## Scope and baseline

Research infrastructure only: an observed campaign link, a verified issuer page,
and current public offering status are three separate claims. None establishes
personal eligibility, transaction access, investment merit, or a recommendation.
No investment, Deal, or decision is created by verification.

Development baseline: main `bf943f11d7add754b6538f4d2ebad8050e6e9436`,
production `934f1ac3b9a8aca7db5ea20059a9e1335844519c`, staging
`c5a224601522483a4eced39e8f99a21e853f4d98`; application tree
`2ceb86d9b9c584cde07ba148592d46afc8958f19`.

The approved historical production report contained 104 offerings, 73 packets,
and no independently verified campaigns. No production database was accessed
for V1.2D. Read-only staging inventory at 2026-10-14 01:16 UTC found 132
offerings, 90 packets, 2,280 evidence rows, and 10 explicit campaign URLs. All
ten retained access outcomes were HTTP 403. Eight directory-derived campaign
records (one ACTIVE, seven RESERVATION) were listing evidence, not successful
independent campaign-page fetches. Two Wefunder URLs had official issuer-site
provenance; the other eight Republic URLs had retained listing history.

Missing exact URLs, inaccessible sources, and unknown historical provenance are
coverage limitations, not automatically parser defects or investment risks.

## Evidence model

- URL lineage: explicit SEC, official issuer outbound link, actual directory
  anchor, owner exact URL, or retained history. Syntax alone is insufficient;
  multiple conflicting links stay ambiguous. Names never generate slugs.
- Page verification: an actual permitted public HTML response must be a campaign
  page and match the issuer using existing legal-name, domain, and SEC
  intermediary evidence. Generic pages, login/challenge HTML, mismatches, and
  ambiguous identities do not verify a page.
- Status: only explicit campaign-body statements qualify. Open, reservations,
  coming soon, closing soon, funded, closed, withdrawn, terminated, and unknown
  remain distinct. Global navigation, negation, conditional and future language
  do not establish opening.

V16 adds append-only `radar_campaign_verification_attempts`, with a per-offering
unique observation fingerprint and indexed chronological history. It does not
backfill or delete legacy facts. Each observation retains the exact URL, source,
timestamp, issuer evidence, bounded status excerpt, method, outcome, explanation,
and actual HTTP request count.

Current status requires the latest successful, matching-URL observation to be
less than 72 hours old, not future dated, and not superseded by failed access.
Failed access preserves prior successful evidence as historical; HTTP 403 is
never closure. SEC lifecycle and New York-local deadline remain separate.
Conflicting SEC and public statements are disclosed without rewriting sources.
Existing authoritative terms and historical platform fallback values remain
retained. Independent page proof is required for the existing verified-page
readiness criterion; READY thresholds are not relaxed.

## Request boundaries

Only public Wefunder, Republic, and StartEngine adapters exist. Other SEC
intermediaries remain honestly unsupported. HTTPS, approved hosts, canonical
paths, public DNS checks, no credentials/ports/query transaction paths, and no
redirect following constrain requests. Login, checkout, payment, API and
transaction endpoints are excluded.

Robots policy is checked before page access (64 KB maximum). Unavailable policy
fails closed; a missing policy (404) permits otherwise safe public paths.
Applicable groups and longest-match rules follow the relevant
[RFC 9309 semantics](https://www.rfc-editor.org/rfc/rfc9309.html). Policies are
cached for at most 24 hours, failures for two hours. No robots or bot bypass is
attempted. HTTP 429 is not immediately retried and blocks further platform
requests for at least two hours or the supplied Retry-After period.

At most one request per second per platform, 10-second connection and 20-second
request limits, bounded HTML (default 2 MB, maximum 3 MB), and at most two
transient attempts remain enforced. Request accounting includes robots calls.
No new issuer crawl, private API, proxy, login reuse, CAPTCHA bypass, provider,
or scheduled job is introduced.

## UI and preservation

Packets and queue rows expose Campaign Verification independently of platform
identity, SEC reconciliation, and Research Attention. Supporting page evidence,
current/historical labels, limitations, questions, and timestamps are visible.
Open campaign requires a permitted source-backed exact URL; it is not labeled
an investable opportunity. Unsaved Deal Scout handoff carries uncertainty and
does not create a saved deal or decision.

V1.2A fairness and V1.2C priority/filter/sort code are unchanged. Access outcomes
and stale observations do not enter material notification fingerprints. A
parser-version change cannot backfill a material-change notification; existing
comparable-source notification behavior is retained. No campaign-status email
feature or notification flush is added.

## Verification and staging procedure

Synthetic transport tests inspect actual outbound destinations and counts;
no tests depend on live platform access. H2 and Docker-backed PostgreSQL suites
cover append-only persistence, replay, canonical preservation, and read models.
Frontend tests cover escaping, safe links, reservation/stale labels and existing
triage behavior. Run full Maven verification, frontend test/lint/build, both npm
audits, strict Fly validation and diff checks before deployment.

Commit the feature and require Docker-enabled CI, normally merge into staging,
and verify identical application trees. Deploy only existing staging resources
without changing secrets, origins, providers, infrastructure or schedules. V16
runs through the existing release migration command. Verify health, worker,
signed-out protection and owner login. After checking for a qualifying scheduled
run, permit at most one normal bounded staging diligence cycle (25 offerings),
not separate discovery/refresh/intake/notification jobs. Report actual evidence
coverage even if every platform remains blocked; do not manufacture success.

Production promotion requires separate approval.
