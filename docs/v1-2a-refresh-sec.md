# V1.2A refresh fairness and SEC coverage

Production is frozen at V1.1.2.1. This release is staging-only until separately approved.

## Selector audit and replacement

Previously, linked CONFIRMED offerings (or LIKELY >=75, or watched issuers) were
selected by confirmed identity first, then offering `updated_at` descending, with
a default bound of 25 (maximum 100). Active/possibly-active records and unknown
records updated within 30 days qualified. Every successful diligence attempt also
updated the offering, creating a feedback loop. Never-processed, stale packets,
and unwatched ambiguous identities had no reserved turn.

The replacement uses linked CONFIRMED/LIKELY/AMBIGUOUS identities, or watched
non-rejected identities, without relaxing packet identity/READY requirements.
Terminal statuses and explicitly elapsed deadlines are excluded. Existing packets
with unknown lifecycle remain reviewable beyond the 30-day intake window.

A weighted round visits NEVER_PROCESSED, OVERDUE, UNRESOLVED, CURRENT, OVERDUE,
NEVER_PROCESSED, UNRESOLVED, OVERDUE. Each class takes its oldest diligence attempt
(creation time when absent), then offering ID. Overlapping membership is safe:
IDs are removed from all pools on selection. Empty slots spill to other pools.
With four disjoint nonempty classes and bound 25, reservations are 7/9/6/3.
Unresolved rows can also consume overdue slots. Same state/time gives same order.
Bounds below four rotate the first reserved class by UTC-independent local date
bucket, so even a deliberately tiny bound does not permanently prefer one class.

Overdue means no successful packet refresh or a refresh at least seven days old.
Failed attempts rotate behind older attempts rather than monopolizing retries.
Every attempt has a one-day cooldown. The default 25 bound is unchanged. New
arrivals cannot outrank old never-processed rows, and the stale/unresolved pools
have independent turns. A finite normal daily backlog receives eventual coverage;
this is not a throughput promise for an unbounded arrival rate.

## Additive V15

`last_resolution_attempt_at` is also written by standalone identity checks, so it
cannot establish diligence attempts, particularly failures before packet saving.
V15 adds a distinct indexed `last_diligence_attempt_at`, initialized only from
existing successful packet refreshes. No historical attempt is invented. Packet
`last_refreshed_at` remains the successful processing timestamp (not proof all
sources succeeded); source outcomes remain separate. Next retry eligibility is
the attempt plus one day. Admin shows counts and the oldest successful refresh.

V15 also adds nullable gross profit, current assets, current liabilities, equity,
and explicit period ending date to the existing financial projection. Existing
JSON evidence metadata retains exact filed labels, normalized concepts, units,
accession/document references and observation dates. No table is rewritten.

## SEC retrieval

Previously the adapter downloaded the entire SGML submission (8 MB bound) without
inspecting document metadata, and discarded financial XML leaves. It now reads
the official filing index (1 MB), prioritizes primary crowdfunding XML, then
relevant structured XML, then primary HTML. Irrelevant PDFs/decks are not fetched.
Declared oversized relevant documents are skipped; at most two relevant bodies
are attempted, each at 8 MB. There is no whole-submission fallback or attachment
crawl. Index links must stay in the same accession directory on an official
HTTPS SEC host. Redirects remain disabled, pacing stays <=2 requests/second, and
each request retains at most three retry attempts. Responses are cancelled while
streaming at the byte bound, not after unbounded buffering.

Each selected SEC-backed offering gets a bounded detail refresh, not merely facts
from this quarter's index. The durable job records SUCCESS, DOCUMENT_TOO_LARGE,
REQUEST_FAILED, PARSE_FAILED, UNSUPPORTED_STRUCTURE and financial NO_FACT_PRESENT
separately. HTTP/parse failures never mean the filing lacks a fact. Admin displays
these counters. Retries are transport attempts; document counters count logical
retrievals, not transport retries.

## Evidence safety

Explicit Form C current/prior facts support revenue, cost of goods/revenue, gross
profit, income/loss, cash, total/current assets, total/current liabilities,
short/long debt, equity and taxes paid. Gross profit is never derived. Filed
relative periods remain MOST_RECENT_FISCAL_YEAR / PRIOR_FISCAL_YEAR unless an
explicit year is present; an explicit ending date is retained independently.
No year is inferred from filing date. Unsupported/malformed monetary values are
rejected. Canonical missing fields and retained financials survive incomplete
refreshes; newer valid explicit amendment values can supersede projections.

Evidence deduplicates by classification, accession, concept, period and normalized
value, not HTML/XML URL. Different accessions and changed values remain historical.
Legacy URL-based fingerprints are compared semantically before insertion to avoid
one-time duplicate inflation. Filed campaign URLs and intermediary/issuer clues
are retained explicitly; an intermediary homepage is not a campaign URL. No URL
guessing, extra platform crawler or 403 bypass is added.

Admin email delivery now reports active Resend (or the legitimate existing server
endpoint), configuration booleans and latest delivery state/time, never API keys,
sender or recipient addresses. Packet confidence remains the existing identity
metric, accurately labeled Identity Confidence; completeness is unchanged.

## Validation gate

Run full Maven verification (PostgreSQL suites must execute in Docker-enabled CI),
frontend tests/lint/build, full and production dependency audits, Fly strict
validation and diff checks. Merge normally to staging only, deploy the exact tree
to the existing staging apps, snapshot retained public data, then run exactly one
normal bounded autonomous-diligence job. Compare selected IDs/buckets, terms,
identity, financial evidence, freshness, completeness and notification fingerprints.
Do not manufacture live fixtures or run a second job to improve the result.
