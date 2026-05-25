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

- Local-first V4 frontend powered by browser `localStorage`
- Manual deal intake with expanded private-market metadata
- Paste-text import for campaign pages, Form C excerpts, offering circular text, founder notes, and user notes
- Transparent regex/string parser that suggests fields for user approval
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
- Dashboard with total deals, decision counts, platform counts, sector counts, highest scoring deals, eligibility risks, watchlist follow-up, evidence follow-up queue, document gaps, unsaved document risks, generated memos, and review-ready deals
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

## Future V5 Ideas

- OCR/import from screenshots and downloaded PDFs
- More robust SEC filing parsing for Reg CF / Reg A documents
- Platform page import helpers for Wefunder, StartEngine, Republic, Fundrise, and similar pages
- Supabase persistence and cross-device sync
- Optional local or user-approved AI summary of offering documents and founder updates
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

V4 intentionally avoids overbuilding authentication, scraping, AI, or backend infrastructure. The goal is an evidence-based manual diligence workstation first.
