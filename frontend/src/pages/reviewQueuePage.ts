import { formatRadarDate, guardRadarView } from '../components/radarUi';
import type { DiligencePacket, DiligencePacketStatus } from '../models/radar';
import { listDiligencePackets } from '../services/radarService';
import { escapeHtml } from '../utils/html';

function money(value: number | null): string {
  return value === null ? 'Could not establish' : new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', maximumFractionDigits: 0
  }).format(value);
}

function card(packet: DiligencePacket): string {
  return `<article class="radar-panel diligence-card">
    <div class="offering-card__head"><div>
      <span class="radar-badge radar-badge--offering">${escapeHtml(packet.status.replaceAll('_', ' '))}</span>
      <h3>${escapeHtml(packet.companyName)}</h3>
      <p>${escapeHtml(packet.platform || 'Platform not established')} · ${escapeHtml(packet.securityType || 'Security not established')}</p>
    </div><strong>${packet.completeness}% complete</strong></div>
    <dl class="offering-facts">
      <div><dt>Valuation / cap</dt><dd>${escapeHtml(packet.valuationOrCap || 'Could not establish')}</dd></div>
      <div><dt>Minimum</dt><dd>${money(packet.minimumInvestment)}</dd></div>
      <div><dt>Deadline</dt><dd>${escapeHtml(packet.deadline ? formatRadarDate(packet.deadline) : 'Could not establish')}</dd></div>
      <div><dt>Identity</dt><dd>${escapeHtml(packet.identityStatus)}</dd></div>
      <div><dt>Evidence confidence</dt><dd>${packet.confidence}/100</dd></div>
      <div><dt>Last refreshed</dt><dd>${escapeHtml(formatRadarDate(packet.lastRefreshedAt))}</dd></div>
    </dl>
    <p><strong>Key risk:</strong> ${escapeHtml(packet.keyRisks[0] || 'No risk summary was established.')}</p>
    <a class="button button--primary button--compact" href="#/review/${packet.id}">Review Diligence</a>
  </article>`;
}

const sections: Array<{ status: DiligencePacketStatus; title: string }> = [
  { status: 'READY', title: 'Ready for Review' },
  { status: 'NEEDS_REVIEW', title: 'Needs Review' },
  { status: 'PARTIAL', title: 'Partial' }
];

function renderPackets(packets: DiligencePacket[]): string {
  return sections.map(({ status, title }) => {
    const matches = packets.filter((packet) => packet.status === status);
    return `<section class="diligence-queue-section"><div class="page-header page-header--row">
      <div><h3>${title}</h3><p>${matches.length} packet${matches.length === 1 ? '' : 's'}</p></div></div>
      <div class="radar-company-list">${matches.length ? matches.map(card).join('')
        : '<div class="radar-empty">No packets in this state.</div>'}</div></section>`;
  }).join('');
}

export function renderReviewQueuePage(): string {
  return `<div class="page radar-page"><div class="page-header page-header--row"><div>
    <p class="page-eyebrow">Autonomous diligence</p><h2>Review Queue</h2>
    <p>Evidence packets assembled from public filings and public offering sources.</p></div></div>
    <div class="notice notice--neutral">Research only. Packet status measures evidence readiness, not investment quality or expected return.</div>
    <div id="diligence-review-queue"><div class="radar-empty">Loading diligence packets...</div></div></div>`;
}

export function bindReviewQueuePageEvents(root: HTMLElement): void {
  const target = root.querySelector<HTMLElement>('#diligence-review-queue');
  if (!target) return;
  void guardRadarView(target, async () => { target.innerHTML = renderPackets(await listDiligencePackets()); });
}
