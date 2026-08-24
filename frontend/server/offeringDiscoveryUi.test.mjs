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
  assert.match(profile, /offering\.deadline \? formatRadarDate\(offering\.deadline\) : 'Unknown'/);
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
