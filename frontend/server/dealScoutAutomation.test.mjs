import assert from 'node:assert/strict';
import test from 'node:test';
import { parseRssItems, runAutomatedDealScoutDigest } from './dealScoutAutomation.mjs';

const AUTOMATION_ENV_NAMES = [
  'DEAL_SCOUT_AUTOMATION_SOURCES_JSON',
  'DEAL_SCOUT_DISCOVERY_MAX_ITEMS',
  'DEAL_SCOUT_DISCOVERY_QUERIES',
  'DEAL_SCOUT_DISCOVERY_RSS_URLS',
  'DEAL_SCOUT_ENABLE_DISCOVERY',
  'DEAL_SCOUT_ENABLE_SEC_EDGAR_DISCOVERY',
  'DEAL_SCOUT_FETCH_DELAY_MS',
  'DEAL_SCOUT_INCLUDE_DISCOVERY_LEADS',
  'DEAL_SCOUT_MAX_MINIMUM_INVESTMENT',
  'DEAL_SCOUT_MAX_RED_FLAGS',
  'DEAL_SCOUT_REQUIRE_NON_ACCREDITED',
  'DEAL_SCOUT_REQUIRE_REG_CF_OR_REG_A',
  'DEAL_SCOUT_SOURCE_URLS'
];

function preserveEnvironment() {
  const originalValues = new Map(
    AUTOMATION_ENV_NAMES.map((name) => [name, process.env[name]])
  );
  const originalFetch = globalThis.fetch;

  return () => {
    for (const [name, value] of originalValues) {
      if (value === undefined) {
        delete process.env[name];
      } else {
        process.env[name] = value;
      }
    }
    globalThis.fetch = originalFetch;
  };
}

test('parses RSS items into discovery inputs', () => {
  const items = parseRssItems(
    '<rss><channel><item><title>GridCool Systems</title><link>https://example.test/gridcool</link><description>Reg CF energy startup</description></item></channel></rss>',
    'https://feed.test/rss'
  );

  assert.deepEqual(items, [
    {
      title: 'GridCool Systems',
      link: 'https://example.test/gridcool',
      description: 'Reg CF energy startup',
      publishedAt: '',
      feedUrl: 'https://feed.test/rss'
    }
  ]);
});

test('deduplicates discovery leads by URL and reports source checks separately', async (context) => {
  const restoreEnvironment = preserveEnvironment();
  context.after(restoreEnvironment);

  process.env.DEAL_SCOUT_AUTOMATION_SOURCES_JSON = '[]';
  process.env.DEAL_SCOUT_DISCOVERY_MAX_ITEMS = '25';
  process.env.DEAL_SCOUT_DISCOVERY_QUERIES = '';
  process.env.DEAL_SCOUT_DISCOVERY_RSS_URLS = 'https://feed.test/rss';
  process.env.DEAL_SCOUT_ENABLE_DISCOVERY = 'false';
  process.env.DEAL_SCOUT_ENABLE_SEC_EDGAR_DISCOVERY = 'false';
  process.env.DEAL_SCOUT_INCLUDE_DISCOVERY_LEADS = 'true';
  process.env.DEAL_SCOUT_SOURCE_URLS = '';

  globalThis.fetch = async () => ({
    ok: true,
    text: async () =>
      '<rss><channel>' +
      '<item><title>Alpha Corp - Republic</title><link>https://example.test/deal</link><description>Reg CF energy startup</description></item>' +
      '<item><title>Alpha Corporation - Wefunder</title><link>https://example.test/deal/</link><description>Reg CF energy startup</description></item>' +
      '</channel></rss>'
  });

  const result = await runAutomatedDealScoutDigest();

  assert.equal(result.sourceCount, 1);
  assert.equal(result.configuredSourceCount, 0);
  assert.equal(result.discoveredSourceCount, 1);
  assert.equal(result.discoveryFeedCount, 1);
  assert.equal(result.checkedCount, 1);
  assert.equal(result.candidateCount, 1);
});
test('rejects automation sources JSON that is not an array', async (context) => {
  const restoreEnvironment = preserveEnvironment();
  context.after(restoreEnvironment);

  process.env.DEAL_SCOUT_AUTOMATION_SOURCES_JSON = '{}';

  await assert.rejects(
    runAutomatedDealScoutDigest(),
    /Invalid DEAL_SCOUT_AUTOMATION_SOURCES_JSON: must be a JSON array/
  );
});

test('rejects negative discovery item limits', async (context) => {
  const restoreEnvironment = preserveEnvironment();
  context.after(restoreEnvironment);

  process.env.DEAL_SCOUT_AUTOMATION_SOURCES_JSON = '[]';
  process.env.DEAL_SCOUT_DISCOVERY_MAX_ITEMS = '-1';

  await assert.rejects(
    runAutomatedDealScoutDigest(),
    /DEAL_SCOUT_DISCOVERY_MAX_ITEMS must be an integer at least 0/
  );
});

test('rejects invalid boolean settings', async (context) => {
  const restoreEnvironment = preserveEnvironment();
  context.after(restoreEnvironment);

  process.env.DEAL_SCOUT_AUTOMATION_SOURCES_JSON = '[]';
  process.env.DEAL_SCOUT_ENABLE_DISCOVERY = 'tru';

  await assert.rejects(
    runAutomatedDealScoutDigest(),
    /DEAL_SCOUT_ENABLE_DISCOVERY must be true or false/
  );
});
