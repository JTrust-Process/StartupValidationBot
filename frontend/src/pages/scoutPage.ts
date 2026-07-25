import type { Deal } from '../models/deal';
import type {
  DigestDay,
  ScoutPreferences,
  WatchlistSourceInput,
  WatchlistSourceType
} from '../models/scout';
import {
  addWatchlistSource,
  generateScoutDigest,
  getReviewCandidates,
  getScoutDashboardSummary,
  getSecurityTypeOptions,
  getSourceTypeLabel,
  loadDealScoutState,
  removeWatchlistSource,
  runDealScoutDigestJob,
  runDealScoutOnce,
  saveDealScoutPreferences,
  sendScoutDigestEmail,
  toggleWatchlistSource
} from '../services/dealScoutService';
import { getDeals } from '../services/dealService';
import { formatDate, formatSecurityType } from '../utils/formatters';
import { escapeAttribute, escapeHtml } from '../utils/html';

const SOURCE_TYPES: WatchlistSourceType[] = [
  'MANUAL',
  'SEC_EDGAR',
  'REPUBLIC',
  'WEFUNDER',
  'STARTENGINE',
  'DEALMAKER',
  'FUNDRISE',
  'JARSY',
  'ROSS_PRE_IPO',
  'OTHER'
];

const DIGEST_DAYS: DigestDay[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY'
];

function selected(current: string | undefined, value: string): string {
  return current === value ? 'selected' : '';
}

function checked(value: boolean): string {
  return value ? 'checked' : '';
}

function sourceTypeOptions(currentValue: WatchlistSourceType = 'MANUAL'): string {
  return SOURCE_TYPES.map(
    (sourceType) => `
      <option value="${sourceType}" ${selected(currentValue, sourceType)}>
        ${escapeHtml(getSourceTypeLabel(sourceType))}
      </option>
    `
  ).join('');
}

function dealOptions(deals: Deal[]): string {
  return deals
    .map(
      (deal) => `
        <option value="${deal.id}">
          ${escapeHtml(deal.companyName)}
        </option>
      `
    )
    .join('');
}

function renderPreferencesForm(preferences: ScoutPreferences): string {
  const securityTypes = getSecurityTypeOptions();

  return `
    <form class="form-grid" id="scout-preferences-form">
      <div class="form-field">
        <label for="preferredThemes">Preferred Sectors / Themes</label>
        <input id="preferredThemes" name="preferredThemes" type="text" value="${escapeAttribute(preferences.preferredThemes)}" placeholder="AI, fintech, climate, defense..." />
      </div>

      <div class="form-field">
        <label for="excludedSectors">Excluded Sectors</label>
        <input id="excludedSectors" name="excludedSectors" type="text" value="${escapeAttribute(preferences.excludedSectors)}" placeholder="crypto, cannabis..." />
      </div>

      <div class="form-field">
        <label for="maxMinimumInvestment">Max Minimum Investment</label>
        <input id="maxMinimumInvestment" name="maxMinimumInvestment" type="number" min="0" step="1" value="${preferences.maxMinimumInvestment ?? ''}" placeholder="500" />
      </div>

      <div class="form-field">
        <label for="maxRedFlags">Max Red Flags</label>
        <input id="maxRedFlags" name="maxRedFlags" type="number" min="0" step="1" value="${preferences.maxRedFlags}" />
      </div>

      <label class="toggle-field">
        <input name="requireNonAccreditedEligibility" type="checkbox" ${checked(preferences.requireNonAccreditedEligibility)} />
        Require non-accredited eligibility
      </label>

      <label class="toggle-field">
        <input name="requireRegCfOrRegA" type="checkbox" ${checked(preferences.requireRegCfOrRegA)} />
        Require Reg CF or Reg A
      </label>

      <div class="form-field">
        <label for="weeklyDigestDay">Weekly Digest Day</label>
        <select id="weeklyDigestDay" name="weeklyDigestDay">
          ${DIGEST_DAYS.map(
            (day) => `<option value="${day}" ${selected(preferences.weeklyDigestDay, day)}>${escapeHtml(day)}</option>`
          ).join('')}
        </select>
      </div>

      <div class="form-field">
        <label for="weeklyDigestTime">Weekly Digest Time</label>
        <input id="weeklyDigestTime" name="weeklyDigestTime" type="time" value="${escapeAttribute(preferences.weeklyDigestTime)}" />
      </div>

      <div class="form-field form-field--full">
        <label for="emailRecipient">Email Recipient</label>
        <input id="emailRecipient" name="emailRecipient" type="email" value="${escapeAttribute(preferences.emailRecipient)}" placeholder="you@example.com" />
      </div>

      <div class="form-field form-field--full">
        <label>Preferred Security Types</label>
        <div class="checklist-grid">
          ${securityTypes
            .map(
              (securityType) => `
                <label class="checklist-item">
                  <input name="preferredSecurityTypes" type="checkbox" value="${securityType}" ${checked(preferences.preferredSecurityTypes.includes(securityType))} />
                  <span>${formatSecurityType(securityType)}</span>
                </label>
              `
            )
            .join('')}
        </div>
      </div>

      <div class="form-actions">
        <button class="button button--primary" type="submit">Save Preferences</button>
      </div>
    </form>
  `;
}

function renderSourceForm(deals: Deal[]): string {
  return `
    <form class="form-grid" id="scout-source-form">
      <div class="form-field">
        <label for="sourceType">Source Type</label>
        <select id="sourceType" name="sourceType">
          ${sourceTypeOptions()}
        </select>
      </div>

      <div class="form-field">
        <label for="sourceCompanyName">Company Name</label>
        <input id="sourceCompanyName" name="companyName" type="text" placeholder="Optional but helpful" />
      </div>

      <div class="form-field">
        <label for="linkedDealId">Linked Deal</label>
        <select id="linkedDealId" name="dealId">
          <option value="">No linked deal</option>
          ${dealOptions(deals)}
        </select>
      </div>

      <label class="toggle-field">
        <input name="enabled" type="checkbox" checked />
        Enabled
      </label>

      <div class="form-field form-field--full">
        <label for="sourceUrl">Public URL</label>
        <input id="sourceUrl" name="url" type="url" placeholder="SEC filing URL or public source URL" />
      </div>

      <div class="form-field form-field--full">
        <label for="sourceNotes">Notes</label>
        <textarea id="sourceNotes" name="notes" rows="3" placeholder="Why track this source, what to watch, or manual context..."></textarea>
      </div>

      <div class="form-field form-field--full">
        <label for="pastedText">Pasted Source Text</label>
        <textarea id="pastedText" name="pastedText" rows="7" placeholder="For Manual sources, paste allowed campaign page, filing, newsletter, or notes text here."></textarea>
      </div>

      <div class="form-actions">
        <button class="button button--primary" type="submit">Add Watchlist Source</button>
      </div>
    </form>
  `;
}

function renderSourcesTable(): string {
  const state = loadDealScoutState();

  if (!state.sources.length) {
    return '<p class="empty-copy">No sources yet. Add a Manual source with pasted text to start safely.</p>';
  }

  return `
    <table class="data-table">
      <thead>
        <tr>
          <th>Source</th>
          <th>Company</th>
          <th>Status</th>
          <th>Last Checked</th>
          <th>Notes</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${state.sources
          .map(
            (source) => `
              <tr>
                <td>
                  <div>${escapeHtml(getSourceTypeLabel(source.sourceType))}</div>
                  <div class="table-subtext">${source.enabled ? 'Enabled' : 'Paused'}</div>
                  ${source.url ? `<div class="table-subtext">${escapeHtml(source.url)}</div>` : ''}
                </td>
                <td>${escapeHtml(source.companyName || '-')}</td>
                <td>
                  <span class="status-chip status-chip--${source.lastStatus === 'ERROR' || source.lastStatus === 'NEEDS_MANUAL_PASTE' ? 'pass' : source.lastStatus === 'OK' ? 'invest-small' : 'watch'}">
                    ${escapeHtml(source.lastStatus.replace(/_/g, ' '))}
                  </span>
                  ${source.lastError ? `<div class="table-subtext">${escapeHtml(source.lastError)}</div>` : ''}
                </td>
                <td>${formatDate(source.lastCheckedAt)}</td>
                <td>${escapeHtml(source.notes || source.pastedText.slice(0, 120) || '-')}</td>
                <td>
                  <div class="workspace-actions workspace-actions--wrap">
                    <button class="button button--secondary button--small" type="button" data-toggle-source="${source.id}">
                      ${source.enabled ? 'Pause' : 'Enable'}
                    </button>
                    <button class="button button--danger button--small" type="button" data-delete-source="${source.id}">
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            `
          )
          .join('')}
      </tbody>
    </table>
  `;
}

function renderCandidates(): string {
  const candidates = getReviewCandidates().slice(0, 8);

  if (!candidates.length) {
    return '<p class="empty-copy">No candidates match the current preferences. Add sources or loosen filters.</p>';
  }

  return `
    <table class="data-table">
      <thead>
        <tr>
          <th>Company</th>
          <th>Source</th>
          <th>Score</th>
          <th>Why Matched</th>
          <th>Next Step</th>
        </tr>
      </thead>
      <tbody>
        ${candidates
          .map(
            (candidate) => `
              <tr>
                <td>
                  <a class="table-link" href="${escapeAttribute(candidate.appLink)}">${escapeHtml(candidate.companyName)}</a>
                  <div class="table-subtext">${escapeHtml(candidate.sector)}</div>
                </td>
                <td>${escapeHtml(candidate.platformOrSource)}</td>
                <td><strong>${candidate.score}</strong></td>
                <td>
                  <div class="suggestion-tags suggestion-tags--inline">
                    ${candidate.whyMatched.slice(0, 4).map((reason) => `<span>${escapeHtml(reason)}</span>`).join('')}
                  </div>
                </td>
                <td>${escapeHtml(candidate.suggestedNextStep)}</td>
              </tr>
            `
          )
          .join('')}
      </tbody>
    </table>
  `;
}

function renderNotableChanges(): string {
  const snapshots = loadDealScoutState().snapshots.filter((snapshot) => snapshot.notableChanges.length).slice(0, 8);

  if (!snapshots.length) {
    return '<p class="empty-copy">No notable changes captured yet.</p>';
  }

  return `
    <div class="mini-list">
      ${snapshots
        .map(
          (snapshot) => `
            <div class="mini-list__item mini-list__item--stacked">
              <strong>Source ${snapshot.sourceId} on ${formatDate(snapshot.checkedAt)}</strong>
              <span>${escapeHtml(snapshot.notableChanges.join(', '))}</span>
            </div>
          `
        )
        .join('')}
    </div>
  `;
}

function readPreferences(form: HTMLFormElement): ScoutPreferences {
  const formData = new FormData(form);
  const maxMinimumInvestmentValue = String(formData.get('maxMinimumInvestment') ?? '').trim();
  const maxRedFlagsValue = Number(formData.get('maxRedFlags') ?? 4);

  return {
    preferredThemes: String(formData.get('preferredThemes') ?? '').trim(),
    maxMinimumInvestment: maxMinimumInvestmentValue ? Number(maxMinimumInvestmentValue) : undefined,
    excludedSectors: String(formData.get('excludedSectors') ?? '').trim(),
    maxRedFlags: Number.isFinite(maxRedFlagsValue) ? Math.max(0, Math.trunc(maxRedFlagsValue)) : 4,
    requireNonAccreditedEligibility: formData.get('requireNonAccreditedEligibility') === 'on',
    requireRegCfOrRegA: formData.get('requireRegCfOrRegA') === 'on',
    preferredSecurityTypes: formData.getAll('preferredSecurityTypes') as ScoutPreferences['preferredSecurityTypes'],
    weeklyDigestDay: String(formData.get('weeklyDigestDay') ?? 'FRIDAY') as DigestDay,
    weeklyDigestTime: String(formData.get('weeklyDigestTime') ?? '09:00'),
    emailRecipient: String(formData.get('emailRecipient') ?? '').trim()
  };
}

function readSourceInput(form: HTMLFormElement): WatchlistSourceInput {
  const formData = new FormData(form);
  const dealIdValue = String(formData.get('dealId') ?? '').trim();

  return {
    sourceType: String(formData.get('sourceType') ?? 'MANUAL') as WatchlistSourceType,
    dealId: dealIdValue ? Number(dealIdValue) : undefined,
    url: String(formData.get('url') ?? '').trim(),
    companyName: String(formData.get('companyName') ?? '').trim(),
    enabled: formData.get('enabled') === 'on',
    notes: String(formData.get('notes') ?? '').trim(),
    pastedText: String(formData.get('pastedText') ?? '').trim()
  };
}

function refreshScoutPage(): void {
  window.dispatchEvent(new HashChangeEvent('hashchange'));
}

export function renderScoutPage(): string {
  const state = loadDealScoutState();
  const summary = getScoutDashboardSummary();
  const deals = getDeals();
  const preview = state.lastDigestPreview || generateScoutDigest().body;

  return `
    <div class="page">
      <div class="page-header page-header--row">
        <div>
          <h2>Deal Scout</h2>
          <p>Monitor allowed sources, prioritize deals to review, and preview the weekly research digest.</p>
        </div>

        <div class="workspace-actions workspace-actions--wrap">
          <button class="button button--secondary" id="run-scout-button" type="button">Run Scout Now</button>
          <button class="button button--primary" id="run-digest-job-button" type="button">Run Digest Job</button>
        </div>
      </div>

      <div class="notice notice--warning">
        Deal Scout is for research triage only. It does not recommend investments and it must not bypass logins, paywalls, captchas, robots.txt, rate limits, anti-bot protections, or source Terms of Service.
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Sources Monitored</h3>
          <p class="metric">${summary.enabledSourceCount}</p>
          <p class="metric-subtext">${summary.sourceCount} total sources</p>
        </div>

        <div class="card">
          <h3>Source Errors</h3>
          <p class="metric">${summary.sourcesWithErrors.length}</p>
          <p class="metric-subtext">Public fetch or source issues</p>
        </div>

        <div class="card">
          <h3>Top Candidate</h3>
          <p class="metric">${summary.topCandidates[0]?.score ?? '-'}</p>
          <p class="metric-subtext">${escapeHtml(summary.topCandidates[0]?.companyName ?? 'No match yet')}</p>
        </div>

        <div class="card">
          <h3>Next Digest</h3>
          <p class="metric metric--text">${escapeHtml(summary.nextDigestLabel)}</p>
          <p class="metric-subtext">${escapeHtml(state.preferences.emailRecipient || 'No recipient set')}</p>
        </div>
      </div>

      <div class="split-grid">
        <div class="card">
          <div class="page-header">
            <h3>Scout Preferences</h3>
            <p>Controls candidate scoring, filtering, and digest timing.</p>
          </div>
          ${renderPreferencesForm(state.preferences)}
        </div>

        <div class="card">
          <div class="page-header">
            <h3>Add Watchlist Source</h3>
            <p>Manual pasted text works fully. Public fetches are polite, limited, and may require manual paste if blocked.</p>
          </div>
          ${renderSourceForm(deals)}
        </div>
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Watchlist Sources</h3>
          <p>Tracked URLs, filings, and manual pasted source text.</p>
        </div>
        ${renderSourcesTable()}
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Top Review Candidates</h3>
          <p>Prioritized companies to consider researching, not companies to invest in.</p>
        </div>
        ${renderCandidates()}
      </div>

      <div class="split-grid">
        <div class="card">
          <div class="page-header">
            <h3>Notable Changes</h3>
            <p>Snapshot changes worth reviewing manually.</p>
          </div>
          ${renderNotableChanges()}
        </div>

        <div class="card">
          <div class="page-header">
            <h3>Manual Paste Needed</h3>
            <p>Sources where public fetch is unavailable or a pasted update is needed.</p>
          </div>
          ${
            summary.manualUpdateSources.length
              ? `<div class="mini-list">${summary.manualUpdateSources
                  .map(
                    (source) => `
                      <div class="mini-list__item mini-list__item--stacked">
                        <strong>${escapeHtml(source.companyName || getSourceTypeLabel(source.sourceType))}</strong>
                        <span>${escapeHtml(source.lastError || 'Add pasted source text before running Scout.')}</span>
                      </div>
                    `
                  )
                  .join('')}</div>`
              : '<p class="empty-copy">No manual source updates needed.</p>'
          }
        </div>
      </div>

      <div class="card">
        <div class="page-header page-header--row">
          <div>
            <h3>Email Digest Preview</h3>
            <p>Preview stays local. Sending requires the server-side token and configured email provider.</p>
          </div>
          <div class="workspace-actions workspace-actions--wrap">
            <button class="button button--secondary" id="generate-digest-preview-button" type="button">Generate Preview</button>
            <button class="button button--secondary" id="send-digest-preview-button" type="button">Send / Preview</button>
          </div>
        </div>
        <div class="form-field form-field--full">
          <label for="digest-server-token">Server token</label>
          <input id="digest-server-token" type="password" autocomplete="off" placeholder="Required only when sending" />
        </div>
        <div class="notice notice--neutral notice--compact" id="email-send-status">
          The token is used for one request and is never stored in localStorage.
        </div>
        <pre class="import-preview" id="digest-preview">${escapeHtml(preview)}</pre>
      </div>
    </div>
  `;
}

export function bindScoutPageEvents(root: HTMLElement): void {
  const preferencesForm = root.querySelector<HTMLFormElement>('#scout-preferences-form');
  const sourceForm = root.querySelector<HTMLFormElement>('#scout-source-form');
  const runScoutButton = root.querySelector<HTMLButtonElement>('#run-scout-button');
  const runDigestJobButton = root.querySelector<HTMLButtonElement>('#run-digest-job-button');
  const generateDigestButton = root.querySelector<HTMLButtonElement>('#generate-digest-preview-button');
  const sendDigestButton = root.querySelector<HTMLButtonElement>('#send-digest-preview-button');
  const digestPreview = root.querySelector<HTMLElement>('#digest-preview');
  const digestServerToken = root.querySelector<HTMLInputElement>('#digest-server-token');
  const emailSendStatus = root.querySelector<HTMLElement>('#email-send-status');

  preferencesForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    saveDealScoutPreferences(readPreferences(preferencesForm));
    refreshScoutPage();
  });

  sourceForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    const input = readSourceInput(sourceForm);

    if (!input.companyName && !input.url && !input.pastedText) {
      window.alert('Add a company name, public URL, or pasted source text.');
      return;
    }

    addWatchlistSource(input);
    refreshScoutPage();
  });

  root.querySelectorAll<HTMLButtonElement>('[data-toggle-source]').forEach((button) => {
    button.addEventListener('click', () => {
      toggleWatchlistSource(Number(button.dataset.toggleSource));
      refreshScoutPage();
    });
  });

  root.querySelectorAll<HTMLButtonElement>('[data-delete-source]').forEach((button) => {
    button.addEventListener('click', () => {
      if (!window.confirm('Delete this watchlist source and its snapshots?')) return;
      removeWatchlistSource(Number(button.dataset.deleteSource));
      refreshScoutPage();
    });
  });

  runScoutButton?.addEventListener('click', async () => {
    const result = await runDealScoutOnce();
    window.alert(`Scout checked ${result.checked} source(s), created ${result.snapshotsCreated} snapshot(s), and saw ${result.errors} error(s).`);
    refreshScoutPage();
  });

  runDigestJobButton?.addEventListener('click', async () => {
    const result = await runDealScoutDigestJob();
    window.alert(`Digest job checked ${result.run.checked} source(s) and generated ${result.digest.candidates.length} candidate(s).`);
    refreshScoutPage();
  });

  generateDigestButton?.addEventListener('click', () => {
    const digest = generateScoutDigest();
    if (digestPreview) digestPreview.textContent = digest.body;
  });

  sendDigestButton?.addEventListener('click', async () => {
    if (emailSendStatus) {
      emailSendStatus.textContent = 'Sending...';
      emailSendStatus.className = 'notice notice--neutral notice--compact';
    }

    const result = await sendScoutDigestEmail(
      generateScoutDigest(),
      digestServerToken?.value ?? ''
    );
    if (digestServerToken) digestServerToken.value = '';

    if (emailSendStatus) {
      emailSendStatus.textContent = result.message;
      emailSendStatus.className = `notice notice--compact ${
        result.status === 'SENT'
          ? 'notice--neutral'
          : result.status === 'PREVIEW_ONLY'
            ? 'notice--warning'
            : 'notice--danger'
      }`;
    }
  });
}
