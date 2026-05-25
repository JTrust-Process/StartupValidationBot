import type {
  Deal,
  DealDocument,
  DealExportPayload,
  DealInput,
  EvidenceClaim,
  RedFlagMap
} from '../models/deal';
import { RED_FLAG_DEFINITIONS } from '../models/deal';

const STORAGE_KEY = 'startupDealOs.deals.v2';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function asNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  return undefined;
}

function asDealDecision(value: unknown): Deal['decision'] {
  if (value === 'PASS' || value === 'WATCH' || value === 'INVEST_SMALL') {
    return value;
  }

  return 'WATCH';
}

function asRevenueStatus(value: unknown): Deal['revenueStatus'] {
  if (
    value === 'PRE_REVENUE' ||
    value === 'EARLY_REVENUE' ||
    value === 'REVENUE' ||
    value === 'UNCLEAR'
  ) {
    return value;
  }

  return 'UNCLEAR';
}

function asInvestorEligibility(value: unknown): Deal['investorEligibility'] {
  if (
    value === 'NON_ACCREDITED' ||
    value === 'ACCREDITED_ONLY' ||
    value === 'UNCLEAR'
  ) {
    return value;
  }

  return 'UNCLEAR';
}

function asOfferingExemption(value: unknown): Deal['offeringExemption'] {
  if (
    value === 'REG_CF' ||
    value === 'REG_A' ||
    value === 'REG_D' ||
    value === 'UNKNOWN' ||
    value === 'OTHER'
  ) {
    return value;
  }

  return 'UNKNOWN';
}

function asSecurityType(value: unknown): Deal['securityType'] {
  if (
    value === 'SAFE' ||
    value === 'EQUITY' ||
    value === 'NOTE' ||
    value === 'REVENUE_SHARE' ||
    value === 'FUND_INTEREST' ||
    value === 'SPV' ||
    value === 'OTHER' ||
    value === 'UNKNOWN'
  ) {
    return value;
  }

  return 'UNKNOWN';
}

function asLiquidity(value: unknown): Deal['liquidity'] {
  if (
    value === 'ILLIQUID' ||
    value === 'REDEMPTION_WINDOW' ||
    value === 'SECONDARY_POSSIBLE' ||
    value === 'UNKNOWN'
  ) {
    return value;
  }

  return 'UNKNOWN';
}

function asEvidenceSourceType(value: unknown): EvidenceClaim['sourceType'] {
  if (
    value === 'CAMPAIGN_PAGE' ||
    value === 'FORM_C' ||
    value === 'FORM_CA' ||
    value === 'OFFERING_CIRCULAR' ||
    value === 'SAFE_AGREEMENT' ||
    value === 'SUBSCRIPTION_AGREEMENT' ||
    value === 'INVESTOR_DECK' ||
    value === 'FOUNDER_STATEMENT' ||
    value === 'PRESS' ||
    value === 'USER_NOTE' ||
    value === 'OTHER'
  ) {
    return value;
  }

  return 'USER_NOTE';
}

function asDealDocumentType(value: unknown): DealDocument['documentType'] {
  if (
    value === 'CAMPAIGN_PAGE' ||
    value === 'FORM_C' ||
    value === 'FORM_CA' ||
    value === 'OFFERING_CIRCULAR' ||
    value === 'SAFE_AGREEMENT' ||
    value === 'SUBSCRIPTION_AGREEMENT' ||
    value === 'INVESTOR_DECK' ||
    value === 'PRESS' ||
    value === 'USER_NOTE' ||
    value === 'OTHER'
  ) {
    return value;
  }

  return 'USER_NOTE';
}

function asEvidenceStrength(value: unknown): EvidenceClaim['evidenceStrength'] {
  if (
    value === 'STRONG' ||
    value === 'MEDIUM' ||
    value === 'WEAK' ||
    value === 'MISSING'
  ) {
    return value;
  }

  return 'MISSING';
}

export function createEmptyRedFlags(): RedFlagMap {
  return RED_FLAG_DEFINITIONS.reduce((flags, definition) => {
    flags[definition.key] = false;
    return flags;
  }, {} as RedFlagMap);
}

function normalizeRedFlags(value: unknown): RedFlagMap {
  const defaults = createEmptyRedFlags();
  if (!isRecord(value)) return defaults;

  return RED_FLAG_DEFINITIONS.reduce((flags, definition) => {
    flags[definition.key] = value[definition.key] === true;
    return flags;
  }, defaults);
}

function normalizeIgnoredRedFlags(value: unknown): Deal['ignoredSuggestedRedFlags'] {
  if (!Array.isArray(value)) return [];

  const allowedKeys = new Set(RED_FLAG_DEFINITIONS.map((definition) => definition.key));
  return value.filter((key): key is Deal['ignoredSuggestedRedFlags'][number] => {
    return typeof key === 'string' && allowedKeys.has(key as Deal['ignoredSuggestedRedFlags'][number]);
  });
}

function normalizeEvidenceClaims(value: unknown): EvidenceClaim[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((claimValue, index) => {
      if (!isRecord(claimValue)) return null;

      const now = new Date().toISOString();
      return {
        id: Math.trunc(asNumber(claimValue.id) ?? index + 1),
        claim: asString(claimValue.claim),
        sourceType: asEvidenceSourceType(claimValue.sourceType),
        sourceText: asString(claimValue.sourceText),
        evidenceStrength: asEvidenceStrength(claimValue.evidenceStrength),
        verified: claimValue.verified === true,
        notes: asString(claimValue.notes),
        createdAt: asString(claimValue.createdAt, now),
        updatedAt: asString(claimValue.updatedAt, now)
      };
    })
    .filter((claim): claim is EvidenceClaim => Boolean(claim));
}

function normalizeDocuments(value: unknown): DealDocument[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((documentValue, index) => {
      if (!isRecord(documentValue)) return null;

      const now = new Date().toISOString();
      return {
        id: Math.trunc(asNumber(documentValue.id) ?? index + 1),
        title: asString(documentValue.title, `Document ${index + 1}`),
        documentType: asDealDocumentType(documentValue.documentType),
        sourceUrl: asString(documentValue.sourceUrl),
        pastedText: asString(documentValue.pastedText),
        createdAt: asString(documentValue.createdAt, now),
        updatedAt: asString(documentValue.updatedAt, now)
      };
    })
    .filter((document): document is DealDocument => Boolean(document));
}

function normalizeStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string');
}

function normalizeDeal(value: unknown, fallbackId: number): Deal | null {
  if (!isRecord(value)) return null;

  const now = new Date().toISOString();
  const id = Math.trunc(asNumber(value.id) ?? fallbackId);
  const decision = asDealDecision(value.decision ?? value.status);
  const valuationOrCap =
    asString(value.valuationOrCap) ||
    (asNumber(value.valuation) ? String(asNumber(value.valuation)) : '');

  return {
    id,
    companyName: asString(value.companyName, 'Untitled Deal'),
    platform: asString(value.platform),
    sector: asString(value.sector),
    offeringUrl: asString(value.offeringUrl ?? value.website),
    minimumInvestment: asNumber(value.minimumInvestment),
    valuationOrCap,
    amountRaised: asNumber(value.amountRaised),
    revenueStatus: asRevenueStatus(value.revenueStatus),
    investorEligibility: asInvestorEligibility(value.investorEligibility),
    offeringExemption: asOfferingExemption(value.offeringExemption),
    securityType: asSecurityType(value.securityType ?? value.roundType),
    liquidity: asLiquidity(value.liquidity),
    lockupPeriod: asString(value.lockupPeriod),
    platformFees: asString(value.platformFees),
    thesis: asString(value.thesis),
    mainRisk: asString(value.mainRisk),
    nextMilestone: asString(value.nextMilestone),
    rawDealText: asString(value.rawDealText),
    decision,
    status: decision,
    shortDescription: asString(value.shortDescription),
    quickScore: Math.max(0, Math.min(10, Math.trunc(asNumber(value.quickScore) ?? 0))),
    deepScore: asNumber(value.deepScore) ?? null,
    redFlags: normalizeRedFlags(value.redFlags),
    ignoredSuggestedRedFlags: normalizeIgnoredRedFlags(value.ignoredSuggestedRedFlags),
    evidenceClaims: normalizeEvidenceClaims(value.evidenceClaims),
    documents: normalizeDocuments(value.documents),
    ignoredDocumentRiskIds: normalizeStringArray(value.ignoredDocumentRiskIds),
    dealMemo: isRecord(value.dealMemo)
      ? {
          content: asString(value.dealMemo.content),
          generatedAt: asString(value.dealMemo.generatedAt, now),
          updatedAt: asString(value.dealMemo.updatedAt, now)
        }
      : null,
    createdAt: asString(value.createdAt, now),
    updatedAt: asString(value.updatedAt, now),
    quickScreen: isRecord(value.quickScreen)
      ? {
          businessClarity: Math.trunc(asNumber(value.quickScreen.businessClarity) ?? 0),
          tractionEvidence: Math.trunc(asNumber(value.quickScreen.tractionEvidence) ?? 0),
          edge: Math.trunc(asNumber(value.quickScreen.edge) ?? 0),
          priceSanity: Math.trunc(asNumber(value.quickScreen.priceSanity) ?? 0),
          trustTransparency: Math.trunc(asNumber(value.quickScreen.trustTransparency) ?? 0),
          total: Math.trunc(asNumber(value.quickScreen.total) ?? 0),
          whatIsIt: asString(value.quickScreen.whatIsIt),
          whyMightItWin: asString(value.quickScreen.whyMightItWin),
          bestProofPoint: asString(value.quickScreen.bestProofPoint),
          biggestDoubt: asString(value.quickScreen.biggestDoubt),
          whySpendingTime: asString(value.quickScreen.whySpendingTime)
        }
      : null,
    decisionNotes: isRecord(value.decisionNotes)
      ? {
          rationale: asString(value.decisionNotes.rationale),
          whatWouldChangeMyMind: asString(value.decisionNotes.whatWouldChangeMyMind),
          nextMilestoneNeeded: asString(value.decisionNotes.nextMilestoneNeeded)
        }
      : isRecord(value.decision)
        ? {
            rationale: asString(value.decision.rationale),
            whatWouldChangeMyMind: asString(value.decision.whatWouldChangeMyMind),
            nextMilestoneNeeded: asString(value.decision.nextMilestoneNeeded)
          }
        : null,
    deepDiligence: isRecord(value.deepDiligence)
      ? {
          businessModelScore: Math.trunc(asNumber(value.deepDiligence.businessModelScore) ?? 3),
          businessModelNote: asString(value.deepDiligence.businessModelNote),
          marketCustomerScore: Math.trunc(asNumber(value.deepDiligence.marketCustomerScore) ?? 3),
          marketCustomerNote: asString(value.deepDiligence.marketCustomerNote),
          tractionQualityScore: Math.trunc(asNumber(value.deepDiligence.tractionQualityScore) ?? 3),
          tractionQualityNote: asString(value.deepDiligence.tractionQualityNote),
          competitiveEdgeScore: Math.trunc(asNumber(value.deepDiligence.competitiveEdgeScore) ?? 3),
          competitiveEdgeNote: asString(value.deepDiligence.competitiveEdgeNote),
          riskScore: Math.trunc(asNumber(value.deepDiligence.riskScore) ?? 3),
          riskNote: asString(value.deepDiligence.riskNote),
          total: Math.trunc(asNumber(value.deepDiligence.total) ?? 15)
        }
      : null,
    review: isRecord(value.review)
      ? {
          nextReviewDate: asString(value.review.nextReviewDate),
          reviewNote: asString(value.review.reviewNote),
          thesisDirection:
            value.review.thesisDirection === 'STRONGER' ||
            value.review.thesisDirection === 'WEAKER' ||
            value.review.thesisDirection === 'UNCHANGED'
              ? value.review.thesisDirection
              : 'UNCHANGED'
        }
      : null
  };
}

function getNextId(deals: Deal[]): number {
  return deals.reduce((maxId, deal) => Math.max(maxId, deal.id), 0) + 1;
}

function readPayload(): unknown {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return [];

  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return [];
  }
}

export function loadStoredDeals(): Deal[] {
  const payload = readPayload();
  const rawDeals = Array.isArray(payload)
    ? payload
    : isRecord(payload) && Array.isArray(payload.deals)
      ? payload.deals
      : [];

  return rawDeals
    .map((value, index) => normalizeDeal(value, index + 1))
    .filter((deal): deal is Deal => Boolean(deal))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export function saveStoredDeals(deals: Deal[]): void {
  const payload: DealExportPayload = {
    version: 4,
    exportedAt: new Date().toISOString(),
    deals
  };

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
}

export function createStoredDeal(input: DealInput): Deal {
  const deals = loadStoredDeals();
  const now = new Date().toISOString();
  const deal: Deal = {
    id: getNextId(deals),
    ...input,
    rawDealText: input.rawDealText ?? '',
    status: input.decision,
    quickScore: 0,
    deepScore: null,
    redFlags: createEmptyRedFlags(),
    ignoredSuggestedRedFlags: [],
    evidenceClaims: [],
    documents: [],
    ignoredDocumentRiskIds: [],
    dealMemo: null,
    createdAt: now,
    updatedAt: now,
    quickScreen: null,
    decisionNotes: null,
    deepDiligence: null,
    review: null
  };

  saveStoredDeals([deal, ...deals]);
  return deal;
}

export function updateStoredDeal(dealId: number, input: DealInput): Deal {
  const deals = loadStoredDeals();
  const existingDeal = deals.find((deal) => deal.id === dealId);

  if (!existingDeal) {
    throw new Error(`Deal ${dealId} not found`);
  }

  const updatedDeal: Deal = {
    ...existingDeal,
    ...input,
    rawDealText: input.rawDealText ?? existingDeal.rawDealText,
    status: input.decision,
    updatedAt: new Date().toISOString()
  };

  saveStoredDeals(deals.map((deal) => (deal.id === dealId ? updatedDeal : deal)));
  return updatedDeal;
}

export function replaceStoredDeal(updatedDeal: Deal): void {
  const deals = loadStoredDeals();
  saveStoredDeals(
    deals
      .map((deal) => (deal.id === updatedDeal.id ? updatedDeal : deal))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  );
}

export function deleteStoredDeal(dealId: number): void {
  saveStoredDeals(loadStoredDeals().filter((deal) => deal.id !== dealId));
}

export function exportStoredDeals(): DealExportPayload {
  return {
    version: 4,
    exportedAt: new Date().toISOString(),
    deals: loadStoredDeals()
  };
}

export function importStoredDeals(json: string): Deal[] {
  const payload = JSON.parse(json) as unknown;
  const rawDeals = Array.isArray(payload)
    ? payload
    : isRecord(payload) && Array.isArray(payload.deals)
      ? payload.deals
      : null;

  if (!rawDeals) {
    throw new Error('JSON must be an array of deals or an exported Startup Deal OS payload.');
  }

  const importedDeals = rawDeals
    .map((value, index) => normalizeDeal(value, index + 1))
    .filter((deal): deal is Deal => Boolean(deal))
    .map((deal, index) => ({
      ...deal,
      id: index + 1,
      updatedAt: deal.updatedAt || new Date().toISOString()
    }));

  saveStoredDeals(importedDeals);
  return importedDeals;
}
