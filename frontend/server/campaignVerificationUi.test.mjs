import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const dataUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
async function module(path) {
  return ts.transpileModule(await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.ES2022 }
  }).outputText;
}
let component = await module('components/campaignVerification.ts');
for (const path of ['html', 'urls']) component = component.replaceAll(`from '../utils/${path}'`, `from '${dataUrl(await module(`utils/${path}.ts`))}'`);
const { campaignVerificationView, campaignHeadline, permittedCampaignUrl, publicOfferingStatus } = await import(dataUrl(component));
function value(overrides = {}) {
  return { offeringId: 1, urlEvidence: { url: 'https://wefunder.com/acme', state: 'EXPLICIT_OBSERVED', classification: 'SEC_FILED_FACT',
    sourceUrl: 'https://www.sec.gov/Archives/primary_doc.xml', observedAt: null, explanation: 'Exact filed URL' },
  pageState: 'NOT_CHECKED', publicStatus: 'UNKNOWN', fresh: false, lastSuccessfulVerification: null, latestAttempt: null,
  latestObservation: null, successfulObservation: null, historicalStatus: null, limitations: ['Public advertising does not establish personal eligibility.'],
  questions: ['Can the public page be independently checked?'], discrepancies: [], ...overrides };
}
test('an observed URL provides a source-backed link but never claims independent page verification', () => {
  assert.equal(campaignHeadline(value()), 'URL found - page not independently verified');
  const rendered = campaignVerificationView(value(), 'SEC_CONFIRMED');
  assert.match(rendered, /Open campaign/);
  assert.match(rendered, /NOT CHECKED/);
  assert.match(rendered, /Current public offering status not independently established/);
  assert.doesNotMatch(rendered, /Public campaign previously verified|verified investment opportunity/);
});
test('absent or unknown URL lineage does not expose an Open campaign action', () => {
  assert.equal(campaignHeadline(null), 'No canonical campaign link established');
  assert.equal(permittedCampaignUrl(value({ urlEvidence: { ...value().urlEvidence, state: 'UNVERIFIED' } })), null);
  assert.doesNotMatch(campaignVerificationView(null, 'SEC_CONFIRMED'), /Open campaign/);
});
test('reservation and coming-soon public states do not claim investing is open', () => {
  for (const status of ['RESERVATION', 'COMING_SOON']) {
    const v = value({ pageState: 'VERIFIED', fresh: true, publicStatus: status });
    assert.doesNotMatch(campaignHeadline(v), /investing currently open/);
    assert.match(publicOfferingStatus(v), status === 'RESERVATION' ? /reservations only/ : /not open/);
  }
});
test('historical positive status and failed refresh are explicitly unavailable', () => {
  const v = value({ pageState: 'ACCESS_BLOCKED', publicStatus: 'STALE_OR_UNAVAILABLE', historicalStatus: 'ACTIVE', lastSuccessfulVerification: '2026-10-01T12:00:00' });
  const rendered = campaignVerificationView(v, 'SEC_CONFIRMED');
  assert.match(campaignHeadline(v), /Previously verified - current status unavailable/);
  assert.match(rendered, /not proof of current availability/);
  assert.doesNotMatch(rendered, /Public page indicates investing currently open/);
});
test('unsafe or non-campaign destinations are never rendered as campaign actions', () => {
  for (const url of ['http://wefunder.com/acme', 'https://attacker.example/acme', 'https://127.0.0.1/acme', 'https://wefunder.com/login',
    'https://wefunder.com/acme?token=x', 'https://wefunder.com:8443/acme', 'https://startengine.com/offering/acme/invest'])
    assert.equal(permittedCampaignUrl(value({ urlEvidence: { ...value().urlEvidence, url } })), null);
});
test('source excerpts, reasons, and unsafe evidence URLs are escaped', () => {
  const v = value({ latestObservation: { observedAt: '2026-10-13T12:00:00', sourceClassification: 'PLATFORM_ISSUER_CLAIM', sourceOutcome: 'HTTP_200',
    issuerEvidence: '<img src=x onerror=alert(1)>', statusExcerpt: '<script>alert(1)</script>', verificationMethod: 'EXACT', httpRequests: 1 },
  urlEvidence: { ...value().urlEvidence, sourceUrl: 'javascript:alert(1)' } });
  const html = campaignVerificationView(v, 'SEC_CONFIRMED');
  assert.doesNotMatch(html, /<img|<script|href="javascript:/);
  assert.match(html, /&lt;script&gt;/);
});
test('compact queue disclosure preserves the research-only qualification and distinct evidence labels', () => {
  const html = campaignVerificationView(value(), 'SEC_CONFIRMED', true);
  assert.match(html, /^<details/);
  for (const label of ['Campaign Verification', 'Campaign URL provenance', 'Page verification', 'Public offering status', 'Latest access attempt']) assert.ok(html.includes(label));
  assert.match(html, /personal eligibility/);
});
