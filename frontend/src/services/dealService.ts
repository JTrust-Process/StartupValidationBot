import type {
  Deal,
  DealDecision,
  DealInput,
  DecisionInput,
  DeepDiligenceInput,
  EvidenceClaimInput,
  QuickScreenInput,
  RedFlagKey,
  RedFlagMap,
  ReviewData
} from '../models/deal';
import {
  createStoredDeal,
  deleteStoredDeal,
  exportStoredDeals,
  importStoredDeals,
  loadStoredDeals,
  replaceStoredDeal,
  updateStoredDeal
} from '../storage/dealStorage';

let dealsCache: Deal[] = [];

function setCache(deals: Deal[]): void {
  dealsCache = deals.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export async function loadDeals(): Promise<Deal[]> {
  const deals = loadStoredDeals();
  setCache(deals);
  return deals;
}

export async function loadDealById(id: number): Promise<Deal> {
  const deal = loadStoredDeals().find((storedDeal) => storedDeal.id === id);
  if (!deal) throw new Error(`Deal ${id} not found`);

  upsertDealInCache(deal);
  return deal;
}

export function getDeals(): Deal[] {
  return dealsCache;
}

export function getDealById(id: number): Deal | undefined {
  return dealsCache.find((deal) => deal.id === id);
}

function upsertDealInCache(updatedDeal: Deal): void {
  const exists = dealsCache.some((deal) => deal.id === updatedDeal.id);
  setCache(
    exists
      ? dealsCache.map((deal) => (deal.id === updatedDeal.id ? updatedDeal : deal))
      : [updatedDeal, ...dealsCache]
  );
}

export async function createDeal(input: DealInput): Promise<Deal> {
  const deal = createStoredDeal(input);
  upsertDealInCache(deal);
  return deal;
}

export async function updateDeal(dealId: number, input: DealInput): Promise<Deal> {
  const deal = updateStoredDeal(dealId, input);
  upsertDealInCache(deal);
  return deal;
}

export async function deleteDeal(dealId: number): Promise<void> {
  deleteStoredDeal(dealId);
  setCache(dealsCache.filter((deal) => deal.id !== dealId));
}

export async function saveQuickScreen(
  dealId: number,
  input: QuickScreenInput
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const total =
    input.businessClarity +
    input.tractionEvidence +
    input.edge +
    input.priceSanity +
    input.trustTransparency;

  const updatedDeal: Deal = {
    ...deal,
    quickScore: total,
    quickScreen: {
      ...input,
      total
    },
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function saveDecision(
  dealId: number,
  input: DecisionInput
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const updatedDeal: Deal = {
    ...deal,
    decision: input.decision,
    status: input.decision,
    decisionNotes: {
      rationale: input.rationale,
      whatWouldChangeMyMind: input.whatWouldChangeMyMind,
      nextMilestoneNeeded: input.nextMilestoneNeeded
    },
    nextMilestone: input.nextMilestoneNeeded || deal.nextMilestone,
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function saveDeepDiligence(
  dealId: number,
  input: DeepDiligenceInput
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const total =
    input.businessModelScore +
    input.marketCustomerScore +
    input.tractionQualityScore +
    input.competitiveEdgeScore +
    input.riskScore;

  const updatedDeal: Deal = {
    ...deal,
    deepScore: total,
    deepDiligence: {
      ...input,
      total
    },
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function saveReview(
  dealId: number,
  input: ReviewData
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const updatedDeal: Deal = {
    ...deal,
    review: input,
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function saveRedFlags(
  dealId: number,
  redFlags: RedFlagMap
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const updatedDeal: Deal = {
    ...deal,
    redFlags,
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function acceptSuggestedRedFlags(
  dealId: number,
  redFlagKeys: RedFlagKey[]
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const uniqueKeys = Array.from(new Set(redFlagKeys));
  const updatedDeal: Deal = {
    ...deal,
    redFlags: uniqueKeys.reduce(
      (flags, key) => ({
        ...flags,
        [key]: true
      }),
      deal.redFlags
    ),
    ignoredSuggestedRedFlags: deal.ignoredSuggestedRedFlags.filter(
      (key) => !uniqueKeys.includes(key)
    ),
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function ignoreSuggestedRedFlags(
  dealId: number,
  redFlagKeys: RedFlagKey[]
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const ignoredSuggestedRedFlags = Array.from(
    new Set([...deal.ignoredSuggestedRedFlags, ...redFlagKeys])
  );
  const updatedDeal: Deal = {
    ...deal,
    ignoredSuggestedRedFlags,
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function saveEvidenceClaim(
  dealId: number,
  input: EvidenceClaimInput
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const now = new Date().toISOString();
  const nextId =
    deal.evidenceClaims.reduce((maxId, claim) => Math.max(maxId, claim.id), 0) + 1;
  const updatedDeal: Deal = {
    ...deal,
    evidenceClaims: [
      {
        id: nextId,
        ...input,
        createdAt: now,
        updatedAt: now
      },
      ...deal.evidenceClaims
    ],
    updatedAt: now
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

export async function deleteEvidenceClaim(
  dealId: number,
  claimId: number
): Promise<Deal> {
  const deal = getRequiredDeal(dealId);
  const updatedDeal: Deal = {
    ...deal,
    evidenceClaims: deal.evidenceClaims.filter((claim) => claim.id !== claimId),
    updatedAt: new Date().toISOString()
  };

  replaceStoredDeal(updatedDeal);
  upsertDealInCache(updatedDeal);
  return updatedDeal;
}

function getRequiredDeal(dealId: number): Deal {
  const deal = getDealById(dealId);
  if (!deal) throw new Error(`Deal ${dealId} not found`);
  return deal;
}

export function exportDealsAsJson(): string {
  return JSON.stringify(exportStoredDeals(), null, 2);
}

export async function importDealsFromJson(json: string): Promise<Deal[]> {
  const deals = importStoredDeals(json);
  setCache(deals);
  return deals;
}

export function getQuickScreenOutcome(total: number): string {
  if (total <= 3) return 'Weak first pass';
  if (total <= 6) return 'Needs more proof';
  if (total <= 8) return 'Worth deeper review';
  return 'High interest';
}

export function getDeepDiligenceOutcome(total: number): string {
  if (total <= 9) return 'Weak';
  if (total <= 15) return 'Mixed';
  if (total <= 20) return 'Interesting';
  return 'Strong';
}

export function getFinalScore(deal: Deal): number {
  const quickScore = Math.max(0, Math.min(100, deal.quickScore * 10));

  if (typeof deal.deepScore !== 'number') {
    return quickScore;
  }

  const deepScore = Math.max(0, Math.min(100, Math.round((deal.deepScore / 25) * 100)));
  return Math.round(quickScore * 0.45 + deepScore * 0.55);
}

export function getFinalRecommendation(score: number): {
  label: string;
  decision: DealDecision;
  suggestedCheckSize: string;
} {
  if (score <= 49) {
    return {
      label: 'Pass',
      decision: 'PASS',
      suggestedCheckSize: '$0'
    };
  }

  if (score <= 69) {
    return {
      label: 'Watch',
      decision: 'WATCH',
      suggestedCheckSize: '$0'
    };
  }

  if (score <= 84) {
    return {
      label: 'Small check only',
      decision: 'INVEST_SMALL',
      suggestedCheckSize: '$100-$250'
    };
  }

  return {
    label: 'High conviction, still risky',
    decision: 'INVEST_SMALL',
    suggestedCheckSize: '$250-$500 max'
  };
}

export function getRedFlagCount(deal: Deal): number {
  return Object.values(deal.redFlags).filter(Boolean).length;
}

export function getRiskStatus(deal: Deal): {
  label: string;
  tone: 'green' | 'yellow' | 'red';
} {
  const redFlagCount = getRedFlagCount(deal);

  if (redFlagCount >= 4) {
    return {
      label: 'Red risk',
      tone: 'red'
    };
  }

  if (redFlagCount >= 1) {
    return {
      label: 'Yellow risk',
      tone: 'yellow'
    };
  }

  return {
    label: 'Green risk',
    tone: 'green'
  };
}
