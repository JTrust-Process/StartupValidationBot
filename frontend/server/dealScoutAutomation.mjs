import { digestTextToHtml, DISCLAIMER } from './email/digestHtml.mjs';
import { sendDealScoutDigestEmail } from './email/resendClient.mjs';

const SOURCE_LABELS = {
  REPUBLIC: 'Republic',
  WEFUNDER: 'Wefunder',
  STARTENGINE: 'StartEngine',
  DEALMAKER: 'DealMaker',
  FUNDRISE: 'Fundrise',
  JARSY: 'Jarsy',
  ROSS_PRE_IPO: 'Ross Pre-IPO',
  SEC_EDGAR: 'SEC EDGAR',
  MANUAL: 'Manual',
  OTHER: 'Other'
};

const RISK_KEYWORDS = [
  ['eligibilityConcern', /accredited[-\s]?only|accredited investors only|506\(c\)|regulation d|reg d/i, 'Accredited-only or unclear eligibility'],
  ['preIpoLanguage', /pre[-\s]?ipo|possible future ipo|scarce|allocation[-\s]?limited/i, 'Vague pre-IPO language'],
  ['unclearSecurity', /security type\s*:\s*(unknown|unclear)|not direct shares|spv|fund interest/i, 'No clear security type'],
  ['highValuation', /valuation\s*:\s*(not disclosed|unknown|unclear)|high valuation|crazy valuation/i, 'Crazy/high valuation or valuation unclear'],
  ['revenueConcern', /no revenue|pre[-\s]?revenue|not currently profitable|net losses|losses|going concern/i, 'No revenue, unclear traction, or losses'],
  ['weakEvidence', /unnamed customers|lois are not guaranteed|projected|forward[-\s]?looking/i, 'No named customers or weak evidence'],
  ['guaranteedReturn', /guaranteed return|not guaranteed|target irr|projected returns/i, 'Guaranteed or projected return concern'],
  ['wirePressure', /wire quickly|limited time|act now|pressure/i, 'Pressure to wire money quickly'],
  ['feeConcern', /hidden fees|fees not clearly|fees may apply|carried interest|management fee|platform\/intermediary fees/i, 'Hidden or unclear fees'],
  ['illiquidity', /illiquid|no public market|transfer restrictions|difficult to resell|lockup/i, 'Illiquidity not clearly disclosed']
];

function clean(value) {
  return String(value ?? '').replace(/\s+/g, ' ').trim();
}

function parseList(value) {
  return String(value ?? '')
    .split(/[,;\n]/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function parseListPreserveCase(value) {
  return String(value ?? '')
    .split(/[,;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function parseBooleanEnv(name, fallback) {
  const value = process.env[name];
  if (value === undefined || value === '') return fallback;
  return /^(1|true|yes)$/i.test(value);
}

function parseNumberEnv(name, fallback) {
  const parsed = Number(process.env[name]);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function wait(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function parseMoney(value) {
  const normalized = String(value ?? '').replace(/[$,\s]/g, '').toLowerCase();
  const match = normalized.match(/^(\d+(?:\.\d+)?)(m|mm|million|k|thousand)?$/);
  if (!match) return undefined;

  const amount = Number(match[1]);
  if (!Number.isFinite(amount)) return undefined;
  if (['m', 'mm', 'million'].includes(match[2] ?? '')) return Math.round(amount * 1_000_000);
  if (['k', 'thousand'].includes(match[2] ?? '')) return Math.round(amount * 1_000);
  return Math.round(amount);
}

function formatCurrency(value) {
  if (!Number.isFinite(value)) return 'Unknown';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  }).format(value);
}

function matchLine(rawText, labelPattern) {
  const pattern = new RegExp(`^\\s*(?:${labelPattern})\\s*[:|-]\\s*(.+)$`, 'im');
  return clean(rawText.match(pattern)?.[1] ?? '');
}

function decodeXml(value) {
  return String(value ?? '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
    .replace(/&#x([a-f0-9]+);/gi, (_, code) => String.fromCharCode(parseInt(code, 16)));
}

function stripTags(value) {
  return clean(decodeXml(value).replace(/<[^>]+>/g, ' '));
}

function extractTag(block, tagName) {
  const match = block.match(new RegExp(`<${tagName}\\b[^>]*>([\\s\\S]*?)<\\/${tagName}>`, 'i'));
  return match?.[1] ?? '';
}

function extractLink(block) {
  const atomHref = block.match(/<link\b[^>]*href=["']([^"']+)["'][^>]*\/?>/i)?.[1];
  const tagLink = extractTag(block, 'link');
  return clean(decodeXml(atomHref || tagLink));
}

export function parseRssItems(xml, feedUrl = '') {
  const blocks = [
    ...(String(xml ?? '').match(/<item\b[\s\S]*?<\/item>/gi) ?? []),
    ...(String(xml ?? '').match(/<entry\b[\s\S]*?<\/entry>/gi) ?? [])
  ];

  return blocks
    .map((block) => ({
      title: stripTags(extractTag(block, 'title')),
      link: extractLink(block),
      description: stripTags(
        extractTag(block, 'description') ||
          extractTag(block, 'summary') ||
          extractTag(block, 'content') ||
          extractTag(block, 'content:encoded')
      ),
      publishedAt: stripTags(extractTag(block, 'pubDate') || extractTag(block, 'updated') || extractTag(block, 'published')),
      feedUrl
    }))
    .filter((item) => item.title || item.link || item.description);
}

function inferSourceType(url) {
  const normalized = String(url ?? '').toLowerCase();
  if (normalized.includes('republic.com')) return 'REPUBLIC';
  if (normalized.includes('wefunder.com')) return 'WEFUNDER';
  if (normalized.includes('startengine.com')) return 'STARTENGINE';
  if (normalized.includes('dealmaker')) return 'DEALMAKER';
  if (normalized.includes('fundrise.com')) return 'FUNDRISE';
  if (normalized.includes('sec.gov')) return 'SEC_EDGAR';
  return 'OTHER';
}

function inferPlatform(source, rawText) {
  if (source.sourceType && source.sourceType !== 'OTHER') return SOURCE_LABELS[source.sourceType] ?? source.sourceType;
  const text = `${source.url ?? ''} ${rawText}`.toLowerCase();
  if (text.includes('republic')) return 'Republic';
  if (text.includes('wefunder')) return 'Wefunder';
  if (text.includes('startengine')) return 'StartEngine';
  if (text.includes('dealmaker')) return 'DealMaker';
  if (text.includes('fundrise')) return 'Fundrise';
  if (text.includes('sec.gov')) return 'SEC EDGAR';
  return 'Other';
}

function inferCompanyName(source, rawText) {
  if (source.companyName) return clean(source.companyName);
  const explicit =
    matchLine(rawText, 'company(?:\\s+name)?') ||
    matchLine(rawText, 'issuer(?:\\s+name)?') ||
    clean(rawText.match(/^([A-Z][A-Za-z0-9&.,' -]{2,80})\s+is\s+raising\b/m)?.[1] ?? '');
  if (explicit) return explicit;
  if (source.url) {
    try {
      const path = new URL(source.url).pathname.split('/').filter(Boolean).pop();
      if (path) {
        return path
          .replace(/[-_]+/g, ' ')
          .replace(/\b\w/g, (letter) => letter.toUpperCase());
      }
    } catch {
      return 'Unknown company';
    }
  }
  return 'Unknown company';
}

function inferEligibility(rawText) {
  if (/accredited investors only|accredited[-\s]?only|506\(c\)|regulation d|reg d/i.test(rawText)) return 'ACCREDITED_ONLY';
  if (/non[-\s]?accredited|open to all investors|regulation crowdfunding|reg cf|regulation a|reg a/i.test(rawText)) {
    return 'NON_ACCREDITED';
  }
  return 'UNCLEAR';
}

function inferExemption(rawText) {
  if (/regulation crowdfunding|reg cf/i.test(rawText)) return 'REG_CF';
  if (/regulation a|reg a\+?/i.test(rawText)) return 'REG_A';
  if (/regulation d|reg d|506\(c\)|506\(b\)/i.test(rawText)) return 'REG_D';
  return 'UNKNOWN';
}

function inferSecurityType(rawText) {
  if (/crowd safe|\bsafe\b/i.test(rawText)) return 'SAFE';
  if (/common stock|preferred stock|equity/i.test(rawText)) return 'EQUITY';
  if (/convertible note|\bnote\b/i.test(rawText)) return 'NOTE';
  if (/revenue share|revenue participation/i.test(rawText)) return 'REVENUE_SHARE';
  if (/fund interest|limited partnership|lp interest/i.test(rawText)) return 'FUND_INTEREST';
  if (/\bspv\b|special purpose vehicle/i.test(rawText)) return 'SPV';
  return 'UNKNOWN';
}

function extractMoneyField(rawText, labelPattern) {
  const pattern = new RegExp(`(?:${labelPattern})\\s*(?:is|of|:)?\\s*(\\$?[\\d,.]+\\s*(?:million|thousand|m|mm|k)?)`, 'i');
  const direct = clean(rawText.match(pattern)?.[1] ?? '').replace(/\.$/, '');
  if (direct) return direct;

  return matchLine(rawText, labelPattern);
}

function detectRisks(rawText) {
  return RISK_KEYWORDS
    .filter(([, pattern]) => pattern.test(rawText))
    .map(([key, , label]) => ({ key, label }));
}

function extractDeal(source, rawText) {
  const companyName = inferCompanyName(source, rawText);
  const platform = inferPlatform(source, rawText);
  const sector = matchLine(rawText, 'sector|industry|theme') || 'Unknown';
  const minimumInvestment = extractMoneyField(rawText, 'minimum investment|min investment');
  const valuationOrCap =
    extractMoneyField(rawText, 'valuation cap') ||
    extractMoneyField(rawText, 'valuation') ||
    matchLine(rawText, 'valuation cap|valuation');
  const amountRaised = extractMoneyField(rawText, 'amount raised|raised');
  const investorCount = clean(
    rawText.match(/(?:investor|backer)\s+count\s*[:|-]?\s*([\d,]+)/i)?.[1] ??
      rawText.match(/([\d,]+)\s+(?:investors?|backers?)\b/i)?.[1] ??
      ''
  );
  const deadline =
    matchLine(rawText, 'deadline|offering deadline') ||
    clean(rawText.match(/closes?\s+(?:on\s+)?([A-Z][a-z]+\s+\d{1,2},?\s+\d{4})/i)?.[1] ?? '');
  const eligibility = inferEligibility(rawText);
  const exemption = inferExemption(rawText);
  const securityType = inferSecurityType(rawText);
  const liquidity = matchLine(rawText, 'liquidity') || (/illiquid|no public market/i.test(rawText) ? 'Illiquid' : 'Unknown');
  const fees = matchLine(rawText, 'fees|platform fees') || (/fees/i.test(rawText) ? 'Fees mentioned; review exact investor cost.' : 'Unknown');
  const risks = detectRisks(rawText);
  const dataConfidence = estimateDataConfidence({
    minimumInvestment,
    valuationOrCap,
    amountRaised,
    eligibility,
    exemption,
    securityType,
    liquidity,
    fees,
    rawText
  });

  return {
    companyName,
    platform,
    sector,
    sourceUrl: source.url ?? '',
    minimumInvestment,
    minimumInvestmentValue: parseMoney(minimumInvestment),
    valuationOrCap,
    amountRaised,
    investorCount,
    deadline,
    eligibility,
    exemption,
    securityType,
    liquidity,
    fees,
    risks,
    dataConfidence,
    rawText
  };
}

function estimateDataConfidence(deal) {
  let score = 20;
  if (deal.minimumInvestment) score += 10;
  if (deal.valuationOrCap) score += 10;
  if (deal.amountRaised) score += 8;
  if (deal.eligibility === 'NON_ACCREDITED') score += 14;
  if (deal.eligibility === 'ACCREDITED_ONLY') score += 4;
  if (deal.exemption !== 'UNKNOWN') score += 14;
  if (deal.securityType !== 'UNKNOWN') score += 12;
  if (deal.liquidity !== 'Unknown') score += 6;
  if (deal.fees !== 'Unknown') score += 6;
  if (/customer|contract|revenue|gross margin|arr|mrr|pilot/i.test(deal.rawText)) score += 8;
  if (/unknown|unclear|not disclosed|not clearly/i.test(deal.rawText)) score -= 12;
  return Math.max(0, Math.min(100, Math.round(score)));
}

function buildGoogleNewsRssUrl(query) {
  return `https://news.google.com/rss/search?q=${encodeURIComponent(query)}&hl=en-US&gl=US&ceid=US:en`;
}

function buildSecEdgarFormCFeedUrl() {
  return 'https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&type=C&owner=include&count=100&output=atom';
}

function inferCompanyNameFromTitle(title) {
  return clean(
    String(title ?? '')
      .replace(/^(?:C|C\/A|C-U|C-AR|C-TR)\s+-\s*/i, '')
      .replace(/\s+\|\s+.*$/, '')
      .replace(/\s+-\s+(Republic|Wefunder|StartEngine|DealMaker|SEC.*|Form C.*|Google News).*$/i, '')
      .replace(/\b(raises?|raising|launches?|files?|announces?)\b.*$/i, '')
  ) || 'Discovered lead';
}

function defaultDiscoveryQueries() {
  return [
    'Reg CF startup raising Republic',
    'Regulation Crowdfunding startup Wefunder',
    'StartEngine Reg CF offering startup',
    'Republic startup offering non accredited investors',
    'Form C startup Regulation Crowdfunding'
  ];
}

function loadDiscoveryConfig() {
  const enabled = parseBooleanEnv('DEAL_SCOUT_ENABLE_DISCOVERY', false);
  const configuredQueries = parseListPreserveCase(process.env.DEAL_SCOUT_DISCOVERY_QUERIES);
  const rssUrls = parseListPreserveCase(process.env.DEAL_SCOUT_DISCOVERY_RSS_URLS);
  const enableSecEdgar = parseBooleanEnv('DEAL_SCOUT_ENABLE_SEC_EDGAR_DISCOVERY', false);
  const queries = configuredQueries.length ? configuredQueries : defaultDiscoveryQueries();

  return {
    enabled,
    rssUrls: [
      ...rssUrls,
      ...(enabled ? queries.map(buildGoogleNewsRssUrl) : []),
      ...(enableSecEdgar ? [buildSecEdgarFormCFeedUrl()] : [])
    ],
    maxItems: parseNumberEnv('DEAL_SCOUT_DISCOVERY_MAX_ITEMS', 25),
    fetchLinks: parseBooleanEnv('DEAL_SCOUT_DISCOVERY_FETCH_LINKS', false),
    requestDelayMs: parseNumberEnv('DEAL_SCOUT_FETCH_DELAY_MS', 750)
  };
}

async function fetchTextUrl(url) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch(url, {
      method: 'GET',
      redirect: 'follow',
      signal: controller.signal,
      headers: {
        'User-Agent': 'StartupDealOS/1.0 research-digest; contact: configured-user',
        Accept: 'application/rss+xml,application/atom+xml,text/xml,text/html,text/plain;q=0.9,*/*;q=0.8'
      }
    });

    if (!response.ok) {
      return {
        ok: false,
        text: '',
        error: `Public fetch failed with HTTP ${response.status}.`
      };
    }

    return {
      ok: true,
      text: await response.text(),
      error: ''
    };
  } catch (error) {
    return {
      ok: false,
      text: '',
      error: error.name === 'AbortError' ? 'Public fetch timed out.' : error.message
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function discoverSources() {
  const config = loadDiscoveryConfig();
  if (!config.enabled && !config.rssUrls.length) return { sources: [], results: [] };

  const sources = [];
  const results = [];
  let nextId = 100_000;

  for (const rssUrl of config.rssUrls) {
    const fetchResult = await fetchTextUrl(rssUrl);
    let discoveredItems = fetchResult.ok
      ? parseRssItems(fetchResult.text, rssUrl)
      : [];
    if (/sec\.gov\/cgi-bin\/browse-edgar/i.test(rssUrl) && /type=C\b/i.test(rssUrl)) {
      discoveredItems = discoveredItems.filter(
        (item) => /^(C|C\/A)\s+-/i.test(item.title) && !/^CERT\b/i.test(item.title)
      );
    }
    discoveredItems = discoveredItems.slice(0, config.maxItems);

    results.push({
      source: {
        id: nextId,
        sourceType: 'OTHER',
        url: rssUrl,
        companyName: 'Discovery feed',
        enabled: true,
        notes: 'Discovery feed fetch',
        pastedText: '',
        discoveryLead: true
      },
      status: fetchResult.ok ? 'OK' : 'ERROR',
      rawText: '',
      error: fetchResult.error,
      discoveredCount: discoveredItems.length
    });

    discoveredItems.forEach((item) => {
      const isSecFormC = /sec\.gov\/cgi-bin\/browse-edgar/i.test(rssUrl) && /^(C|C\/A)\s+-/i.test(item.title);
      const text = [
        item.title,
        item.description,
        isSecFormC ? 'Offering exemption: Reg CF. Investor eligibility: Non-accredited investors may be eligible subject to Regulation Crowdfunding limits.' : '',
        item.publishedAt ? `Published: ${item.publishedAt}` : ''
      ]
        .filter(Boolean)
        .join('\n');
      const url = item.link || item.feedUrl;

      sources.push({
        id: nextId += 1,
        sourceType: inferSourceType(url),
        url,
        companyName: inferCompanyNameFromTitle(item.title),
        enabled: true,
        notes: `Discovered from ${rssUrl}`,
        pastedText: text,
        discoveryLead: true,
        fetchUrl: config.fetchLinks
      });
    });

    if (config.requestDelayMs > 0) await wait(config.requestDelayMs);
  }

  return {
    sources,
    results
  };
}

function loadAutomationSources() {
  const json = process.env.DEAL_SCOUT_AUTOMATION_SOURCES_JSON;
  const sources = [];

  if (json?.trim()) {
    try {
      const parsed = JSON.parse(json);
      if (Array.isArray(parsed)) sources.push(...parsed);
    } catch (error) {
      throw new Error(`Invalid DEAL_SCOUT_AUTOMATION_SOURCES_JSON: ${error.message}`);
    }
  }

  parseListPreserveCase(process.env.DEAL_SCOUT_SOURCE_URLS).forEach((url) => {
    sources.push({
      sourceType: inferSourceType(url),
      url,
      companyName: '',
      enabled: true,
      notes: '',
      pastedText: ''
    });
  });

  return sources
    .map((source, index) => ({
      id: source.id ?? index + 1,
      sourceType: source.sourceType || inferSourceType(source.url),
      url: String(source.url ?? '').trim(),
      companyName: String(source.companyName ?? '').trim(),
      enabled: source.enabled !== false,
      notes: String(source.notes ?? '').trim(),
      pastedText: String(source.pastedText ?? '').trim(),
      discoveryLead: Boolean(source.discoveryLead),
      fetchUrl: Boolean(source.fetchUrl)
    }))
    .filter((source) => source.enabled && (source.url || source.pastedText || source.notes));
}

function loadPreferences() {
  return {
    preferredThemes: parseList(process.env.DEAL_SCOUT_PREFERRED_THEMES || 'AI infrastructure,data centers,energy,fintech,automation'),
    maxMinimumInvestment: parseNumberEnv('DEAL_SCOUT_MAX_MINIMUM_INVESTMENT', 500),
    maxRedFlags: parseNumberEnv('DEAL_SCOUT_MAX_RED_FLAGS', 6),
    requireNonAccredited: parseBooleanEnv('DEAL_SCOUT_REQUIRE_NON_ACCREDITED', true),
    requireRegCfOrRegA: parseBooleanEnv('DEAL_SCOUT_REQUIRE_REG_CF_OR_REG_A', true),
    includeDiscoveryLeads: parseBooleanEnv('DEAL_SCOUT_INCLUDE_DISCOVERY_LEADS', true)
  };
}

async function fetchSource(source) {
  if ((source.pastedText || source.notes) && !source.fetchUrl) {
    return {
      source,
      status: 'OK',
      rawText: [source.pastedText, source.notes].filter(Boolean).join('\n\n')
    };
  }

  if (!/^https?:\/\//i.test(source.url)) {
    return { source, status: 'ERROR', rawText: '', error: 'Source URL must start with http or https.' };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch(source.url, {
      method: 'GET',
      redirect: 'follow',
      signal: controller.signal,
      headers: {
        'User-Agent': 'StartupDealOS/1.0 research-digest; contact: configured-user',
        Accept: 'text/html,text/plain,application/json;q=0.9,*/*;q=0.8'
      }
    });

    if (!response.ok) {
      return {
        source,
        status: 'ERROR',
        rawText: '',
        error: `Public fetch failed with HTTP ${response.status}.`
      };
    }

    const rawText = await response.text();
    return {
      source,
      status: 'OK',
      rawText: [source.pastedText, source.notes, rawText].filter(Boolean).join('\n\n')
    };
  } catch (error) {
    return {
      source,
      status: 'ERROR',
      rawText: '',
      error: error.name === 'AbortError' ? 'Public fetch timed out.' : error.message
    };
  } finally {
    clearTimeout(timeout);
  }
}

function scoreCandidate(deal, preferences) {
  const text = `${deal.companyName} ${deal.sector} ${deal.rawText}`.toLowerCase();
  const matchedThemes = preferences.preferredThemes.filter((theme) => text.includes(theme));
  const themeScore = preferences.preferredThemes.length ? Math.min(20, matchedThemes.length * 10) : 10;
  const accessScore =
    deal.eligibility === 'NON_ACCREDITED' && ['REG_CF', 'REG_A'].includes(deal.exemption)
      ? 20
      : deal.eligibility === 'NON_ACCREDITED'
        ? 14
        : deal.eligibility === 'UNCLEAR'
          ? 6
          : 0;
  const confidenceScore = Math.min(20, Math.round(deal.dataConfidence * 0.2));
  const tractionScore = /revenue|customer|contract|pilot|gross margin|arr|mrr/i.test(deal.rawText) ? 12 : 4;
  const valuation = parseMoney(deal.valuationOrCap);
  const valuationScore = valuation ? (valuation <= 25_000_000 ? 10 : valuation <= 75_000_000 ? 7 : 3) : 4;
  const urgencyScore = deal.deadline ? 5 : 0;
  const redFlagPenalty = Math.min(15, deal.risks.length * 3);

  return {
    score: Math.max(0, Math.min(100, Math.round(themeScore + accessScore + confidenceScore + tractionScore + valuationScore + urgencyScore - redFlagPenalty))),
    whyMatched: [
      matchedThemes.length ? `Matches themes: ${matchedThemes.join(', ')}` : 'No preferred theme match',
      accessScore >= 20 ? 'Non-accredited Reg CF/Reg A access indicated' : `Access needs review: ${deal.eligibility}/${deal.exemption}`,
      `Data confidence ${deal.dataConfidence}/100`,
      `${deal.risks.length} risk suggestion(s)`
    ]
  };
}

function preferencesAllow(deal, preferences, source) {
  if (source.discoveryLead && preferences.includeDiscoveryLeads) {
    if (deal.eligibility === 'ACCREDITED_ONLY' || deal.exemption === 'REG_D') return false;
    if (deal.minimumInvestmentValue && deal.minimumInvestmentValue > preferences.maxMinimumInvestment) return false;
    if (deal.risks.length > preferences.maxRedFlags) return false;
    return true;
  }

  if (preferences.requireNonAccredited && deal.eligibility !== 'NON_ACCREDITED') return false;
  if (preferences.requireRegCfOrRegA && !['REG_CF', 'REG_A'].includes(deal.exemption)) return false;
  if (deal.minimumInvestmentValue && deal.minimumInvestmentValue > preferences.maxMinimumInvestment) return false;
  if (deal.risks.length > preferences.maxRedFlags) return false;
  return true;
}

function buildCandidate(deal, source, preferences) {
  const score = scoreCandidate(deal, preferences);

  return {
    companyName: deal.companyName,
    platformOrSource: deal.platform,
    sector: deal.sector,
    sourceUrl: deal.sourceUrl,
    score: score.score,
    whyMatched: score.whyMatched,
    keyTerms: [
      `Eligibility: ${deal.eligibility}`,
      `Exemption: ${deal.exemption}`,
      `Security: ${deal.securityType}`,
      `Minimum: ${deal.minimumInvestmentValue ? formatCurrency(deal.minimumInvestmentValue) : deal.minimumInvestment || 'Unknown'}`,
      `Valuation/cap: ${deal.valuationOrCap || 'Unknown'}`,
      deal.deadline ? `Deadline: ${deal.deadline}` : ''
    ].filter(Boolean),
    strongestEvidence: deal.amountRaised
      ? `Amount raised: ${deal.amountRaised}`
      : deal.investorCount
        ? `Investor count: ${deal.investorCount}`
        : 'No strong evidence captured yet.',
    mainRedFlags: deal.risks.map((risk) => risk.label),
    notableChanges: [],
    suggestedNextStep: source.discoveryLead
      ? 'Open the discovered source and confirm whether this is a real Reg CF or Reg A offering before adding it to your deal tracker.'
      : source.url
      ? 'Open the source and review offering documents before making any decision.'
      : 'Review the configured source notes and add a deal record if it is worth tracking.'
  };
}

function buildDigest(candidates, sourceResults) {
  const body = [
    'Subject: Weekly Startup Deal Scout - companies to consider researching',
    '',
    DISCLAIMER,
    '',
    candidates.length
      ? candidates
          .slice(0, 5)
          .map(
            (candidate, index) => `${index + 1}. ${candidate.companyName} (${candidate.platformOrSource})
Review-priority score: ${candidate.score}/100
Why it matched: ${candidate.whyMatched.join('; ')}
Key terms: ${candidate.keyTerms.join('; ')}
Strongest evidence: ${candidate.strongestEvidence}
Main red flags: ${candidate.mainRedFlags.length ? candidate.mainRedFlags.join('; ') : 'None detected yet'}
Suggested next step: ${candidate.suggestedNextStep}
Open: ${candidate.sourceUrl || 'No URL configured'}`
          )
          .join('\n\n')
      : 'No candidates matched the current automated scout preferences. Add public source URLs, loosen filters, or configure pasted source text on the server.',
    '',
    sourceResults.some((result) => result.status !== 'OK')
      ? `Source issues: ${sourceResults
          .filter((result) => result.status !== 'OK')
          .map((result) => `${result.source.companyName || result.source.url || `Source ${result.source.id}`}: ${result.error}`)
          .join('; ')}`
      : 'Source issues: none from this run.',
    '',
    'Reminder: this email is for research triage only. It is not a recommendation to buy, sell, or invest.'
  ].join('\n');

  return {
    subject: 'Weekly Startup Deal Scout - deals to review',
    text: body,
    html: digestTextToHtml(body)
  };
}

function dedupeSources(sources) {
  const seen = new Set();

  return sources.filter((source) => {
    const key = `${String(source.url || '').toLowerCase()}|${String(source.companyName || '').toLowerCase()}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export async function runAutomatedDealScoutDigest({ send = false } = {}) {
  const configuredSources = loadAutomationSources();
  const discovery = await discoverSources();
  const sources = dedupeSources([...configuredSources, ...discovery.sources]);
  const preferences = loadPreferences();
  const sourceResults = [...discovery.results];
  const candidates = [];

  for (const source of sources) {
    const fetchResult = await fetchSource(source);
    sourceResults.push(fetchResult);

    if (fetchResult.status !== 'OK') continue;

    const deal = extractDeal(source, fetchResult.rawText);
    if (!preferencesAllow(deal, preferences, source)) continue;
    candidates.push(buildCandidate(deal, source, preferences));
  }

  candidates.sort((a, b) => b.score - a.score);
  const digest = buildDigest(candidates, sourceResults);
  const result = {
    ok: true,
    generatedAt: new Date().toISOString(),
    sourceCount: sources.length,
    configuredSourceCount: configuredSources.length,
    discoveredSourceCount: discovery.sources.length,
    checkedCount: sourceResults.filter((item) => item.status === 'OK').length,
    errorCount: sourceResults.filter((item) => item.status !== 'OK').length,
    candidateCount: candidates.length,
    candidates: candidates.slice(0, 5),
    digest,
    email: null
  };

  if (send) {
    result.email = await sendDealScoutDigestEmail({
      subject: digest.subject,
      text: digest.text,
      html: digest.html
    });
    result.ok = Boolean(result.email?.ok);
  }

  return result;
}
