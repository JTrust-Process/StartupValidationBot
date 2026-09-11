import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (path) => readFile(new URL(`../src/${path}`, import.meta.url), 'utf8');

test('Offerings page keeps availability separate from investment quality and exposes review actions', async () => {
  const page = await source('pages/offeringsPage.ts');
  assert.match(page, /Offering availability is not an investment recommendation/);
  assert.match(page, /Open SEC Filing/);
  assert.match(page, /Evaluate in Deal Scout/);
  assert.match(page, /Already in Deal Scout/);
  assert.match(page, /offering\.deadline \? formatRadarDate\(offering\.deadline\) : 'Unknown'/);
});

test('only confirmed active evidence creates the strong Radar badge', async () => {
  const [home, list, profile] = await Promise.all([
    source('pages/radarHomePage.ts'),
    source('pages/radarPage.ts'),
    source('pages/radarCompanyPage.ts')
  ]);
  assert.match(home, /offering\.matchStatus === 'CONFIRMED'/);
  assert.match(home, /\['ACTIVE', 'POSSIBLY_ACTIVE'\]/);
  assert.match(home, /Offering Found/);
  assert.match(list, /matchStatus: 'CONFIRMED'/);
  assert.match(profile, /offering\.matchStatus === 'CONFIRMED'/);
  assert.match(home, /offering\.matchStatus === 'CONFIRMED'/);
  assert.match(profile, /offering\.deadline \? formatRadarDate\(offering\.deadline\) : 'Unknown'/);
});

test('likely and ambiguous offerings stay in manual review surfaces', async () => {
  const page = await source('pages/offeringsPage.ts');
  assert.match(page, /\['LIKELY', 'AMBIGUOUS'\]\.includes\(offering\.matchStatus\)/);
  assert.match(page, /Possible/);
});

test('Deal Scout handoff is user submitted and carries public offering identity', async () => {
  const [page, service] = await Promise.all([
    source('pages/newDealPage.ts'),
    source('services/dealService.ts')
  ]);
  assert.match(page, /offeringDiscoveryId/);
  assert.match(page, /SEC-filed Regulation Crowdfunding offering/);
  assert.match(page, /form\.addEventListener\('submit'/);
  assert.match(service, /An SEC-filed offering statement exists/);
  assert.doesNotMatch(page, /createDeal\([^)]*offering[^)]*\).*prefill/s);
});

test('Admin persists the latest offering discovery run counts', async () => {
  const page = await source('pages/radarAdminPage.ts');
  assert.match(page, /Records inspected/);
  assert.match(page, /New offerings/);
  assert.match(page, /Updated offerings/);
  assert.match(page, /offerings\.errorCount/);
  assert.match(page, /Autonomous Diligence/);
  assert.match(page, /Emails queued \/ sent \/ failed/);
  assert.match(page, /diligence\.resend\.configured/);
  assert.match(page, /Native Offering Intake/);
  assert.match(page, /diligence\.nativeCandidatesFound/);
  assert.match(page, /diligence\.nativeSources/);
  assert.match(page, /Review Queue before \/ after/);
});

test('native platform offerings remain distinct from SEC-filed evidence in the UI', async () => {
  const [offerings, profile, newDeal] = await Promise.all([
    source('pages/offeringsPage.ts'),
    source('pages/radarCompanyPage.ts'),
    source('pages/newDealPage.ts')
  ]);
  assert.match(offerings, /offering\.secFilingUrl/);
  assert.match(offerings, /public listing/);
  assert.match(offerings, /offering\.reconciliationStatus/);
  assert.match(profile, /Public platform evidence/);
  assert.match(newDeal, /Public \$\{offering\.platform\} Regulation Crowdfunding offering/);
  assert.match(newDeal, /SEC reconciliation/);
});

test('Review Queue and detail keep filed facts separate and never auto-create a Deal', async () => {
  const [queue, detail, newDeal] = await Promise.all([
    source('pages/reviewQueuePage.ts'),
    source('pages/diligenceDetailPage.ts'),
    source('pages/newDealPage.ts')
  ]);
  assert.match(queue, /Ready for Review/);
  assert.match(queue, /Needs Review/);
  assert.match(queue, /Partial/);
  assert.match(detail, /SEC-Filed Facts/);
  assert.match(detail, /Platform \/ Issuer Claims/);
  assert.match(detail, /termProvenance/);
  assert.match(detail, /SEC reconciliation/);
  assert.match(detail, /Platform identity/);
  assert.match(detail, /Material discrepancies require review/);
  assert.match(detail, /Automated Search Coverage/);
  assert.match(detail, /shortTerm === null && longTerm === null/);
  assert.match(detail, /money\(totalDebt\(period\.shortTermDebt, period\.longTermDebt\)\)/);
  assert.match(detail, /diligencePacketId=/);
  assert.match(newDeal, /prefillFromDiligence/);
  assert.match(newDeal, /Review every field before explicitly creating/);
  assert.match(newDeal, /Effective terms retain their source classification/);
  assert.equal((newDeal.match(/createDeal\(/g) || []).length, 1);
  assert.match(newDeal, /form\.addEventListener\('submit'[\s\S]*createDeal\(input\)/);
});
