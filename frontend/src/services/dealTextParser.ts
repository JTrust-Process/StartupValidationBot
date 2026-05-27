import type {
  DealImportRecordInput,
  DealInput,
  ImportFieldSuggestion,
  ImportMode,
  ImportRedFlagSuggestion,
  ImportSection,
  ImportSectionName,
  InvestorEligibility,
  OfferingExemption,
  RedFlagKey,
  RevenueStatus,
  SuggestionConfidence
} from '../models/deal';
import { RED_FLAG_DEFINITIONS } from '../models/deal';

export interface ParsedDealText {
  importRecord: DealImportRecordInput;
  cleanedText: string;
  sections: ImportSection[];
  fieldSuggestions: ImportFieldSuggestion[];
  suggestedRedFlags: ImportRedFlagSuggestion[];
}

interface ParseOptions {
  importMode: ImportMode;
  title: string;
  sourceUrl: string;
  rawText: string;
}

interface FieldRule {
  fieldName: keyof DealInput;
  patterns: RegExp[];
  confidence: SuggestionConfidence;
  fromText?: (value: string, text: string) => string;
}

interface RiskRule {
  redFlagKey: RedFlagKey;
  keywords: string[];
  confidence: SuggestionConfidence;
}

const KNOWN_PLATFORMS = [
  'Wefunder',
  'StartEngine',
  'Republic',
  'Fundrise',
  'Jarsy',
  'Ross Pre-IPO'
];
const PLATFORM_PATTERN = new RegExp(`(${KNOWN_PLATFORMS.join('|')})`, 'i');

const SECTION_LABELS: Record<ImportSectionName, string> = {
  CORE_TERMS: 'Core Terms',
  COMPANY_DESCRIPTION: 'Company Description',
  TRACTION_CLAIMS: 'Traction Claims',
  FINANCIALS: 'Financials',
  RISK_FACTORS: 'Risk Factors',
  FEES_USE_OF_PROCEEDS: 'Fees / Use of Proceeds',
  LEGAL_ELIGIBILITY: 'Legal / Eligibility',
  NOISE_IGNORE: 'Noise / Ignore'
};

const NOISE_PATTERNS = [
  /^(log in|sign up|invest now|reserve|follow|share|learn more|read more|view all|comments?|updates?|faq|footer|privacy|terms)$/i,
  /^(facebook|twitter|linkedin|instagram|youtube)$/i,
  /^(copyright|all rights reserved|cookie|accept cookies)/i,
  /^(home|about|portfolio|contact|careers|help)$/i
];

const FIELD_RULES: FieldRule[] = [
  {
    fieldName: 'companyName',
    patterns: [/company\s*name\s*[:|-]\s*([^\n]+)/i, /issuer\s*[:|-]\s*([^\n]+)/i, /legal\s*name\s*[:|-]\s*([^\n]+)/i],
    confidence: 'HIGH'
  },
  {
    fieldName: 'platform',
    patterns: [PLATFORM_PATTERN],
    confidence: 'HIGH'
  },
  {
    fieldName: 'offeringUrl',
    patterns: [/(https?:\/\/[^\s)]+)/i],
    confidence: 'MEDIUM'
  },
  {
    fieldName: 'minimumInvestment',
    patterns: [/minimum\s+(?:investment|purchase|amount)\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i, /invest\s+as\s+little\s+as\s*(\$?[\d,.]+)/i],
    confidence: 'HIGH',
    fromText: (value) => String(parseMoney(value) ?? value)
  },
  {
    fieldName: 'valuationOrCap',
    patterns: [/valuation\s+cap\s*[:|-]?\s*([^\n.]+)/i, /pre-money\s+valuation\s*[:|-]?\s*([^\n.]+)/i, /valuation\s*[:|-]?\s*([^\n.]+)/i],
    confidence: 'HIGH'
  },
  {
    fieldName: 'amountRaised',
    patterns: [/amount\s+raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i, /raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|mm|million|thousand)?)/i],
    confidence: 'MEDIUM',
    fromText: (value) => String(parseMoney(value) ?? value)
  },
  {
    fieldName: 'offeringExemption',
    patterns: [/(Reg(?:ulation)?\s+CF|Reg(?:ulation)?\s+A|Reg(?:ulation)?\s+D|506\s*\(?c\)?)/i],
    confidence: 'HIGH',
    fromText: (value) => detectOfferingExemption(value)
  },
  {
    fieldName: 'investorEligibility',
    patterns: [
      /investor\s+eligibility\s*[:|-]\s*([^\n]+)/i,
      /(non-accredited|accredited investors only|accredited-only|506\s*\(?c\)?|reg(?:ulation)?\s*d|unclear eligibility)/i
    ],
    confidence: 'HIGH',
    fromText: (value, text) => detectEligibility(`${value} ${text}`, detectOfferingExemption(`${value} ${text}`))
  },
  {
    fieldName: 'securityType',
    patterns: [/(SAFE|simple agreement for future equity|equity|common stock|preferred stock|convertible note|revenue share|fund interest|SPV)/i],
    confidence: 'HIGH',
    fromText: (value) => detectSecurityType(value)
  },
  {
    fieldName: 'liquidity',
    patterns: [/(illiquid|no secondary market|no public market|redemption window|secondary possible|transferable)/i],
    confidence: 'MEDIUM',
    fromText: (value) => detectLiquidity(value)
  },
  {
    fieldName: 'lockupPeriod',
    patterns: [/lock[-\s]?up(?:\s+period)?\s*[:|-]?\s*([^\n]+)/i, /transfer restrictions?\s*[:|-]?\s*([^\n]+)/i],
    confidence: 'MEDIUM'
  },
  {
    fieldName: 'platformFees',
    patterns: [/platform\s+fees?\s*[:|-]\s*([^\n]+)/i, /investor\s+fees?\s*[:|-]\s*([^\n]+)/i, /fees?\s*[:|-]\s*([^\n]{3,120})/i],
    confidence: 'MEDIUM'
  },
  {
    fieldName: 'revenueStatus',
    patterns: [/(no revenue|pre-revenue|early revenue|generated revenue|revenue)/i],
    confidence: 'MEDIUM',
    fromText: (value, text) => detectRevenueStatus(`${value} ${text}`)
  },
  {
    fieldName: 'nextMilestone',
    patterns: [/milestone\s*[:|-]\s*([^\n]+)/i, /next\s+(?:milestone|catalyst)\s*[:|-]\s*([^\n]+)/i],
    confidence: 'LOW'
  }
];

const RISK_RULES: RiskRule[] = [
  { redFlagKey: 'eligibilityConcern', keywords: ['accredited-only', 'accredited only', 'reg d', '506(c)', '506 c'], confidence: 'HIGH' },
  { redFlagKey: 'missingOfferingDocuments', keywords: ['no offering documents', 'documents missing', 'offering documents unavailable'], confidence: 'MEDIUM' },
  { redFlagKey: 'vaguePreIpoLanguage', keywords: ['pre-ipo', 'pre ipo'], confidence: 'HIGH' },
  { redFlagKey: 'unclearSecurityType', keywords: ['reservation', 'senior to safe'], confidence: 'MEDIUM' },
  { redFlagKey: 'unclearRevenueOrTraction', keywords: ['projected', 'no revenue', 'going concern', 'losses'], confidence: 'HIGH' },
  { redFlagKey: 'weakCustomerEvidence', keywords: ['considering hosting', 'founder control'], confidence: 'MEDIUM' },
  { redFlagKey: 'guaranteedReturnClaims', keywords: ['guaranteed'], confidence: 'HIGH' },
  { redFlagKey: 'wirePressure', keywords: ['wire quickly'], confidence: 'HIGH' },
  { redFlagKey: 'unclearFees', keywords: ['hidden fees', 'fees unknown', 'unclear fees'], confidence: 'HIGH' },
  { redFlagKey: 'unclearIlliquidity', keywords: ['illiquid', 'no public market', 'transfer restrictions'], confidence: 'HIGH' },
  { redFlagKey: 'eligibilityConcern', keywords: ['unclear', 'unknown'], confidence: 'LOW' }
];

function clean(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

function lineClean(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

function parseMoney(value: string): number | undefined {
  const normalized = value.replace(/[$,\s]/g, '').toLowerCase();
  const match = normalized.match(/^(\d+(?:\.\d+)?)(m|mm|million|k|thousand)?$/);
  if (!match) return undefined;

  const amount = Number(match[1]);
  if (!Number.isFinite(amount)) return undefined;
  if (['m', 'mm', 'million'].includes(match[2] ?? '')) return Math.round(amount * 1_000_000);
  if (['k', 'thousand'].includes(match[2] ?? '')) return Math.round(amount * 1_000);
  return Math.round(amount);
}

function detectOfferingExemption(text: string): OfferingExemption {
  if (/reg\s*cf|regulation\s+crowdfunding/i.test(text)) return 'REG_CF';
  if (/reg\s*a|regulation\s+a/i.test(text)) return 'REG_A';
  if (/reg\s*d|regulation\s+d|506\s*\(?c\)?|506\s*\(?b\)?/i.test(text)) return 'REG_D';
  return 'UNKNOWN';
}

function detectEligibility(text: string, exemption: OfferingExemption): InvestorEligibility {
  if (
    /investor\s+eligibility\s*[:|-]\s*unclear|eligibility\s+(?:is\s+)?unclear|unclear eligibility|must be confirmed|confirm\s+(?:in writing\s+)?whether\s+non[\s-]+accredited|may be limited to accredited/i.test(text)
  ) {
    return 'UNCLEAR';
  }
  if (/accredited[\s-]+only|accredited investors only|506\s*\(?c\)?|reg\s*d|regulation\s+d/i.test(text)) return 'ACCREDITED_ONLY';
  if (/open to non[\s-]+accredited|non[\s-]+accredited investors may participate|non[\s-]+accredited investors subject|available to non[\s-]+accredited|reg\s*cf|regulation\s+crowdfunding|reg\s*a/i.test(text)) return 'NON_ACCREDITED';
  if (exemption === 'REG_CF' || exemption === 'REG_A') return 'NON_ACCREDITED';
  if (exemption === 'REG_D') return 'ACCREDITED_ONLY';
  return 'UNCLEAR';
}

function detectSecurityType(text: string): DealInput['securityType'] {
  if (/\bsafe\b|future equity/i.test(text)) return 'SAFE';
  if (/\bequity\b|common stock|preferred stock/i.test(text)) return 'EQUITY';
  if (/\bnote\b|convertible note/i.test(text)) return 'NOTE';
  if (/revenue share|rev share/i.test(text)) return 'REVENUE_SHARE';
  if (/fund interest|fund units/i.test(text)) return 'FUND_INTEREST';
  if (/\bspv\b|special purpose vehicle/i.test(text)) return 'SPV';
  return 'UNKNOWN';
}

function detectLiquidity(text: string): DealInput['liquidity'] {
  if (/illiquid|no secondary market|no public market|transfer restrictions/i.test(text)) return 'ILLIQUID';
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
    /market\s*[:|-]\s*([^\n]+)/i
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

function matchFirst(text: string, patterns: RegExp[]): string {
  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match?.[1]) return clean(match[1]);
  }
  return '';
}

function isNoiseLine(line: string): boolean {
  const text = lineClean(line);
  if (!text) return true;
  if (text.length <= 2) return true;
  if (text.length <= 18 && NOISE_PATTERNS.some((pattern) => pattern.test(text))) return true;
  if (/^[^a-zA-Z0-9]+$/.test(text)) return true;
  return false;
}

function preprocessLazyText(rawText: string): string {
  const counts = new Map<string, number>();
  const lines = rawText.split(/\r?\n/).map(lineClean);

  lines.forEach((line) => {
    const key = line.toLowerCase();
    if (key) counts.set(key, (counts.get(key) ?? 0) + 1);
  });

  return lines
    .filter((line) => {
      if (isNoiseLine(line)) return false;
      const count = counts.get(line.toLowerCase()) ?? 0;
      if (count >= 3 && line.length < 80) return false;
      if (line.length < 8 && !/^\$?\d/.test(line)) return false;
      return true;
    })
    .join('\n');
}

function classifyLine(line: string): ImportSectionName {
  if (isNoiseLine(line)) return 'NOISE_IGNORE';
  if (/minimum|valuation|security|offering|raise|deadline|investment|share price|price per share/i.test(line)) return 'CORE_TERMS';
  if (/revenue|income|loss|cash|debt|financial|arr|mrr|sales/i.test(line)) return 'FINANCIALS';
  if (/risk|going concern|illiquid|losses|dilution|no public market|transfer restrictions|guaranteed|wire quickly|hidden fees/i.test(line)) return 'RISK_FACTORS';
  if (/fee|fees|use of proceeds|proceeds|intermediary/i.test(line)) return 'FEES_USE_OF_PROCEEDS';
  if (/reg cf|reg a|reg d|506|accredited|non-accredited|eligibility|legal|issuer|form c/i.test(line)) return 'LEGAL_ELIGIBILITY';
  if (/customer|contract|traction|users|pilot|partnership|revenue growth|growth/i.test(line)) return 'TRACTION_CLAIMS';
  if (/about|mission|company|founded|builds|platform|product|solution/i.test(line)) return 'COMPANY_DESCRIPTION';
  return line.length > 220 ? 'COMPANY_DESCRIPTION' : 'NOISE_IGNORE';
}

function detectSections(rawText: string, cleanedText: string): ImportSection[] {
  const buckets = new Map<ImportSectionName, string[]>();
  const sourceLines = cleanedText.split(/\r?\n/).map(lineClean).filter(Boolean);
  const rawNoiseLines = rawText
    .split(/\r?\n/)
    .map(lineClean)
    .filter((line) => line && isNoiseLine(line))
    .slice(0, 40);

  sourceLines.forEach((line) => {
    const bucket = classifyLine(line);
    buckets.set(bucket, [...(buckets.get(bucket) ?? []), line]);
  });

  if (rawNoiseLines.length) {
    buckets.set('NOISE_IGNORE', [...(buckets.get('NOISE_IGNORE') ?? []), ...rawNoiseLines]);
  }

  return Object.entries(SECTION_LABELS).map(([sectionName, label]) => {
    const lines = buckets.get(sectionName as ImportSectionName) ?? [];
    return {
      sectionName: sectionName as ImportSectionName,
      label,
      text: lines.slice(0, 16).join('\n'),
      lineCount: lines.length
    };
  });
}

function sourceSnippet(text: string, pattern: RegExp): string {
  const match = text.match(pattern);
  if (!match?.index && match?.index !== 0) return '';
  const start = Math.max(0, match.index - 90);
  const end = Math.min(text.length, match.index + match[0].length + 140);
  return clean(text.slice(start, end));
}

function addSuggestion(
  suggestions: ImportFieldSuggestion[],
  fieldName: keyof DealInput,
  suggestedValue: string,
  confidence: SuggestionConfidence,
  sourceSnippetValue: string
): void {
  if (!suggestedValue) return;
  if (suggestions.some((suggestion) => suggestion.fieldName === fieldName && suggestion.suggestedValue === suggestedValue)) return;

  suggestions.push({
    id: `${fieldName}-${suggestions.length + 1}`,
    fieldName,
    suggestedValue,
    confidence,
    sourceSnippet: sourceSnippetValue,
    accepted: false
  });
}

function generateFieldSuggestions(cleanedText: string, sections: ImportSection[]): ImportFieldSuggestion[] {
  const suggestions: ImportFieldSuggestion[] = [];
  const sectionText = (sectionName: ImportSectionName) =>
    sections.find((section) => section.sectionName === sectionName)?.text || '';
  const combinedText = [
    sectionText('CORE_TERMS'),
    sectionText('LEGAL_ELIGIBILITY'),
    sectionText('FINANCIALS'),
    sectionText('FEES_USE_OF_PROCEEDS'),
    cleanedText
  ].join('\n');

  FIELD_RULES.forEach((rule) => {
    for (const pattern of rule.patterns) {
      const match = combinedText.match(pattern);
      if (!match?.[1]) continue;

      addSuggestion(
        suggestions,
        rule.fieldName,
        rule.fromText ? rule.fromText(match[1], combinedText) : clean(match[1]),
        rule.confidence,
        sourceSnippet(combinedText, pattern) || clean(match[0])
      );
      break;
    }
  });

  const sector = detectSector(cleanedText);
  addSuggestion(suggestions, 'sector', sector, sector ? 'LOW' : 'LOW', sector ? `Detected sector keyword in pasted text.` : '');

  const description =
    sectionText('COMPANY_DESCRIPTION')
      .split('\n')
      .find((line) => line.length >= 40 && line.length <= 420) ||
    cleanedText
      .split(/\n\s*\n|\n/)
      .map(clean)
      .find((line) => line.length >= 70 && line.length <= 420) ||
    '';
  addSuggestion(suggestions, 'shortDescription', description, description ? 'MEDIUM' : 'LOW', description);

  const traction = sectionText('TRACTION_CLAIMS').split('\n').filter(Boolean).slice(0, 3).join(' ');
  addSuggestion(suggestions, 'thesis', traction, traction ? 'LOW' : 'LOW', traction);

  const risk = sectionText('RISK_FACTORS').split('\n').filter(Boolean).slice(0, 3).join(' ');
  addSuggestion(suggestions, 'mainRisk', risk, risk ? 'MEDIUM' : 'LOW', risk);

  return suggestions.filter((suggestion) => Boolean(suggestion.suggestedValue));
}

function redFlagLabel(key: RedFlagKey): string {
  return RED_FLAG_DEFINITIONS.find((definition) => definition.key === key)?.label ?? key;
}

function generateRiskSuggestions(text: string): ImportRedFlagSuggestion[] {
  const normalized = text.toLowerCase();
  const suggestions: ImportRedFlagSuggestion[] = [];

  RISK_RULES.forEach((rule) => {
    const keyword = rule.keywords.find((candidate) => normalized.includes(candidate.toLowerCase()));
    if (!keyword) return;
    if (suggestions.some((suggestion) => suggestion.redFlagKey === rule.redFlagKey && suggestion.sourceSnippet.includes(keyword))) return;

    const keywordPattern = new RegExp(keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i');
    suggestions.push({
      id: `${rule.redFlagKey}-${suggestions.length + 1}`,
      redFlagKey: rule.redFlagKey,
      label: redFlagLabel(rule.redFlagKey),
      confidence: rule.confidence,
      sourceSnippet: sourceSnippet(text, keywordPattern) || keyword,
      accepted: false
    });
  });

  return suggestions;
}

export function parseDealText(options: ParseOptions): ParsedDealText {
  const rawText = options.rawText.trim();
  const cleanedText = options.importMode === 'LAZY' ? preprocessLazyText(rawText) : rawText;
  const sections = detectSections(rawText, cleanedText);
  const fieldSuggestions = generateFieldSuggestions(cleanedText, sections);
  const suggestedRedFlags = generateRiskSuggestions(cleanedText);

  return {
    cleanedText,
    sections,
    fieldSuggestions,
    suggestedRedFlags,
    importRecord: {
      importMode: options.importMode,
      title: options.title,
      sourceUrl: options.sourceUrl,
      rawText,
      cleanedText,
      sections,
      fieldSuggestions,
      suggestedRedFlags
    }
  };
}
