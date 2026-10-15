import type { CampaignVerification } from '../models/radar';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

const publicLabels: Record<string, string> = {
  ACTIVE: 'Public page indicates investing currently open',
  RESERVATION: 'Public page indicates reservations only',
  COMING_SOON: 'Public page indicates coming soon (not open)',
  CLOSING_SOON: 'Public page indicates offering closing soon',
  FUNDED: 'Public page indicates offering funded',
  CLOSED: 'Public page indicates offering closed',
  WITHDRAWN: 'Public page indicates offering withdrawn',
  TERMINATED: 'Public page indicates offering terminated',
  UNKNOWN: 'Current public offering status not independently established',
  CONFLICTING: 'Conflicting SEC and public status evidence - review required',
  STALE_OR_UNAVAILABLE: 'Prior status stale or currently inaccessible'
};
export function publicOfferingStatus(value?: CampaignVerification | null): string {
  return publicLabels[value?.publicStatus || 'UNKNOWN'] || publicLabels.UNKNOWN;
}
export function campaignHeadline(value?: CampaignVerification | null): string {
  if (!value?.urlEvidence.url) return value?.urlEvidence.state === 'CONFLICTING'
    ? 'Conflicting campaign links - review required' : 'No canonical campaign link established';
  if (!value.fresh && value.lastSuccessfulVerification) return 'Previously verified - current status unavailable';
  if (value.pageState === 'VERIFIED' && value.fresh) return value.publicStatus === 'UNKNOWN'
    ? 'Page verified - currently accepting investments not established' : publicOfferingStatus(value);
  return 'URL found - page not independently verified';
}
export function permittedCampaignUrl(value?: CampaignVerification | null): string | null {
  if (!value || !['EXPLICIT_OBSERVED', 'HISTORICAL'].includes(value.urlEvidence.state)) return null;
  const url = safeExternalUrl(value.urlEvidence.url);
  if (!url) return null;
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || parsed.port || parsed.search || parsed.hash || parsed.username || parsed.password) return null;
  const host = parsed.hostname.replace(/^www\./, '');
  if (!['wefunder.com', 'republic.com', 'startengine.com'].includes(host)) return null;
  const path = parsed.pathname.replace(/^\/+|\/+$/g, '');
  if (host === 'startengine.com' ? !/^offering\/[a-z\d._-]+$/i.test(path) : !/^[a-z\d._-]+$/i.test(path)) return null;
  if (/^(?:login|signin|signup|invest|checkout|about|explore|companies|discover|learn|privacy|terms)$/i.test(path.split('/').pop() || '')) return null;
  return url;
}
function timestamp(value: string | null): string {
  if (!value) return 'Not established';
  const date = new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? 'Not established' : date.toLocaleString('en-US', { timeZone: 'America/New_York' });
}
export function campaignVerificationView(value: CampaignVerification | null | undefined, platformIdentity: string, compact = false): string {
  const heading = escapeHtml(campaignHeadline(value));
  const url = permittedCampaignUrl(value);
  const evidence = value?.latestObservation;
  const retained = value?.successfulObservation;
  const source = safeExternalUrl(value?.urlEvidence.sourceUrl);
  const contents = `<p>${heading}</p><dl class="offering-facts">
    <div><dt>Platform identity</dt><dd>${escapeHtml(platformIdentity)}</dd></div>
    <div><dt>Campaign URL</dt><dd>${escapeHtml(value?.urlEvidence.url || 'No exact source-backed URL established')}</dd></div>
    <div><dt>Campaign URL provenance</dt><dd>${escapeHtml(value?.urlEvidence.state.replaceAll('_', ' ') || 'UNVERIFIED')}
      <small>${escapeHtml(value?.urlEvidence.classification.replaceAll('_', ' ') || 'Source not established')}</small>
      <small>${escapeHtml(value?.urlEvidence.explanation || 'URL lineage is not established')}; observed ${escapeHtml(timestamp(value?.urlEvidence.observedAt || null))}</small>
      ${source ? `<a href="${escapeAttribute(source)}" target="_blank" rel="noreferrer">URL evidence source</a>` : ''}</dd></div>
    <div><dt>Page verification</dt><dd>${escapeHtml(value?.pageState.replaceAll('_', ' ') || 'NOT CHECKED')}</dd></div>
    <div><dt>Public offering status</dt><dd>${escapeHtml(publicOfferingStatus(value))}</dd></div>
    <div><dt>Last successful verification</dt><dd>${escapeHtml(timestamp(value?.lastSuccessfulVerification || null))}</dd></div>
    <div><dt>Latest access attempt</dt><dd>${escapeHtml(timestamp(value?.latestAttempt || null))}
      <small>${escapeHtml(evidence?.sourceOutcome.replaceAll('_', ' ') || 'NOT CHECKED')}; ${evidence?.httpRequests || 0} HTTP request(s)</small></dd></div>
    ${value?.historicalStatus ? `<div><dt>Retained historical status</dt><dd>${escapeHtml(value.historicalStatus.replaceAll('_', ' '))} - not proof of current availability</dd></div>` : ''}
  </dl>${url ? `<a class="button button--secondary button--compact" href="${escapeAttribute(url)}" target="_blank" rel="noreferrer">Open campaign</a>` : ''}
  ${evidence || retained ? `<details><summary>Supporting page evidence</summary>${[evidence, retained].filter((v, i, all) => v && (i === 0 || v.observedAt !== all[0]?.observedAt)).map(v => v ? `<p>${escapeHtml(v.sourceClassification.replaceAll('_', ' '))}; observed ${escapeHtml(timestamp(v.observedAt))}</p>
    <p>${escapeHtml(v.issuerEvidence || 'Issuer match not independently established.')}</p>
    <blockquote>${escapeHtml(v.statusExcerpt || 'No explicit public status excerpt.')}</blockquote>
    <small>${escapeHtml(v.verificationMethod)}; ${escapeHtml(v.sourceOutcome)}</small>
    <p>${escapeHtml(v.explanation)}</p>` : '').join('')}</details>` : ''}
  ${(value?.discrepancies.length || 0) > 0 ? `<p class="notice notice--warning">${value?.discrepancies.map(escapeHtml).join(' ')}</p>` : ''}
  ${(value?.questions.length || 0) > 0 ? `<strong>Unresolved research questions</strong><ul>${value?.questions.map(q => `<li>${escapeHtml(q)}</li>`).join('')}</ul>` : ''}
  <p class="radar-muted">${escapeHtml(value?.limitations.join(' ') || 'A known platform or URL does not verify a campaign, current offering status, or personal investor eligibility.')}</p>`;
  return compact ? `<details class="campaign-verification"><summary>Campaign Verification: ${heading}</summary>${contents}</details>`
    : `<section class="radar-panel campaign-verification"><h3>Campaign Verification</h3>${contents}</section>`;
}
