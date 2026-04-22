export type DealStatus = 'new' | 'watch' | 'pass' | 'invest-small';

export interface QuickScreenData {
  businessClarity: number;
  tractionEvidence: number;
  edge: number;
  priceSanity: number;
  trustTransparency: number;
  total: number;
  whatIsIt: string;
  whyMightItWin: string;
  bestProofPoint: string;
  biggestDoubt: string;
  whySpendingTime: string;
}

export interface DecisionData {
  status: Exclude<DealStatus, 'new'>;
  rationale: string;
  whatWouldChangeMyMind: string;
  nextMilestoneNeeded: string;
}

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
  quickScreen?: QuickScreenData;
  decision?: DecisionData;
}