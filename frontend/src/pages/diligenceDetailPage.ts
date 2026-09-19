import { formatRadarDate, guardRadarView } from '../components/radarUi';
import type { CompanyInvestmentAvailability, DiligenceEvidence, DiligencePacket } from '../models/radar';
import { getDiligencePacket, getInvestmentAvailability, refreshDiligencePacket } from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

function money(value: number | null): string {
  return value === null ? 'Could not establish' : new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', maximumFractionDigits: 0
  }).format(value);
}

function totalDebt(shortTerm: number | null, longTerm: number | null): number | null {
  return shortTerm === null && longTerm === null ? null : (shortTerm ?? 0) + (longTerm ?? 0);
}

function list(items: string[], empty: string): string {
  return items.length ? `<ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
    : `<p class="radar-muted">${escapeHtml(empty)}</p>`;
}

function termSource(packet: DiligencePacket, key: string): string {
  const source = packet.termProvenance[key];
  if (!source) return '<small class="radar-muted">Source not established</small>';
  const classification = source.classification === 'SEC_FILED_FACT' ? 'SEC-filed fact' : 'Platform issuer claim';
  return `<small class="radar-muted">Source: ${escapeHtml(source.sourceType)} · ${classification}</small>`;
}

function evidenceTable(items: DiligenceEvidence[]): string {
  if (!items.length) return '<p class="radar-muted">No evidence in this source class.</p>';
  return `<div class="table-wrap"><table class="data-table"><thead><tr><th>Fact</th><th>Value</th><th>Period</th><th>Source</th></tr></thead><tbody>
    ${items.map((item) => { const url = safeExternalUrl(item.sourceUrl); return `<tr>
      <td>${escapeHtml(item.factKey.replaceAll('_', ' '))}</td><td>${escapeHtml(item.factValue)}</td>
      <td>${escapeHtml(item.period || 'Current')}</td><td>${url ? `<a href="${escapeAttribute(url)}" target="_blank" rel="noreferrer">${escapeHtml(item.sourceTitle)}</a>` : escapeHtml(item.sourceTitle)}</td>
    </tr>`; }).join('')}</tbody></table></div>`;
}

function availabilityView(value: CompanyInvestmentAvailability): string {
  return `<section class="radar-panel"><h3>Automated Search Coverage</h3>
    <p class="radar-muted">Last full check: ${escapeHtml(formatRadarDate(value.lastFullCheck))}</p>
    <div class="radar-status-grid">${value.checks.map((check) => `<div><span>${escapeHtml(check.sourceType.replaceAll('_', ' '))}</span>
      <strong>${escapeHtml(check.status.replaceAll('_', ' '))}</strong><small>${escapeHtml(check.resultSummary)}</small>
      ${check.unresolvedQuestion ? `<small>Unresolved: ${escapeHtml(check.unresolvedQuestion)}</small>` : ''}</div>`).join('')}</div></section>`;
}

function packetView(packet: DiligencePacket, availability: CompanyInvestmentAvailability): string {
  const sec = safeExternalUrl(packet.secFilingUrl);
  const campaign = safeExternalUrl(packet.campaignUrl);
  const secEvidence = packet.evidence.filter((item) => item.classification === 'SEC_FILED_FACT');
  const claims = packet.evidence.filter((item) => item.classification !== 'SEC_FILED_FACT');
  return `<div class="page-header page-header--row"><div><p class="page-eyebrow">Diligence ${escapeHtml(packet.status.replaceAll('_', ' '))}</p>
    <h2>${escapeHtml(packet.companyName)}</h2><p>${escapeHtml(packet.summary)}</p></div>
    <div><strong>${packet.completeness}% complete</strong><p class="radar-muted">Evidence confidence ${packet.confidence}/100</p></div></div>
    ${packet.materialDiscrepancies.length ? `<div class="notice notice--warning"><strong>Material discrepancies require review.</strong>${list(packet.materialDiscrepancies, '')}</div>` : ''}
    <section class="radar-panel"><h3>Offering Terms</h3><dl class="offering-facts">
      <div><dt>Platform</dt><dd>${escapeHtml(packet.platform || 'Could not establish')}</dd></div>
      <div><dt>Security</dt><dd>${escapeHtml(packet.securityType || 'Could not establish')}${termSource(packet, 'securityType')}</dd></div>
      <div><dt>Valuation / cap</dt><dd>${escapeHtml(packet.valuationOrCap || 'Could not establish')}${termSource(packet, packet.valuationCap !== null ? 'valuationCap' : 'valuation')}</dd></div>
      <div><dt>Minimum</dt><dd>${money(packet.minimumInvestment)}${termSource(packet, 'minimumInvestment')}</dd></div>
      <div><dt>Target / maximum</dt><dd>${money(packet.targetAmount)} / ${money(packet.maximumAmount)}${termSource(packet, packet.targetAmount !== null ? 'targetAmount' : 'maximumAmount')}</dd></div>
      <div><dt>Raised</dt><dd>${money(packet.amountRaised)}${termSource(packet, 'amountRaised')}</dd></div>
      <div><dt>Deadline</dt><dd>${escapeHtml(packet.deadline ? formatRadarDate(packet.deadline) : 'Could not establish')}${termSource(packet, 'deadline')}</dd></div>
      <div><dt>Platform status</dt><dd>${escapeHtml(packet.platformStatus?.replaceAll('_', ' ') || 'Could not establish')}${termSource(packet, 'platformStatus')}</dd></div>
      <div><dt>Platform identity</dt><dd>${escapeHtml(packet.platformIdentityStatus)}</dd></div>
      <div><dt>SEC reconciliation</dt><dd>${escapeHtml(packet.secReconciliationStatus)}</dd></div>
      <div><dt>Radar match</dt><dd>${escapeHtml(packet.identityStatus)}</dd></div></dl></section>
    <section class="radar-panel"><h3>Financial Snapshot</h3>${packet.financials.length ? `<div class="table-wrap"><table class="data-table"><thead><tr><th>Period</th><th>Revenue</th><th>COGS</th><th>Net income</th><th>Cash</th><th>Assets</th><th>Liabilities</th><th>Debt</th></tr></thead><tbody>
      ${packet.financials.map((period) => `<tr><td>${escapeHtml(period.period)}</td><td>${money(period.revenue)}</td><td>${money(period.costOfGoods)}</td><td>${money(period.netIncome)}</td><td>${money(period.cash)}</td><td>${money(period.assets)}</td><td>${money(period.liabilities)}</td><td>${money(totalDebt(period.shortTermDebt, period.longTermDebt))}</td></tr>`).join('')}</tbody></table></div>` : '<p class="radar-muted">Could not establish structured multi-period financials.</p>'}</section>
    <section class="radar-panel"><h3>SEC-Filed Facts</h3>${evidenceTable(secEvidence)}</section>
    <section class="radar-panel"><h3>Platform / Issuer Claims</h3><p class="radar-muted">Claims remain distinct from filed facts.</p>${evidenceTable(claims)}</section>
    <div class="diligence-two-column"><section class="radar-panel"><h3>Key Risks</h3>${list(packet.keyRisks, 'No risk synthesis available.')}</section>
      <section class="radar-panel"><h3>Bull Case</h3>${list(packet.bullCase, 'No synthesis available.')}</section>
      <section class="radar-panel"><h3>Bear Case</h3>${list(packet.bearCase, 'No synthesis available.')}</section>
      <section class="radar-panel"><h3>Unanswered Questions</h3>${list(packet.unansweredQuestions, 'No open questions recorded.')}</section></div>
    <section class="radar-panel"><h3>Monitoring Milestones</h3>${list(packet.nextMonitoringMilestones, 'No milestones recorded.')}
      <h4>Sources checked</h4>${list(packet.sourcesChecked, 'No sources recorded.')}
      <h4>Data not found</h4>${list(packet.dataNotFound, 'No missing data recorded.')}
      <p class="radar-muted">Generated ${escapeHtml(formatRadarDate(packet.generatedAt))}; refreshed ${escapeHtml(formatRadarDate(packet.lastRefreshedAt))}.</p></section>
    ${availabilityView(availability)}
    <div class="form-actions form-actions--start">
      ${sec ? `<a class="button button--secondary" href="${escapeAttribute(sec)}" target="_blank" rel="noreferrer">Open SEC Filing</a>` : ''}
      ${campaign ? `<a class="button button--secondary" href="${escapeAttribute(campaign)}" target="_blank" rel="noreferrer">Open Campaign</a>` : ''}
      <a class="button button--secondary" href="#/radar/company/${packet.radarCompanyId}">Open Radar Profile</a>
      <button class="button button--secondary" id="refresh-diligence" type="button">Refresh Diligence</button>
      <a class="button button--primary" href="#/deals/new?radarCompanyId=${packet.radarCompanyId}&offeringDiscoveryId=${packet.offeringId}&diligencePacketId=${packet.id}">Evaluate in Deal Scout</a>
    </div><p class="radar-muted">Deal Scout opens as an unsaved draft. You make the final Pass, Watch, or Invest Small decision.</p>`;
}

export function renderDiligenceDetailPage(): string {
  return `<div class="page radar-page"><div id="diligence-detail"><div class="radar-empty">Loading public diligence evidence...</div></div></div>`;
}

export function bindDiligenceDetailPageEvents(root: HTMLElement, path: string): void {
  const target = root.querySelector<HTMLElement>('#diligence-detail');
  const packetId = Number(path.split('/').pop());
  if (!target || !Number.isInteger(packetId) || packetId <= 0) return;
  const load = async () => {
    const packet = await getDiligencePacket(packetId);
    const availability = await getInvestmentAvailability(packet.radarCompanyId);
    target.innerHTML = packetView(packet, availability);
    target.querySelector<HTMLButtonElement>('#refresh-diligence')?.addEventListener('click', async (event) => {
      const button = event.currentTarget as HTMLButtonElement;
      button.disabled = true;
      try { await refreshDiligencePacket(packetId); await load(); }
      finally { button.disabled = false; }
    });
  };
  void guardRadarView(target, load);
}
