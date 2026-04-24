import type {
  CreateDealRequest,
  DealResponse,
  DecisionRequest,
  DeepDiligenceRequest,
  QuickScreenRequest,
  ReviewRequest
} from '../models/api';
import {
  createDeal as createDealApi,
  deleteDeal as deleteDealApi,
  getDealById as getDealByIdApi,
  getDeals as getDealsApi,
  saveDecision as saveDecisionApi,
  saveDeepDiligence as saveDeepDiligenceApi,
  saveQuickScreen as saveQuickScreenApi,
  saveReview as saveReviewApi,
  updateDeal as updateDealApi
} from './api';
import {
  getDealFromCache,
  getDealsCache,
  removeDealFromCache,
  setDealsCache,
  upsertDealInCache
} from '../state/apiDealStore';

export async function loadDeals(): Promise<DealResponse[]> {
  const deals = await getDealsApi();
  setDealsCache(deals);
  return deals;
}

export async function loadDealById(id: number): Promise<DealResponse> {
  const deal = await getDealByIdApi(id);
  upsertDealInCache(deal);
  return deal;
}

export function getDeals(): DealResponse[] {
  return getDealsCache();
}

export function getDealById(id: number): DealResponse | undefined {
  return getDealFromCache(id);
}

export async function createDeal(input: CreateDealRequest): Promise<DealResponse> {
  const deal = await createDealApi(input);
  upsertDealInCache(deal);
  return deal;
}

export async function updateDeal(
  dealId: number,
  input: CreateDealRequest
): Promise<DealResponse> {
  const deal = await updateDealApi(dealId, input);
  upsertDealInCache(deal);
  return deal;
}

export async function deleteDeal(dealId: number): Promise<void> {
  await deleteDealApi(dealId);
  removeDealFromCache(dealId);
}

export async function saveQuickScreen(
  dealId: number,
  input: QuickScreenRequest
): Promise<DealResponse> {
  const deal = await saveQuickScreenApi(dealId, input);
  upsertDealInCache(deal);
  return deal;
}

export async function saveDecision(
  dealId: number,
  input: DecisionRequest
): Promise<DealResponse> {
  const deal = await saveDecisionApi(dealId, input);
  upsertDealInCache(deal);
  return deal;
}

export async function saveDeepDiligence(
  dealId: number,
  input: DeepDiligenceRequest
): Promise<DealResponse> {
  const deal = await saveDeepDiligenceApi(dealId, input);
  upsertDealInCache(deal);
  return deal;
}

export async function saveReview(
  dealId: number,
  input: ReviewRequest
): Promise<DealResponse> {
  const deal = await saveReviewApi(dealId, input);
  upsertDealInCache(deal);
  return deal;
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