export interface RadarSource {
  id: number;
  sourceKey: string;
  sourceType: 'RSS' | 'PRODUCT_HUNT' | 'MANUAL' | 'YC_DIRECTORY';
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
  message: string;
}

export interface RadarCompanyFilters {
  search?: string;
  sector?: string;
  minRadar?: number;
  sort?: 'radar' | 'newest' | 'updated';
}
