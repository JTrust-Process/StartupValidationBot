import { formatRadarDate, renderRadarError, renderScore } from '../components/radarUi';
import type { RadarAdminCompanyDetail, RadarAnalysis, RadarCompanyDetail } from '../models/radar';
import {
  getRadarAdminCompany,
  getRadarAdminSession,
  getRadarCompany,
  runRadarDeepDive,
  setRadarCompanyIgnored,
  unwatchRadarCompany,
  watchRadarCompany
} from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

function list(items: string[], empty: string): string {
  return items.length
    ? `<ul class="radar-bullet-list">${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
    : `<p class="radar-muted">${escapeHtml(empty)}</p>`;
}

function renderAnalysis(analysis: RadarAnalysis | null): string {
  if (!analysis) return '<div class="radar-empty">No analysis has been generated yet.</div>';
  return `
    <section class="radar-memo-section">
      <h3>Executive summary</h3><p>${escapeHtml(analysis.summary)}</p>
      <p class="radar-muted">${escapeHtml(analysis.analysisOrigin)} via ${escapeHtml(analysis.provider)} / ${escapeHtml(analysis.model)}. Confidence: ${escapeHtml(analysis.confidence)}.</p>
    </section>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Problem</h3><p>${escapeHtml(analysis.problem)}</p></section>
      <section class="radar-memo-section"><h3>Solution</h3><p>${escapeHtml(analysis.solution)}</p></section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Business model</h3><p>${escapeHtml(analysis.businessModel)}</p></section>
      <section class="radar-memo-section"><h3>Stage</h3><p>${escapeHtml(analysis.stage)}</p></section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Founders</h3>${list(analysis.founders, 'Founder details remain unknown.')}</section>
      <section class="radar-memo-section"><h3>Funding and investors</h3><p>${escapeHtml(analysis.fundingSummary || 'Unknown')}</p>${list(analysis.likelyInvestors, 'No source-supported investors captured.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Source-supported facts</h3>${list(analysis.facts, 'No facts captured.')}</section>
      <section class="radar-memo-section"><h3>Analyst inferences</h3>${list(analysis.inferences, 'No inferences captured.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Why it is interesting</h3>${list(analysis.whyInteresting, 'No importance thesis yet.')}</section>
      <section class="radar-memo-section"><h3>Momentum signals</h3>${list(analysis.momentumSignals, 'No momentum signal captured.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Traction</h3>${list(analysis.tractionSignals, 'No source-supported traction captured.')}</section>
      <section class="radar-memo-section"><h3>Technical differentiation</h3>${list(analysis.technicalDifferentiation, 'No technical differentiation captured.')}</section>
    </div>
    <section class="radar-memo-section"><h3>Market signals</h3>${list(analysis.marketSignals, 'No market signals captured.')}</section>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Bull case</h3>${list(analysis.bullCase, 'Insufficient evidence for a bull case.')}</section>
      <section class="radar-memo-section"><h3>Bear case</h3>${list(analysis.bearCase, 'Insufficient evidence for a bear case.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Risks and unknowns</h3>${list(analysis.risks, 'No risks captured.')}</section>
      <section class="radar-memo-section"><h3>Unanswered questions</h3>${list(analysis.unansweredQuestions, 'No follow-up questions yet.')}</section>
    </div>
    <section class="radar-memo-section"><h3>Why it matters</h3><p>${escapeHtml(analysis.whyItMatters)}</p></section>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h3>Trend tags</h3>${list(analysis.trendTags, 'No trend tags captured.')}</section>
      <section class="radar-memo-section"><h3>Monitoring triggers</h3>${list(analysis.monitoringTriggers, 'No monitoring triggers captured.')}</section>
    </div>
    <section class="radar-memo-section">
      <h3>Score components</h3>
      <div class="radar-dimension-grid">
        ${Object.entries(analysis.radarDimensions).map(([key, value]) => `<div><span>${escapeHtml(key.replace(/([A-Z])/g, ' $1'))}</span><strong>${value}</strong></div>`).join('')}
      </div>
      ${list(analysis.radarScoreInputs, 'No additional AI score evidence captured.')}
      <p class="radar-muted">Radar score remains deterministic and is separate from Deal Scout investment scoring.</p>
    </section>
  `;
}

function renderDetail(detail: RadarCompanyDetail | RadarAdminCompanyDetail): string {
  const company = detail.company;
  const analysis = detail.latestAnalysis;
  const companyUrl = safeExternalUrl(company.websiteUrl);
  const admin = 'watchlistNotes' in detail ? detail : null;
  return `
    <div class="radar-deep-header">
      <div>
        <a class="radar-back-link" href="#/radar">Back to Radar</a>
        <h2>${escapeHtml(company.name)}</h2>
        <p>${escapeHtml(company.description || 'No source summary captured yet.')}</p>
        <div class="radar-tags"><span class="radar-tag">${escapeHtml(company.sector)}</span>${company.categories.map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}</div>
      </div>
      <div class="radar-deep-scores">${renderScore('Radar', company.radarScore)}${admin ? renderScore('Personal', admin.company.personalScore) : ''}</div>
    </div>
    <div class="form-actions form-actions--start">
      ${companyUrl ? `<a class="button button--secondary" href="${escapeAttribute(companyUrl)}" target="_blank" rel="noreferrer">Visit company</a>` : ''}
    </div>
    <div id="radar-company-status" aria-live="polite"></div>
    ${admin ? `
      <section class="radar-panel radar-company-admin">
        <div class="page-header page-header--row"><div><h3>Private controls</h3><p>${admin.company.watched ? 'Watching this company.' : 'Not currently watched.'}</p></div><span class="status-pill">${admin.company.ignored ? 'Ignored' : 'Active'}</span></div>
        <div class="radar-admin-form-grid">
          <div class="form-field radar-form-span"><label for="radar-watch-notes">Watchlist notes</label><textarea id="radar-watch-notes" maxlength="8000">${escapeHtml(admin.watchlistNotes || '')}</textarea></div>
          <div class="form-field"><label for="radar-next-review">Next review</label><input id="radar-next-review" type="datetime-local" value="${escapeAttribute((admin.nextReviewAt || '').slice(0, 16))}" /></div>
        </div>
        <div class="form-actions form-actions--start">
          <button id="radar-watch-button" class="button button--primary" type="button">${admin.company.watched ? 'Update watch' : 'Watch'}</button>
          ${admin.company.watched ? '<button id="radar-unwatch-button" class="button button--secondary" type="button">Remove from watchlist</button>' : ''}
          <button id="radar-deep-dive-button" class="button button--secondary" type="button">Run Deep Dive</button>
          <button id="radar-ignore-button" class="button button--secondary" type="button">${admin.company.ignored ? 'Restore' : 'Ignore'}</button>
        </div>
      </section>
    ` : '<section class="radar-panel"><p class="radar-muted"><a href="#/radar-admin">Sign in</a> to watch, ignore, or run a Deep Dive.</p></section>'}
    <section class="radar-analysis">${renderAnalysis(analysis)}</section>
    <section class="radar-panel">
      <h3>Research sources</h3>
      ${detail.researchSources.length ? `<div class="radar-source-list">${detail.researchSources.map((source) => `
        <div><strong>${escapeHtml(source.title)}</strong><span>${escapeHtml(source.sourceType)}</span>${safeExternalUrl(source.url) ? `<a href="${escapeAttribute(safeExternalUrl(source.url))}" target="_blank" rel="noreferrer">Open source</a>` : ''}</div>
      `).join('')}</div>` : '<p class="radar-muted">No research citations captured.</p>'}
    </section>
    <section class="radar-panel">
      <h3>Change history</h3>
      ${detail.snapshots.length ? `<div class="radar-snapshot-list">${detail.snapshots.map((snapshot) => `<div><strong>${escapeHtml(formatRadarDate(snapshot.capturedAt))}</strong><span>${escapeHtml(snapshot.notableChanges.join(', '))}</span></div>`).join('')}</div>` : '<p class="radar-muted">No snapshots captured.</p>'}
    </section>
  `;
}

export function renderRadarCompanyPage(): string {
  return '<div class="page radar-page" id="radar-company-detail"><div class="radar-empty">Loading company research...</div></div>';
}

export function bindRadarCompanyPageEvents(root: HTMLElement, path: string): void {
  const container = root.querySelector<HTMLElement>('#radar-company-detail');
  const companyId = Number(path.split('/').pop());
  if (!container || !companyId) return;

  const load = async () => {
    try {
      const session = await getRadarAdminSession();
      const detail = session.authenticated
        ? await getRadarAdminCompany(companyId)
        : await getRadarCompany(companyId);
      container.innerHTML = renderDetail(detail);
      if (!session.authenticated || !('watchlistNotes' in detail)) return;
      const adminDetail = detail as RadarAdminCompanyDetail;
      const status = container.querySelector<HTMLElement>('#radar-company-status');
      const run = async (button: HTMLButtonElement | null, action: () => Promise<unknown>, message: string) => {
        if (button) button.disabled = true;
        try {
          await action();
          if (status) status.innerHTML = `<div class="alert alert--success">${escapeHtml(message)}</div>`;
          await load();
        } catch (error) {
          if (status) status.innerHTML = renderRadarError(error);
        } finally {
          if (button) button.disabled = false;
        }
      };
      const watchButton = container.querySelector<HTMLButtonElement>('#radar-watch-button');
      watchButton?.addEventListener('click', () => {
        const notes = container.querySelector<HTMLTextAreaElement>('#radar-watch-notes')?.value || '';
        const review = container.querySelector<HTMLInputElement>('#radar-next-review')?.value || null;
        void run(watchButton, () => watchRadarCompany(companyId, notes, review), 'Watchlist updated.');
      });
      const unwatchButton = container.querySelector<HTMLButtonElement>('#radar-unwatch-button');
      unwatchButton?.addEventListener('click', () => void run(unwatchButton,
        () => unwatchRadarCompany(companyId), 'Removed from watchlist.'));
      const deepDiveButton = container.querySelector<HTMLButtonElement>('#radar-deep-dive-button');
      deepDiveButton?.addEventListener('click', () => void run(deepDiveButton,
        () => runRadarDeepDive(companyId), 'Deep Dive complete.'));
      const ignoreButton = container.querySelector<HTMLButtonElement>('#radar-ignore-button');
      ignoreButton?.addEventListener('click', () => void run(ignoreButton,
        () => setRadarCompanyIgnored(companyId, !adminDetail.company.ignored),
        adminDetail.company.ignored ? 'Company restored.' : 'Company ignored.'));
    } catch (error) {
      container.innerHTML = renderRadarError(error);
    }
  };

  void load();
}
