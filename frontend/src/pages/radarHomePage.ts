import { formatRadarDate, guardRadarView, renderScore } from '../components/radarUi';
import type {
  RadarCompanyChange,
  RadarHome,
  RadarHomeCompanyCard,
  RadarHomeSection,
  RadarTrendDetail
} from '../models/radar';
import { getRadarHome } from '../services/radarService';
import { escapeHtml } from '../utils/html';

const SIGNIFICANCE_TONE: Record<string, string> = {
  MAJOR: 'radar-tier radar-tier--major',
  IMPORTANT: 'radar-tier radar-tier--important',
  INTERESTING: 'radar-tier radar-tier--interesting',
  MINOR: 'radar-tier radar-tier--minor'
};

const VELOCITY_GLYPH: Record<string, string> = {
  RISING: '↑',
  COOLING: '↓',
  STEADY: '→',
  NEW: '•',
  UNKNOWN: '·'
};

export function renderRadarHomePage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header page-header--row">
        <div>
          <h2>Radar Home</h2>
          <p>What is worth knowing about today. Scores measure research importance and personal
             relevance &mdash; never investment quality.</p>
        </div>
        <a class="button button--secondary" href="#/radar/all">Browse all companies</a>
      </div>

      <section class="radar-summary" id="radar-home-summary"></section>
      <div id="radar-home-body"><div class="radar-empty">Loading Radar Home...</div></div>
    </div>
  `;
}

function bullets(items: string[], limit: number): string {
  if (!items.length) return '';
  return `<ul class="radar-bullet-list">${items.slice(0, limit)
    .map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
}

function companyCard(card: RadarHomeCompanyCard): string {
  const batch = [card.accelerator, card.acceleratorBatch].filter(Boolean).join(' ');
  return `
    <article class="radar-home-card" data-company-id="${card.id}">
      <header class="radar-home-card__head">
        <div>
          <a class="radar-home-card__name" href="#/radar/company/${card.id}">${escapeHtml(card.name)}</a>
          ${batch ? `<span class="radar-badge">${escapeHtml(batch)}</span>` : ''}
          ${card.watched ? '<span class="radar-badge radar-badge--watch">Watching</span>' : ''}
        </div>
        ${card.highlight ? `<span class="radar-highlight">${escapeHtml(card.highlight)}</span>` : ''}
      </header>

      <div class="radar-home-card__scores">
        ${renderScore('Radar', card.radarScore)}
        ${renderScore('Personal', card.personalScore)}
      </div>

      <p class="radar-home-card__summary">${escapeHtml(card.description || 'No source summary captured yet.')}</p>

      <div class="radar-tags">
        ${card.sector && card.sector !== 'Unknown' ? `<span class="radar-tag">${escapeHtml(card.sector)}</span>` : ''}
        ${card.categories.slice(0, 3).map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}
        <span class="radar-meta">${card.sourceCount} source${card.sourceCount === 1 ? '' : 's'}</span>
      </div>

      <div class="radar-home-card__reasons">
        <div>
          <h4>Why it matters</h4>
          ${bullets(card.whyItMatters, 3) || '<p class="radar-muted">No corroborating signal yet.</p>'}
        </div>
        <div>
          <h4>Why you might care</h4>
          ${bullets(card.whyYouMightCare, 2) || '<p class="radar-muted">No configured interest matched.</p>'}
        </div>
      </div>

      <footer class="radar-home-card__actions">
        <a class="button button--secondary button--compact" href="#/radar/company/${card.id}">Open profile</a>
      </footer>
    </article>
  `;
}

function changeRow(change: RadarCompanyChange): string {
  const tone = SIGNIFICANCE_TONE[change.significance] ?? SIGNIFICANCE_TONE.MINOR;
  return `
    <article class="radar-change-row">
      <div class="radar-change-row__head">
        <a href="#/radar/company/${change.companyId}"><strong>${escapeHtml(change.companyName)}</strong></a>
        <span class="${tone}">${escapeHtml(change.significance)}</span>
        <span class="radar-meta">${escapeHtml(formatRadarDate(change.detectedAt))}</span>
      </div>
      <p>${escapeHtml(change.summary)}</p>
      ${change.previousValue && change.currentValue ? `
        <div class="radar-change-delta">
          <div><span>Previous</span><strong>${escapeHtml(change.previousValue)}</strong></div>
          <div><span>Current</span><strong>${escapeHtml(change.currentValue)}</strong></div>
        </div>` : ''}
      ${change.whyItMatters ? `<p class="radar-muted">${escapeHtml(change.whyItMatters)}</p>` : ''}
    </article>
  `;
}

function trendRow(trend: RadarTrendDetail): string {
  const glyph = VELOCITY_GLYPH[trend.velocityDirection] ?? '·';
  return `
    <article class="radar-trend-compact">
      <div class="radar-trend-compact__head">
        <a href="#/trends"><strong>${escapeHtml(trend.name)}</strong></a>
        <span class="radar-velocity radar-velocity--${escapeHtml(trend.velocityDirection.toLowerCase())}">
          ${glyph} ${escapeHtml(trend.velocityDirection.toLowerCase())}
        </span>
        <span class="radar-confidence">Confidence: ${escapeHtml(trend.confidence.toLowerCase())}</span>
      </div>
      <p class="radar-muted">${escapeHtml(trend.velocityNote || trend.summary)}</p>
      <div class="radar-tags">
        <span class="radar-meta">${trend.companyCount} compan${trend.companyCount === 1 ? 'y' : 'ies'}</span>
        ${trend.companies.slice(0, 4).map((company) =>
          `<a class="radar-tag" href="#/radar/company/${company.id}">${escapeHtml(company.name)}</a>`).join('')}
      </div>
    </article>
  `;
}

function sectionHtml(section: RadarHomeSection): string {
  let body: string;
  let count: number;

  if (section.kind === 'COMPANIES') {
    count = section.companies.length;
    body = count
      ? `<div class="radar-home-grid">${section.companies.map(companyCard).join('')}</div>`
      : '<div class="radar-empty">Nothing in this section yet.</div>';
  } else if (section.kind === 'CHANGES') {
    count = section.changes.length;
    body = count
      ? `<div class="radar-change-list">${section.changes.map(changeRow).join('')}</div>`
      : '<div class="radar-empty">No meaningful changes from watched companies recently.</div>';
  } else {
    count = section.trends.length;
    body = count
      ? `<div class="radar-trend-compact-list">${section.trends.map(trendRow).join('')}</div>`
      : '<div class="radar-empty">No trend has enough supporting companies yet.</div>';
  }

  return `
    <section class="radar-home-section" id="section-${escapeHtml(section.key)}">
      <div class="radar-home-section__head">
        <div>
          <h3>${escapeHtml(section.title)}</h3>
          <p>${escapeHtml(section.subtitle)}</p>
        </div>
        <span class="radar-meta">${count}</span>
      </div>
      ${body}
    </section>
  `;
}

function render(home: RadarHome, summary: HTMLElement, body: HTMLElement): void {
  summary.innerHTML = `
    <div><span>Companies tracked</span><strong>${home.totalCompanies}</strong></div>
    <div><span>New in 24h</span><strong>${home.newSinceYesterday}</strong></div>
    <div><span>Meaningful changes (14d)</span><strong>${home.meaningfulChanges}</strong></div>
    <div><span>Generated</span><strong>${escapeHtml(formatRadarDate(home.generatedAt))}</strong></div>
  `;
  body.innerHTML = home.sections.map(sectionHtml).join('');
}

export function bindRadarHomePageEvents(root: HTMLElement): void {
  const summary = root.querySelector<HTMLElement>('#radar-home-summary');
  const body = root.querySelector<HTMLElement>('#radar-home-body');
  if (!summary || !body) return;

  // Errors propagate so guardRadarView can turn a 401 into a sign-in form.
  const load = async () => {
    const home = await getRadarHome();
    render(home, summary, body);
  };

  void guardRadarView(body, load);
}
