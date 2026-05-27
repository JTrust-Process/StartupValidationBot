export type DealDecision = 'PASS' | 'WATCH' | 'INVEST_SMALL';
export type DealStatus = DealDecision;
export type ThesisDirection = 'STRONGER' | 'WEAKER' | 'UNCHANGED';

export type InvestorEligibility =
  | 'NON_ACCREDITED'
  | 'ACCREDITED_ONLY'
  | 'UNCLEAR';

export type OfferingExemption =
  | 'REG_CF'
  | 'REG_A'
  | 'REG_D'
  | 'UNKNOWN'
  | 'OTHER';

export type SecurityType =
  | 'SAFE'
  | 'EQUITY'
  | 'NOTE'
  | 'REVENUE_SHARE'
  | 'FUND_INTEREST'
  | 'SPV'
  | 'OTHER'
  | 'UNKNOWN';

export type LiquidityProfile =
  | 'ILLIQUID'
  | 'REDEMPTION_WINDOW'
  | 'SECONDARY_POSSIBLE'
  | 'UNKNOWN';

export type RevenueStatus =
  | 'PRE_REVENUE'
  | 'EARLY_REVENUE'
  | 'REVENUE'
  | 'UNCLEAR';

export type ImportMode = 'CLEAN' | 'LAZY';
export type SuggestionConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type ImportSectionName =
  | 'CORE_TERMS'
  | 'COMPANY_DESCRIPTION'
  | 'TRACTION_CLAIMS'
  | 'FINANCIALS'
  | 'RISK_FACTORS'
  | 'FEES_USE_OF_PROCEEDS'
  | 'LEGAL_ELIGIBILITY'
  | 'NOISE_IGNORE';

export type EvidenceSourceType =
  | 'CAMPAIGN_PAGE'
  | 'FORM_C'
  | 'FORM_CA'
  | 'OFFERING_CIRCULAR'
  | 'SAFE_AGREEMENT'
  | 'SUBSCRIPTION_AGREEMENT'
  | 'INVESTOR_DECK'
  | 'FOUNDER_STATEMENT'
  | 'PRESS'
  | 'USER_NOTE'
  | 'OTHER';

export type DealDocumentType =
  | 'CAMPAIGN_PAGE'
  | 'FORM_C'
  | 'FORM_CA'
  | 'OFFERING_CIRCULAR'
  | 'SAFE_AGREEMENT'
  | 'SUBSCRIPTION_AGREEMENT'
  | 'INVESTOR_DECK'
  | 'PRESS'
  | 'USER_NOTE'
  | 'OTHER';

export type EvidenceStrength =
  | 'STRONG'
  | 'MEDIUM'
  | 'WEAK'
  | 'MISSING';

export type RedFlagKey =
  | 'eligibilityConcern'
  | 'missingOfferingDocuments'
  | 'vaguePreIpoLanguage'
  | 'unclearSecurityType'
  | 'highValuation'
  | 'unclearRevenueOrTraction'
  | 'weakCustomerEvidence'
  | 'guaranteedReturnClaims'
  | 'wirePressure'
  | 'unclearFees'
  | 'unclearIlliquidity';

export interface RedFlagDefinition {
  key: RedFlagKey;
  label: string;
}

export const RED_FLAG_DEFINITIONS: RedFlagDefinition[] = [
  {
    key: 'eligibilityConcern',
    label: 'Accredited-only or unclear eligibility'
  },
  {
    key: 'missingOfferingDocuments',
    label: 'No offering documents available'
  },
  {
    key: 'vaguePreIpoLanguage',
    label: 'Vague "pre-IPO" language'
  },
  {
    key: 'unclearSecurityType',
    label: 'No clear security type'
  },
  {
    key: 'highValuation',
    label: 'Crazy/high valuation'
  },
  {
    key: 'unclearRevenueOrTraction',
    label: 'No revenue or unclear traction'
  },
  {
    key: 'weakCustomerEvidence',
    label: 'No named customers or weak evidence'
  },
  {
    key: 'guaranteedReturnClaims',
    label: 'Guaranteed return claims'
  },
  {
    key: 'wirePressure',
    label: 'Pressure to wire money quickly'
  },
  {
    key: 'unclearFees',
    label: 'Hidden or unclear fees'
  },
  {
    key: 'unclearIlliquidity',
    label: 'Illiquidity not clearly disclosed'
  }
];

export type RedFlagMap = Record<RedFlagKey, boolean>;

export interface EvidenceClaim {
  id: number;
  claim: string;
  sourceType: EvidenceSourceType;
  sourceText: string;
  evidenceStrength: EvidenceStrength;
  verified: boolean;
  notes: string;
  createdAt: string;
  updatedAt: string;
}

export interface EvidenceClaimInput {
  claim: string;
  sourceType: EvidenceSourceType;
  sourceText: string;
  evidenceStrength: EvidenceStrength;
  verified: boolean;
  notes: string;
}

export interface DealDocument {
  id: number;
  title: string;
  documentType: DealDocumentType;
  sourceUrl: string;
  pastedText: string;
  createdAt: string;
  updatedAt: string;
}

export interface DealDocumentInput {
  title: string;
  documentType: DealDocumentType;
  sourceUrl: string;
  pastedText: string;
}

export interface DealMemo {
  content: string;
  generatedAt: string;
  updatedAt: string;
}

export interface ImportSection {
  sectionName: ImportSectionName;
  label: string;
  text: string;
  lineCount: number;
}

export interface ImportFieldSuggestion {
  id: string;
  fieldName: keyof DealInput;
  suggestedValue: string;
  confidence: SuggestionConfidence;
  sourceSnippet: string;
  accepted: boolean;
  ignored?: boolean;
}

export interface ImportRedFlagSuggestion {
  id: string;
  redFlagKey: RedFlagKey;
  label: string;
  confidence: SuggestionConfidence;
  sourceSnippet: string;
  accepted: boolean;
  ignored?: boolean;
}

export interface DealImportRecord {
  id: number;
  dealId: number;
  importMode: ImportMode;
  title: string;
  sourceUrl: string;
  rawText: string;
  cleanedText: string;
  sections: ImportSection[];
  fieldSuggestions: ImportFieldSuggestion[];
  suggestedRedFlags: ImportRedFlagSuggestion[];
  createdAt: string;
}

export interface DealImportRecordInput {
  importMode: ImportMode;
  title: string;
  sourceUrl: string;
  rawText: string;
  cleanedText: string;
  sections: ImportSection[];
  fieldSuggestions: ImportFieldSuggestion[];
  suggestedRedFlags: ImportRedFlagSuggestion[];
}

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

export interface DecisionNotes {
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
  id: number;
  companyName: string;
  platform: string;
  sector: string;
  offeringUrl: string;
  minimumInvestment?: number;
  valuationOrCap: string;
  amountRaised?: number;
  revenueStatus: RevenueStatus;
  investorEligibility: InvestorEligibility;
  offeringExemption: OfferingExemption;
  securityType: SecurityType;
  liquidity: LiquidityProfile;
  lockupPeriod: string;
  platformFees: string;
  thesis: string;
  mainRisk: string;
  nextMilestone: string;
  rawDealText: string;
  decision: DealDecision;
  status: DealStatus;
  shortDescription: string;
  quickScore: number;
  deepScore?: number | null;
  redFlags: RedFlagMap;
  ignoredSuggestedRedFlags: RedFlagKey[];
  evidenceClaims: EvidenceClaim[];
  documents: DealDocument[];
  importRecords: DealImportRecord[];
  ignoredDocumentRiskIds: string[];
  dealMemo?: DealMemo | null;
  createdAt: string;
  updatedAt: string;
  quickScreen?: QuickScreenData | null;
  decisionNotes?: DecisionNotes | null;
  deepDiligence?: DeepDiligenceData | null;
  review?: ReviewData | null;
}

export interface DealInput {
  companyName: string;
  platform: string;
  sector: string;
  offeringUrl: string;
  minimumInvestment?: number;
  valuationOrCap: string;
  amountRaised?: number;
  revenueStatus: RevenueStatus;
  investorEligibility: InvestorEligibility;
  offeringExemption: OfferingExemption;
  securityType: SecurityType;
  liquidity: LiquidityProfile;
  lockupPeriod: string;
  platformFees: string;
  thesis: string;
  mainRisk: string;
  nextMilestone: string;
  rawDealText?: string;
  importRecord?: DealImportRecordInput;
  initialRedFlags?: RedFlagKey[];
  decision: DealDecision;
  shortDescription: string;
}

export interface QuickScreenInput {
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

export interface DecisionInput extends DecisionNotes {
  decision: DealDecision;
}

export interface DeepDiligenceInput {
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

export interface DealExportPayload {
  version: 4;
  exportedAt: string;
  deals: Deal[];
}
