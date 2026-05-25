import type {
  Deal,
  DealDocument,
  DealInput,
  EvidenceClaimInput,
  RedFlagKey
} from '../models/deal';
import {
  getDataConfidenceLabel,
  getDataConfidenceScore,
  getSuggestedRedFlags
} from './riskAnalysis';
import {
  formatCurrency,
  formatDealStatus,
  formatInvestorEligibility,
  formatLiquidity,
  formatOfferingExemption,
  formatSecurityType
} from '../utils/formatters';

export interface DocumentExtractionItem {
  id: string;
  label: string;
  value: string;
  targetField?: keyof DealInput;
  evidenceClaim?: EvidenceClaimInput;
  sourceText: string;
}

export interface DocumentRiskItem {
  id: string;
  label: string;
  matchedText: string;
  redFlagKey?: RedFlagKey;
  evidenceClaim: EvidenceClaimInput;
}

interface ExtractionPattern {
  id: string;
  label: string;
  patterns: RegExp[];
  targetField?: keyof DealInput;
  toValue?: (value: string, document: DealDocument) => string;
}

interface RiskPattern {
  id: string;
  label: string;
  pattern: RegExp;
  redFlagKey?: RedFlagKey;
}

const EXTRACTION_PATTERNS: ExtractionPattern[] = [
  {
    id: 'issuer',
    label: 'Company / legal issuer name',
    patterns: [/issuer\s*[:|-]\s*([^\n]+)/i, /company\s+name\s*[:|-]\s*([^\n]+)/i],
    targetField: 'companyName'
  },
  {
    id: 'exemption',
    label: 'Offering exemption',
    patterns: [/(Reg(?:ulation)?\s+CF|Reg(?:ulation)?\s+A|Reg(?:ulation)?\s+D|506\s*\(?c\)?)/i],
    targetField: 'offeringExemption',
    toValue: (value) => {
      if (/cf|crowdfunding/i.test(value)) return 'REG_CF';
      if (/\ba\b/i.test(value)) return 'REG_A';
      if (/d|506/i.test(value)) return 'REG_D';
      return 'UNKNOWN';
    }
  },
  {
    id: 'security',
    label: 'Security type',
    patterns: [/(SAFE|simple agreement for future equity|common stock|preferred stock|convertible note|revenue share|fund interest|SPV)/i],
    targetField: 'securityType',
    toValue: (value) => {
      if (/safe|future equity/i.test(value)) return 'SAFE';
      if (/stock|equity/i.test(value)) return 'EQUITY';
      if (/note/i.test(value)) return 'NOTE';
      if (/revenue share/i.test(value)) return 'REVENUE_SHARE';
      if (/fund/i.test(value)) return 'FUND_INTEREST';
      if (/spv/i.test(value)) return 'SPV';
      return 'UNKNOWN';
    }
  },
  {
    id: 'valuation',
    label: 'Valuation cap or valuation',
    patterns: [/valuation\s+cap\s*[:|-]?\s*([^\n.]+)/i, /pre-money\s+valuation\s*[:|-]?\s*([^\n.]+)/i, /valuation\s*[:|-]?\s*([^\n.]+)/i],
    targetField: 'valuationOrCap'
  },
  {
    id: 'minimum',
    label: 'Minimum investment',
    patterns: [/minimum\s+(?:investment|purchase)\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|million|thousand)?)/i],
    targetField: 'minimumInvestment'
  },
  {
    id: 'maximumRaise',
    label: 'Maximum raise',
    patterns: [/maximum\s+(?:offering|raise|amount)\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'targetRaise',
    label: 'Target raise',
    patterns: [/target\s+(?:offering|raise|amount)\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'amountRaised',
    label: 'Amount raised',
    patterns: [/amount\s+raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|million|thousand)?)/i, /raised\s*[:|-]?\s*(\$?[\d,.]+\s*(?:k|m|million|thousand)?)/i],
    targetField: 'amountRaised'
  },
  {
    id: 'deadline',
    label: 'Deadline',
    patterns: [/deadline\s*[:|-]?\s*([^\n.]+)/i, /offering\s+ends\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'eligibility',
    label: 'Investor eligibility',
    patterns: [/(non-accredited|accredited investors only|accredited-only|accredited investors|506\s*\(?c\)?)/i],
    targetField: 'investorEligibility',
    toValue: (value) => (/non-accredited/i.test(value) ? 'NON_ACCREDITED' : 'ACCREDITED_ONLY')
  },
  {
    id: 'platform',
    label: 'Platform / intermediary',
    patterns: [/intermediary\s*[:|-]\s*([^\n]+)/i, /(Wefunder|StartEngine|Republic|Fundrise|Jarsy|Ross Pre-IPO)/i],
    targetField: 'platform'
  },
  {
    id: 'fees',
    label: 'Fees',
    patterns: [/platform\s+fees?\s*[:|-]?\s*([^\n]+)/i, /investor\s+fees?\s*[:|-]?\s*([^\n]+)/i, /fees?\s*[:|-]\s*([^\n]{3,140})/i],
    targetField: 'platformFees'
  },
  {
    id: 'revenue',
    label: 'Revenue',
    patterns: [/revenue\s*[:|-]?\s*([^\n.]+)/i, /total\s+revenues?\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'netIncomeLoss',
    label: 'Net income / loss',
    patterns: [/net\s+(?:income|loss)\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'cash',
    label: 'Cash on hand',
    patterns: [/cash(?:\s+and\s+cash\s+equivalents)?\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'debt',
    label: 'Debt',
    patterns: [/(?:total\s+)?debt\s*[:|-]?\s*([^\n.]+)/i, /notes?\s+payable\s*[:|-]?\s*([^\n.]+)/i]
  },
  {
    id: 'goingConcern',
    label: 'Going concern language',
    patterns: [/(going concern[^\n.]*|substantial doubt[^\n.]*)/i]
  },
  {
    id: 'useOfProceeds',
    label: 'Use of proceeds',
    patterns: [/use\s+of\s+proceeds\s*[:|-]?\s*([\s\S]{20,360}?)(?:\n\s*\n|$)/i]
  },
  {
    id: 'relatedParty',
    label: 'Related-party transactions',
    patterns: [/related[-\s]party\s+transactions?\s*[:|-]?\s*([\s\S]{20,300}?)(?:\n\s*\n|$)/i]
  },
  {
    id: 'transferRestrictions',
    label: 'Transfer restrictions',
    patterns: [/transfer\s+restrictions?\s*[:|-]?\s*([\s\S]{20,300}?)(?:\n\s*\n|$)/i, /(restricted from transferring[^\n.]*)/i]
  },
  {
    id: 'votingControl',
    label: 'Voting/control terms',
    patterns: [/(voting rights?[^\n.]*|founder control[^\n.]*|majority voting[^\n.]*)/i]
  },
  {
    id: 'liquidationSeniority',
    label: 'Liquidation preference or seniority language',
    patterns: [/(liquidation preference[^\n.]*|senior securities[^\n.]*|senior to[^\n.]*)/i]
  }
];

const RISK_PATTERNS: RiskPattern[] = [
  { id: 'going-concern', label: 'Going concern', pattern: /going concern|substantial doubt/i, redFlagKey: 'unclearRevenueOrTraction' },
  { id: 'net-losses', label: 'Net losses', pattern: /net losses?|losses from operations|accumulated deficit/i, redFlagKey: 'unclearRevenueOrTraction' },
  { id: 'limited-history', label: 'Limited operating history', pattern: /limited operating history|early stage company/i, redFlagKey: 'weakCustomerEvidence' },
  { id: 'no-revenue', label: 'No revenue', pattern: /no revenue|pre-revenue|not generated revenue/i, redFlagKey: 'unclearRevenueOrTraction' },
  { id: 'dilution', label: 'Dilution', pattern: /dilution|dilutive/i, redFlagKey: 'highValuation' },
  { id: 'senior-securities', label: 'Senior securities', pattern: /senior securities|senior to safe|liquidation preference/i, redFlagKey: 'unclearSecurityType' },
  { id: 'founder-control', label: 'Founder control', pattern: /founder control|majority voting|control of the company/i },
  { id: 'related-party', label: 'Related party', pattern: /related[-\s]party|conflicts? of interest/i },
  { id: 'regulatory-risk', label: 'Regulatory risk', pattern: /regulatory risk|government approval|compliance risk/i },
  { id: 'transfer-restrictions', label: 'Transfer restrictions', pattern: /transfer restrictions?|restricted from transferring|lock-up|lockup/i, redFlagKey: 'unclearIlliquidity' },
  { id: 'illiquid', label: 'Illiquid / no public market', pattern: /illiquid|no public market|no secondary market/i, redFlagKey: 'unclearIlliquidity' },
  { id: 'fees', label: 'Platform or intermediary fees', pattern: /platform fees?|intermediary fees?|investor fees?/i, redFlagKey: 'unclearFees' },
  { id: 'conflicts', label: 'Conflicts of interest', pattern: /conflicts? of interest/i }
];

function clean(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

function snippetAround(text: string, pattern: RegExp): string {
  const match = text.match(pattern);
  if (!match?.index && match?.index !== 0) return '';

  const start = Math.max(0, match.index - 100);
  const end = Math.min(text.length, match.index + match[0].length + 160);
  return clean(text.slice(start, end));
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

function normalizeTargetValue(item: ExtractionPattern, rawValue: string, document: DealDocument): string {
  const value = clean(rawValue);
  if (item.toValue) return item.toValue(value, document);
  if (item.targetField === 'minimumInvestment' || item.targetField === 'amountRaised') {
    return String(parseMoney(value) ?? value);
  }
  return value;
}

function toEvidenceClaim(item: ExtractionPattern, value: string, sourceText: string, document: DealDocument): EvidenceClaimInput {
  return {
    claim: `${item.label}: ${value}`,
    sourceType: document.documentType,
    sourceText,
    evidenceStrength: 'MEDIUM',
    verified: false,
    notes: `Extracted from ${document.title}.`
  };
}

export function extractDocumentItems(document: DealDocument): DocumentExtractionItem[] {
  return EXTRACTION_PATTERNS.flatMap((item) => {
    for (const pattern of item.patterns) {
      const match = document.pastedText.match(pattern);
      if (!match?.[1]) continue;

      const sourceText = snippetAround(document.pastedText, pattern) || clean(match[0]);
      const value = normalizeTargetValue(item, match[1], document);
      return [
        {
          id: `${document.id}-${item.id}`,
          label: item.label,
          value,
          targetField: item.targetField,
          evidenceClaim: toEvidenceClaim(item, value, sourceText, document),
          sourceText
        }
      ];
    }

    return [];
  });
}

export function extractDocumentRisks(document: DealDocument): DocumentRiskItem[] {
  return RISK_PATTERNS.flatMap((risk) => {
    if (!risk.pattern.test(document.pastedText)) return [];

    const matchedText = snippetAround(document.pastedText, risk.pattern);
    return [
      {
        id: `${document.id}-${risk.id}`,
        label: risk.label,
        matchedText,
        redFlagKey: risk.redFlagKey,
        evidenceClaim: {
          claim: `Risk factor detected: ${risk.label}`,
          sourceType: document.documentType,
          sourceText: matchedText,
          evidenceStrength: 'MEDIUM',
          verified: false,
          notes: `Detected from ${document.title}.`
        }
      }
    ];
  });
}

export function getAllDocumentExtractions(deal: Deal): DocumentExtractionItem[] {
  return deal.documents.flatMap(extractDocumentItems);
}

export function getAllDocumentRisks(deal: Deal): DocumentRiskItem[] {
  const ignored = new Set(deal.ignoredDocumentRiskIds);
  return deal.documents.flatMap(extractDocumentRisks).filter((risk) => !ignored.has(risk.id));
}

function getMemoFinalScore(deal: Deal): number {
  const quickScore = Math.max(0, Math.min(100, deal.quickScore * 10));

  if (typeof deal.deepScore !== 'number') {
    return quickScore;
  }

  const deepScore = Math.max(0, Math.min(100, Math.round((deal.deepScore / 25) * 100)));
  return Math.round(quickScore * 0.45 + deepScore * 0.55);
}

function getMemoRecommendation(score: number): {
  label: string;
  suggestedCheckSize: string;
} {
  if (score <= 49) return { label: 'Pass', suggestedCheckSize: '$0' };
  if (score <= 69) return { label: 'Watch', suggestedCheckSize: '$0' };
  if (score <= 84) return { label: 'Small check only', suggestedCheckSize: '$100-$250' };
  return { label: 'High conviction, still risky', suggestedCheckSize: '$250-$500 max' };
}

function listOrNone(values: string[]): string {
  return values.length ? values.map((value) => `- ${value}`).join('\n') : '- None captured yet.';
}

export function generateDealMemoContent(deal: Deal): string {
  const finalScore = getMemoFinalScore(deal);
  const recommendation = getMemoRecommendation(finalScore);
  const confidence = getDataConfidenceScore(deal);
  const extractionItems = getAllDocumentExtractions(deal);
  const documentRisks = getAllDocumentRisks(deal);
  const suggestedFlags = getSuggestedRedFlags(deal);
  const checkedFlags = Object.entries(deal.redFlags)
    .filter(([, checked]) => checked)
    .map(([key]) => key);
  const evidenceSummary = deal.evidenceClaims.map(
    (claim) => `${claim.claim} (${claim.evidenceStrength}${claim.verified ? ', verified' : ''})`
  );
  const unansweredQuestions = generateFollowUpQuestions(deal);

  return `# ${deal.companyName || 'Deal'} Memo

## Company overview
${deal.shortDescription || 'No company overview captured yet.'}

Sector: ${deal.sector || 'Unknown'}
Platform: ${deal.platform || 'Unknown'}

## Offering terms
- Exemption: ${formatOfferingExemption(deal.offeringExemption)}
- Security: ${formatSecurityType(deal.securityType)}
- Minimum investment: ${formatCurrency(deal.minimumInvestment)}
- Valuation / cap: ${deal.valuationOrCap || 'Unknown'}
- Amount raised: ${formatCurrency(deal.amountRaised)}
- Fees: ${deal.platformFees || 'Unknown'}

## Why it could work
${deal.thesis || deal.quickScreen?.whyMightItWin || 'No thesis captured yet.'}

## Why it could fail
${deal.mainRisk || deal.deepDiligence?.riskNote || 'No main risk captured yet.'}

## Evidence summary
Data confidence: ${confidence}/100 (${getDataConfidenceLabel(confidence)})
${listOrNone(evidenceSummary)}

## Red flags
${listOrNone([...checkedFlags, ...suggestedFlags.map((flag) => `${flag.label} (suggested)`), ...documentRisks.map((risk) => `${risk.label} (document risk)`)])}

## Valuation notes
${deal.valuationOrCap || 'No valuation notes captured.'}

## Investor eligibility/access
${formatInvestorEligibility(deal.investorEligibility)}. Confirm access before sharing sensitive data or sending funds.

## Liquidity/exit path
${formatLiquidity(deal.liquidity)}. Lockup: ${deal.lockupPeriod || 'Unknown'}.

## Key unanswered questions
${listOrNone(unansweredQuestions)}

## Current recommendation
${recommendation.label} (${formatDealStatus(deal.decision)}), suggested check size ${recommendation.suggestedCheckSize}. This is not financial advice.

## Next review trigger
${deal.nextMilestone || deal.decisionNotes?.nextMilestoneNeeded || deal.review?.nextReviewDate || 'No review trigger captured.'}

## Document extraction notes
${listOrNone(extractionItems.slice(0, 12).map((item) => `${item.label}: ${item.value}`))}
`;
}

export function generateFollowUpQuestions(deal: Deal): string[] {
  const questions: string[] = [];
  const confidence = getDataConfidenceScore(deal);

  if (deal.revenueStatus === 'UNCLEAR' || !deal.evidenceClaims.some((claim) => /revenue/i.test(claim.claim))) {
    questions.push('What revenue is verified in the Form C or offering document?');
  }
  if (!deal.evidenceClaims.some((claim) => /customer|contract|arr|revenue/i.test(claim.claim))) {
    questions.push('Are there named customers, signed contracts, or other strong traction proof?');
  }
  if (deal.securityType === 'SAFE' || /safe/i.test(deal.valuationOrCap)) {
    questions.push('What rights does this SAFE have compared with any senior or preferred securities?');
  }
  if (deal.liquidity === 'UNKNOWN' || deal.liquidity === 'ILLIQUID') {
    questions.push('Are there transfer restrictions, lockups, or any realistic secondary path?');
  }
  if (!deal.platformFees || /unknown|unclear/i.test(deal.platformFees)) {
    questions.push('What fees will the investor actually pay, including platform or intermediary fees?');
  }
  if (deal.investorEligibility === 'UNCLEAR' || deal.offeringExemption === 'UNKNOWN') {
    questions.push('Is this Reg CF, Reg A, Reg D, or another structure, and can non-accredited investors participate?');
  }
  if (deal.redFlags.highValuation || !deal.valuationOrCap) {
    questions.push('What evidence supports the valuation or valuation cap?');
  }
  if (getAllDocumentRisks(deal).some((risk) => /related|conflict/i.test(risk.label))) {
    questions.push('Are related-party transactions or conflicts of interest disclosed clearly enough?');
  }
  if (confidence < 50) {
    questions.push('Which missing evidence would materially increase confidence in this deal record?');
  }

  return Array.from(new Set(questions));
}
