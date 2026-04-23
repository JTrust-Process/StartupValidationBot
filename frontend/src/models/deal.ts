export type DealStatus = 'new' | 'watch' | 'pass' | 'invest-small';
export type ThesisDirection = 'stronger' | 'weaker' | 'unchanged';

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

export interface DeepDiligenceData {
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
  total: number;
}

export interface ReviewData {
  nextReviewDate: string;
  reviewNote: string;
  thesisDirection: ThesisDirection;
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
  deepDiligence?: DeepDiligenceData;
  review?: ReviewData;
}