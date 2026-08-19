import { formatRadarDate, guardRadarView, renderCompanyRows } from '../components/radarUi';
import type { RadarCompanyChange } from '../models/radar';
import { listRadarAdminCompanies, listRecentRadarChanges } from '../services/radarService';
import { escapeHtml } from '../utils/html';

const SIGNIFICANCE_TONE: Record<string, string> = {
  MAJOR: 'radar-tier radar-tier--major',
  IMPORTANT: 'radar-tier radar-tier--important',
  INTERESTING: 'radar-tier radar-tier--interesting',
  MINOR: 'radar-tier radar-tier--minor'
};

function renderChanges(changes: RadarCompanyChange[]): string {
  if (!changes.length) {
    return '<div class="radar-empty">No meaningful change detected on watched companies in the last 30 days. '
      + 'Trivial wording edits are deliberately ignored.</div>';
  }
  return `<div class="radar-change-list">${changes.map((change) => `
    <article class="radar-change-row">
      <div class="radar-change-row__head">
        <a href="#/radar/company/${change.companyId}"><strong>${escapeHtml(change.companyName)}</strong></a>
        <span class="${SIGNIFICANCE_TONE[change.significance] ?? SIGNIFICANCE_TONE.MINOR}">
          ${escapeHtml(change.significance)}</span>
        <span class="radar-meta">${escapeHtml(formatRadarDate(change.detectedAt))}</span>
      </div>
      <p>${escapeHtml(change.summary)}</p>
      ${change.previousValue && change.currentValue ? `
        <div class="radar-change-delta">
          <div><span>Previous</span><strong>${escapeHtml(change.previousValue)}</strong></div>
          <div><span>Current</span><strong>${escapeHtml(change.currentValue)}</strong></div>
        </div>` : ''}
      ${change.whyItMatters ? `<p class="radar-muted">${escapeHtml(change.whyItMatters)}</p>` : ''}
    </article>`).join('')}</div>`;
}

export function renderWatchlistPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header">
        <h2>Startup Watchlist</h2>
        <p>Companies you are following for product, traction, funding, or strategic changes.</p>
      </div>
      <section class="radar-panel">
        <div class="page-header page-header--row"><div><h3>Meaningful updates</h3>
          <p>Important and Major changes from the last 30 days, newest first.</p></div></div>
        <div id="radar-watchlist-changes"><div class="radar-empty">Loading updates...</div></div>
      </section>

      <section class="radar-panel">
        <div class="page-header page-header--row"><div><h3>Followed companies</h3></div></div>
        <div class="radar-company-list" id="radar-watchlist"><div class="radar-empty">Loading watchlist...</div></div>
      </section>
    </div>
  `;
}

export function bindWatchlistPageEvents(root: HTMLElement): void {
  const list = root.querySelector<HTMLElement>('#radar-watchlist');
  if (!list) return;
  const changesBlock = root.querySelector<HTMLElement>('#radar-watchlist-changes');

  // Errors propagate so guardRadarView can turn a 401 into a sign-in form.
  const load = async () => {
    const [companies, changes] = await Promise.all([
      listRadarAdminCompanies({ watched: true }),
      listRecentRadarChanges({ watched: true, minSignificance: 'IMPORTANT', days: 30, limit: 40 })
    ]);
    list.innerHTML = renderCompanyRows(companies);
    if (changesBlock) changesBlock.innerHTML = renderChanges(changes);
  };
  void guardRadarView(list, load);
}
