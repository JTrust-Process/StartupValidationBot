import { guardRadarView, formatRadarDate } from '../components/radarUi';
import type { RadarOffering } from '../models/radar';
import { getDeals, loadDeals } from '../services/dealService';
import { listRadarOfferings } from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

function money(value: number | null): string {
  return value === null ? 'Unknown' : new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', maximumFractionDigits: 0
  }).format(value);
}

function handoff(offering: RadarOffering): string {
  const existing = getDeals().find((deal) => deal.offeringDiscoveryId === offering.id
    || (offering.radarCompanyId !== null && deal.radarCompanyId === offering.radarCompanyId
      && Boolean(deal.offeringUrl) && deal.offeringUrl === offering.offeringUrl));
  if (existing) return `<a class="button button--secondary button--compact" href="#/deals/${existing.id}">Already in Deal Scout</a>`;
  const params = new URLSearchParams();
  if (offering.radarCompanyId !== null) params.set('radarCompanyId', String(offering.radarCompanyId));
  params.set('offeringDiscoveryId', String(offering.id));
  return `<a class="button button--primary button--compact" href="#/deals/new?${params}">Evaluate in Deal Scout</a>`;
}

function card(offering: RadarOffering): string {
  const filing = safeExternalUrl(offering.secFilingUrl);
  const page = safeExternalUrl(offering.offeringUrl);
  return `<article class="radar-panel offering-card">
    <div class="offering-card__head"><div><span class="radar-badge radar-badge--offering">${escapeHtml(offering.matchStatus)}</span>
      <h3>${escapeHtml(offering.companyName || offering.issuerName)}</h3>
      <p>${escapeHtml(offering.platform)} · ${escapeHtml(offering.filingType)} filed ${escapeHtml(formatRadarDate(offering.filingDate))}</p></div>
      <span class="status-pill">${escapeHtml(offering.status.replaceAll('_', ' '))}</span></div>
    <dl class="offering-facts">
      <div><dt>Exemption</dt><dd>Regulation Crowdfunding</dd></div>
      <div><dt>Security</dt><dd>${escapeHtml(offering.securityType || 'Unknown')}</dd></div>
      <div><dt>Target / maximum</dt><dd>${money(offering.targetAmount)} / ${money(offering.maximumAmount)}</dd></div>
      <div><dt>Deadline</dt><dd>${escapeHtml(offering.deadline ? formatRadarDate(offering.deadline) : 'Unknown')}</dd></div>
      <div><dt>Match confidence</dt><dd>${offering.matchConfidence}/100</dd></div>
      <div><dt>Last verified</dt><dd>${escapeHtml(formatRadarDate(offering.lastSeenAt))}</dd></div>
    </dl>
    <p class="radar-muted">${escapeHtml(offering.matchReason)}</p>
    <div class="form-actions form-actions--start">
      ${offering.radarCompanyId ? `<a class="button button--secondary button--compact" href="#/radar/company/${offering.radarCompanyId}">View Radar Profile</a>` : ''}
      ${filing ? `<a class="button button--secondary button--compact" href="${escapeAttribute(filing)}" target="_blank" rel="noreferrer">Open SEC Filing</a>` : ''}
      ${page ? `<a class="button button--secondary button--compact" href="${escapeAttribute(page)}" target="_blank" rel="noreferrer">Open Offering Page</a>` : ''}
      ${handoff(offering)}
    </div>
  </article>`;
}

function renderResults(offerings: RadarOffering[]): string {
  const selected = (document.querySelector<HTMLSelectElement>('#offering-view')?.value || 'confirmed');
  const filtered = offerings.filter((offering) => selected === 'confirmed'
    ? offering.matchStatus === 'CONFIRMED' && ['ACTIVE', 'POSSIBLY_ACTIVE', 'UNKNOWN'].includes(offering.status)
    : selected === 'possible'
      ? ['LIKELY', 'AMBIGUOUS'].includes(offering.matchStatus)
      : ['ENDED', 'WITHDRAWN', 'TERMINATED'].includes(offering.status));
  return filtered.length ? filtered.map(card).join('') : '<div class="radar-empty">No offerings in this view.</div>';
}

export function renderOfferingsPage(): string {
  return `<div class="page radar-page"><div class="page-header page-header--row"><div>
    <p class="page-eyebrow">Diligence intake</p><h2>Investment Offerings</h2>
    <p>Public securities offerings discovered for companies on your Radar.</p></div></div>
    <div class="notice notice--neutral">Offering availability is not an investment recommendation. An SEC-filed offering statement verifies the filing, not every issuer claim.</div>
    <section class="radar-panel"><label for="offering-view">View</label>
      <select id="offering-view"><option value="confirmed">Confirmed</option><option value="possible">Possible</option><option value="historical">Ended / Historical</option></select></section>
    <div id="offering-results"><div class="radar-empty">Loading offering evidence...</div></div></div>`;
}

export function bindOfferingsPageEvents(root: HTMLElement): void {
  const results = root.querySelector<HTMLElement>('#offering-results');
  const select = root.querySelector<HTMLSelectElement>('#offering-view');
  if (!results) return;
  void guardRadarView(results, async () => {
    const [offerings] = await Promise.all([listRadarOfferings(), loadDeals()]);
    const paint = () => { results.innerHTML = renderResults(offerings); };
    paint();
    select?.addEventListener('change', paint);
  });
}
