# Stage 7A Staging Test Plan

Run this plan only after deploying the Stage 7A branch to isolated staging and applying Flyway V9.
Use browser developer tools to confirm Radar session credentials remain HttpOnly cookies and never
appear in Local Storage.

1. **Authentication**: Open the staging Radar URL in a signed-out browser, confirm Radar data and
   `GET /api/deal-workspaces` return `401`, sign in, and confirm both surfaces load.
2. **Radar Home**: Verify New Today, Best Matches, High Momentum, and Emerging Trends render without
   duplicate companies or console errors.
3. **Radar company detail**: Open a real company and verify scores, sources, Similar Startups, changes,
   and profile actions still render.
4. **Radar handoff**: Click **Evaluate an Offering** and verify the hash includes `radarCompanyId`.
5. **New Deal prefill**: Confirm only company name, sector, public description, and company website are
   prefilled. Confirm platform, minimum, valuation, security, and offering terms remain blank/unknown.
6. **Create deal**: Enter a real offering platform and terms, create the workspace, and confirm its
   Origin section links back to the Radar company.
7. **Hard refresh**: Refresh the deal URL and confirm the same server ID and complete fields return.
8. **Reload deal**: Navigate away and reopen it from Deals; confirm nested workspace content is intact.
9. **Quick Screen**: Save every score and note, refresh, and confirm totals and notes persist.
10. **Deep Diligence**: Save all dimensions and notes, refresh, and confirm the final score persists.
11. **Decision**: Save Pass, Watch, or Invest Small plus rationale and milestone; refresh and verify.
12. **Documents**: Add realistic Form C text, inspect extraction/risk suggestions, refresh, and verify.
13. **Evidence**: Add verified and weak evidence claims, delete one, refresh, and verify the final list.
14. **Memo**: Generate, edit, and save a memo; refresh and confirm the edited text remains.
15. **Text Import**: Paste a realistic Republic Reg CF Crowd SAFE example with clear eligibility,
    minimum investment, valuation cap, amount raised, illiquidity, and risk language. Accept selected
    suggestions, create the deal, refresh, and confirm terms, raw import, sections, and risks persist.
16. **Duplicate protection**: Return to the same Radar company and click **Evaluate an Offering**.
    Confirm the existing-workspace warning and link appear and no deal is created automatically.
17. **LocalStorage migration**: In a browser with legacy deals, confirm the dashboard notice appears
    only after authentication. Click Migrate, confirm a JSON download starts first, verify copied and
    skipped counts, retry safely, and confirm the original localStorage payload remains.
18. **Cross-browser persistence**: Sign in from a second browser/profile and confirm the created deals
    and all nested workspace data are available.
19. **Delete**: Delete a disposable deal, refresh its old URL, and confirm it returns not found.
20. **Unauthorized API access**: From a signed-out client call both Vercel and direct Fly
    `GET /api/deal-workspaces`; both must return `401`. A Radar worker token must also return `401`.
21. **Invalid-origin write**: Send an authenticated POST/PUT with an Origin other than the configured
    staging frontend; confirm `403`. Repeat from the staging UI and confirm the valid write succeeds.
22. **Radar discovery regression**: Run the deterministic discovery job once and verify source counts,
    deduplication, snapshots, and job status remain healthy.
23. **AI Deep Dive regression**: Run one cached and one uncached Deep Dive within the configured budget;
    verify provider diagnostics and cache behavior remain unchanged.
24. **Trends/watchlist regression**: Watch, unwatch, ignore, restore, visit, rescore, and open Trends;
    verify Personal Relevance and trend velocity remain deterministic and no Deal workspace is created.

After logout, confirm Deal pages no longer display cached private workspaces and protected requests
return `401`. Do not promote staging until every failed step has an attached browser/API log.

## Router Provider Experiment

Router is not part of the default Stage 7A configuration. Before a separate Router experiment, disable
content recording in the Router account, keep all credentials server-side, and obtain both configured model
IDs from Router's authenticated model catalog. Use a Router-supported routing/benchmark alias for routine
enrichment and a pinned model for Deep Dive. Do not enable Router on stable staging until the experiment
branch has been reviewed. A Router failure must remain visible in provider telemetry and must not call Groq.
