import { formatRadarDate, guardRadarView } from '../components/radarUi';
import type { ResearchEntry, ResearchQueue, ResearchSupport } from '../models/radar';
import { getResearchQueue } from '../services/radarService';
import { escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';
import { defaultResearchFilters, filterResearchEntries, researchAttention, sortResearchEntries } from '../utils/researchTriage';
import type { ResearchFilters } from '../utils/researchTriage';
import { campaignVerificationView } from '../components/campaignVerification';

const categories = {
  TIME_SENSITIVE: 'Time-sensitive review', SUBSTANTIVE_RESEARCH: 'Substantive research',
  INVESTIGATE: 'Investigation needed', BLOCKED: 'Blocked research', ENDED: 'Historical offering'
};

function money(value: number | null): string {
  return value === null ? 'Could not establish' : new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', maximumFractionDigits: 0
  }).format(value);
}

function supportHtml(support: ResearchSupport): string {
  const url = safeExternalUrl(support.sourceUrl);
  return `<li><code>${escapeHtml(support.field)}</code>: ${escapeHtml(support.value)}
    <span class="radar-muted">${escapeHtml(support.classification.replaceAll('_', ' '))}
    ${support.evidenceId === null ? '' : ` · Evidence ${support.evidenceId}`}
    ${support.reference ? ` · ${escapeHtml(support.reference)}` : ''}
    ${support.observedAt ? ` · Observed ${escapeHtml(formatRadarDate(support.observedAt))}` : ''}</span>
    ${url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noreferrer">Source</a>` : ''}</li>`;
}

function card(entry: ResearchEntry, compact = false): string {
  const packet = entry.packet;
  const mainReasons = entry.reasons.filter((reason) => reason.weight > 0 || ['SOURCE_ACCESS_BLOCKED', 'OFFERING_ENDED', 'ISSUER_LINK_REJECTED'].includes(reason.code));
  return `<article class="radar-panel diligence-card research-card" data-packet-id="${packet.id}">
    <div class="offering-card__head"><div>
      <span class="radar-badge radar-badge--offering">${escapeHtml(packet.status.replaceAll('_', ' '))}</span>
      <span class="radar-tag">${escapeHtml(entry.lifecycle.replaceAll('_', ' '))}</span>
      <h3><a href="#/review/${packet.id}">${escapeHtml(packet.companyName)}</a></h3>
      <p>${escapeHtml(packet.platform || 'Platform not established')} · ${escapeHtml(categories[entry.category])}</p>
    </div><strong>${packet.completeness}% complete</strong></div>
    <ul class="research-reasons">${mainReasons.slice(0, compact ? 2 : 3).map((reason) => `<li>${escapeHtml(reason.explanation)}</li>`).join('')}</ul>
    <dl class="offering-facts">
      <div><dt>Identity Confidence</dt><dd>${escapeHtml(entry.currentIdentityStatus)} · ${entry.currentIdentityConfidence}/100</dd></div>
      <div><dt>Last successful refresh</dt><dd>${escapeHtml(formatRadarDate(packet.lastRefreshedAt))}</dd></div>
      <div><dt>Latest source evidence observed</dt><dd>${escapeHtml(entry.latestEvidenceAt ? formatRadarDate(entry.latestEvidenceAt) : 'Not established')}</dd></div>
      <div><dt>Deadline</dt><dd>${escapeHtml(packet.deadline ? formatRadarDate(packet.deadline) : 'Could not establish')}</dd></div>
      ${compact ? '' : `<div><dt>Security</dt><dd>${escapeHtml(packet.securityType || 'Could not establish')}</dd></div>
      <div><dt>Minimum</dt><dd>${money(packet.minimumInvestment)}</dd></div>
      <div><dt>Valuation / cap</dt><dd>${escapeHtml(packet.valuationOrCap || 'Could not establish')}</dd></div>
      <div><dt>Platform identity</dt><dd>${escapeHtml(packet.platformIdentityStatus)}</dd></div>
      <div><dt>SEC reconciliation</dt><dd>${escapeHtml(packet.secReconciliationStatus)}</dd></div>`}
    </dl>
    <div><strong>Unresolved questions</strong><ul>${entry.questions.slice(0, 3).map((question) => `<li>${escapeHtml(question)}</li>`).join('') || '<li>No additional question recorded.</li>'}</ul></div>
    <details class="research-explanation"><summary>Why this needs attention</summary>
      ${entry.reasons.map((reason) => `<div class="research-reason"><code>${escapeHtml(reason.code)}</code>
      <p>${escapeHtml(reason.explanation)}</p><ul>${reason.supports.map(supportHtml).join('')}</ul></div>`).join('')}
      ${entry.questions.length > 3 ? `<h4>Other research questions</h4><ul>${entry.questions.slice(3).map((q) => `<li>${escapeHtml(q)}</li>`).join('')}</ul>` : ''}
    </details>
    ${campaignVerificationView(packet.campaignVerification, packet.platformIdentityStatus, true)}
    <a class="button button--secondary button--compact" href="#/review/${packet.id}">Review packet</a>
  </article>`;
}

function options(values: Array<[string, string]>, selected: string): string {
  return values.map(([value, label]) => `<option value="${escapeHtml(value)}"${value === selected ? ' selected' : ''}>${escapeHtml(label)}</option>`).join('');
}

function filtersHtml(queue: ResearchQueue, filters: ResearchFilters): string {
  const platforms = [...new Set(queue.entries.map((entry) => entry.packet.platform || ''))].filter(Boolean).sort();
  const fields: Array<[keyof ResearchFilters, string, Array<[string, string]>]> = [
    ['status', 'Packet status', [['', 'All statuses'], ['READY', 'Ready for Review'], ['PARTIAL', 'Partial'], ['NEEDS_REVIEW', 'Needs Review'], ['FAILED', 'Failed'], ['PENDING', 'Pending'], ['RESOLVING_IDENTITY', 'Resolving identity'], ['GATHERING_EVIDENCE', 'Gathering evidence']]],
    ['lifecycle', 'Offering lifecycle', [['', 'All lifecycles'], ['ACTIVE', 'Active'], ['POSSIBLY_ACTIVE', 'Possibly active'], ['UNKNOWN', 'Unknown'], ['ENDED', 'Ended / withdrawn / terminated']]],
    ['completeness', 'Evidence completeness', [['', 'All completeness levels'], ['high', '80-100%'], ['medium', '50-79%'], ['low', 'Below 50%']]],
    ['platform', 'Platform', [['', 'All platforms'], ...platforms.map((platform): [string, string] => [platform, platform])]],
    ['sort', 'Queue order', [['attention', 'Research attention'], ['deadline', 'Deadline'], ['updated', 'Most recently refreshed']]]
  ];
  return `<form id="research-filters" class="research-filter-grid">${fields.map(([name, label, values]) => `<div class="form-field">
    <label for="research-${name}">${label}</label><select id="research-${name}" name="${name}">${options(values, filters[name])}</select></div>`).join('')}</form>`;
}

function section(title: string, entries: ResearchEntry[]): string {
  return `<section class="diligence-queue-section"><div class="page-header page-header--row"><div>
    <h3>${escapeHtml(title)} <span class="radar-muted">${entries.length}</span></h3></div></div>
    <div class="radar-company-list">${entries.length ? entries.map((entry) => card(entry)).join('') : '<div class="radar-empty">No packets match this section.</div>'}</div></section>`;
}

export function renderResearchQueue(queue: ResearchQueue, filters: ResearchFilters = defaultResearchFilters): string {
  const matches = filterResearchEntries(queue.entries, filters);
  const attention = researchAttention(matches);
  const selected = new Set(attention.map((entry) => entry.packet.id));
  const remaining = sortResearchEntries(matches.filter((entry) => !selected.has(entry.packet.id)), filters.sort);
  const current = remaining.filter((entry) => entry.category !== 'ENDED' && entry.category !== 'BLOCKED');
  const ended = remaining.filter((entry) => entry.category === 'ENDED');
  const blocked = remaining.filter((entry) => entry.category === 'BLOCKED');
  return `${filtersHtml(queue, filters)}<p class="radar-muted" role="status">${matches.length} of ${queue.entries.length} packets · As of ${escapeHtml(queue.deadlineDate)} (${escapeHtml(queue.timeZone)})</p>
    <div class="radar-summary">${[['READY', 'Ready for Review'], ['NEEDS_REVIEW', 'Needs Review'], ['PARTIAL', 'Partial']].map(([status, label]) =>
      `<div><span>${label}</span><strong>${matches.filter((entry) => entry.packet.status === status).length}</strong></div>`).join('')}</div>
    <section class="diligence-queue-section research-attention"><h3>Research attention <span class="radar-muted">${attention.length}</span></h3>
    <div class="research-attention-grid">${attention.length ? attention.map((entry) => card(entry, true)).join('') : '<div class="radar-empty">No active research-attention candidates match these filters.</div>'}</div></section>
    ${section('Ready for Review', current.filter((entry) => entry.packet.status === 'READY'))}
    ${section('Needs Review', current.filter((entry) => entry.packet.status === 'NEEDS_REVIEW'))}
    ${section('Partial', current.filter((entry) => entry.packet.status === 'PARTIAL'))}
    ${current.some((entry) => !['READY', 'NEEDS_REVIEW', 'PARTIAL'].includes(entry.packet.status))
      ? section('Other packet states', current.filter((entry) => !['READY', 'NEEDS_REVIEW', 'PARTIAL'].includes(entry.packet.status))) : ''}
    ${blocked.length ? section('Blocked research', blocked) : ''}
    <details class="diligence-queue-section research-ended"><summary>Ended offerings (${ended.length})</summary>${section('Historical packets', ended)}</details>`;
}

export function renderReviewQueuePage(): string {
  return `<div class="page radar-page"><div class="page-header page-header--row"><div>
    <p class="page-eyebrow">Autonomous diligence</p><h2>Review Queue</h2>
    <p>Evidence packets assembled from public filings and public offering sources.</p></div></div>
    <div class="notice notice--neutral">Research attention measures what to examine next, not investment merit. Packet status measures evidence readiness. Missing data is not an investment verdict.</div>
    <div id="diligence-review-queue"><div class="radar-empty">Loading diligence packets...</div></div></div>`;
}

export function bindReviewQueuePageEvents(root: HTMLElement): void {
  const target = root.querySelector<HTMLElement>('#diligence-review-queue');
  if (!target) return;
  let queue: ResearchQueue | null = null;
  target.addEventListener('change', (event) => {
    if (!(event.target instanceof HTMLSelectElement) || !queue) return;
    const form = target.querySelector<HTMLFormElement>('#research-filters');
    if (!form) return;
    const filters = Object.fromEntries(new FormData(form)) as unknown as ResearchFilters;
    const focus = event.target.id;
    target.innerHTML = renderResearchQueue(queue, filters);
    target.querySelector<HTMLElement>(`#${focus}`)?.focus();
  });
  target.addEventListener('submit', (event) => event.preventDefault());
  void guardRadarView(target, async () => {
    queue = await getResearchQueue();
    if (target.isConnected) target.innerHTML = renderResearchQueue(queue);
  });
}
