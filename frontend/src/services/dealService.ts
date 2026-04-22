import type { Deal } from '../models/deal';

const mockDeals: Deal[] = [
  {
    id: '1',
    companyName: 'Aegis Motion',
    sector: 'Defense Tech',
    platform: 'Wefunder',
    roundType: 'SAFE',
    quickScore: 8,
    deepScore: 34,
    status: 'watch',
    minimumInvestment: 100,
    valuation: 12000000
  },
  {
    id: '2',
    companyName: 'Harbor Grid',
    sector: 'Energy',
    platform: 'Republic',
    roundType: 'Equity',
    quickScore: 6,
    deepScore: 24,
    status: 'pass',
    minimumInvestment: 50,
    valuation: 8000000
  },
  {
    id: '3',
    companyName: 'Sentinel Forge',
    sector: 'Dual Use',
    platform: 'StartEngine',
    roundType: 'SAFE',
    quickScore: 9,
    deepScore: 39,
    status: 'invest-small',
    minimumInvestment: 250,
    valuation: 15000000
  }
];

export function getDeals(): Deal[] {
  return mockDeals;
}

export function getDealById(id: string): Deal | undefined {
  return mockDeals.find((deal) => deal.id === id);
}