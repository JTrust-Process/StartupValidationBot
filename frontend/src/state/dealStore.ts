import type { Deal } from '../models/deal';

const initialDeals: Deal[] = [
  {
    id: '1',
    companyName: 'Aegis Motion',
    sector: 'Defense Tech',
    platform: 'Wefunder',
    roundType: 'SAFE',
    shortDescription: 'Autonomous mobility systems for contested logistics.',
    quickScore: 8,
    deepScore: 34,
    status: 'watch',
    minimumInvestment: 100,
    valuation: 12000000,
    createdAt: new Date().toISOString()
  },
  {
    id: '2',
    companyName: 'Harbor Grid',
    sector: 'Energy',
    platform: 'Republic',
    roundType: 'Equity',
    shortDescription: 'Grid resilience software for industrial energy operators.',
    quickScore: 6,
    deepScore: 24,
    status: 'pass',
    minimumInvestment: 50,
    valuation: 8000000,
    createdAt: new Date().toISOString()
  },
  {
    id: '3',
    companyName: 'Sentinel Forge',
    sector: 'Dual Use',
    platform: 'StartEngine',
    roundType: 'SAFE',
    shortDescription: 'Manufacturing intelligence platform for defense suppliers.',
    quickScore: 9,
    deepScore: 39,
    status: 'invest-small',
    minimumInvestment: 250,
    valuation: 15000000,
    createdAt: new Date().toISOString()
  }
];

let deals: Deal[] = [...initialDeals];

export function getDealsState(): Deal[] {
  return deals;
}

export function getDealStateById(id: string): Deal | undefined {
  return deals.find((deal) => deal.id === id);
}

export function addDealState(
  dealInput: Omit<Deal, 'id' | 'createdAt'>
): Deal {
  const newDeal: Deal = {
    ...dealInput,
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString()
  };

  deals = [newDeal, ...deals];
  return newDeal;
}