import { renderRadarError, renderScore } from '../components/radarUi';
import { listRadarTrends } from '../services/radarService';
import { escapeHtml } from '../utils/html';

export function renderTrendsPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header page-header--row">
        <div><h2>Emerging Trends</h2><p>Category clusters from startups discovered in the last 30 days.</p></div>
      </div>
      <div id="trends-status" aria-live="polite"></div>
      <section class="radar-trend-list" id="radar-trend-list"><div class="radar-empty">Loading trends...</div></section>
    </div>
  `;
}

async function refresh(root: HTMLElement): Promise<void> {
  const list = root.querySelector<HTMLElement>('#radar-trend-list');
  if (!list) return;
  try {
    const trends = await listRadarTrends();
    list.innerHTML = trends.length ? trends.map((trend) => `
      <article class="radar-trend-row">
        <div class="radar-trend-row__heading">
          <div><h3>${escapeHtml(trend.name)}</h3><p>${escapeHtml(trend.summary)}</p></div>
          ${renderScore('Momentum', trend.momentumScore)}
        </div>
        <div class="radar-trend-companies">
          ${trend.companies.slice(0, 8).map((company) => `
            <a href="#/radar/company/${company.id}"><strong>${escapeHtml(company.name)}</strong><span>Radar ${company.radarScore}</span></a>
          `).join('')}
        </div>
      </article>
    `).join('') : '<div class="radar-empty">No trend has at least two recent companies yet. Run discovery, then rebuild trends.</div>';
  } catch (error) {
    list.innerHTML = renderRadarError(error);
  }
}

export function bindTrendsPageEvents(root: HTMLElement): void {
  void refresh(root);
}
