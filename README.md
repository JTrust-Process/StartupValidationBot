# StartupValidationBot

A builder-first startup investing workstation for running private-market and crowdfunding deals through a structured diligence framework.

This project started from a simple idea: most retail startup investing feels messy, hype-driven, and inconsistent. I wanted a tool that forced me to slow down, score deals the same way every time, write down my thesis, and track whether the company actually earned more conviction over time.

Instead of relying on vibes, this app turns startup investing into a repeatable workflow.

## What it does

StartupValidationBot lets me:

- add a startup deal manually
- run a quick screen
- assign a pass / watch / invest-small decision
- complete a deeper diligence pass
- save a review checkpoint
- revisit the deal later with the original thesis still intact
- track the pipeline from a dashboard

The goal is not to automate investing.
The goal is to build a better decision process.

## Core workflow

Each deal moves through a structured sequence:

1. **Deal Intake**
   - company name
   - sector
   - platform
   - round type
   - valuation
   - minimum investment
   - short description

2. **Quick Screen**
   - business clarity
   - traction evidence
   - edge
   - valuation sanity
   - trust / transparency

3. **Decision**
   - pass
   - watch
   - invest-small

4. **Deep Diligence**
   - business model
   - market / customer
   - traction quality
   - competitive edge
   - risk

5. **Review Tracking**
   - next review date
   - review note
   - thesis direction

## Why I built it

I wanted something that sat between:
- random note-taking
- spreadsheet chaos
- overhyped “AI investor” ideas

The result is a personal diligence workstation:
part investment dashboard, part thesis journal, part review system.

This also became a way to test my own thinking on real companies and compare how different startup opportunities stack up when forced through the same framework.

## Tech stack

- **Vite**
- **Vanilla TypeScript**
- **HTML / CSS**
- **localStorage** for persistence

I intentionally built this without a heavy framework so I could focus on app structure, state flow, rendering, and design from first principles.

## Features in v1

- deal creation flow
- deals table
- deal workspace page
- quick-screen scoring
- decision capture
- deep-diligence scoring
- review tracking
- dashboard metrics
- local persistence
- custom command-center design system

## Design direction

The UI is styled as a blend of:
- terminal finance dashboard
- sports scoreboard energy
- darker command-center workspace

I wanted it to feel more like a real decision desk than a generic CRUD app.

## What I learned building it

This project pushed me on:
- structuring frontend state without React
- designing a multi-step workflow from scratch
- building reusable UI patterns in vanilla TypeScript
- thinking like both a product designer and an end user
- translating investing judgment into a usable system

It also reinforced something important:

A lot of good products are not about replacing judgment.
They are about improving the quality of judgment.

## Roadmap

### Next likely upgrades
- deals table filtering and sorting
- cleaner display labels and empty states
- export / import of saved deal data
- backend persistence
- authentication
- multi-device sync

### Later-stage ideas
- AI-assisted deal memo drafting
- structured extraction from pasted deal pages
- change tracking between original thesis and later updates
- watchlist reminders
- more agent-like review workflows

## Status

**v1 is complete as a usable personal diligence tool.**

The current version is strong enough to run real companies through a repeatable evaluation process and preserve the results across sessions.

## Repo purpose

This repo is both:
- a usable personal investing workflow tool
- a showcase project for product thinking, frontend architecture, and practical decision-system design