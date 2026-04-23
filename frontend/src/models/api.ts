export type DealStatus = 'NEW' | 'WATCH' | 'PASS' | 'INVEST_SMALL';
export type ThesisDirection = 'STRONGER' | 'WEAKER' | 'UNCHANGED';

export interface QuickScreenResponse {
  id: number;
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

export interface DecisionResponse {
  id: number;
  status: DealStatus;
  rationale: string;
  whatWouldChangeMyMind: string;
  nextMilestoneNeeded: string;
}

export interface DeepDiligenceResponse {
  id: number;
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

export interface ReviewResponse {
  id: number;
  nextReviewDate: string;
  reviewNote: string;
  thesisDirection: ThesisDirection;
}

export interface DealResponse {
  id: number;
  companyName: string;
  sector: string;
  platform: string;
  roundType: string;
  shortDescription: string;
  valuation?: number;
  minimumInvestment?: number;
  status: DealStatus;
  quickScore: number;
  deepScore?: number | null;
  createdAt: string;
  updatedAt: string;
  quickScreen?: QuickScreenResponse | null;
  decision?: DecisionResponse | null;
  deepDiligence?: DeepDiligenceResponse | null;
  review?: ReviewResponse | null;
}

export interface CreateDealRequest {
  companyName: string;
  sector: string;
  platform: string;
  roundType: string;
  shortDescription: string;
  valuation?: number;
  minimumInvestment?: number;
}

export interface QuickScreenRequest {
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

export interface DecisionRequest {
  status: Exclude<DealStatus, 'NEW'>;
  rationale: string;
  whatWouldChangeMyMind: string;
  nextMilestoneNeeded: string;
}

export interface DeepDiligenceRequest {
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

export interface ReviewRequest {
  nextReviewDate: string;
  reviewNote: string;
  thesisDirection: ThesisDirection;
}