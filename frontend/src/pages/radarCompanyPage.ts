import { formatRadarDate, guardRadarView, renderRadarError, renderScore } from '../components/radarUi';
import type {
  RadarAdminCompanyDetail,
  RadarAnalysis,
  RadarCompanyChange,
  RadarCompanyDetail,
  RadarRelevanceExplanation,
  RadarSimilarCompany
} from '../models/radar';
import {
  getRadarAdminCompany,
  getRadarAdminSession,
  getRadarCompany,
  getRadarRelevance,
  listRadarCompanyChanges,
  listSimilarRadarCompanies,
  recordRadarSignal,
  runRadarDeepDive,
  setRadarCompanyIgnored,
  unwatchRadarCompany,
  watchRadarCompany
} from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

const SIGNIFICANCE_TONE: Record<string, string> = {
  MAJOR: 'radar-tier radar-tier--major',
  IMPORTANT: 'radar-tier radar-tier--important',
  INTERESTING: 'radar-tier radar-tier--interesting',
  MINOR: 'radar-tier radar-tier--minor'
};

function list(items: string[], empty: string): string {
  return items.length
    ? `<ul class="radar-bullet-list">${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
    : `<p class="radar-muted">${escapeHtml(empty)}</p>`;
}

/** The full memo. Kept behind a disclosure so the profile stays scannable by default. */
function renderDeepDiveMemo(analysis: RadarAnalysis | null): string {
  if (!analysis) return '<div class="radar-empty">No Deep Dive has been generated yet.</div>';
  return `
    <section class="radar-memo-section">
      <h4>Executive summary</h4><p>${escapeHtml(analysis.summary)}</p>
      <p class="radar-muted">${escapeHtml(analysis.analysisOrigin)} via ${escapeHtml(analysis.provider)} /
        ${escapeHtml(analysis.model)}. Confidence: ${escapeHtml(analysis.confidence)}.</p>
    </section>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h4>Problem</h4><p>${escapeHtml(analysis.problem)}</p></section>
      <section class="radar-memo-section"><h4>Solution</h4><p>${escapeHtml(analysis.solution)}</p></section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h4>Business model</h4><p>${escapeHtml(analysis.businessModel)}</p></section>
      <section class="radar-memo-section"><h4>Stage</h4><p>${escapeHtml(analysis.stage)}</p></section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h4>Source-supported facts</h4>${list(analysis.facts, 'No facts captured.')}</section>
      <section class="radar-memo-section"><h4>Analyst inferences</h4>${list(analysis.inferences, 'No inferences captured.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h4>Technical differentiation</h4>${list(analysis.technicalDifferentiation, 'None captured.')}</section>
      <section class="radar-memo-section"><h4>Market signals</h4>${list(analysis.marketSignals, 'None captured.')}</section>
    </div>
    <div class="radar-two-column">
      <section class="radar-memo-section"><h4>Bull case</h4>${list(analysis.bullCase, 'Insufficient evidence.')}</section>
      <section class="radar-memo-section"><h4>Bear case</h4>${list(analysis.bearCase, 'Insufficient evidence.')}</section>
    </div>
    <section class="radar-memo-section"><h4>Unanswered questions</h4>${list(analysis.unansweredQuestions, 'None yet.')}</section>
    <section class="radar-memo-section">
      <h4>Score components</h4>
      <div class="radar-dimension-grid">
        ${Object.entries(analysis.radarDimensions).map(([key, value]) =>
          `<div><span>${escapeHtml(key.replace(/([A-Z])/g, ' $1'))}</span><strong>${value}</strong></div>`).join('')}
      </div>
      <p class="radar-muted">Radar score is deterministic and separate from Deal Scout investment scoring.</p>
    </section>
  `;
}

function renderProfile(detail: RadarCompanyDetail | RadarAdminCompanyDetail): string {
  const company = detail.company;
  const analysis = detail.latestAnalysis;
  const companyUrl = safeExternalUrl(company.websiteUrl);
  const admin = 'watchlistNotes' in detail ? detail : null;

  return `
    <div class="radar-deep-header">
      <div>
        <a class="radar-back-link" href="#/radar">Back to Radar Home</a>
        <h2>${escapeHtml(company.name)}${[company.accelerator, company.acceleratorBatch]
          .filter(Boolean).join(' ')
          ? `<span class="radar-badge">${escapeHtml([company.accelerator, company.acceleratorBatch]
              .filter(Boolean).join(' '))}</span>` : ''}</h2>
        <p class="radar-one-liner">${escapeHtml(analysis?.summary || company.description
          || 'No source summary captured yet.')}</p>
        <div class="radar-tags">
          ${company.sector && company.sector !== 'Unknown'
            ? `<span class="radar-tag">${escapeHtml(company.sector)}</span>` : ''}
          ${company.categories.map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}
          <span class="radar-meta">${company.sourceCount} source${company.sourceCount === 1 ? '' : 's'}</span>
          <span class="radar-meta">First seen ${escapeHtml(formatRadarDate(company.firstSeenAt))}</span>
        </div>
      </div>
      <div class="radar-deep-scores">
        ${renderScore('Radar', company.radarScore)}
        ${admin ? renderScore('Personal', admin.company.personalScore) : ''}
      </div>
    </div>

    <div class="form-actions form-actions--start radar-profile-actions">
      ${companyUrl ? `<a id="radar-visit-link" class="button button--secondary"
          href="${escapeAttribute(companyUrl)}" target="_blank" rel="noreferrer">Visit site</a>` : ''}
      ${admin ? `
        <button id="radar-deep-dive-button" class="button button--primary" type="button">Run Deep Dive</button>
        <button id="radar-watch-button" class="button button--secondary" type="button">
          ${admin.company.watched ? 'Update watch' : 'Watch'}</button>
        ${admin.company.watched
          ? '<button id="radar-unwatch-button" class="button button--secondary" type="button">Unwatch</button>' : ''}
        <button id="radar-ignore-button" class="button button--secondary" type="button">
          ${admin.company.ignored ? 'Restore' : 'Ignore'}</button>
      ` : ''}
    </div>
    <div id="radar-company-status" aria-live="polite"></div>

    <div class="radar-profile-grid">
      <section class="radar-panel">
        <h3>Why it matters</h3>
        ${list(analysis?.whyInteresting ?? [], 'No importance thesis captured yet.')}
        ${analysis?.whyItMatters ? `<p>${escapeHtml(analysis.whyItMatters)}</p>` : ''}
      </section>
      <section class="radar-panel">
        <h3>Why I care</h3>
        <div id="radar-relevance-block"><p class="radar-muted">Loading relevance...</p></div>
      </section>
    </div>

    <div class="radar-profile-grid">
      <section class="radar-panel">
        <h3>Traction</h3>
        ${list(analysis?.tractionSignals ?? [], 'No source-supported traction captured.')}
      </section>
      <section class="radar-panel">
        <h3>Funding and investors</h3>
        <p>${escapeHtml(analysis?.fundingSummary || 'Unknown from current sources.')}</p>
        ${list(analysis?.likelyInvestors ?? [], 'No source-supported investors captured.')}
      </section>
    </div>

    <div class="radar-profile-grid">
      <section class="radar-panel">
        <h3>Risks</h3>
        ${list(analysis?.risks ?? [], 'No risks captured yet.')}
      </section>
      <section class="radar-panel">
        <h3>Watch for</h3>
        ${list(analysis?.monitoringTriggers ?? [], 'No watch triggers captured yet.')}
      </section>
    </div>

    <section class="radar-panel">
      <div class="page-header page-header--row"><div><h3>Recent changes</h3>
        <p>Detected deterministically from stored snapshots and tiered by significance.</p></div></div>
      <div id="radar-change-block"><p class="radar-muted">Loading changes...</p></div>
    </section>

    <section class="radar-panel">
      <div class="page-header page-header--row"><div><h3>Similar startups</h3>
        <p>Ranked from shared categories, trends, sector and business model. No AI call is made.</p></div></div>
      <div id="radar-similar-block"><p class="radar-muted">Loading similar startups...</p></div>
    </section>

    ${admin ? `
      <section class="radar-panel radar-company-admin">
        <h3>Watchlist notes</h3>
        <div class="radar-admin-form-grid">
          <div class="form-field radar-form-span">
            <label for="radar-watch-notes">Notes</label>
            <textarea id="radar-watch-notes" maxlength="8000">${escapeHtml(admin.watchlistNotes || '')}</textarea>
          </div>
          <div class="form-field">
            <label for="radar-next-review">Next review</label>
            <input id="radar-next-review" type="datetime-local"
              value="${escapeAttribute((admin.nextReviewAt || '').slice(0, 16))}" />
          </div>
        </div>
      </section>
    ` : ''}

    <section class="radar-panel">
      <h3>Sources</h3>
      ${detail.researchSources.length ? `<div class="radar-source-list">${detail.researchSources.map((source) => `
        <div><strong>${escapeHtml(source.title)}</strong><span>${escapeHtml(source.sourceType)}</span>
        ${safeExternalUrl(source.url)
          ? `<a href="${escapeAttribute(safeExternalUrl(source.url))}" target="_blank" rel="noreferrer">Open</a>`
          : ''}</div>
      `).join('')}</div>` : '<p class="radar-muted">No research citations captured.</p>'}
    </section>

    <details class="radar-panel radar-deep-dive-details">
      <summary><h3>Deep Dive memo</h3></summary>
      <div class="radar-analysis">${renderDeepDiveMemo(analysis)}</div>
    </details>
  `;
}

function renderRelevance(explanation: RadarRelevanceExplanation): string {
  return `
    <div class="radar-relevance-score">${renderScore('Personal relevance', explanation.score)}</div>
    ${explanation.matchedInterests.length
      ? `<div class="radar-tags">${explanation.matchedInterests
          .map((interest) => `<span class="radar-tag">${escapeHtml(interest)}</span>`).join('')}</div>`
      : ''}
    ${list(explanation.reasons, 'No relevance reasons captured.')}
    <p class="radar-muted"><a href="#/radar-admin">Edit your interests</a> to change how this is scored.</p>
  `;
}

function renderChanges(changes: RadarCompanyChange[]): string {
  if (!changes.length) return '<p class="radar-muted">No changes detected since discovery.</p>';
  return `<div class="radar-change-list">${changes.map((change) => `
    <article class="radar-change-row">
      <div class="radar-change-row__head">
        <span class="${SIGNIFICANCE_TONE[change.significance] ?? SIGNIFICANCE_TONE.MINOR}">
          ${escapeHtml(change.significance)}</span>
        <span class="radar-meta">${escapeHtml(change.changeType.replaceAll('_', ' ').toLowerCase())}</span>
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

function renderSimilar(similar: RadarSimilarCompany[]): string {
  if (!similar.length) {
    return '<p class="radar-muted">No comparable company in your Radar yet. Discover more startups to '
      + 'populate this.</p>';
  }
  return `<div class="radar-similar-list">${similar.map((company) => `
    <article class="radar-similar-row">
      <div>
        <a href="#/radar/company/${company.companyId}"><strong>${escapeHtml(company.name)}</strong></a>
        <span class="radar-badge">${escapeHtml(company.relationship)}</span>
      </div>
      <div class="radar-tags">
        ${company.categories.slice(0, 4)
          .map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}
        <span class="radar-meta">Radar ${company.radarScore}</span>
        <span class="radar-meta">Match ${company.score}</span>
      </div>
      ${list(company.reasons, 'No shared attributes recorded.')}
    </article>`).join('')}</div>`;
}

export function renderRadarCompanyPage(): string {
  return '<div class="page radar-page" id="radar-company-detail">'
    + '<div class="radar-empty">Loading company research...</div></div>';
}

export function bindRadarCompanyPageEvents(root: HTMLElement, path: string): void {
  const container = root.querySelector<HTMLElement>('#radar-company-detail');
  const companyId = Number(path.split('/').pop());
  if (!container || !companyId) return;

  const load = async () => {
    const session = await getRadarAdminSession();
    const detail = session.authenticated
      ? await getRadarAdminCompany(companyId)
      : await getRadarCompany(companyId);
    container.innerHTML = renderProfile(detail);

    // Secondary panels load independently so one slow query cannot blank the profile.
    void fillPanel('#radar-relevance-block', () => getRadarRelevance(companyId).then(renderRelevance));
    void fillPanel('#radar-change-block', () => listRadarCompanyChanges(companyId, 12).then(renderChanges));
    void fillPanel('#radar-similar-block', () => listSimilarRadarCompanies(companyId, 6).then(renderSimilar));

    if (!session.authenticated || !('watchlistNotes' in detail)) return;
    bindAdminActions(detail as RadarAdminCompanyDetail);
  };

  const fillPanel = async (selector: string, produce: () => Promise<string>) => {
    const target = container.querySelector<HTMLElement>(selector);
    if (!target) return;
    try {
      target.innerHTML = await produce();
    } catch (error) {
      target.innerHTML = renderRadarError(error);
    }
  };

  const bindAdminActions = (adminDetail: RadarAdminCompanyDetail) => {
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
      void run(watchButton, async () => {
        await watchRadarCompany(companyId, notes, review);
        await recordRadarSignal(companyId, 'WATCH');
      }, 'Watchlist updated.');
    });

    const unwatchButton = container.querySelector<HTMLButtonElement>('#radar-unwatch-button');
    unwatchButton?.addEventListener('click', () => void run(unwatchButton,
      () => unwatchRadarCompany(companyId), 'Removed from watchlist.'));

    const deepDiveButton = container.querySelector<HTMLButtonElement>('#radar-deep-dive-button');
    deepDiveButton?.addEventListener('click', () => void run(deepDiveButton, async () => {
      await runRadarDeepDive(companyId);
      await recordRadarSignal(companyId, 'DEEP_DIVE');
    }, 'Deep Dive complete.'));

    const ignoreButton = container.querySelector<HTMLButtonElement>('#radar-ignore-button');
    ignoreButton?.addEventListener('click', () => void run(ignoreButton, async () => {
      const nextIgnored = !adminDetail.company.ignored;
      await setRadarCompanyIgnored(companyId, nextIgnored);
      if (nextIgnored) await recordRadarSignal(companyId, 'IGNORE');
    }, adminDetail.company.ignored ? 'Company restored.' : 'Company ignored.'));

    // Visiting the company's own site is a genuine interest signal, so record it before navigating.
    container.querySelector<HTMLAnchorElement>('#radar-visit-link')?.addEventListener('click', () => {
      void recordRadarSignal(companyId, 'VISIT').catch(() => undefined);
    });
  };

  void guardRadarView(container, load);
}
