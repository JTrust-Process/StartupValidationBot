import type {
  EvidenceClaimInput,
  ImportFieldSuggestion,
  ImportRedFlagSuggestion,
  ImportSection,
  InvestorEligibility,
  OfferingExemption,
  SecurityType
} from './deal';

export type WatchlistSourceType =
  | 'REPUBLIC'
  | 'WEFUNDER'
  | 'STARTENGINE'
  | 'DEALMAKER'
  | 'FUNDRISE'
  | 'JARSY'
  | 'ROSS_PRE_IPO'
  | 'SEC_EDGAR'
  | 'MANUAL'
  | 'OTHER';

export type WatchlistSourceStatus =
  | 'NEVER_CHECKED'
  | 'OK'
  | 'ERROR'
  | 'SKIPPED'
  | 'NEEDS_MANUAL_PASTE';

export type DigestDay =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface WatchlistSource {
  id: number;
  sourceType: WatchlistSourceType;
  dealId?: number;
  url: string;
  companyName: string;
  enabled: boolean;
  lastCheckedAt: string;
  lastStatus: WatchlistSourceStatus;
  notes: string;
  pastedText: string;
  lastError: string;
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistSourceInput {
  sourceType: WatchlistSourceType;
  dealId?: number;
  url: string;
  companyName: string;
  enabled: boolean;
  notes: string;
  pastedText: string;
}

export interface DealSnapshot {
  id: number;
  dealId: number;
  sourceId: number;
  checkedAt: string;
  amountRaised?: number;
  investorCount?: number;
  deadline: string;
  minimumInvestment?: number;
  valuationOrCap: string;
  securityType: SecurityType;
  offeringExemption: OfferingExemption;
  investorEligibility: InvestorEligibility;
  dataConfidence: number;
  redFlagCount: number;
  rawTextHash: string;
  notableChanges: string[];
  createdAt: string;
}

export interface ScoutPreferences {
  preferredThemes: string;
  maxMinimumInvestment?: number;
  excludedSectors: string;
  maxRedFlags: number;
  requireNonAccreditedEligibility: boolean;
  requireRegCfOrRegA: boolean;
  preferredSecurityTypes: SecurityType[];
  weeklyDigestDay: DigestDay;
  weeklyDigestTime: string;
  emailRecipient: string;
}

export interface ScoutLogEntry {
  id: number;
  createdAt: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
}

export interface ScoutState {
  version: 5;
  sources: WatchlistSource[];
  snapshots: DealSnapshot[];
  preferences: ScoutPreferences;
  lastRunAt: string;
  lastDigestPreview: string;
  logs: ScoutLogEntry[];
  updatedAt: string;
}

export interface SourceFetchResult {
  source: WatchlistSource;
  rawText: string;
  structuredFields: Partial<DealSnapshot>;
  status: WatchlistSourceStatus;
  error?: string;
}

export interface SourceParseResult {
  fieldSuggestions: ImportFieldSuggestion[];
  evidenceClaims: EvidenceClaimInput[];
  riskSuggestions: ImportRedFlagSuggestion[];
  sections: ImportSection[];
  snapshotData: Partial<DealSnapshot>;
  rawTextHash: string;
}

export interface ReviewCandidate {
  id: string;
  companyName: string;
  platformOrSource: string;
  sector: string;
  sourceUrl: string;
  appLink: string;
  score: number;
  whyMatched: string[];
  keyTerms: string[];
  strongestEvidence: string;
  mainRedFlags: string[];
  notableChanges: string[];
  suggestedNextStep: string;
}

export interface ScoutDigest {
  subject: string;
  body: string;
  candidates: ReviewCandidate[];
  generatedAt: string;
}

export interface EmailSendResult {
  status: 'PREVIEW_ONLY' | 'NOT_CONFIGURED' | 'SENT' | 'ERROR';
  message: string;
}
