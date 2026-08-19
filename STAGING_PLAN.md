# Startup Radar - Staging Configuration Plan

## Current status

The reviewed Phase 2 implementation, migrations V4-V6, and B1-B3 staging hardening are present on
`codex/startup-radar-v1`. The branch has passed backend, frontend, lint, build, Fly configuration,
and Docker-enabled PostgreSQL verification. This document is a deployment and staging-validation
runbook only. No staging deployment has been performed yet.

---

## 1. Staging configuration

Nothing below contains a secret value. Set secrets with `fly secrets set` / the Vercel dashboard.

### Fly — REQUIRED

| Variable | Value | Notes |
|---|---|---|
| `DATABASE_URL` | from `fly postgres attach` | Fly sets `postgres://`; `DatabaseUrlEnvironmentPostProcessor` converts it to JDBC |
| `RADAR_ADMIN_PASSWORD_HASH` | PBKDF2 hash | generate it with the verified standalone Java command in section 3 |
| `RADAR_BROWSER_ORIGIN` | `https://<staging>.vercel.app` | exact origin, no path, no trailing slash, no `#/radar` |
| `APP_ALLOWED_ORIGINS` | `https://<staging>.vercel.app` | must contain the value above or `/admin/status` reports `browserAdmin: false` |
| `RADAR_RUN_TOKEN` | 64+ random chars | server-to-server only; never reaches the browser |
| `RADAR_AUTH_SECURE_COOKIE` | `true` | |
| `RADAR_AUTH_SAME_SITE` | `Strict` | |

### Fly — REQUIRED, must stay OFF for the first deploy

| Variable | Value |
|---|---|
| `RADAR_ENABLE_AI` | `false` |
| `RADAR_DEMO_FIXTURE_ENABLED` | `false` |
| `RADAR_RSS_URLS` | *unset* |

### Fly — OPTIONAL now

| Variable | Value | Notes |
|---|---|---|
| `PRODUCT_HUNT_TOKEN` | token | source stays disabled without it |
| `RADAR_ENABLE_HACKER_NEWS` | `true` (default) | no key needed |
| `RADAR_APP_URL` | `https://<staging>.vercel.app/#/radar` | digest deep-links |
| `RADAR_TIME_ZONE` | `America/New_York` (default) | |

### Fly — LEAVE UNSET in staging (important)

| Variable | Why |
|---|---|
| `DEAL_SCOUT_RUN_URL` | The Node Deal Scout service is not deployed on Fly. Blank means a clean skip |
| `RADAR_EMAIL_SEND_URL` | same reasoning; blank means "preview only", which is what you want first |
| `RADAR_EMAIL_TOKEN`, `DEAL_SCOUT_RUN_TOKEN` | not needed while the above are unset |
| `JPA_DDL_AUTO` | leave unset so `validate` applies |

I verified `RadarDigestService` treats both blank URLs as "skip and report", and wraps live calls in
`catch (Exception)` — so an unset Deal Scout endpoint degrades the digest rather than failing the job.

### Vercel — project root `frontend/`

| Variable | Value |
|---|---|
| `RADAR_BACKEND_ORIGIN` | `https://<fly-staging-app>.fly.dev` — server-side only, HTTPS, no path |

**Do not set `VITE_RADAR_API_BASE_URL`.** `frontend/.env.example` ships it as
`http://localhost:8080/api/radar` for local dev. Copying that file into Vercel would point the SPA at
localhost. The code defends against this (`resolveRadarApiBase` rejects absolute cross-origin URLs when
`import.meta.env.PROD`), so it degrades to `/api/radar` rather than breaking — but do not rely on the
guard. Leave the variable unset.

**Never set in Vercel:** `RADAR_RUN_TOKEN`, `GROQ_API_KEY`, `DATABASE_URL`, `RADAR_ADMIN_PASSWORD_HASH`.

### Groq — only after §5 passes

`RADAR_ENABLE_AI=true`, `AI_PROVIDER=groq`, `GROQ_API_KEY=<secret>`,
`RADAR_AI_MODEL=openai/gpt-oss-20b`, `RADAR_DEEP_DIVE_MODEL=openai/gpt-oss-120b`,
`RADAR_AI_MAX_ITEMS_PER_RUN=5` for the first controlled run (default is 25).

### Resend — not needed for staging

Leave unconfigured. Digest preview works without it.

---

## 2. Resolved staging hardening

### B1 - Fly daily-use reliability

The HTTP service keeps one web Machine running in the primary region. The web process uses 1 GB of
RAM, shared CPU, and `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`. The option is scoped to the web
command so it cannot unintentionally change the worker or release process. This keeps a stopped
Spring JVM off the first daily browser request's critical path without assuming a specific startup
duration.

The worker remains on 512 MB and shared CPU. Its schedule, process isolation, readiness checks, and
graceful-shutdown settings are unchanged.

### B2 - Password hash documentation

The broken Maven `exec:java` instruction has been removed. README and section 3 now provide the same
verified standalone Java command.

### B3 - Migration and initial-integration documentation

`backend/.env.example` now states that Flyway owns migrations V1-V6. It also leaves
`DEAL_SCOUT_RUN_URL` and `RADAR_EMAIL_SEND_URL` blank. Initial staging keeps `RADAR_ENABLE_AI=false`
and `RADAR_RSS_URLS` unset, and Vercel must not define `VITE_RADAR_API_BASE_URL`.

### Remaining operational note

Both `web` and `worker` run Flyway validation on boot after the release command migrates the database.
Flyway locking makes concurrent starts safe; a process may briefly wait for the migration lock.

---

## 3. Admin authentication — exact procedure

```powershell
cd backend
.\mvnw.cmd -q -DskipTests compile
java -cp target/classes com.startupvalidationbot.radar.auth.RadarPasswordHashTool
```

It prompts twice, requires 12+ characters, zeroes the char arrays, and prints one line:
`pbkdf2-sha256$310000$<salt>$<hash>`.

Store **only** that string as the Fly secret. Keep the plaintext in your password manager and nowhere
else — not in `.env`, not in shell history, not in this repo.

```powershell
fly secrets set RADAR_ADMIN_PASSWORD_HASH='pbkdf2-sha256$310000$...' --app <staging-app>
```

Use single quotes in PowerShell: the hash contains `$`.

### What to verify once staging is up

| Check | Expected |
|---|---|
| `GET /api/radar/companies` logged out | `401` |
| `GET /api/radar/health` logged out | `200` |
| `GET /api/radar/auth/session` logged out | `200`, `{"authenticated":false,"configured":true}` |
| Login with correct password | `200`, `Set-Cookie` with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/radar` |
| `GET /api/radar/companies` with cookie | `200` |
| Logout | `204`, `Max-Age=0`, subsequent reads `401` |
| Session past `RADAR_AUTH_SESSION_HOURS` | `401` |
| 6 bad passwords | `429`, and still `429` after `fly apps restart` (proves V5 durability) |
| Browser devtools → Application → Local Storage | **no session token** |
| Any `/api/radar` request from the browser | **no `Authorization` header** |

If `RADAR_ADMIN_PASSWORD_HASH` is unset, `/auth/session` returns `configured:false` and the SPA shows a
configuration error rather than an unusable login box. There is no fail-open path.

---

## 4. Deployment runbook

```powershell
# --- Fly ---
fly apps create startup-radar-staging
fly postgres create --name startup-radar-staging-db --region iad
fly postgres attach startup-radar-staging-db --app startup-radar-staging   # sets DATABASE_URL

fly secrets set --app startup-radar-staging `
  RADAR_ADMIN_PASSWORD_HASH='<hash>' `
  RADAR_RUN_TOKEN='<64+ random>' `
  RADAR_BROWSER_ORIGIN='https://<staging>.vercel.app' `
  APP_ALLOWED_ORIGINS='https://<staging>.vercel.app' `
  RADAR_AUTH_SECURE_COOKIE='true' `
  RADAR_ENABLE_AI='false' `
  RADAR_DEMO_FIXTURE_ENABLED='false'

# edit fly.toml: app = "startup-radar-staging"   (do not reuse the production name)
fly deploy --config fly.toml
fly logs --app startup-radar-staging          # confirm the release command ran Flyway V1-V6
fly status --app startup-radar-staging        # confirm one web and one worker machine

# --- Vercel ---
# project root: frontend/    (dashboard setting; vercel.json cannot encode it)
# env: RADAR_BACKEND_ORIGIN = https://startup-radar-staging.fly.dev
# do NOT set VITE_RADAR_API_BASE_URL
```

### Verifying the request path

```powershell
curl.exe -i https://<staging>.vercel.app/api/radar/health          # 200 through the proxy
curl.exe -i https://<staging>.vercel.app/api/radar/companies       # 401
curl.exe -i https://startup-radar-staging.fly.dev/api/radar/companies  # 401 direct too
```

Then confirm the proxy strips worker credentials — this must **not** succeed:

```powershell
curl.exe -i -H "Authorization: Bearer <RADAR_RUN_TOKEN>" `
  https://<staging>.vercel.app/api/radar/admin/status     # expect 401
curl.exe -i -H "Authorization: Bearer <RADAR_RUN_TOKEN>" `
  https://startup-radar-staging.fly.dev/api/radar/admin/status   # expect 200 (direct, by design)
```

That pair is the single most important security check in staging: the browser path must not be able
to present the worker token, while server-to-server still can.

---

## 5. Deterministic validation checklist (AI off)

Run in this order and stop at the first failure:

1. Login succeeds; `/admin/status` shows `databaseHealthy: true`, `aiEnabled: false`
2. Admin → Run jobs → `discovery`. Expect Hacker News launches only
3. **Inspect every company created.** For each: is the name a real company (not a headline)? Is
   `domain` the company's own site and never `news.ycombinator.com`? Did two different launches
   collapse into one record?
4. Radar Home renders six sections; no company appears in two sections
5. Open a company: Radar Score, Personal Relevance, Why it matters, Why I care, Similar Startups,
   Sources all populated or honestly empty
6. Watch → appears in Watchlist; Ignore → drops out of Home; Restore → returns
7. Deep Dive with AI off → deterministic analysis, no error
8. Admin → Rescore all → `companiesRescored` > 0, `aiCalls` unchanged
9. `/admin/export` downloads; grep it for `apikey`, `SUPER`, `Bearer` — expect nothing
10. Trends → rebuild → percentages only where prior-window ≥ 3 companies

### Only then: one RSS feed

Enable exactly one preset (`rss-preset-techcrunch-venture`), run discovery, and read every resulting
company. You are looking for: headline-shaped names, publisher domains, and the same startup appearing
twice. If any appear, disable it and tell me what you saw — that is a real bug, not tuning.

---

## 6. Arbital diagnostic

Add manually first (Admin → add company) rather than waiting for discovery, so the identity path is
controlled:

- Name: `Arbital`
- Website: the company's own domain (**not** a news article URL)
- Description: paste their own public one-liner

Then walk: profile → Radar Score → Personal Relevance → categories/trends → Similar Startups →
Watch → snapshot → Deep Dive.

Judge it against these, and record the answers verbatim rather than a verdict:

- Does the summary tell you something you did not already know?
- Is Personal Relevance sensible given your interests, and do the stated reasons match?
- Is Radar Score defensible from the evidence shown?
- Does "Why it matters" cite real corroboration, or is it the single-source fallback line?
- Are Similar Startups genuinely comparable, or category noise?
- Are sources visible and clickable?
- Too dense or too sparse?
- Anything misleading?

**Do not tune scores so Arbital ranks highly.** If the score looks wrong, the interesting question is
which input is wrong.

---

## 7. Verification record

| Check | Result |
|---|---|
| Phase 2 and migrations V4-V6 on `codex/startup-radar-v1` | confirmed |
| GitHub CI Java verification | 149 passed, 0 failed, 0 skipped |
| PostgreSQL/Testcontainers verification | 17 tests passed on PostgreSQL 16.15 |
| Local `mvnw.cmd verify` after B1-B3 | 149 discovered; 132 passed, 17 skipped because Docker was unavailable |
| Frontend `npm test` | 9 passed |
| Frontend lint and production build | passed |
| `fly config validate --strict` | passed |
| `git diff --check` | passed |

---

## 8. Recommended next steps

1. Review the final Fly and Vercel app names, browser origins, and required secret names.
2. Deploy Fly and Vercel with AI disabled and the optional integration URLs unset.
3. Complete the deterministic staging checklist in section 5.
4. Run the Arbital diagnostic in section 6 and record the observed output.
5. Enable Groq only after deterministic staging is stable, starting with the documented low item cap.
