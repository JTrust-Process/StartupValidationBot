import type { RadarSystemStatus } from '../models/radar';
import {
  addManualRadarCompany,
  downloadRadarExport,
  getRadarAdminSession,
  getRadarSystemStatus,
  listRadarAdminSources,
  loginRadarAdmin,
  logoutRadarAdmin,
  runRadarJob,
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
      ${status.recentJobFailures.length ? `<div class="radar-failure-list"><h4>Recent job failures</h4>${status.recentJobFailures.map((failure) => `<p><strong>${escapeHtml(failure.jobType)}</strong> ${escapeHtml(failure.errorMessage || failure.status)}</p>`).join('')}</div>` : ''}
    </section>
  `;
}

function adminHtml(status: RadarSystemStatus, sources: Awaited<ReturnType<typeof listRadarAdminSources>>): string {
  return `
    <div class="radar-admin-heading"><span class="status-pill status-pill--green">Authenticated</span><button id="radar-logout-button" class="button button--secondary" type="button">Log out</button></div>
    ${statusHtml(status)}
    <section class="radar-panel">
      <h3>Run jobs</h3>
      <div class="form-actions form-actions--start">
        ${['discovery', 'watchlist', 'trends', 'digest-preview'].map((job) => `<button class="button button--secondary" type="button" data-radar-job="${job}">${escapeHtml(job.replace('-', ' '))}</button>`).join('')}
      </div>
    </section>
    <section class="radar-panel">
      <h3>Discovery sources</h3>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>Name</th><th>Type</th><th>URL</th><th>State</th></tr></thead><tbody>
        ${sources.map((source) => `<tr><td>${escapeHtml(source.name)}</td><td>${escapeHtml(source.sourceType)}</td><td class="table-subtext">${escapeHtml(source.url || 'Manual')}</td><td>${source.enabled ? 'Enabled' : 'Disabled'}</td></tr>`).join('')}
      </tbody></table></div>
      <form id="radar-source-form" class="radar-admin-form-grid">
        <div class="form-field"><label for="source-key">Source key</label><input id="source-key" name="sourceKey" pattern="[a-z0-9][a-z0-9-]{0,159}" required /></div>
        <div class="form-field"><label for="source-name">Name</label><input id="source-name" name="name" maxlength="240" required /></div>
        <div class="form-field"><label for="source-type">Type</label><select id="source-type" name="sourceType"><option>RSS</option><option>PRODUCT_HUNT</option><option>MANUAL</option><option>YC_DIRECTORY</option></select></div>
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

  const bindAdmin = () => {
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
        button.disabled = true;
        try {
          const result = await runRadarJob(button.dataset.radarJob || '');
          showMessage(result.message, !result.ok);
          await load();
        } catch (error) {
          showMessage(error instanceof Error ? error.message : 'Job failed.', true);
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
          sourceUrl: String(data.get('sourceUrl') || '').trim()
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
      const [status, sources] = await Promise.all([getRadarSystemStatus(), listRadarAdminSources()]);
      content.innerHTML = adminHtml(status, sources);
      bindAdmin();
    } catch (error) {
      content.innerHTML = renderRadarError(error);
    }
  };

  void load();
}
