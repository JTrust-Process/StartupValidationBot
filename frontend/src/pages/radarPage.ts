import { renderCompanyRows, renderRadarError, formatRadarDate } from '../components/radarUi';
import type { RadarCompanyFilters, RadarSource } from '../models/radar';
import {
  getRadarApiBase,
  listRadarCompanies,
  listRadarSources
} from '../services/radarService';
import { escapeHtml } from '../utils/html';

function renderSourceRows(sources: RadarSource[]): string {
  return sources.map((source) => `
    <tr>
      <td><strong>${escapeHtml(source.name)}</strong><div class="table-subtext">${escapeHtml(source.sourceType)}</div></td>
      <td>${source.enabled ? '<span class="status-pill status-pill--green">Enabled</span>' : '<span class="status-pill">Disabled</span>'}</td>
      <td>${escapeHtml(source.lastStatus.replaceAll('_', ' '))}${source.lastError ? `<div class="table-subtext">${escapeHtml(source.lastError)}</div>` : ''}</td>
      <td>${escapeHtml(formatRadarDate(source.lastCheckedAt))}</td>
    </tr>
  `).join('');
}

export function renderRadarPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header page-header--row">
        <div>
          <h2>Startup Radar</h2>
          <p>Discover important startups. Scores measure research importance and personal relevance, not investment quality.</p>
        </div>
      </div>

      <div id="radar-status" aria-live="polite"></div>

      <section class="radar-panel radar-connection">
        <div>
          <h3>Server connection</h3>
          <p>API: <code>${escapeHtml(getRadarApiBase())}</code>. <a href="#/radar-admin">Sign in</a> for private controls and system status.</p>
        </div>
      </section>

      <section class="radar-panel">
        <form id="radar-filter-form" class="radar-filter-grid">
          <div class="form-field">
            <label for="radar-search">Search</label>
            <input id="radar-search" name="search" type="search" placeholder="Company, product, domain" />
          </div>
          <div class="form-field">
            <label for="radar-sector">Sector / theme</label>
            <input id="radar-sector" name="sector" type="text" placeholder="Infrastructure, energy..." />
          </div>
          <div class="form-field">
            <label for="radar-min-score">Minimum Radar score</label>
            <input id="radar-min-score" name="minRadar" type="number" min="0" max="100" step="5" />
          </div>
          <div class="form-field">
            <label for="radar-sort">Sort</label>
            <select id="radar-sort" name="sort">
              <option value="radar">Radar score</option>
              <option value="newest">Newest discovery</option>
              <option value="updated">Recently updated</option>
            </select>
          </div>
        </form>
      </section>

      <section class="radar-summary" id="radar-summary"></section>
      <section class="radar-company-list" id="radar-company-list"><div class="radar-empty">Loading Radar...</div></section>

      <section class="radar-panel">
        <div class="page-header page-header--row">
          <div><h3>Discovery sources</h3><p>RSS is fetched automatically. Product Hunt activates when its server token is configured.</p></div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Source</th><th>State</th><th>Last status</th><th>Last checked</th></tr></thead>
            <tbody id="radar-source-rows"><tr><td colspan="4">Loading sources...</td></tr></tbody>
          </table>
        </div>
      </section>
    </div>
  `;
}

function readFilters(form: HTMLFormElement): RadarCompanyFilters {
  const data = new FormData(form);
  const minRadar = Number(data.get('minRadar'));
  return {
    search: String(data.get('search') ?? '').trim(),
    sector: String(data.get('sector') ?? '').trim(),
    minRadar: Number.isFinite(minRadar) && String(data.get('minRadar') ?? '') ? minRadar : undefined,
    sort: String(data.get('sort') ?? 'radar') as RadarCompanyFilters['sort']
  };
}

async function refreshRadar(root: HTMLElement): Promise<void> {
  const list = root.querySelector<HTMLElement>('#radar-company-list');
  const summary = root.querySelector<HTMLElement>('#radar-summary');
  const sourceRows = root.querySelector<HTMLElement>('#radar-source-rows');
  const form = root.querySelector<HTMLFormElement>('#radar-filter-form');
  if (!list || !summary || !sourceRows || !form) return;
  try {
    const [companies, sources] = await Promise.all([listRadarCompanies(readFilters(form)), listRadarSources()]);
    list.innerHTML = renderCompanyRows(companies);
    sourceRows.innerHTML = renderSourceRows(sources);
    const strong = companies.filter((company) => company.radarScore >= 70).length;
    const recentlyUpdated = companies.filter((company) => Date.now() - new Date(company.lastSeenAt).getTime()
      <= 7 * 24 * 60 * 60 * 1000).length;
    summary.innerHTML = `
      <div><span>Visible companies</span><strong>${companies.length}</strong></div>
      <div><span>Radar 70+</span><strong>${strong}</strong></div>
      <div><span>Updated this week</span><strong>${recentlyUpdated}</strong></div>
      <div><span>Enabled sources</span><strong>${sources.filter((source) => source.enabled).length}</strong></div>
    `;
  } catch (error) {
    list.innerHTML = renderRadarError(error);
  }
}

export function bindRadarPageEvents(root: HTMLElement): void {
  const filterForm = root.querySelector<HTMLFormElement>('#radar-filter-form');
  let filterTimer = 0;
  filterForm?.addEventListener('input', () => {
    window.clearTimeout(filterTimer);
    filterTimer = window.setTimeout(() => void refreshRadar(root), 180);
  });
  filterForm?.addEventListener('change', () => void refreshRadar(root));

  void refreshRadar(root);
}
