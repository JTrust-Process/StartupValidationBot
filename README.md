# Startup Intelligence / Venture Radar

A personal startup intelligence platform with a preserved local-first diligence workspace for non-accredited retail investors reviewing risky startup and private-market deals.

The Radar discovers and monitors startups across public, permitted sources. Deal Scout separately helps a user evaluate accessible private-market opportunities from platforms such as Wefunder, StartEngine, Republic, Fundrise, Jarsy, and similar sources. An interesting company is not automatically a good investment, so Radar Score, Personal Relevance, and Deal Scout investment scoring remain separate.

This is a research organization tool. It is not financial, legal, or tax advice.

## Target User

- Non-accredited startup/private-market investor
- Startup ecosystem researcher who wants an automated shortlist of companies and themes worth understanding
- Manually reviews crowdfunding, Reg CF, Reg A, fund, SPV, and pre-IPO-style offerings
- Wants a consistent process before sharing sensitive information or sending funds
- Needs local persistence, evidence tracking, document analysis, and JSON backup before adding accounts, scraping, or AI

## Current Features

- Persistent PostgreSQL Startup Radar with Flyway migrations
- Modular discovery adapters for configurable RSS/Atom feeds, the official Product Hunt API, and manual sources
- Conservative YC directory entry retained as manual-only rather than relying on an undocumented scraper
- Company deduplication by normalized domain, normalized name, and legal-name aliases
- Company snapshots, source citations, watchlist status, meaningful change labels, and duplicate-safe discoveries
- Provider-neutral Radar AI interface with Groq as the first implementation
- `openai/gpt-oss-20b` for bounded routine enrichment and `openai/gpt-oss-120b` only for protected manual Deep Dives
- Strict JSON-schema output validation, bounded retries, persisted provider attempts, stable caching, and deterministic fallback
- Public-data-only provider payload that is isolated from Deal Scout, offering documents, notes, and browser storage
- Separate 0-100 Radar Score and Personal Relevance score; neither is an investment recommendation
- Public Radar filters for search, sector/theme, score, recency, and sort order
- Watchlist, 30-day trend clustering, and a structured company deep-dive workspace
- Idempotent discovery, watchlist, trend, and combined-digest jobs with retry handling and persisted run logs
- Combined weekly digest with new startups, watchlist updates, and existing Deal Scout candidates
- Local-first Deal Scout frontend powered by browser `localStorage`
- Manual deal intake with expanded private-market metadata
- Paste-text import for campaign pages, full-page copy/paste dumps, Form C excerpts, offering circular text, founder notes, and user notes
- Clean Import and Lazy Import modes for focused text versus messy platform page dumps
- Raw import records saved per deal, preserving original pasted text separately from parser-cleaned text
- Lazy Import preprocessing for repeated lines, page navigation, buttons, footer text, and short UI-only noise
- Section detection for core terms, company description, traction claims, financials, risk factors, fees/use of proceeds, legal/eligibility, and noise
- Transparent regex/string parser that suggests fields with High / Medium / Low confidence for user approval
- Editable import review panel where suggestions can be accepted, batch-accepted, ignored, or changed before creating the deal
- Fields for platform, sector, offering URL, minimum investment, valuation/cap, amount raised, revenue status, eligibility, exemption, security type, liquidity, lockup, fees, thesis, risk, next milestone, and decision
- Eligibility warning banners for accredited-only, Reg D, unclear eligibility, and unknown exemptions
- Red flag checklist with green/yellow/red risk status
- Suggested red flags from risk-heavy language, with manual accept/ignore controls
- Risk language warning when major risk text exists but no red flags are checked
- Evidence / claim tracker with source type, source text, strength, verification, and notes
- Per-deal document library for pasted campaign pages, Form C, Form C-A, offering circulars, SAFE agreements, subscription agreements, investor decks, press, notes, and local `.txt` / `.md` text files
- Transparent document extraction suggestions for offering terms, financial snippets, fees, use of proceeds, transfer restrictions, control terms, and seniority language
- Risk factor extraction for going concern language, losses, limited operating history, dilution, senior securities, founder control, related-party issues, regulatory risk, illiquidity, no public market, platform fees, and conflicts
- Editable deal memo generator with company overview, offering terms, thesis, failure case, evidence summary, red flags, valuation notes, access, liquidity, unanswered questions, recommendation, check size, and next review trigger
- Follow-up question generator based on missing fields, weak evidence, red flags, and document risks
- Data Confidence score from 0-100 separate from investment attractiveness
- Quick Screen scoring
- Deep Diligence scoring
- Final recommendation bands:
  - 0-49: Pass
  - 50-69: Watch
  - 70-84: Small check only
  - 85+: High conviction, still risky
- Suggested check-size guardrails:
  - Pass: $0
  - Watch: $0
  - Small check only: $100-$250
  - High conviction: $250-$500 max
- Dashboard with total deals, decision counts, platform counts, sector counts, highest scoring deals, eligibility risks, watchlist follow-up, evidence follow-up queue, document gaps, import-review health, unsaved document risks, generated memos, and review-ready deals
- Deal Scout watchlist sources for Republic, Wefunder, StartEngine, DealMaker, Fundrise, Jarsy, Ross Pre-IPO, SEC EDGAR, Manual, and Other sources
- Conservative source monitor architecture that prefers manual pasted text, public SEC/public URLs when feasible, and never bypasses logins, paywalls, captchas, rate limits, anti-bot protections, robots.txt, or Terms of Service
- Deal snapshots for monitored sources with amount raised, investor count, deadline, terms, exemption, eligibility, data confidence, red flags, raw text hash, and notable changes
- Review-priority scoring from theme fit, non-accredited access, evidence quality, traction, valuation sanity, red flags, and urgency
- User Deal Scout preferences for preferred themes, excluded sectors, max minimum investment, max red flags, eligibility requirements, exemption requirements, preferred security types, weekly digest timing, and email recipient
- Weekly digest generator with 3-5 companies to consider researching, framed as a research shortlist rather than financial advice
- Development email preview mode plus server-only Resend email sending support, with credentials kept out of the browser
- Search and filters by company name, platform, sector, decision, investor eligibility, and offering exemption
- JSON export/import for backups and moving local research between browsers

## How to Run Locally

Radar requires PostgreSQL and the Spring backend. Copy the templates to local `.env` files without committing them, or set equivalent shell environment variables. At minimum configure `DATABASE_URL`, the worker-only `RADAR_RUN_TOKEN`, `RADAR_ADMIN_PASSWORD_HASH`, `RADAR_BROWSER_ORIGIN`, and `VITE_RADAR_API_BASE_URL`. AI is optional and disabled by default.

Generate the admin password hash locally. The password is read without echo and only the PBKDF2-SHA256 hash is printed:

```powershell
cd backend
.\mvnw.cmd -q -DskipTests compile
java -cp target/classes com.startupvalidationbot.radar.auth.RadarPasswordHashTool
```

Start the Java 21 backend:

```powershell
cd backend
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/startup_validation_bot"
$env:RADAR_RUN_TOKEN="replace-with-a-long-random-token"
$env:RADAR_ADMIN_PASSWORD_HASH='paste-the-generated-hash'
$env:RADAR_BROWSER_ORIGIN="http://localhost:5173"
$env:RADAR_AUTH_SECURE_COOKIE="false"
.\mvnw.cmd spring-boot:run
```

Start the frontend in a second terminal:

```powershell
cd frontend
npm install
$env:VITE_RADAR_API_BASE_URL="http://localhost:8080/api/radar"
npm run dev
```

Then open the local Vite URL shown in the terminal, usually:

```text
http://localhost:5173
```

To run the frontend quality checks and production build:

```bash
cd frontend
npm test
npm run lint
npm run build
```

The Diligence and Deal Scout routes continue to work from browser `localStorage` if the backend is offline. Radar, Watchlist, Trends, and server jobs require the backend.

Run backend tests and package with Java 21:

```bash
cd backend
./mvnw test
./mvnw package
```

## Startup Radar Architecture

New Radar data lives in provider-independent PostgreSQL tables managed by Flyway. Existing Deal Scout and diligence records remain in browser `localStorage`; no silent migration or destructive conversion occurs. Use the existing JSON export before moving browsers or devices. A later authenticated sync can import those backups into server storage without changing their current schema.

General company, trend, and source-status reads use sanitized public DTOs. They omit Personal Relevance, watch/ignore state, watchlist notes, next-review dates, source configuration, raw snapshots, and source excerpts. Protected reads and mutations accept either a server-side browser session or the separate worker bearer token. `RADAR_RUN_TOKEN` remains server-only and is never sent to Vite.

Production browser administration uses the Vercel function at `/api/radar/*` as a same-origin reverse proxy to Fly. The proxy forwards the HttpOnly session cookie but strips browser-supplied `Authorization` and `X-Radar-Run-Token` headers. Spring stores only SHA-256 session-token hashes in PostgreSQL, enforces a fixed expiration, validates the exact `RADAR_BROWSER_ORIGIN` for login/logout and mutations, and rate-limits failed logins. Cookies are Secure, HttpOnly, and SameSite=Strict in production. Direct Vercel-to-Fly browser cookie authentication is intentionally unsupported because cross-site cookie blocking makes it unreliable.

The authenticated Admin page provides system status, source management, manual public intake, protected job runs, logout, and Radar JSON export. Authenticated company pages provide Watch, Unwatch, Ignore, Restore, and Deep Dive controls. Sessions expire after `RADAR_AUTH_SESSION_HOURS` and can be revoked immediately with logout.

Discovery jobs fetch only configured public sources. Product Hunt uses its official GraphQL API and requires `PRODUCT_HUNT_TOKEN`. Add RSS/Atom URLs with `RADAR_RSS_URLS` or the protected source API. YC remains manual-only: YC exposes public company pages, but its official `robots.txt` disallows `/companies?*` query-directory crawling and no supported company-discovery API/feed has been identified. The adapters do not bypass authentication, paywalls, captchas, rate limits, robots rules, or source terms.

Every discovery is deduplicated, snapshotted, scored, and linked to a source. Source-supported facts and analyst inferences are displayed separately. Deterministic structured analysis remains the baseline and owns the final Radar and Personal Relevance scores.

## Optional Groq Radar Analysis

Groq is an enhancement, never a runtime dependency. Configure these server-side variables in `backend/.env` or the host secret manager:

```env
RADAR_ENABLE_AI=true
AI_PROVIDER=groq
GROQ_API_KEY=
RADAR_AI_MODEL=openai/gpt-oss-20b
RADAR_DEEP_DIVE_MODEL=openai/gpt-oss-120b
RADAR_AI_MAX_ITEMS_PER_RUN=25
RADAR_AI_MAX_RETRIES=2
RADAR_AI_PROMPT_VERSION=radar-v2
RADAR_AI_SCHEMA_VERSION=radar-analysis-v1
```

Never prefix `GROQ_API_KEY` or `RADAR_RUN_TOKEN` with `VITE_`. The provider receives only company name/domain/website, externally sourced public description, sector/categories, headquarters/founding year, public accelerator/launch/funding/investor/traction text, eligible public RSS/Product Hunt source URLs/excerpts, and source count. Manual Radar excerpts are excluded from provider text. Deal Scout pasted text, Form C contents, offering documents, user notes, evidence records, localStorage, email addresses, keys, tokens, and private investment research are never joined into the provider payload.

Routine runs use `openai/gpt-oss-20b`. A shared per-run budget is consumed only on cache misses, prioritizing newly discovered companies, watched companies with meaningful public changes, then other changed/stale companies. With the default setting, a daily run makes at most 25 AI calls and usually fewer. Protected manual Deep Dive requests use `openai/gpt-oss-120b`; saved results are reused while the public input, provider, model, prompt version, and schema version remain unchanged.

Groq output must satisfy the strict typed analysis schema. Invalid output is retried within `RADAR_AI_MAX_RETRIES`; credential errors, model errors, timeouts, rate limits, malformed responses, and provider outages are recorded without secrets. The affected company then receives cached or newly generated deterministic analysis, and the worker continues.

## V2 Roadmap Completed

- Migrated the frontend experience away from backend-required boot into browser `localStorage`
- Expanded the deal model for non-accredited startup/private-market diligence
- Added red flag tracking and risk status
- Added legal/access warning banners
- Added final recommendation and check-size guardrails
- Improved dashboard coverage
- Added search and filters
- Added JSON backup export/import
- Updated docs around the local-first V2 product direction

## V3 Roadmap Completed

- Added paste-text deal import without scraping, login, backend sync, or external AI APIs
- Added parser suggestions for core deal fields and risk snippets
- Added raw deal text storage for each deal
- Added evidence claim records for campaign pages, Form C, offering circulars, founder statements, press, user notes, and other sources
- Added suggested red flags from risk keywords such as unclear, unknown, accredited-only, Reg D, 506(c), pre-IPO, guaranteed, no revenue, projected, illiquid, going concern, losses, founder control, senior to SAFE, hidden fees, and wire quickly
- Added the unscored risk warning for deals with risk-heavy language but zero checked red flags
- Added Data Confidence scoring separate from Final Score
- Added dashboard follow-up queue for unclear eligibility, unknown exemption, missing offering docs, unclear revenue, unclear fees, low confidence, next milestones, and due reviews

## V4 Roadmap Completed

- Added a per-deal document library for pasted text records
- Added document extraction using transparent regex/string matching
- Added accept-to-profile and save-as-evidence flows for extracted document items
- Added risk factor extraction from documents
- Added conversion of document risks into red flags, evidence claims, or main-risk notes
- Added editable deal memo generation and saving
- Added follow-up question generation
- Added dashboard cards for missing documents, unsaved document risks, low data confidence, generated memos, and ready-for-review deals

## Import Workflow Upgrade Completed

- Added Clean Import for focused deal sections and Lazy Import for noisy full-page copy/paste dumps
- Preserved original raw pasted text as deal import records in localStorage
- Added parser-cleaned text, detected sections, confidence-labeled field suggestions, and suggested red flags
- Kept all parser output user-approved: no deal fields are overwritten automatically
- Added dashboard cards for imports needing review, low-confidence suggestions, raw imports without accepted fields, and total raw imports

## V5 Roadmap Completed

- Added Deal Scout watchlist/source records with enabled status, last check status, notes, pasted text, and optional linked deals
- Added modular source fetching and parsing functions:
  - Manual/user-pasted sources work fully
  - Public URL fetches are conservative and may require manual paste when blocked by CORS, access controls, or source rules
  - SEC EDGAR is represented as a source type for public filing URLs
- Added deal snapshots and change detection for amount raised, investor count, deadlines, terms, exemption, security type, new risk language, confidence shifts, document language, and reservation/live-offering language
- Added review-priority scoring and preference filters
- Added a Deal Scout page for sources, preferences, candidate rankings, notable changes, manual-paste needs, and digest previews
- Added dashboard Deal Scout cards and candidate/change summaries
- Added weekly email digest generation with research-only language and a required financial-advice disclaimer
- Added preview-only email behavior in development and server-only Resend sending support for production/server runtimes
- Added a manual digest job function that can later be invoked by GitHub Actions, cron, Render cron, or another worker
- Added V6 automated server-side digest support from configured public/source-text inputs, with a protected run endpoint for cron jobs

## Source Monitoring Rules

- Use official APIs, public filings, user-saved URLs, user-pasted page text, RSS/newsletter sources, and SEC EDGAR data where available.
- Do not bypass logins, paywalls, captchas, rate limits, anti-bot protections, robots.txt, or source Terms of Service.
- If a public source cannot be fetched safely, paste allowed text into the Manual source field or Deal Text Import.
- Treat scout output as a triage queue: deals to review, not companies to invest in.

## Email Digest Setup

Local/browser development is preview-only:

```bash
VITE_DEAL_SCOUT_EMAIL_MODE=preview
```

For local setup, copy `frontend/.env.example` to `frontend/.env`. Vite reads the `VITE_*`
values from that file, and the local Deal Scout email server also loads the server-only
values from the same file.

`VITE_*` variables are exposed to the browser bundle. Never put email API keys in a `VITE_*` variable.

### Resend Setup

1. Create a Resend account at [resend.com](https://resend.com/).
2. Create an API key in the Resend dashboard.
3. Copy `frontend/.env.example` to `frontend/.env` for local testing, or set the same variables in your production server-side environment.
4. Configure server-only variables:

```bash
EMAIL_PROVIDER=resend
RESEND_API_KEY=
RESEND_FROM=Startup Deal OS <onboarding@resend.dev>
DEAL_SCOUT_EMAIL_RECIPIENT=
DEAL_SCOUT_ALLOW_CLIENT_RECIPIENT=false
DEAL_SCOUT_ALLOWED_ORIGIN=http://127.0.0.1:5173,http://localhost:5173
DEAL_SCOUT_RUN_TOKEN=use-a-long-random-token
```

`RESEND_API_KEY` must not be prefixed with `VITE_`. It must only be read by server-side code, a cron worker, or a server-side job.
Set `DEAL_SCOUT_ALLOWED_ORIGIN` to the deployed frontend origin when the app is hosted, for example `https://your-app.example.com`. For local work, both `127.0.0.1` and `localhost` are allowed because Vite may be opened with either hostname.
Keep `DEAL_SCOUT_ALLOW_CLIENT_RECIPIENT=false` for hosted use so the server sends only to `DEAL_SCOUT_EMAIL_RECIPIENT`.
Set `DEAL_SCOUT_RUN_TOKEN` before enabling email sending. Both `/api/deal-scout/digest/send` and `/api/deal-scout/digest/run` reject unauthenticated requests.

The browser can call the authenticated send endpoint only when explicitly configured:

```bash
VITE_DEAL_SCOUT_EMAIL_MODE=resend
VITE_DEAL_SCOUT_EMAIL_ENDPOINT=http://127.0.0.1:8787/api/deal-scout/digest/send
```

Enter `DEAL_SCOUT_RUN_TOKEN` in the Scout page only when sending manually. The value is
attached as a bearer token for that request, then cleared; it is not bundled, logged, or saved
in localStorage. Never create `VITE_DEAL_SCOUT_RUN_TOKEN`.

Run the local server-side email endpoint from `frontend/`:

```bash
npm run email:server
```

For a hosted Node service, use `npm run start:email`. The email server reads the platform-provided `PORT` automatically and binds to `0.0.0.0` when `PORT` is present.

You can also send a prepared JSON payload through the server-side script:

```bash
npm run email:send -- --file=deal-scout-digest.json
```

Example payload:

```json
{
  "to": "you@example.com",
  "subject": "Weekly Startup Deal Scout - deals to review",
  "text": "This is a research shortlist, not financial advice. Review offering documents and risks before investing."
}
```

## Automated Scout Digest

V6 adds a server-side digest runner so you do not need to open the browser every week. Because the main app is still localStorage-first, the automated runner uses server-side discovery settings instead of reading your browser data.

Turn on discovery so the bot searches broad feeds/queries for leads:

```bash
DEAL_SCOUT_ENABLE_DISCOVERY=true
DEAL_SCOUT_DISCOVERY_QUERIES=Reg CF startup raising Republic;Regulation Crowdfunding startup Wefunder;StartEngine Reg CF offering startup
DEAL_SCOUT_PREFERRED_THEMES=AI infrastructure,data centers,energy,fintech,automation
```

Optional discovery feeds:

```bash
DEAL_SCOUT_DISCOVERY_RSS_URLS=https://example.com/startup-deals.rss
DEAL_SCOUT_ENABLE_SEC_EDGAR_DISCOVERY=true
DEAL_SCOUT_DISCOVERY_MAX_ITEMS=25
DEAL_SCOUT_DISCOVERY_FETCH_LINKS=false
DEAL_SCOUT_INCLUDE_DISCOVERY_LEADS=true
```

`DEAL_SCOUT_DISCOVERY_FETCH_LINKS=false` means the bot ranks the RSS/search result text itself rather than crawling the linked page. Keep it false unless a source clearly allows automated public fetching.

You can still add specific sources as optional extras:

```bash
DEAL_SCOUT_SOURCE_URLS=https://example.com/deal-one,https://example.com/deal-two
DEAL_SCOUT_AUTOMATION_SOURCES_JSON=[{"sourceType":"REPUBLIC","url":"https://republic.com/example","companyName":"Example Co","enabled":true}]
```

Automation filters and trigger token:

```bash
DEAL_SCOUT_MAX_MINIMUM_INVESTMENT=500
DEAL_SCOUT_MAX_RED_FLAGS=6
DEAL_SCOUT_REQUIRE_NON_ACCREDITED=true
DEAL_SCOUT_REQUIRE_REG_CF_OR_REG_A=true
DEAL_SCOUT_FETCH_DELAY_MS=750
DEAL_SCOUT_RUN_TOKEN=use-a-long-random-token
```

Preview from `frontend/` without sending:

```bash
npm run scout:digest:preview
```

Send from `frontend/`:

```bash
npm run scout:digest:send
```

Trigger the hosted server endpoint manually when diagnosing the legacy Deal Scout service:

```bash
curl -X POST \
  -H "Authorization: Bearer $DEAL_SCOUT_RUN_TOKEN" \
  https://startupvalidationbot.onrender.com/api/deal-scout/digest/run
```

The `.github/workflows/deal-scout-digest.yml` workflow is retained as a manual diagnostic only. It has no production schedule. If you run it manually, add:

- GitHub repository variable `DEAL_SCOUT_DIGEST_RUN_URL` = `https://startupvalidationbot.onrender.com/api/deal-scout/digest/run`
- GitHub repository secret `DEAL_SCOUT_RUN_TOKEN` = the same token configured on the Render email server

The automated runner only searches configured RSS/search feeds, optional SEC EDGAR Form C feed results, specific public URLs you provide, or source text you configure. It does not bypass logins, paywalls, captchas, robots.txt, rate limits, anti-bot systems, or source terms.

## Running the Digest Manually

For now, open the local app and use:

```text
Deal Scout -> Run Digest Job
```

That runs the local scout check, stores snapshots, and generates an email preview. Use the automated server-side runner above when you want the digest to run while the browser is closed.

To test the Resend path with the GridCool synthetic source:

- Add GridCool Systems as a Manual Deal Scout source.
- Run `Deal Scout -> Run Digest Job`.
- Confirm the digest preview says "research shortlist, not financial advice."
- Set `VITE_DEAL_SCOUT_EMAIL_MODE=resend` and run `npm run email:server` in a server-side terminal with `RESEND_API_KEY`, `RESEND_FROM`, `DEAL_SCOUT_EMAIL_RECIPIENT`, and `DEAL_SCOUT_RUN_TOKEN` configured.
- Enter the server token in the Scout page and click `Send / Preview`.
- A successful send returns a Resend email id; missing configuration returns a clear failure such as `missing RESEND_API_KEY` or `missing recipient`.

## Fly.io Web And Worker Deployment

The root `Dockerfile` uses Java 21. `fly.toml` defines separate `web` and `worker` process groups; only `web` receives HTTP traffic. The worker runs Spring schedules for discovery, watchlist refresh, trends, and the weekly combined digest. A Fly release command runs Flyway with Hibernate DDL disabled before either process is replaced. Migrations are additive, checksum-validated, and Flyway clean is disabled.

Readiness uses `/actuator/health/readiness` and includes database connectivity; health details are never exposed. SIGTERM triggers graceful HTTP and scheduler shutdown with a 60-second drain. PostgreSQL-backed job leases prevent overlap across processes, while `(job_type, idempotency_key)` uniqueness keeps scheduled retries idempotent. A crashed lease expires after `RADAR_JOB_LEASE_MINUTES`; keep one worker for predictable scheduling even though the database lock provides a second guardrail.

Staging deployment procedure:

1. Create a dedicated Fly staging app and Postgres database, then set the `app` value in `fly.toml` to that staging app.
2. Generate a password hash with `RadarPasswordHashTool`; keep the plaintext password only in your password manager.
3. Set the Fly secrets below. Use the exact Vercel staging origin with no path, slash, or hash route.
4. In the Vercel project rooted at `frontend`, set server-only `RADAR_BACKEND_ORIGIN=https://<fly-staging-app>.fly.dev`. Set `VITE_RADAR_API_BASE_URL=/api/radar` or remove it so the same-origin default is used.
5. Run `fly deploy --config fly.toml`. The release command must succeed before web/worker replacement proceeds.
6. Run `fly scale count web=1 worker=1`, then verify `/actuator/health/readiness`, browser login/logout, Admin system status, one digest preview, and a JSON export.

```bash
fly secrets set DATABASE_URL="postgres://..." \
  RADAR_RUN_TOKEN="..." \
  RADAR_ADMIN_PASSWORD_HASH='pbkdf2-sha256$...' \
  RADAR_BROWSER_ORIGIN="https://your-staging-frontend.vercel.app" \
  APP_ALLOWED_ORIGINS="https://your-staging-frontend.vercel.app" \
  RADAR_AUTH_SECURE_COOKIE="true" \
  RADAR_AUTH_SAME_SITE="Strict" \
  PRODUCT_HUNT_TOKEN="..." \
  RADAR_ENABLE_AI="true" \
  AI_PROVIDER="groq" \
  GROQ_API_KEY="..." \
  RADAR_AI_MODEL="openai/gpt-oss-20b" \
  RADAR_DEEP_DIVE_MODEL="openai/gpt-oss-120b" \
  DEAL_SCOUT_RUN_URL="https://your-email-service/api/deal-scout/digest/run" \
  DEAL_SCOUT_RUN_TOKEN="..." \
  RADAR_EMAIL_SEND_URL="https://your-email-service/api/deal-scout/digest/send" \
  RADAR_EMAIL_TOKEN="..."
fly deploy
fly scale count web=1 worker=1
```

Required staging secrets are `DATABASE_URL`, `RADAR_RUN_TOKEN`, `RADAR_ADMIN_PASSWORD_HASH`, `RADAR_BROWSER_ORIGIN`, and `APP_ALLOWED_ORIGINS`. `GROQ_API_KEY`, Product Hunt, Deal Scout, and email secrets are required only when those integrations are enabled. Never put any of these secrets in `VITE_*`. See Fly's [process group documentation](https://fly.io/docs/launch/processes/).

The worker posts a preview request to the authenticated Deal Scout `/digest/run` endpoint, combines those candidates with Radar and Watchlist content, and sends the final message through the existing authenticated Resend `/digest/send` endpoint. The digest says companies and deals to review, never investments to buy.

## Radar Status, Fixture, And Export

`GET /api/radar/admin/status` is authenticated and reports database health, latest jobs, recent failures, discovery and AI counters, configured provider/model names, and integration-configured booleans. It never returns environment values, URLs containing credentials, API keys, tokens, database URLs, cookies, or authorization headers.

For a deterministic development demo, start the backend with `RADAR_DEMO_FIXTURE_ENABLED=true` and `RADAR_ENABLE_AI=false`, then invoke the protected fixture once:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/radar/admin/fixtures/synthetic" `
  -Headers @{ Authorization = "Bearer $env:RADAR_RUN_TOKEN" }
```

The fixture performs manual ingestion, normalization, domain deduplication, persistence, two snapshots, deterministic Radar and Personal scoring, Watchlist, deterministic Deep Dive, trend clustering, and digest preview. It uses only `.example` URLs and never calls Groq or Product Hunt. Keep `RADAR_DEMO_FIXTURE_ENABLED=false` in staging and production. `RadarEndToEndFixtureIntegrationTest` runs the same workflow automatically.

The Admin page downloads `GET /api/radar/admin/export` as `startup-radar-export.json`. The export includes companies, normalized discovery references and hashes, public source metadata, sanitized snapshots, analyses, watchlist, trends, and research-source references. It excludes raw discovery text, source configuration JSON, sessions, password hashes, worker tokens, credentials, database settings, AI attempt errors, email settings, and all unrelated Deal Scout/diligence data.

## Next Radar Iterations

- OCR/import from screenshots and downloaded PDFs
- More robust SEC filing parsing for Reg CF / Reg A documents
- Platform page import helpers for Wefunder, StartEngine, Republic, Fundrise, and similar pages, only where allowed
- Authenticated multi-device access and encrypted user settings
- Optional, separately consented AI summary of offering documents; private diligence remains outside Radar AI
- Server-side email worker with SMTP/provider implementation
- More official accelerator, funding-announcement, and VC portfolio connectors
- Deal alerts and follow-up reminders
- Evidence templates by deal type and exemption
- Portfolio-level exposure limits and allocation rules
- Browser extension or bookmarklet for capturing page text into local import

## Repository Structure

```text
StartupValidationBot/
  frontend/   Vite + TypeScript Radar UI plus local-first Deal Scout/Diligence
  backend/    Java 21 Spring Boot Radar API, PostgreSQL migrations, and scheduled worker
  Dockerfile  Fly web/worker image
  fly.toml    Fly process-group and health-check configuration
```

The platform intentionally avoids aggressive scraping, AI investment decisions, brokerage actions, or automated investing. It is a research system, not financial advice.
