import type { Deal } from '../models/deal';
import { addDealState, getDealsState, getDealStateById } from '../state/dealStore';

export interface CreateDealInput {
  companyName: string;
  sector: string;
  platform: string;
  roundType: string;
  shortDescription: string;
  valuation?: number;
  minimumInvestment?: number;
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