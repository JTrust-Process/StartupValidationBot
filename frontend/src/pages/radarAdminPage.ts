import type { AutonomousDiligenceDiagnostics, OfferingDiagnostics, RadarInterest, RadarSystemStatus } from '../models/radar';
import {
  RadarApiError,
  addManualRadarCompany,
  downloadRadarExport,
  getRadarAdminSession,
  getRadarInterests,
  getRadarSystemStatus,
  getOfferingDiagnostics,
  getAutonomousDiligenceDiagnostics,
  listRadarAdminSources,
  loginRadarAdmin,
  logoutRadarAdmin,
  recomputeRadarRelevance,
  runRadarJob,
  saveRadarInterests,
  upsertRadarSource
} from '../services/radarService';
import { escapeHtml } from '../utils/html';
import { formatRadarDate, renderRadarError } from '../components/radarUi';

export function renderRadarAdminPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header page-header--row">
        <div><h2>Radar Administration</h2><p>Private watchlist, sources, jobs, exports, and system health.</p></div>
      </div>
      <div id="radar-admin-status" aria-live="polite"></div>
      <section id="radar-admin-content" class="radar-panel"><div class="radar-empty">Checking session...</div></section>
    </div>
  `;
}

function loginHtml(): string {
  return `
    <form id="radar-login-form" class="radar-auth-form">
      <div class="form-field">
        <label for="radar-admin-password">Admin password</label>
        <input id="radar-admin-password" name="password" type="password" minlength="12" maxlength="256" autocomplete="current-password" required />
      </div>
      <button class="button button--primary" type="submit">Sign in</button>
    </form>
  `;
}

function runLabel(status: RadarSystemStatus['lastDiscoveryRun']): string {
  if (!status) return 'Never';
  return `${status.status} / ${formatRadarDate(status.completedAt || status.startedAt)}`;
}

function statusHtml(status: RadarSystemStatus): string {
  const integrations = Object.entries(status.integrations).map(([name, configured]) => `
    <div><span>${escapeHtml(name.replace(/([A-Z])/g, ' $1'))}</span><strong class="${configured ? 'text-good' : 'text-warn'}">${configured ? 'Configured' : 'Not configured'}</strong></div>
  `).join('');
  const providerComparisons = status.aiProviderComparisons ?? [];
  const comparisons = providerComparisons.length ? `
    <div class="table-wrap"><table class="data-table"><thead><tr><th>Provider</th><th>Type</th><th>Requested / actual model</th><th>Success / failure / cache</th><th>Retries</th><th>Avg latency</th><th>Tokens in / out</th><th>Reported cost</th></tr></thead><tbody>
      ${providerComparisons.map((row) => `<tr><td>${escapeHtml(row.provider)}</td><td>${escapeHtml(row.analysisType)}</td><td class="table-subtext">${escapeHtml(row.requestedModel)} / ${escapeHtml(row.actualModel)}</td><td>${row.successes} / ${row.failures} / ${row.cacheHits}</td><td>${row.retries}</td><td>${row.averageLatencyMs === null ? 'Unknown' : `${row.averageLatencyMs} ms`}</td><td>${row.inputTokens} / ${row.outputTokens}</td><td>${row.providerCostUsd === null ? 'Not reported' : `$${row.providerCostUsd.toFixed(6)}`}</td></tr>`).join('')}
    </tbody></table></div>` : '<p class="radar-muted">No Groq or Router attempts recorded yet.</p>';
  return `
    <section class="radar-panel">
      <div class="page-header page-header--row"><div><h3>System status</h3><p>Sanitized runtime and job telemetry.</p></div><button id="radar-export-button" class="button button--secondary" type="button">Export Radar JSON</button></div>
      <div class="radar-status-grid">
        <div><span>Database</span><strong>${status.databaseHealthy ? 'Healthy' : 'Unavailable'}</strong></div>
        <div><span>Discovery</span><strong>${escapeHtml(runLabel(status.lastDiscoveryRun))}</strong></div>
        <div><span>Enrichment</span><strong>${escapeHtml(formatRadarDate(status.lastEnrichmentRun))}</strong></div>
        <div><span>Watchlist</span><strong>${escapeHtml(runLabel(status.lastWatchlistRefresh))}</strong></div>
        <div><span>Trends</span><strong>${escapeHtml(runLabel(status.lastTrendRun))}</strong></div>
        <div><span>Digest</span><strong>${escapeHtml(formatRadarDate(status.lastDigest))}</strong></div>
        <div><span>Discoveries</span><strong>${status.discoveriesProcessed}</strong></div>
        <div><span>AI calls / cache / failures</span><strong>${status.aiCalls} / ${status.aiCacheHits} / ${status.aiFailures}</strong></div>
      </div>
      <p class="radar-muted">${escapeHtml(status.aiProvider)} / ${escapeHtml(status.routineModel)}; Deep Dive: ${escapeHtml(status.deepDiveModel)}. AI ${status.aiEnabled ? 'enabled' : 'disabled'}.</p>
      <div class="radar-integration-grid">${integrations}</div>
      <h4>AI provider comparison</h4>
      <p class="radar-muted">Deterministic attempt telemetry only. Router cost remains unknown unless its API reports it.</p>
      ${comparisons}
      ${status.recentJobFailures.length ? `<div class="radar-failure-list"><h4>Recent job failures</h4>${status.recentJobFailures.map((failure) => `<p><strong>${escapeHtml(failure.jobType)}</strong> ${escapeHtml(failure.errorMessage || failure.status)}</p>`).join('')}</div>` : ''}
    </section>
  `;
}

function adminHtml(status: RadarSystemStatus, sources: Awaited<ReturnType<typeof listRadarAdminSources>>,
  offerings: OfferingDiagnostics, diligence: AutonomousDiligenceDiagnostics): string {
  return `
    <div class="radar-admin-heading"><span class="status-pill status-pill--green">Authenticated</span><button id="radar-logout-button" class="button button--secondary" type="button">Log out</button></div>
    ${statusHtml(status)}
    <section class="radar-panel" id="radar-interests-panel">
      <div class="page-header page-header--row">
        <div>
          <h3>Personal relevance interests</h3>
          <p>Personal relevance is computed from these interests plus your Watch / Ignore / Deep Dive /
             Visit history. It is deterministic, costs no AI calls, and is never an investment signal.</p>
        </div>
        <button id="radar-recompute-button" class="button button--secondary" type="button">Rescore all</button>
      </div>
      <div id="radar-interests-editor"><p class="radar-muted">Loading interests...</p></div>
    </section>

    <section class="radar-panel">
      <h3>Run jobs</h3>
      <div class="form-actions form-actions--start">
        ${['discovery', 'offering-discovery', 'autonomous-diligence', 'watchlist', 'trends', 'digest-preview'].map((job) => `<button class="button button--secondary" type="button" data-radar-job="${job}">${escapeHtml(job.replaceAll('-', ' '))}</button>`).join('')}
      </div>
    </section>
    <section class="radar-panel">
      <h3>Autonomous Diligence</h3>
      <div class="radar-status-grid">
        <div><span>Last run</span><strong>${escapeHtml(diligence.lastRunStatus)}</strong><small>${escapeHtml(formatRadarDate(diligence.lastRunCompletedAt || diligence.lastRunStartedAt))}</small></div>
        <div><span>Duration</span><strong>${diligence.lastRunDurationMs === null ? 'Unknown' : `${Math.round(diligence.lastRunDurationMs / 1000)}s`}</strong></div>
        <div><span>Companies / offerings</span><strong>${diligence.companiesConsidered} / ${diligence.offeringsConsidered}</strong></div>
        <div><span>Identity / campaign resolved</span><strong>${diligence.identitiesResolved} / ${diligence.campaignsResolved}</strong></div>
        <div><span>Ready / partial / review</span><strong>${diligence.packetsReady} / ${diligence.packetsPartial} / ${diligence.needsReview}</strong></div>
        <div><span>Platform errors / AI fallback</span><strong>${diligence.platformErrors} / ${diligence.aiFallbacks}</strong></div>
        <div><span>Emails queued / sent / failed</span><strong>${diligence.emailsQueued} / ${diligence.emailsSent} / ${diligence.emailsFailed}</strong></div>
        <div><span>Resend</span><strong>${diligence.resend.configured ? 'Configured' : 'Not configured'}</strong><small>${escapeHtml(diligence.resend.lastStatus)}${diligence.resend.lastMessageId ? ` / ${escapeHtml(diligence.resend.lastMessageId)}` : ''}</small></div>
      </div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>Platform</th><th>Last checked</th><th>Status</th><th>Requests</th><th>Campaigns</th><th>Error</th></tr></thead><tbody>
        ${diligence.platforms.length ? diligence.platforms.map((platform) => `<tr><td>${escapeHtml(platform.platform)}</td><td>${escapeHtml(formatRadarDate(platform.lastCheckedAt))}</td><td>${escapeHtml(platform.status)}</td><td>${platform.requests}</td><td>${platform.campaignsFound}</td><td class="table-subtext">${escapeHtml(platform.error || '')}</td></tr>`).join('')
          : '<tr><td colspan="6">No platform checks recorded yet.</td></tr>'}
      </tbody></table></div>
      ${diligence.resend.lastError ? `<p class="radar-muted">Last email error: ${escapeHtml(diligence.resend.lastError)}</p>` : ''}
    </section>
    <section class="radar-panel">
      <h3>Offering Discovery</h3>
      <div class="radar-status-grid">
        <div><span>Source health</span><strong>${escapeHtml(offerings.sourceStatus)}</strong></div>
        <div><span>Latest job</span><strong>${escapeHtml(offerings.lastJobStatus)}</strong></div>
        <div><span>Last successful run</span><strong>${escapeHtml(formatRadarDate(offerings.sourceLastSuccessAt))}</strong></div>
        <div><span>Duration</span><strong>${offerings.lastJobDurationMs === null ? 'Unknown' : `${Math.round(offerings.lastJobDurationMs / 1000)}s`}</strong></div>
        <div><span>Records inspected</span><strong>${offerings.recordsInspected}</strong></div>
        <div><span>New offerings</span><strong>${offerings.newOfferings}</strong></div>
        <div><span>Updated offerings</span><strong>${offerings.updatedOfferings}</strong></div>
        <div><span>Errors</span><strong>${offerings.errorCount}</strong></div>
        <div><span>Offerings stored</span><strong>${offerings.offeringsStored}</strong></div>
        <div><span>Confirmed matches</span><strong>${offerings.confirmedMatches}</strong></div>
        <div><span>Possible matches</span><strong>${offerings.possibleMatches}</strong></div>
      </div>
      ${offerings.sourceError ? `<p class="radar-muted">${escapeHtml(offerings.sourceError)}</p>` : ''}
    </section>
    <section class="radar-panel">
      <h3>Discovery sources</h3>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>Name</th><th>Type</th><th>URL</th><th>State</th></tr></thead><tbody>
        ${sources.map((source) => `<tr><td>${escapeHtml(source.name)}</td><td>${escapeHtml(source.sourceType)}</td><td class="table-subtext">${escapeHtml(source.url || 'Manual')}</td><td>${source.enabled ? 'Enabled' : 'Disabled'}</td></tr>`).join('')}
      </tbody></table></div>
      <form id="radar-source-form" class="radar-admin-form-grid">
        <div class="form-field"><label for="source-key">Source key</label><input id="source-key" name="sourceKey" pattern="[a-z0-9][a-z0-9-]{0,159}" required /></div>
        <div class="form-field"><label for="source-name">Name</label><input id="source-name" name="name" maxlength="240" required /></div>
        <div class="form-field"><label for="source-type">Type</label><select id="source-type" name="sourceType"><option>RSS</option><option>PRODUCT_HUNT</option><option>MANUAL</option><option>YC_DIRECTORY</option><option>HACKER_NEWS</option></select></div>
        <div class="form-field"><label for="source-url">Public URL</label><input id="source-url" name="url" type="url" maxlength="1200" /></div>
        <label class="checkbox-row"><input name="enabled" type="checkbox" checked /> Enabled</label>
        <button class="button button--primary" type="submit">Save source</button>
      </form>
    </section>
    <section class="radar-panel">
      <h3>Manual public discovery</h3>
      <form id="radar-manual-company-form" class="radar-admin-form-grid">
        <div class="form-field"><label for="manual-company-name">Company</label><input id="manual-company-name" name="companyName" maxlength="300" required /></div>
        <div class="form-field"><label for="manual-company-site">Website</label><input id="manual-company-site" name="websiteUrl" type="url" maxlength="1200" required /></div>
        <div class="form-field"><label for="manual-company-sector">Sector</label><input id="manual-company-sector" name="sector" maxlength="160" /></div>
        <div class="form-field"><label for="manual-company-source">Public source URL</label><input id="manual-company-source" name="sourceUrl" type="url" maxlength="1200" required /></div>
        <div class="form-field radar-form-span"><label for="manual-company-description">Public description</label><textarea id="manual-company-description" name="description" maxlength="8000" required></textarea></div>
        <label class="checkbox-row radar-form-span"><input name="publicEvidence" type="checkbox" required /> I confirm this description and source are official public evidence.</label>
        <button class="button button--primary" type="submit">Add discovery</button>
      </form>
    </section>
  `;
}

export function bindRadarAdminPageEvents(root: HTMLElement): void {
  const content = root.querySelector<HTMLElement>('#radar-admin-content');
  const message = root.querySelector<HTMLElement>('#radar-admin-status');
  if (!content || !message) return;

  const showMessage = (value: string, failed = false) => {
    message.innerHTML = `<div class="${failed ? 'alert alert--danger' : 'alert alert--success'}">${escapeHtml(value)}</div>`;
  };

  const bindLogin = () => {
    const form = content.querySelector<HTMLFormElement>('#radar-login-form');
    form?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = form.querySelector<HTMLButtonElement>('button[type="submit"]');
      if (button) button.disabled = true;
      try {
        await loginRadarAdmin(String(new FormData(form).get('password') || ''));
        form.reset();
        await load();
      } catch (error) {
        showMessage(error instanceof Error ? error.message : 'Login failed.', true);
      } finally {
        if (button) button.disabled = false;
      }
    });
  };

  const renderInterests = (interests: RadarInterest[]): string => `
    <form id="radar-interests-form" class="radar-interests-form">
      <p class="radar-muted">One interest per line: <code>label | weight 1-25 | comma,separated,keywords</code>.
         Keywords match whole words only, so "ai" will not match "retail".</p>
      <div class="form-field radar-form-span">
        <label for="radar-interests-text">Interests</label>
        <textarea id="radar-interests-text" name="interests" rows="14">${escapeHtml(interests
          .map((interest) => `${interest.label} | ${interest.weight} | ${interest.keywords.join(', ')}`)
          .join('\n'))}</textarea>
      </div>
      <div class="form-actions form-actions--start">
        <button class="button button--primary" type="submit">Save interests and rescore</button>
      </div>
    </form>
  `;

  const parseInterests = (raw: string): RadarInterest[] => raw.split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [label = '', weight = '', keywords = ''] = line.split('|').map((part) => part.trim());
      const parsedWeight = Number(weight);
      return {
        label,
        weight: Number.isFinite(parsedWeight) && parsedWeight > 0 ? Math.round(parsedWeight) : 10,
        keywords: keywords.split(',').map((keyword) => keyword.trim()).filter(Boolean)
      };
    })
    .filter((interest) => interest.label.length > 0);

  const loadInterests = async () => {
    const editor = content.querySelector<HTMLElement>('#radar-interests-editor');
    if (!editor) return;
    try {
      const profile = await getRadarInterests();
      editor.innerHTML = renderInterests(profile.interests);
      const form = editor.querySelector<HTMLFormElement>('#radar-interests-form');
      form?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = form.querySelector<HTMLButtonElement>('button[type="submit"]');
        if (button) button.disabled = true;
        try {
          const raw = String(new FormData(form).get('interests') || '');
          const result = await saveRadarInterests(parseInterests(raw));
          showMessage(`Interests saved. ${result.companiesRescored} compan`
            + `${result.companiesRescored === 1 ? 'y' : 'ies'} rescored.`);
          await loadInterests();
        } catch (error) {
          showMessage(error instanceof Error ? error.message : 'Saving interests failed.', true);
        } finally {
          if (button) button.disabled = false;
        }
      });
    } catch (error) {
      editor.innerHTML = renderRadarError(error);
    }
  };

  const bindAdmin = () => {
    void loadInterests();
    const recompute = content.querySelector<HTMLButtonElement>('#radar-recompute-button');
    recompute?.addEventListener('click', async () => {
      recompute.disabled = true;
      try {
        const result = await recomputeRadarRelevance();
        showMessage(`${result.companiesRescored} personal score`
          + `${result.companiesRescored === 1 ? '' : 's'} refreshed.`);
      } catch (error) {
        showMessage(error instanceof Error ? error.message : 'Rescore failed.', true);
      } finally { recompute.disabled = false; }
    });
    content.querySelector<HTMLButtonElement>('#radar-logout-button')?.addEventListener('click', async () => {
      await logoutRadarAdmin();
      await load();
    });
    content.querySelector<HTMLButtonElement>('#radar-export-button')?.addEventListener('click', async () => {
      try { await downloadRadarExport(); showMessage('Radar export downloaded.'); }
      catch (error) { showMessage(error instanceof Error ? error.message : 'Export failed.', true); }
    });
    content.querySelectorAll<HTMLButtonElement>('[data-radar-job]').forEach((button) => {
      button.addEventListener('click', async () => {
        const jobType = button.dataset.radarJob || '';
        button.disabled = true;
        try {
          const result = await runRadarJob(jobType);
          showMessage(`${result.message} ${result.processed} inspected, ${result.created} new, `
            + `${result.updated} updated, ${result.errorCount} errors.`, !result.ok);
          await load();
        } catch (error) {
          const proxyMayHaveTimedOut = jobType === 'discovery'
            && error instanceof RadarApiError
            && (error.status === 0 || error.status >= 500);
          showMessage(proxyMayHaveTimedOut
            ? 'The browser request ended before discovery reported back. The durable backend job may still be running; refresh System Status before retrying.'
            : error instanceof Error ? error.message : 'Job failed.', true);
        } finally { button.disabled = false; }
      });
    });
    const sourceForm = content.querySelector<HTMLFormElement>('#radar-source-form');
    sourceForm?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const data = new FormData(sourceForm);
      try {
        await upsertRadarSource({
          sourceKey: String(data.get('sourceKey') || '').trim(),
          sourceType: String(data.get('sourceType') || ''),
          name: String(data.get('name') || '').trim(),
          url: String(data.get('url') || '').trim() || null,
          enabled: data.get('enabled') === 'on'
        });
        showMessage('Source saved.');
        await load();
      } catch (error) { showMessage(error instanceof Error ? error.message : 'Source save failed.', true); }
    });
    const companyForm = content.querySelector<HTMLFormElement>('#radar-manual-company-form');
    companyForm?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const data = new FormData(companyForm);
      try {
        await addManualRadarCompany({
          companyName: String(data.get('companyName') || '').trim(),
          websiteUrl: String(data.get('websiteUrl') || '').trim(),
          description: String(data.get('description') || '').trim(),
          sector: String(data.get('sector') || '').trim(),
          sourceUrl: String(data.get('sourceUrl') || '').trim(),
          evidenceClassification: 'PUBLIC_OFFICIAL'
        });
        companyForm.reset();
        showMessage('Company added to Radar.');
      } catch (error) { showMessage(error instanceof Error ? error.message : 'Discovery failed.', true); }
    });
  };

  const load = async () => {
    try {
      const session = await getRadarAdminSession();
      if (!session.authenticated) {
        content.innerHTML = loginHtml();
        bindLogin();
        return;
      }
      const [status, sources, offerings, diligence] = await Promise.all([
        getRadarSystemStatus(), listRadarAdminSources(), getOfferingDiagnostics(), getAutonomousDiligenceDiagnostics()
      ]);
      content.innerHTML = adminHtml(status, sources, offerings, diligence);
      bindAdmin();
    } catch (error) {
      content.innerHTML = renderRadarError(error);
    }
  };

  void load();
}
