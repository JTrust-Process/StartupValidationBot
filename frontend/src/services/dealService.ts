import type {
  Deal,
  DecisionData,
  DeepDiligenceData,
  QuickScreenData,
  ReviewData,
  ThesisDirection
} from '../models/deal';
import {
  addDealState,
  getDealsState,
  getDealStateById,
  resetDealsState,
  updateDealDecisionState,
  updateDealDeepDiligenceState,
  updateDealQuickScreenState,
  updateDealReviewState
} from '../state/dealStore';

export interface CreateDealInput {
  companyName: string;
  sector: string;
  platform: string;
  roundType: string;
  shortDescription: string;
  valuation?: number;
  minimumInvestment?: number;
}

export interface SaveQuickScreenInput {
  dealId: string;
  businessClarity: number;
  tractionEvidence: number;
  edge: number;
  priceSanity: number;
  trustTransparency: number;
  whatIsIt: string;
  whyMightItWin: string;
  bestProofPoint: string;
  biggestDoubt: string;
  whySpendingTime: string;
}

export interface SaveDecisionInput {
  dealId: string;
  status: Exclude<Deal['status'], 'new'>;
  rationale: string;
  whatWouldChangeMyMind: string;
  nextMilestoneNeeded: string;
}

export interface SaveDeepDiligenceInput {
  dealId: string;
  businessModelScore: number;
  businessModelNote: string;
  marketCustomerScore: number;
  marketCustomerNote: string;
  tractionQualityScore: number;
  tractionQualityNote: string;
  competitiveEdgeScore: number;
  competitiveEdgeNote: string;
  riskScore: number;
  riskNote: string;
}

export interface SaveReviewInput {
  dealId: string;
  nextReviewDate: string;
  reviewNote: string;
  thesisDirection: ThesisDirection;
}

export function getDeals(): Deal[] {
  return getDealsState();
}

export function getDealById(id: string): Deal | undefined {
  return getDealStateById(id);
}

export function createDeal(input: CreateDealInput): Deal {
  return addDealState({
    companyName: input.companyName,
    sector: input.sector,
    platform: input.platform,
    roundType: input.roundType,
    shortDescription: input.shortDescription,
    valuation: input.valuation,
    minimumInvestment: input.minimumInvestment,
    quickScore: 0,
    deepScore: undefined,
    status: 'new'
  });
}

export function getQuickScreenOutcome(total: number): string {
  if (total <= 3) return 'Pass';
  if (total <= 6) return 'Reps Only';
  if (total <= 8) return 'Dig Deeper';
  return 'High Interest';
}

export function getDeepDiligenceOutcome(total: number): string {
  if (total <= 9) return 'Weak';
  if (total <= 15) return 'Mixed';
  if (total <= 20) return 'Interesting';
  return 'Strong';
}

export function saveQuickScreen(input: SaveQuickScreenInput): Deal | undefined {
  const total =
    input.businessClarity +
    input.tractionEvidence +
    input.edge +
    input.priceSanity +
    input.trustTransparency;

  const quickScreen: QuickScreenData = {
    businessClarity: input.businessClarity,
    tractionEvidence: input.tractionEvidence,
    edge: input.edge,
    priceSanity: input.priceSanity,
    trustTransparency: input.trustTransparency,
    total,
    whatIsIt: input.whatIsIt,
    whyMightItWin: input.whyMightItWin,
    bestProofPoint: input.bestProofPoint,
    biggestDoubt: input.biggestDoubt,
    whySpendingTime: input.whySpendingTime
  };

  return updateDealQuickScreenState(input.dealId, quickScreen);
}

export function saveDecision(input: SaveDecisionInput): Deal | undefined {
  const decision: DecisionData = {
    status: input.status,
    rationale: input.rationale,
    whatWouldChangeMyMind: input.whatWouldChangeMyMind,
    nextMilestoneNeeded: input.nextMilestoneNeeded
  };

  return updateDealDecisionState(input.dealId, decision);
}

export function saveDeepDiligence(
  input: SaveDeepDiligenceInput
): Deal | undefined {
  const total =
    input.businessModelScore +
    input.marketCustomerScore +
    input.tractionQualityScore +
    input.competitiveEdgeScore +
    input.riskScore;

  const deepDiligence: DeepDiligenceData = {
    businessModelScore: input.businessModelScore,
    businessModelNote: input.businessModelNote,
    marketCustomerScore: input.marketCustomerScore,
    marketCustomerNote: input.marketCustomerNote,
    tractionQualityScore: input.tractionQualityScore,
    tractionQualityNote: input.tractionQualityNote,
    competitiveEdgeScore: input.competitiveEdgeScore,
    competitiveEdgeNote: input.competitiveEdgeNote,
    riskScore: input.riskScore,
    riskNote: input.riskNote,
    total
  };

  return updateDealDeepDiligenceState(input.dealId, deepDiligence);
}

export function saveReview(input: SaveReviewInput): Deal | undefined {
  const review: ReviewData = {
    nextReviewDate: input.nextReviewDate,
    reviewNote: input.reviewNote,
    thesisDirection: input.thesisDirection
  };

  return updateDealReviewState(input.dealId, review);
}

export function resetDeals(): void {
  resetDealsState();
}