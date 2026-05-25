import type { Deal, RedFlagKey } from '../models/deal';
import { RED_FLAG_DEFINITIONS } from '../models/deal';

export interface SuggestedRedFlag {
  key: RedFlagKey;
  label: string;
  matchedKeywords: string[];
  reason: string;
}

interface RiskRule {
  key: RedFlagKey;
  keywords: string[];
  reason: string;
}

const RISK_RULES: RiskRule[] = [
  {
    key: 'eligibilityConcern',
    keywords: ['accredited-only', 'accredited only', 'reg d', '506(c)', '506 c'],
    reason: 'The text may indicate accredited-only access or a Reg D structure.'
  },
  {
    key: 'missingOfferingDocuments',
    keywords: ['no offering documents', 'offering documents unavailable', 'documents missing'],
    reason: 'The text suggests offering documents may be missing or unavailable.'
  },
  {
    key: 'vaguePreIpoLanguage',
    keywords: ['pre-ipo', 'pre ipo'],
    reason: 'The text uses pre-IPO language that should be checked against the actual security.'
  },
  {
    key: 'unclearSecurityType',
    keywords: ['unknown security', 'unclear security', 'senior to safe', 'reservation'],
    reason: 'The text may indicate unclear rights, reservation language, or security seniority concerns.'
  },
  {
    key: 'highValuation',
    keywords: ['high valuation', 'crazy valuation', 'valuation risk', 'overvalued'],
    reason: 'The text points to valuation risk.'
  },
  {
    key: 'unclearRevenueOrTraction',
    keywords: ['no revenue', 'unclear revenue', 'unclear traction', 'projected', 'going concern', 'losses'],
    reason: 'The text may indicate weak, projected, or unclear traction.'
  },
  {
    key: 'weakCustomerEvidence',
    keywords: ['no named customers', 'weak evidence', 'considering hosting', 'pilot only'],
    reason: 'The text may lack strong customer evidence.'
  },
  {
    key: 'guaranteedReturnClaims',
    keywords: ['guaranteed', 'guaranteed return', 'risk-free'],
    reason: 'Guaranteed return language is a major diligence concern.'
  },
  {
    key: 'wirePressure',
    keywords: ['wire quickly', 'wire money quickly', 'limited time wire', 'send funds immediately'],
    reason: 'The text may pressure the user to send funds quickly.'
  },
  {
    key: 'unclearFees',
    keywords: ['hidden fees', 'unclear fees', 'unknown fees', 'fees unknown'],
    reason: 'The text may indicate fees are hidden or unclear.'
  },
  {
    key: 'unclearIlliquidity',
    keywords: ['illiquid', 'no secondary market', 'lock-up', 'lockup', 'redemption unavailable'],
    reason: 'The text points to illiquidity or lockup risk.'
  }
];

const GENERAL_RISK_KEYWORDS = ['unclear', 'unknown'];

function normalizeText(value: string): string {
  return value.toLowerCase().replace(/\s+/g, ' ');
}

function getDealRiskText(deal: Deal): string {
  const evidenceText = deal.evidenceClaims
    .map((claim) => [claim.claim, claim.sourceText, claim.notes].join(' '))
    .join(' ');
  const documentText = deal.documents
    .map((document) => [document.title, document.pastedText].join(' '))
    .join(' ');

  return [
    deal.rawDealText,
    deal.shortDescription,
    deal.thesis,
    deal.mainRisk,
    deal.nextMilestone,
    deal.platformFees,
    deal.quickScreen?.whatIsIt,
    deal.quickScreen?.whyMightItWin,
    deal.quickScreen?.bestProofPoint,
    deal.quickScreen?.biggestDoubt,
    deal.quickScreen?.whySpendingTime,
    deal.deepDiligence?.businessModelNote,
    deal.deepDiligence?.marketCustomerNote,
    deal.deepDiligence?.tractionQualityNote,
    deal.deepDiligence?.competitiveEdgeNote,
    deal.deepDiligence?.riskNote,
    deal.review?.reviewNote,
    evidenceText,
    documentText
  ]
    .filter(Boolean)
    .join(' ');
}

function definitionLabelFor(key: RedFlagKey): string {
  return RED_FLAG_DEFINITIONS.find((definition) => definition.key === key)?.label ?? key;
}

export function getDetectedRiskKeywords(deal: Deal): string[] {
  const normalizedText = normalizeText(getDealRiskText(deal));
  const keywords = new Set<string>();

  RISK_RULES.forEach((rule) => {
    rule.keywords.forEach((keyword) => {
      if (normalizedText.includes(keyword)) {
        keywords.add(keyword);
      }
    });
  });

  GENERAL_RISK_KEYWORDS.forEach((keyword) => {
    if (normalizedText.includes(keyword)) {
      keywords.add(keyword);
    }
  });

  return Array.from(keywords).sort((a, b) => a.localeCompare(b));
}

export function getSuggestedRedFlags(deal: Deal): SuggestedRedFlag[] {
  const normalizedText = normalizeText(getDealRiskText(deal));
  const ignored = new Set(deal.ignoredSuggestedRedFlags);

  const suggestions = RISK_RULES.map((rule) => {
    const matchedKeywords = rule.keywords.filter((keyword) => normalizedText.includes(keyword));

    if (!matchedKeywords.length || deal.redFlags[rule.key] || ignored.has(rule.key)) {
      return null;
    }

    return {
      key: rule.key,
      label: definitionLabelFor(rule.key),
      matchedKeywords,
      reason: rule.reason
    };
  }).filter((suggestion): suggestion is SuggestedRedFlag => Boolean(suggestion));

  if (
    !deal.redFlags.eligibilityConcern &&
    !ignored.has('eligibilityConcern') &&
    (deal.investorEligibility === 'UNCLEAR' || deal.investorEligibility === 'ACCREDITED_ONLY')
  ) {
    suggestions.push({
      key: 'eligibilityConcern',
      label: definitionLabelFor('eligibilityConcern'),
      matchedKeywords: [deal.investorEligibility === 'UNCLEAR' ? 'unclear eligibility' : 'accredited-only'],
      reason: 'Investor eligibility should be confirmed before relying on this snapshot.'
    });
  }

  if (
    !deal.redFlags.unclearSecurityType &&
    !ignored.has('unclearSecurityType') &&
    deal.securityType === 'UNKNOWN'
  ) {
    suggestions.push({
      key: 'unclearSecurityType',
      label: definitionLabelFor('unclearSecurityType'),
      matchedKeywords: ['unknown security type'],
      reason: 'The security type is unknown.'
    });
  }

  if (
    !deal.redFlags.unclearFees &&
    !ignored.has('unclearFees') &&
    (!deal.platformFees || /unknown|unclear/i.test(deal.platformFees))
  ) {
    suggestions.push({
      key: 'unclearFees',
      label: definitionLabelFor('unclearFees'),
      matchedKeywords: ['fees unclear'],
      reason: 'Platform or transaction fees are not clear.'
    });
  }

  return suggestions.filter(
    (suggestion, index, allSuggestions) =>
      allSuggestions.findIndex((item) => item.key === suggestion.key) === index
  );
}

export function hasUnscoredRiskLanguage(deal: Deal): boolean {
  return getDetectedRiskKeywords(deal).length > 0 && Object.values(deal.redFlags).every((value) => !value);
}

export function getDataConfidenceScore(deal: Deal): number {
  let score = deal.evidenceClaims.length ? 45 : 20;
  if (deal.documents.length) score += Math.min(20, deal.documents.length * 8);

  deal.evidenceClaims.forEach((claim) => {
    if (claim.evidenceStrength === 'STRONG') score += 16;
    if (claim.evidenceStrength === 'MEDIUM') score += 9;
    if (claim.evidenceStrength === 'WEAK') score += 2;
    if (claim.evidenceStrength === 'MISSING') score -= 12;
    if (claim.verified) score += 5;
  });

  if (deal.investorEligibility === 'UNCLEAR') score -= 15;
  if (deal.investorEligibility === 'ACCREDITED_ONLY') score -= 8;
  if (deal.offeringExemption === 'UNKNOWN') score -= 15;
  if (deal.securityType === 'UNKNOWN') score -= 12;
  if (deal.revenueStatus === 'UNCLEAR') score -= 10;
  if (!deal.platformFees || /unknown|unclear/i.test(deal.platformFees)) score -= 10;
  if (deal.redFlags.missingOfferingDocuments) score -= 15;
  if (deal.redFlags.unclearSecurityType) score -= 8;
  if (deal.redFlags.unclearFees) score -= 8;

  return Math.max(0, Math.min(100, Math.round(score)));
}

export function getDataConfidenceLabel(score: number): string {
  if (score < 40) return 'Low confidence';
  if (score < 70) return 'Medium confidence';
  return 'High confidence';
}
