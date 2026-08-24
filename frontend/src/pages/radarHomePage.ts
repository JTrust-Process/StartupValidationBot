import { formatRadarDate, guardRadarView, renderScore } from '../components/radarUi';
import type {
  RadarCompanyChange,
  RadarHome,
  RadarHomeCompanyCard,
  RadarHomeSection,
  RadarTrendDetail,
  RadarOffering
} from '../models/radar';
import { getRadarHome, listRadarOfferings } from '../services/radarService';
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
      <div class="page-header page-header--row intelligence-feed-header">
        <div>
          <p class="page-eyebrow">Startup research</p>
          <h2>Intelligence Feed</h2>
          <p>Your daily startup discovery and research terminal.</p>
          <p class="radar-disclaimer">Radar scores measure research importance, not investment quality.</p>
        </div>
        <a class="button button--primary" href="#/radar/all">Search companies</a>
      </div>

      <section class="radar-summary" id="radar-home-summary"></section>
      <section class="radar-daily-brief" id="radar-daily-brief" aria-labelledby="daily-brief-title"></section>
      <div id="radar-home-body"><div class="radar-empty">Loading Radar Home...</div></div>
    </div>
  `;
}

function bullets(items: string[], limit: number): string {
  if (!items.length) return '';
  return `<ul class="radar-bullet-list">${items.slice(0, limit)
    .map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
}

function companyCard(card: RadarHomeCompanyCard, offeringCompanyIds: ReadonlySet<number>): string {
  const batch = [card.accelerator, card.acceleratorBatch].filter(Boolean).join(' ');
  const initial = card.name.trim().charAt(0).toUpperCase() || '?';
  return `
    <article class="radar-home-card" data-company-id="${card.id}">
      <header class="radar-home-card__head">
        <div class="radar-home-card__identity">
          <span class="radar-company-initial" aria-hidden="true">${escapeHtml(initial)}</span>
          <div>
            <a class="radar-home-card__name" href="#/radar/company/${card.id}">${escapeHtml(card.name)}</a>
            <p>${escapeHtml(card.sector || card.categories[0] || 'Unknown sector')}</p>
          </div>
        </div>
        <div class="radar-home-card__badges">
          ${batch ? `<span class="radar-badge">${escapeHtml(batch)}</span>` : ''}
          ${card.watched ? '<span class="radar-badge radar-badge--watch">Watching</span>' : ''}
          ${offeringCompanyIds.has(card.id) ? '<span class="radar-badge radar-badge--offering">Offering Found</span>' : ''}
        </div>
      </header>

      <div class="radar-home-card__scores">
        ${renderScore('Radar', card.radarScore)}
        ${renderScore('Personal', card.personalScore)}
      </div>

      <p class="radar-home-card__summary">${escapeHtml(card.description || 'No source summary captured yet.')}</p>

      <div class="radar-tags">
        ${card.categories.slice(0, 3).map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}
        <span class="radar-meta">${card.sourceCount} source${card.sourceCount === 1 ? '' : 's'}</span>
        ${card.highlight ? `<span class="radar-highlight">${escapeHtml(card.highlight)}</span>` : ''}
      </div>

      <details class="radar-card-disclosure">
        <summary>
          <span>Why is this on my Radar?</span>
          <span class="radar-disclosure-icon" aria-hidden="true">+</span>
        </summary>
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
      </details>

      <footer class="radar-home-card__actions">
        <a class="radar-card-profile-link" href="#/radar/company/${card.id}">View Full Profile <span aria-hidden="true">&rarr;</span></a>
      </footer>
    </article>
  `;
}

function changeRow(change: RadarCompanyChange): string {
  const tone = SIGNIFICANCE_TONE[change.significance] ?? SIGNIFICANCE_TONE.MINOR;
  return `
    <article class="radar-change-row">
      <span class="radar-signal-icon" aria-hidden="true">${escapeHtml(change.companyName.charAt(0) || '!')}</span>
      <div class="radar-change-row__content">
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
      </div>
    </article>
  `;
}

function trendRow(trend: RadarTrendDetail): string {
  const glyph = VELOCITY_GLYPH[trend.velocityDirection] ?? '·';
  const momentum = Math.max(0, Math.min(100, trend.momentumScore));
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
      <div class="radar-trend-bar" aria-label="Momentum ${momentum} out of 100">
        <span style="width: ${momentum}%"></span>
      </div>
      <div class="radar-tags">
        <span class="radar-meta">${trend.companyCount} compan${trend.companyCount === 1 ? 'y' : 'ies'}</span>
        ${trend.companies.slice(0, 4).map((company) =>
          `<a class="radar-tag" href="#/radar/company/${company.id}">${escapeHtml(company.name)}</a>`).join('')}
      </div>
    </article>
  `;
}

const SECTION_TITLES: Record<string, string> = {
  'watchlist-updates': "What's Changed",
  'new-today': 'New on Radar',
  'recently-funded': 'Recently Funded',
  'best-matches': 'Best Matches',
  'high-momentum': 'Trending Companies',
  'emerging-trends': 'Emerging Trends'
};

function sectionHtml(section: RadarHomeSection, offeringCompanyIds: ReadonlySet<number>): string {
  let body: string;
  let count: number;

  if (section.kind === 'COMPANIES') {
    count = section.companies.length;
    body = count
      ? `<div class="radar-home-grid">${section.companies.map((company) => companyCard(company, offeringCompanyIds)).join('')}</div>`
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
          <h3>${escapeHtml(SECTION_TITLES[section.key] || section.title)}</h3>
          <p>${escapeHtml(section.subtitle)}</p>
        </div>
        <span class="radar-meta">${count}</span>
      </div>
      ${body}
    </section>
  `;
}

function dailyBrief(home: RadarHome): string {
  const companies = home.sections.flatMap((section) => section.companies);
  const strongestCompany = [...companies].sort((a, b) => b.radarScore - a.radarScore)[0];
  const strongestChange = home.sections.flatMap((section) => section.changes)[0];
  const strongestTrend = home.sections.flatMap((section) => section.trends)[0];
  const facts = [
    strongestCompany
      ? `<li><strong>${escapeHtml(strongestCompany.name)}</strong> has the strongest current Radar score at ${strongestCompany.radarScore}.</li>`
      : '',
    strongestChange
      ? `<li>The leading recent signal is <strong>${escapeHtml(strongestChange.companyName)}</strong>: ${escapeHtml(strongestChange.summary)}</li>`
      : '',
    strongestTrend
      ? `<li><strong>${escapeHtml(strongestTrend.name)}</strong> is the strongest visible trend, supported by ${strongestTrend.companyCount} companies.</li>`
      : '',
    home.newSinceYesterday > 0
      ? `<li>${home.newSinceYesterday} compan${home.newSinceYesterday === 1 ? 'y was' : 'ies were'} added in the last 24 hours.</li>`
      : ''
  ].filter(Boolean);

  return `
    <div>
      <p class="page-eyebrow">Deterministic snapshot</p>
      <h3 id="daily-brief-title">Daily Radar Brief</h3>
      ${facts.length ? `<ul>${facts.join('')}</ul>` : '<p class="radar-muted">Not enough current data for a brief yet.</p>'}
    </div>
    <button class="button button--secondary" type="button" disabled title="Full daily briefs are not available yet">Full Daily Brief</button>
  `;
}

function render(home: RadarHome, offerings: RadarOffering[], summary: HTMLElement, brief: HTMLElement, body: HTMLElement): void {
  const activeTrends = home.sections.flatMap((section) => section.trends).length;
  summary.innerHTML = `
    <div><span>Companies tracked</span><strong>${home.totalCompanies}</strong></div>
    <div><span>New in 24h</span><strong>${home.newSinceYesterday}</strong></div>
    <div><span>Meaningful changes (14d)</span><strong>${home.meaningfulChanges}</strong></div>
    <div><span>Active trends</span><strong>${activeTrends}</strong></div>
  `;
  brief.innerHTML = dailyBrief(home);
  const offeringCompanyIds = new Set(offerings
    .filter((offering) => offering.matchStatus === 'CONFIRMED'
      && ['ACTIVE', 'POSSIBLY_ACTIVE'].includes(offering.status) && offering.radarCompanyId !== null)
    .map((offering) => offering.radarCompanyId as number));
  body.innerHTML = home.sections.map((section) => sectionHtml(section, offeringCompanyIds)).join('');
}

export function bindRadarHomePageEvents(root: HTMLElement): void {
  const summary = root.querySelector<HTMLElement>('#radar-home-summary');
  const brief = root.querySelector<HTMLElement>('#radar-daily-brief');
  const body = root.querySelector<HTMLElement>('#radar-home-body');
  if (!summary || !brief || !body) return;

  // Errors propagate so guardRadarView can turn a 401 into a sign-in form.
  const load = async () => {
    const [home, offerings] = await Promise.all([getRadarHome(), listRadarOfferings({ matchStatus: 'CONFIRMED' })]);
    render(home, offerings, summary, brief, body);
  };

  void guardRadarView(body, load);
}
