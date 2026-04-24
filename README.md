# StartupValidationBot

A builder-first startup investing workstation for running private-market and crowdfunding deals through a structured diligence framework.

This project started from a simple idea: most retail startup investing feels messy, hype-driven, and inconsistent. I wanted a tool that forced me to slow down, score deals the same way every time, write down my thesis, and track whether a company was actually earning more conviction over time.

Instead of relying on vibes, this app turns startup diligence into a repeatable workflow.

---

## What it does

StartupValidationBot lets me:

- add a startup deal manually
- run a quick screen
- assign a pass / watch / invest-small decision
- complete a deeper diligence pass
- save a review checkpoint
- revisit the deal later with the original thesis still intact
- track the pipeline from a dashboard
- persist everything through a real backend and database

The goal is not to automate investing.

The goal is to build a better decision process.

---

## Why I built it

I wanted something that sat between:

- random note-taking
- spreadsheet chaos
- overhyped “AI investor” ideas

The result is a personal diligence workstation:
part investment dashboard, part thesis journal, part review system.

It also became a way to pressure-test my own thinking on real companies and compare very different startup opportunities using the same framework.

---

## Core workflow

Each deal moves through a structured sequence:

### 1. Deal Intake
Capture the basic metadata:

- company name
- sector
- platform
- round type
- valuation
- minimum investment
- short description

### 2. Quick Screen
First-pass scoring across:

- business clarity
- traction evidence
- edge
- valuation sanity
- trust / transparency

### 3. Decision
Force a deliberate call:

- pass
- watch
- invest-small

### 4. Deep Diligence
Score the deeper quality of the deal across:

- business model
- market / customer
- traction quality
- competitive edge
- risk

### 5. Review Tracking
Revisit the thesis over time with:

- next review date
- review note
- thesis direction

---

## v1 vs v2

### v1
The original version was a local prototype focused on workflow design:

- deal creation
- workspace pages
- scoring flows
- review tracking
- dashboard
- local persistence

### v2
The current version upgrades the app into a real full-stack system:

- Spring Boot backend
- PostgreSQL database
- REST API-driven frontend
- backend persistence instead of local-only state
- full CRUD support for deals
- loading and error handling
- direct workspace loading by backend deal id

This version moves the project from “polished prototype” to “real application.”

---

## Features in v2

- create a deal
- view all deals
- open a deal workspace by id
- edit deal metadata
- delete a deal
- save quick-screen analysis
- save decision notes
- save deep-diligence scoring
- save review tracking
- persist data in PostgreSQL
- refresh-safe frontend loading
- dashboard metrics from backend data
- loading / failure states when backend is unavailable

---

## Tech stack

### Frontend
- Vite
- Vanilla TypeScript
- HTML / CSS

### Backend
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate

### Database
- PostgreSQL

### Tooling / Architecture
- REST API
- Maven
- Lombok
- DTO + service + controller structure

---

## Architecture

The project is split into two applications inside one repo:

```text
StartupValidationBot/
  frontend/
  backend/