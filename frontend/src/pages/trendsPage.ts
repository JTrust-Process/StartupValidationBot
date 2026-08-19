import { guardRadarView, renderScore } from '../components/radarUi';
import { listRadarTrendDetails } from '../services/radarService';
import { escapeHtml } from '../utils/html';

export function renderTrendsPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header page-header--row">
        <div><h2>Emerging Trends</h2><p>Themes grounded in companies actually present in your Radar. Velocity is
             reported as absolute counts until there is enough history to justify a percentage.</p></div>
      </div>
      <div id="trends-status" aria-live="polite"></div>
      <section class="radar-trend-list" id="radar-trend-list"><div class="radar-empty">Loading trends...</div></section>
    </div>
  `;
}

async function refresh(list: HTMLElement): Promise<void> {
  {
    const trends = await listRadarTrendDetails();
    const glyphs: Record<string, string> = {
      RISING: '↑', COOLING: '↓', STEADY: '→', NEW: '•', UNKNOWN: '·'
    };
    list.innerHTML = trends.length ? trends.map((trend) => `
      <article class="radar-trend-row">
        <div class="radar-trend-row__heading">
          <div>
            <h3>${escapeHtml(trend.name)}
              <span class="radar-velocity radar-velocity--${escapeHtml(trend.velocityDirection.toLowerCase())}">
                ${glyphs[trend.velocityDirection] ?? '·'} ${escapeHtml(trend.velocityDirection.toLowerCase())}
              </span>
            </h3>
            <p>${escapeHtml(trend.summary)}</p>
          </div>
          ${renderScore('Momentum', trend.momentumScore)}
        </div>

        <div class="radar-trend-metrics">
          <div><span>Companies</span><strong>${trend.companyCount}</strong></div>
          <div><span>Last 30 days</span><strong>${trend.recentDiscoveries}</strong></div>
          <div><span>Previous 30</span><strong>${trend.priorDiscoveries}</strong></div>
          <div><span>Confidence</span><strong>${escapeHtml(trend.confidence.toLowerCase())}</strong></div>
        </div>

        ${trend.velocityNote ? `<p class="radar-muted">${escapeHtml(trend.velocityNote)}</p>` : ''}
        ${trend.whyItMatters ? `<p class="radar-trend-why">${escapeHtml(trend.whyItMatters)}</p>` : ''}

        <div class="radar-trend-companies">
          ${trend.companies.slice(0, 8).map((company) => `
            <a href="#/radar/company/${company.id}"><strong>${escapeHtml(company.name)}</strong><span>Radar ${company.radarScore}</span></a>
          `).join('')}
        </div>
      </article>
    `).join('') : '<div class="radar-empty">No trend has at least two recent companies yet. Run discovery, then rebuild trends.</div>';
  }
}

export function bindTrendsPageEvents(root: HTMLElement): void {
  const list = root.querySelector<HTMLElement>('#radar-trend-list');
  if (!list) return;
  void guardRadarView(list, () => refresh(list));
}
