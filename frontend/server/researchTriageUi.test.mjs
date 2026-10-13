import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const dataUrl = (source) => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
async function loadModule(path) {
  const source = await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8');
  return dataUrl(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022 } }).outputText);
}
const utility = await loadModule('utils/researchTriage.ts');
const { defaultResearchFilters: defaults, filterResearchEntries, researchAttention, sortResearchEntries } = await import(utility);
const html = await loadModule('utils/html.ts');
const urls = await loadModule('utils/urls.ts');
let page = ts.transpileModule(await readFile(new URL('../src/pages/reviewQueuePage.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ES2022 }
}).outputText;
const stubs = {
  '../utils/researchTriage': utility, '../utils/html': html, '../utils/urls': urls,
  '../components/radarUi': dataUrl('export const formatRadarDate = v => v || "Never"; export const guardRadarView = async (target, load) => load();'),
  '../services/radarService': dataUrl('export const getResearchQueue = async () => { throw new Error("Network forbidden in render tests"); };')
};
for (const [name, url] of Object.entries(stubs)) page = page.replaceAll(`from '${name}'`, `from '${url}'`).replaceAll(`from "${name}"`, `from '${url}'`);
const { renderResearchQueue } = await import(dataUrl(page));

function entry(id, overrides = {}) {
  return {
    packet: { id, radarCompanyId: id, companyName: `Company ${id}`, platform: 'Wefunder', status: 'PARTIAL',
      completeness: 65, deadline: null, lastRefreshedAt: '2026-10-08T16:00:00', securityType: null,
      minimumInvestment: null, valuationOrCap: null, platformIdentityStatus: 'SEC_CONFIRMED', secReconciliationStatus: 'SEC',
      ...overrides.packet },
    lifecycle: 'ACTIVE', category: 'SUBSTANTIVE_RESEARCH', researchPriority: 40, currentIdentityStatus: 'CONFIRMED',
    currentIdentityConfidence: 95, latestEvidenceAt: null, questions: ['What terms are missing?'],
    reasons: [{ code: 'MISSING_RESEARCH_TERMS', explanation: 'Missing data is not a negative investment signal.', weight: 4, supports: [] }],
    ...Object.fromEntries(Object.entries(overrides).filter(([key]) => key !== 'packet'))
  };
}
function queue(entries) {
  return { entries, attention: researchAttention(entries), deadlineDate: '2026-10-08', timeZone: 'America/New_York', version: 'research-triage-v1' };
}

test('attention preserves server ranking, limits to five distinct companies and excludes blocked/ended', () => {
  const entries = [entry(1), entry(2, { packet: { radarCompanyId: 1 } }), entry(3, { category: 'ENDED' }),
    entry(4, { category: 'BLOCKED' }), ...[5, 6, 7, 8, 9].map((id) => entry(id))];
  assert.deepEqual(researchAttention(entries).map((e) => e.packet.id), [1, 5, 6, 7, 8]);
  assert.equal(entries.length, 9);
});
test('status, lifecycle, platform and completeness filters compose without changing data', () => {
  const entries = [entry(1), entry(2, { packet: { status: 'NEEDS_REVIEW', completeness: 80, platform: 'Republic' } }),
    entry(3, { category: 'ENDED', lifecycle: 'WITHDRAWN', packet: { completeness: 20 } })];
  assert.deepEqual(filterResearchEntries(entries, { ...defaults, status: 'NEEDS_REVIEW', completeness: 'high', platform: 'Republic', lifecycle: 'ACTIVE' }).map((e) => e.packet.id), [2]);
  assert.deepEqual(filterResearchEntries(entries, { ...defaults, lifecycle: 'ENDED', completeness: 'low' }).map((e) => e.packet.id), [3]);
  assert.deepEqual(filterResearchEntries(entries, { ...defaults, completeness: 'medium' }).map((e) => e.packet.id), [1]);
});
test('deadline and refreshed sorts put unknowns last and use stable packet ID tie-breaks', () => {
  const entries = [entry(3), entry(2, { packet: { deadline: '2026-10-12' } }), entry(1, { packet: { deadline: '2026-10-12', lastRefreshedAt: '2026-10-09T10:00:00' } })];
  assert.deepEqual(sortResearchEntries(entries, 'deadline').map((e) => e.packet.id), [1, 2, 3]);
  assert.deepEqual(sortResearchEntries(entries, 'updated').map((e) => e.packet.id), [1, 2, 3]);
  assert.deepEqual(entries.map((e) => e.packet.id), [3, 2, 1]);
});
test('render keeps ended offerings separate, every packet exactly once, statuses and existing packet links', () => {
  const entries = [entry(1), entry(2, { category: 'ENDED', lifecycle: 'ENDED' }), entry(3, { category: 'BLOCKED' })];
  const output = renderResearchQueue(queue(entries));
  assert.match(output, /Research attention/);
  assert.match(output, /Ended offerings \(1\)/);
  assert.match(output, /Blocked research/);
  assert.match(output, /Identity Confidence/);
  for (const id of [1, 2, 3]) assert.equal(output.split(`data-packet-id="${id}"`).length - 1, 1);
  assert.match(output, /href="#\/review\/1"/);
  assert.match(output, /Not established/);
  assert.match(output, /MISSING_RESEARCH_TERMS/);
});
test('reason values and names are escaped, unsafe source links are rejected, and zero is not missing', () => {
  const dangerous = entry(1, { packet: { companyName: '<script>alert(1)</script>', minimumInvestment: 0 },
    reasons: [{ code: 'TEST', explanation: '<img onerror=alert(1)>', weight: 1,
      supports: [{ field: 'term', value: '<svg onload=alert(1)>', classification: 'SEC_FILED_FACT', sourceUrl: 'javascript:alert(1)', evidenceId: 4, reference: 'SEC-1', observedAt: null }] }] });
  const output = renderResearchQueue(queue([dangerous]), { ...defaults, lifecycle: 'ENDED' });
  assert.doesNotMatch(output, /<script>|<img|<svg|href="javascript:/);
  const card = renderResearchQueue(queue([...Array.from({ length: 5 }, (_, i) => entry(i + 2)), dangerous]), { ...defaults, platform: 'Wefunder' });
  assert.match(card, /&lt;script&gt;/);
  assert.match(card, /&lt;svg/);
  assert.doesNotMatch(card, /href="javascript:/);
  assert.match(card, /Evidence 4/);
  assert.match(card, /<dt>Minimum<\/dt><dd>\$0<\/dd>/);
});
