import type { DealInput, InvestorEligibility, OfferingExemption, RevenueStatus } from '../models/deal';

export interface ParsedDealText {
  input: DealInput;
  detectedFields: string[];
  riskSnippets: string[];
}

const KNOWN_PLATFORMS = [
  'Wefunder',
  'StartEngine',
  'Republic',
  'Fundrise',
  'Jarsy',
  'Ross Pre-IPO'
];

function clean(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

function matchFirst(text: string, patterns: RegExp[]): string {
  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match?.[1]) return clean(match[1]);
  }

  return '';
}

function parseMoney(value: string): number | undefined {
  const normalized = value.replace(/[$,\s]/g, '').toLowerCase();
  const match = normalized.match(/^(\d+(?:\.\d+)?)(m|mm|million|k|thousand)?$/);
  if (!match) return undefined;

  const base = Number(match[1]);
  if (!Number.isFinite(base)) return undefined;

  const suffix = match[2];
  if (suffix === 'm' || suffix === 'mm' || suffix === 'million') return Math.round(base * 1_000_000);
  if (suffix === 'k' || suffix === 'thousand') return Math.round(base * 1_000);

  return Math.round(base);
}

function detectPlatform(text: string): string {
  const lowerText = text.toLowerCase();
  return KNOWN_PLATFORMS.find((platform) => lowerText.includes(platform.toLowerCase())) ?? '';
}

function detectOfferingExemption(text: string): OfferingExemption {
  if (/reg\s*cf|regulation\s+crowdfunding/i.test(text)) return 'REG_CF';
  if (/reg\s*a|regulation\s+a/i.test(text)) return 'REG_A';
  if (/reg\s*d|506\s*\(?c\)?|506\s*\(?b\)?/i.test(text)) return 'REG_D';
  return 'UNKNOWN';
}

function detectEligibility(text: string, exemption: OfferingExemption): InvestorEligibility {
  if (/accredited[\s-]+only|accredited investors only|506\s*\(?c\)?|reg\s*d/i.test(text)) {
    return 'ACCREDITED_ONLY';
  }

  if (/non[\s-]+accredited|reg\s*cf|regulation\s+crowdfunding|reg\s*a/i.test(text)) {
    return 'NON_ACCREDITED';
  }

  if (exemption === 'REG_CF' || exemption === 'REG_A') return 'NON_ACCREDITED';
  if (exemption === 'REG_D') return 'ACCREDITED_ONLY';

  return 'UNCLEAR';
}

function detectSecurityType(text: string): DealInput['securityType'] {
  if (/\bsafe\b/i.test(text)) return 'SAFE';
  if (/\bequity\b|common stock|preferred stock/i.test(text)) return 'EQUITY';
  if (/\bnote\b|convertible note/i.test(text)) return 'NOTE';
  if (/revenue share|rev share/i.test(text)) return 'REVENUE_SHARE';
  if (/fund interest|fund units/i.test(text)) return 'FUND_INTEREST';
  if (/\bspv\b|special purpose vehicle/i.test(text)) return 'SPV';
  return 'UNKNOWN';
}

function detectLiquidity(text: string): DealInput['liquidity'] {
  if (/illiquid|no secondary market|no public market/i.test(text)) return 'ILLIQUID';
  if (/redemption window|quarterly redemption|monthly redemption/i.test(text)) return 'REDEMPTION_WINDOW';
  if (/secondary market|secondary possible|transferable/i.test(text)) return 'SECONDARY_POSSIBLE';
  return 'UNKNOWN';
}

function detectRevenueStatus(text: string): RevenueStatus {
  if (/no revenue|pre[\s-]+revenue|not generated revenue/i.test(text)) return 'PRE_REVENUE';
  if (/early revenue|initial revenue/i.test(text)) return 'EARLY_REVENUE';
  if (/\brevenue\b|arr|mrr|sales/i.test(text)) return 'REVENUE';
  return 'UNCLEAR';
}

function detectSector(text: string): string {
  const explicit = matchFirst(text, [
    /sector\s*[:|-]\s*([^\n]+)/i,
    /industry\s*[:|-]\s*([^\n]+)/i,
    /market\s*[:|-]\s*([^\n]+?)(?:\n|$)/i
  ]);

  if (explicit) return explicit;

  const sectorHints: Array<[string, string]> = [
    ['AI', '\\bAI\\b|artificial intelligence|machine learning'],
    ['Fintech', 'fintech|payments|banking'],
    ['Real Estate', 'real estate|property|housing'],
    ['Climate', 'climate|carbon|energy|solar|battery'],
    ['Healthcare', 'healthcare|biotech|medical'],
    ['Consumer', 'consumer|retail|brand'],
    ['Defense', 'defense|aerospace|drone']
  ];

  return sectorHints.find(([, pattern]) => new RegExp(pattern, 'i').test(text))?.[0] ?? '';
}

function detectSummary(text: string): string {
  const explicit = matchFirst(text, [
    /summary\s*[:|-]\s*([\s\S]{20,320}?)(?:\n\s*\n|$)/i,
    /description\s*[:|-]\s*([\s\S]{20,320}?)(?:\n\s*\n|$)/i
  ]);

  if (explicit) return explicit;

  return (
    text
      .split(/\n\s*\n/)
      .map((paragraph) => clean(paragraph))
      .find((paragraph) => paragraph.length >= 40 && paragraph.length <= 360) ?? ''
  );
}

function detectRiskSnippets(text: string): string[] {
  return text
    .split(/(?<=[.!?])\s+|\n+/)
    .map((sentence) => clean(sentence))
    .filter((sentence) =>
      /unclear|unknown|reservation|considering hosting|accredited-only|reg d|506\(c\)|pre-ipo|guaranteed|no revenue|projected|illiquid|going concern|losses|founder control|senior to safe|hidden fees|wire quickly/i.test(
        sentence
      )
    )
    .slice(0, 4);
}

function collectDetectedFields(input: DealInput): string[] {
  return Object.entries(input)
    .filter(([key, value]) => key !== 'decision' && key !== 'rawDealText' && Boolean(value))
    .map(([key]) => key);
}

export function parseDealText(rawText: string): ParsedDealText {
  const text = rawText.trim();
  const offeringExemption = detectOfferingExemption(text);
  const investorEligibility = detectEligibility(text, offeringExemption);
  const companyName =
    matchFirst(text, [
      /company\s*name\s*[:|-]\s*([^\n]+)/i,
      /issuer\s*[:|-]\s*([^\n]+)/i,
      /legal\s*name\s*[:|-]\s*([^\n]+)/i
    ]) ||
    clean(text.split('\n').find((line) => line.trim().length > 2 && line.trim().length < 80) ?? '');

  const minimumInvestmentText = matchFirst(text, [
    /minimum\s+(?:investment|purchase|amount)\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i,
    /invest\s+as\s+little\s+as\s*(\$?[\d,.]+)/i
  ]);
  const amountRaisedText = matchFirst(text, [
    /amount\s+raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i,
    /raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i
  ]);
  const valuationOrCap = matchFirst(text, [
    /valuation\s+cap\s*[:|-]?\s*([^\n.]+)/i,
    /valuation\s*[:|-]?\s*([^\n.]+)/i,
    /pre-money\s+valuation\s*[:|-]?\s*([^\n.]+)/i
  ]);
  const platformFees = matchFirst(text, [
    /platform\s+fees?\s*[:|-]\s*([^\n]+)/i,
    /fees?\s*[:|-]\s*([^\n]{3,120})/i
  ]);

  const riskSnippets = detectRiskSnippets(text);

  const input: DealInput = {
    companyName,
    platform: detectPlatform(text),
    sector: detectSector(text),
    offeringUrl: matchFirst(text, [/(https?:\/\/[^\s)]+)/i]),
    minimumInvestment: parseMoney(minimumInvestmentText),
    valuationOrCap,
    amountRaised: parseMoney(amountRaisedText),
    revenueStatus: detectRevenueStatus(text),
    investorEligibility,
    offeringExemption,
    securityType: detectSecurityType(text),
    liquidity: detectLiquidity(text),
    lockupPeriod: matchFirst(text, [/lock[-\s]?up(?:\s+period)?\s*[:|-]?\s*([^\n]+)/i]),
    platformFees,
    thesis: '',
    mainRisk: riskSnippets.join(' '),
    nextMilestone: matchFirst(text, [/milestone\s*[:|-]\s*([^\n]+)/i]),
    rawDealText: text,
    decision: 'WATCH',
    shortDescription: detectSummary(text)
  };

  return {
    input,
    detectedFields: collectDetectedFields(input),
    riskSnippets
  };
}
