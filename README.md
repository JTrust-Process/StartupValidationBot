# StartupValidationBot / Startup Deal OS

A local-first diligence workspace for non-accredited retail investors reviewing risky startup and private-market deals.

The app helps a user manually enter deals from platforms such as Wefunder, StartEngine, Republic, Fundrise, Jarsy, Ross Pre-IPO, and similar sources, then slow down enough to score the opportunity, identify red flags, decide Pass / Watch / Invest Small, and review the thesis later.

This is a research organization tool. It is not financial, legal, or tax advice.

## Target User

- Non-accredited startup/private-market investor
- Manually reviews crowdfunding, Reg CF, Reg A, fund, SPV, and pre-IPO-style offerings
- Wants a consistent process before sharing sensitive information or sending funds
- Needs local persistence, evidence tracking, document analysis, and JSON backup before adding accounts, scraping, or AI

## Current Features

- Local-first V5 frontend powered by browser `localStorage`
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

```bash
cd frontend
npm install
npm run dev
```

Then open the local Vite URL shown in the terminal, usually:

```text
http://localhost:5173
```

To verify a production build:

```bash
cd frontend
npm run build
```

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

`VITE_*` variables are exposed to the browser bundle. Never put email API keys in a `VITE_*` variable.

### Resend Setup

1. Create a Resend account at [resend.com](https://resend.com/).
2. Create an API key in the Resend dashboard.
3. Copy `.env.example` into your server-side environment.
4. Configure server-only variables:

```bash
EMAIL_PROVIDER=resend
RESEND_API_KEY=
RESEND_FROM=Startup Deal OS <onboarding@resend.dev>
DEAL_SCOUT_EMAIL_RECIPIENT=
```

`RESEND_API_KEY` must not be prefixed with `VITE_`. It must only be read by server-side code, a cron worker, or a server-side job.

The browser can call a server endpoint only when explicitly configured:

```bash
VITE_DEAL_SCOUT_EMAIL_MODE=resend
VITE_DEAL_SCOUT_EMAIL_ENDPOINT=http://127.0.0.1:8787/api/deal-scout/digest/send
```

Run the local server-side email endpoint from `frontend/`:

```bash
npm run email:server
```

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

## Running the Digest Manually

For now, open the local app and use:

```text
Deal Scout -> Run Digest Job
```

That runs the local scout check, stores snapshots, and generates an email preview. Later, the same architecture can be moved into GitHub Actions, cron, Render cron, or a small server worker that imports the scout job and sends through SMTP or a transactional email provider.

To test the Resend path with the GridCool synthetic source:

- Add GridCool Systems as a Manual Deal Scout source.
- Run `Deal Scout -> Run Digest Job`.
- Confirm the digest preview says "research shortlist, not financial advice."
- Set `VITE_DEAL_SCOUT_EMAIL_MODE=resend` and run `npm run email:server` in a server-side terminal with `RESEND_API_KEY`, `RESEND_FROM`, and `DEAL_SCOUT_EMAIL_RECIPIENT` configured.
- Click `Send / Preview`.
- A successful send returns a Resend email id; missing configuration returns a clear failure such as `missing RESEND_API_KEY` or `missing recipient`.

## Future V6 Ideas

- OCR/import from screenshots and downloaded PDFs
- More robust SEC filing parsing for Reg CF / Reg A documents
- Platform page import helpers for Wefunder, StartEngine, Republic, Fundrise, and similar pages, only where allowed
- Supabase persistence and cross-device sync
- Optional local or user-approved AI summary of offering documents and founder updates
- Server-side email worker with SMTP/provider implementation
- Official API connectors and RSS/newsletter source adapters
- Deal alerts and follow-up reminders
- Evidence templates by deal type and exemption
- Portfolio-level exposure limits and allocation rules
- Browser extension or bookmarklet for capturing page text into local import

## Repository Structure

```text
StartupValidationBot/
  frontend/   Vite + TypeScript local-first app
  backend/    Existing Spring Boot backend kept for possible future API work
```

V5 intentionally avoids overbuilding authentication, aggressive scraping, AI investment decisions, or brokerage-style workflows. The goal is an evidence-based manual diligence workstation with a careful research scout layer.
