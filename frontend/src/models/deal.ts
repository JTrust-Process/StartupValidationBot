export type DealStatus = 'new' | 'watch' | 'pass' | 'invest-small';

export interface Deal {
  id: string;
  companyName: string;
  sector: string;
  platform: string;
  roundType: string;
  shortDescription: string;
  quickScore: number;
  deepScore?: number;
  status: DealStatus;
  minimumInvestment?: number;
  valuation?: number;
  createdAt: string;
}