import type { DealResponse } from '../models/api';

let dealsCache: DealResponse[] = [];

export function getDealsCache(): DealResponse[] {
  return dealsCache;
}

export function setDealsCache(deals: DealResponse[]): void {
  dealsCache = deals;
}

export function getDealFromCache(id: number): DealResponse | undefined {
  return dealsCache.find((deal) => deal.id === id);
}

export function upsertDealInCache(updatedDeal: DealResponse): void {
  const existingIndex = dealsCache.findIndex((deal) => deal.id === updatedDeal.id);

  if (existingIndex === -1) {
    dealsCache = [updatedDeal, ...dealsCache];
    return;
  }

  dealsCache = dealsCache.map((deal) =>
    deal.id === updatedDeal.id ? updatedDeal : deal
  );
}