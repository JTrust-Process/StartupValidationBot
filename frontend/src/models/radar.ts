export interface RadarSource {
  id: number;
  sourceKey: string;
  sourceType: 'RSS' | 'PRODUCT_HUNT' | 'MANUAL' | 'YC_DIRECTORY' | 'HACKER_NEWS';
  name: string;
  enabled: boolean;
  lastCheckedAt: string | null;
  lastStatus: string;
  lastError: string | null;
}

export interface RadarCompany {
  id: number;
  name: string;
  domain: string | null;
  websiteUrl: string | null;
  description: string;
  sector: string;
  categories: string[];
  radarScore: number;
  sourceCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
  accelerator: string;
  acceleratorBatch: string;
}

export interface RadarAdminCompany extends RadarCompany {
  headquarters: string | null;
  foundedYear: number | null;
  aliases: string[];
  personalScore: number;
  scoreReasoning: string;
  ignored: boolean;
  watched: boolean;
}

export interface RadarAnalysis {
  analysisType: 'RADAR' | 'DEEP_DIVE';
  analysisOrigin: 'AI' | 'DETERMINISTIC' | 'HYBRID';
  provider: string;
  model: string;
  summary: string;
  sector: string;
  problem: string;
  solution: string;
  businessModel: string;
  stage: string;
  founders: string[];
  fundingSummary: string;
  likelyInvestors: string[];
  trendTags: string[];
  monitoringTriggers: string[];
  facts: string[];
  inferences: string[];
  whyInteresting: string[];
  momentumSignals: string[];
  tractionSignals: string[];
  technicalDifferentiation: string[];
  marketSignals: string[];
  risks: string[];
  bullCase: string[];
  bearCase: string[];
  unansweredQuestions: string[];
  whyItMatters: string;
  sourceUrls: string[];
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  radarScoreInputs: string[];
  radarDimensions: Record<string, number>;
  radarScore: number;
  createdAt: string;
}

export interface RadarSnapshot {
  capturedAt: string;
  notableChanges: string[];
}

export interface RadarResearchSource {
  sourceType: string;
  title: string;
  url: string | null;
  sourceDate: string | null;
  evidenceClassification?: 'PUBLIC_OFFICIAL' | 'PUBLIC_NEWS' | 'PRIVATE_USER' | 'UNKNOWN';
}

export interface RadarCompanyDetail {
  company: RadarCompany;
  latestAnalysis: RadarAnalysis | null;
  snapshots: RadarSnapshot[];
  researchSources: RadarResearchSource[];
}

export interface RadarAdminCompanyDetail {
  company: RadarAdminCompany;
  latestAnalysis: RadarAnalysis | null;
  snapshots: Array<RadarSnapshot & { snapshotJson: string }>;
  researchSources: Array<RadarResearchSource & { excerpt: string; fact: boolean }>;
  watchlistNotes: string;
  nextReviewAt: string | null;
}

export interface RadarAdminSession {
  authenticated: boolean;
  expiresAt: string | null;
  /** False when RADAR_ADMIN_PASSWORD_HASH is unset: the deployment cannot authenticate anyone. */
  configured: boolean;
}

export interface RadarJobStatus {
  jobType: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface RadarSystemStatus {
  databaseHealthy: boolean;
  lastDiscoveryRun: RadarJobStatus | null;
  lastEnrichmentRun: string | null;
  lastWatchlistRefresh: RadarJobStatus | null;
  lastTrendRun: RadarJobStatus | null;
  lastDigest: string | null;
  recentJobFailures: RadarJobStatus[];
  discoveriesProcessed: number;
  aiCalls: number;
  aiCacheHits: number;
  aiFailures: number;
  aiEnabled: boolean;
  aiProvider: string;
  routineModel: string;
  deepDiveModel: string;
  integrations: Record<string, boolean>;
  aiProviderComparisons: RadarAiProviderComparison[];
}

export interface RadarAiProviderComparison {
  provider: string;
  requestedModel: string;
  actualModel: string;
  analysisType: string;
  attempts: number;
  successes: number;
  failures: number;
  cacheHits: number;
  retries: number;
  schemaFailures: number;
  malformedFailures: number;
  averageLatencyMs: number | null;
  inputTokens: number;
  outputTokens: number;
  providerCostUsd: number | null;
}

export interface RadarFixtureResult {
  primaryCompanyId: number;
  duplicateCompanyId: number;
  secondCompanyId: number;
  deduplicated: boolean;
  snapshotCount: number;
  radarScore: number;
  personalScore: number;
  watched: boolean;
  analysisType: string;
  analysisOrigin: string;
  trendCount: number;
  digestPeriodKey: string;
  digestPreviewGenerated: boolean;
}

export interface RadarTrend {
  id: number;
  key: string;
  name: string;
  summary: string;
  companyCount: number;
  momentumScore: number;
  periodStart: string;
  periodEnd: string;
  companies: RadarCompany[];
}

export interface RadarJobResult {
  ok: boolean;
  jobType: string;
  idempotencyKey: string;
  duplicate: boolean;
  processed: number;
  created: number;
  updated: number;
  errorCount: number;
  errors: string[];
  diagnostics: string[];
  message: string;
}

export type OfferingStatus = 'ACTIVE' | 'POSSIBLY_ACTIVE' | 'ENDED' | 'WITHDRAWN' | 'TERMINATED' | 'UNKNOWN';
export type OfferingMatchStatus = 'CONFIRMED' | 'LIKELY' | 'AMBIGUOUS' | 'UNMATCHED' | 'REJECTED';

export interface RadarOffering {
  id: number;
  radarCompanyId: number | null;
  companyName: string | null;
  issuerName: string;
  issuerCik: string | null;
  platform: string;
  intermediaryName: string | null;
  offeringUrl: string | null;
  secFilingUrl: string | null;
  accessionNumber: string | null;
  fileNumber: string | null;
  filingType: string;
  filingDate: string;
  offeringExemption: 'REG_CF';
  securityType: string | null;
  minimumInvestment: number | null;
  targetAmount: number | null;
  maximumAmount: number | null;
  valuationOrCap: string | null;
  deadline: string | null;
  amountRaised: number | null;
  status: OfferingStatus;
  source: string;
  matchStatus: OfferingMatchStatus;
  matchConfidence: number;
  matchReason: string;
  firstSeenAt: string;
  lastSeenAt: string;
  provenance: 'SEC_EDGAR' | 'PLATFORM_OFFERING';
  reconciliationStatus: 'PLATFORM_CONFIRMED' | 'SEC_RECONCILED' | 'POSSIBLE' | 'NEEDS_REVIEW';
  platformStatus: 'ACTIVE' | 'RESERVATION' | 'CLOSING_SOON' | 'CLOSED' | 'WITHDRAWN' | 'TERMINATED' | 'UNKNOWN' | null;
}

export interface OfferingDiagnostics {
  offeringsStored: number;
  confirmedMatches: number;
  possibleMatches: number;
  sourceStatus: string;
  sourceLastSuccessAt: string | null;
  sourceError: string | null;
  lastJobStatus: string;
  lastJobStartedAt: string | null;
  lastJobCompletedAt: string | null;
  lastJobDurationMs: number | null;
  recordsInspected: number;
  newOfferings: number;
  updatedOfferings: number;
  errorCount: number;
}

export type DiligencePacketStatus =
  | 'PENDING'
  | 'RESOLVING_IDENTITY'
  | 'GATHERING_EVIDENCE'
  | 'READY'
  | 'PARTIAL'
  | 'NEEDS_REVIEW'
  | 'FAILED';

export type DiligenceEvidenceClassification =
  | 'SEC_FILED_FACT'
  | 'PLATFORM_ISSUER_CLAIM'
  | 'ISSUER_WEBSITE_CLAIM'
  | 'PUBLIC_REPORTING'
  | 'DETERMINISTIC_INFERENCE'
  | 'AI_SYNTHESIS';

export interface DiligenceEvidence {
  id: number;
  sourceType: string;
  sourceUrl: string | null;
  sourceTitle: string;
  factKey: string;
  factValue: string;
  period: string | null;
  classification: DiligenceEvidenceClassification;
  observedAt: string;
  confidence: number;
  rawExcerpt: string | null;
  metadata: Record<string, unknown>;
}

export interface DiligenceFinancialPeriod {
  period: string;
  revenue: number | null;
  costOfGoods: number | null;
  netIncome: number | null;
  cash: number | null;
  assets: number | null;
  liabilities: number | null;
  shortTermDebt: number | null;
  longTermDebt: number | null;
  taxesPaid: number | null;
  sourceAccessionNumber: string | null;
  sourceUrl: string | null;
}

export interface DiligencePacket {
  id: number;
  radarCompanyId: number;
  companyName: string;
  offeringId: number;
  platform: string;
  campaignUrl: string | null;
  secFilingUrl: string | null;
  status: DiligencePacketStatus;
  identityStatus: string;
  completeness: number;
  confidence: number;
  summary: string;
  bullCase: string[];
  bearCase: string[];
  keyRisks: string[];
  unansweredQuestions: string[];
  materialDiscrepancies: string[];
  nextMonitoringMilestones: string[];
  sourcesChecked: string[];
  dataNotFound: string[];
  generatedAt: string;
  lastRefreshedAt: string;
  reviewedAt: string | null;
  securityType: string | null;
  minimumInvestment: number | null;
  targetAmount: number | null;
  maximumAmount: number | null;
  amountRaised: number | null;
  valuationOrCap: string | null;
  deadline: string | null;
  evidence: DiligenceEvidence[];
  financials: DiligenceFinancialPeriod[];
}

export interface InvestmentAvailabilityCheck {
  sourceType: string;
  status: string;
  resultSummary: string;
  unresolvedQuestion: string | null;
  checkedAt: string;
}

export interface CompanyInvestmentAvailability {
  companyId: number;
  lastFullCheck: string | null;
  checks: InvestmentAvailabilityCheck[];
}

  export interface AutonomousDiligenceDiagnostics {
  lastRunStatus: string;
  lastRunStartedAt: string | null;
  lastRunCompletedAt: string | null;
  lastRunDurationMs: number | null;
  companiesConsidered: number;
  offeringsConsidered: number;
  identitiesResolved: number;
  campaignsResolved: number;
  packetsReady: number;
  packetsPartial: number;
  needsReview: number;
  platformErrors: number;
  aiFallbacks: number;
  emailsQueued: number;
    emailsSent: number;
    emailsFailed: number;
    campaignCompaniesEligible: number;
    campaignCompaniesSearched: number;
    campaignCandidatesFound: number;
    campaignConfirmed: number;
    campaignPossible: number;
    campaignRejected: number;
    campaignCacheHits: number;
    campaignDiscoveryErrors: number;
    campaignDiscoveryPlatforms: Array<{
      platform: string;
      capability: string;
      lastCheckedAt: string | null;
      lastSuccessAt: string | null;
      lastFailureAt: string | null;
      status: string;
      requests: number;
      candidates: number;
      resolved: number;
      error: string | null;
    }>;
    nativeCandidatesFound: number;
    nativeActiveCandidates: number;
    nativeNewCompanies: number;
    nativeMatchedCompanies: number;
    nativeNewOfferings: number;
    nativeUpdatedOfferings: number;
    nativeDuplicatesPrevented: number;
    nativePossible: number;
    nativeRejected: number;
    nativeErrors: number;
    nativeSecRecentInspected: number;
    nativeSecPlatformClassified: number;
    nativeSecReconciled: number;
    reviewQueueBefore: number;
    reviewQueueAfter: number;
    nativeSources: Array<{
      source: string;
      capability: string;
      status: string;
      lastCheckedAt: string | null;
      directoryFetched: boolean;
      requests: number;
      detailRequests: number;
      candidates: number;
      activeCandidates: number;
      inserted: number;
      matched: number;
      rejected: number;
      error: string | null;
    }>;
    platforms: Array<{
    platform: string;
    lastCheckedAt: string | null;
    status: string;
    requests: number;
    campaignsFound: number;
    error: string | null;
  }>;
  resend: {
    configured: boolean;
    lastStatus: string;
    lastMessageId: string | null;
    lastError: string | null;
  };
}

export interface RadarCompanyFilters {
  search?: string;
  sector?: string;
  minRadar?: number;
  sort?: 'radar' | 'newest' | 'updated';
}

/* ------------------------------------------------------------------ Phase 2 intelligence layer */

export type ChangeSignificance = 'MINOR' | 'INTERESTING' | 'IMPORTANT' | 'MAJOR';

export interface RadarCompanyChange {
  id: number;
  companyId: number;
  companyName: string;
  changeType: string;
  significance: ChangeSignificance;
  summary: string;
  previousValue: string | null;
  currentValue: string | null;
  whyItMatters: string | null;
  detectedAt: string;
}

export interface RadarHomeCompanyCard {
  id: number;
  name: string;
  description: string;
  sector: string;
  categories: string[];
  accelerator: string;
  acceleratorBatch: string;
  radarScore: number;
  personalScore: number;
  sourceCount: number;
  watched: boolean;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  whyItMatters: string[];
  whyYouMightCare: string[];
  highlight: string;
}

export interface RadarTrendDetail {
  id: number;
  key: string;
  name: string;
  summary: string;
  whyItMatters: string;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  companyCount: number;
  recentDiscoveries: number;
  priorDiscoveries: number;
  velocityDirection: 'NEW' | 'RISING' | 'STEADY' | 'COOLING' | 'UNKNOWN';
  velocityNote: string;
  momentumScore: number;
  companies: RadarAdminCompany[];
}

export interface RadarHomeSection {
  key: string;
  title: string;
  subtitle: string;
  kind: 'COMPANIES' | 'CHANGES' | 'TRENDS';
  companies: RadarHomeCompanyCard[];
  changes: RadarCompanyChange[];
  trends: RadarTrendDetail[];
}

export interface RadarHome {
  generatedAt: string;
  totalCompanies: number;
  newSinceYesterday: number;
  meaningfulChanges: number;
  sections: RadarHomeSection[];
}

export interface RadarInterest {
  label: string;
  weight: number;
  keywords: string[];
}

export interface RadarInterestProfile {
  interests: RadarInterest[];
  updatedAt: string | null;
}

export interface RadarInterestSaveResult {
  profile: RadarInterestProfile;
  companiesRescored: number;
}

export interface RadarRelevanceExplanation {
  score: number;
  matchedInterests: string[];
  reasons: string[];
}

export interface RadarSimilarCompany {
  companyId: number;
  name: string;
  score: number;
  relationship: string;
  reasons: string[];
  categories: string[];
  radarScore: number;
  personalScore: number;
}

export type RadarInteractionSignal = 'WATCH' | 'IGNORE' | 'DEEP_DIVE' | 'VISIT';
