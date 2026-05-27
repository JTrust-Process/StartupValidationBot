import type {
  Deal,
  EvidenceClaimInput,
  ImportFieldSuggestion,
  RedFlagKey,
  SecurityType
} from '../models/deal';
import { RED_FLAG_DEFINITIONS } from '../models/deal';
import type {
  DealSnapshot,
  EmailSendResult,
  ReviewCandidate,
  ScoutDigest,
  ScoutPreferences,
  ScoutState,
  SourceFetchResult,
  SourceParseResult,
  WatchlistSource,
  WatchlistSourceInput,
  WatchlistSourceType
} from '../models/scout';
import {
  appendScoutLog,
  createScoutSource,
  deleteScoutSource,
  loadScoutState,
  saveScoutPreferences,
  saveScoutState,
  updateScoutSource
} from '../storage/scoutStorage';
import { getDeals, getRedFlagCount } from './dealService';
import { parseDealText } from './dealTextParser';
import { getDataConfidenceScore } from './riskAnalysis';
import {
  formatCurrency,
  formatInvestorEligibility,
  formatOfferingExemption,
  formatSecurityType
} from '../utils/formatters';

export interface ScoutRunResult {
  checked: number;
  errors: number;
  skipped: number;
  snapshotsCreated: number;
}

export interface ScoutDashboardSummary {
  sourceCount: number;
  enabledSourceCount: number;
  sourcesWithErrors: WatchlistSource[];
  topCandidates: ReviewCandidate[];
  notableSnapshots: DealSnapshot[];
  manualUpdateSources: WatchlistSource[];
  nextDigestLabel: string;
}

const SOURCE_LABELS: Record<WatchlistSourceType, string> = {
  REPUBLIC: 'Republic',
  WEFUNDER: 'Wefunder',
  STARTENGINE: 'StartEngine',
  DEALMAKER: 'DealMaker',
  FUNDRISE: 'Fundrise',
  JARSY: 'Jarsy',
  ROSS_PRE_IPO: 'Ross Pre-IPO',
  SEC_EDGAR: 'SEC EDGAR',
  MANUAL: 'Manual',
  OTHER: 'Other'
};

const SECURITY_TYPE_ORDER: SecurityType[] = [
  'SAFE',
  'EQUITY',
  'NOTE',
  'REVENUE_SHARE',
  'FUND_INTEREST',
  'SPV',
  'OTHER',
  'UNKNOWN'
];

function clean(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

function parseList(value: string): string[] {
  return value
    .split(/[,;\n]/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function parseMoney(value: string): number | undefined {
  const normalized = value.replace(/[$,\s]/g, '').toLowerCase();
  const match = normalized.match(/^(\d+(?:\.\d+)?)(m|mm|million|k|thousand)?$/);
  if (!match) return undefined;

  const amount = Number(match[1]);
  if (!Number.isFinite(amount)) return undefined;
  if (['m', 'mm', 'million'].includes(match[2] ?? '')) return Math.round(amount * 1_000_000);
  if (['k', 'thousand'].includes(match[2] ?? '')) return Math.round(amount * 1_000);
  return Math.round(amount);
}

function hashText(value: string): string {
  let hash = 5381;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 33) ^ value.charCodeAt(index);
  }

  return (hash >>> 0).toString(16);
}

function getSourceLabel(sourceType: WatchlistSourceType): string {
  return SOURCE_LABELS[sourceType] ?? sourceType;
}

function redFlagLabel(key: RedFlagKey): string {
  return RED_FLAG_DEFINITIONS.find((definition) => definition.key === key)?.label ?? key;
}

function suggestionValue(
  suggestions: ImportFieldSuggestion[],
  fieldName: ImportFieldSuggestion['fieldName']
): string {
  return suggestions.find((suggestion) => suggestion.fieldName === fieldName)?.suggestedValue ?? '';
}

function parseInvestorCount(rawText: string): number | undefined {
  const match =
    rawText.match(/(?:investor|backer)\s+count\s*[:|-]?\s*([\d,]+)/i) ??
    rawText.match(/number\s+of\s+(?:investors?|backers?)\s*[:|-]?\s*([\d,]+)/i) ??
    rawText.match(/([\d,]+)[ \t]+(?:investors?|backers?)\b/i);

  if (!match?.[1]) return undefined;
  const parsed = Number(match[1].replace(/,/g, ''));
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseDeadline(rawText: string): string {
  const match =
    rawText.match(/deadline\s*[:|-]?\s*([^\n.]+)/i) ??
    rawText.match(/offering\s+(?:ends|deadline)\s*[:|-]?\s*([^\n.]+)/i) ??
    rawText.match(/closes?\s+(?:on\s+)?([A-Z][a-z]+\s+\d{1,2},?\s+\d{4})/i);

  return clean(match?.[1] ?? '');
}

function estimateConfidenceFromParse(parseResult: SourceParseResult): number {
  let score = 20;

  parseResult.fieldSuggestions.forEach((suggestion) => {
    if (suggestion.confidence === 'HIGH') score += 8;
    if (suggestion.confidence === 'MEDIUM') score += 5;
    if (suggestion.confidence === 'LOW') score += 2;
  });

  const eligibility = suggestionValue(parseResult.fieldSuggestions, 'investorEligibility');
  const exemption = suggestionValue(parseResult.fieldSuggestions, 'offeringExemption');
  const securityType = suggestionValue(parseResult.fieldSuggestions, 'securityType');
  const fees = suggestionValue(parseResult.fieldSuggestions, 'platformFees');
  const valuation = suggestionValue(parseResult.fieldSuggestions, 'valuationOrCap');

  if (!eligibility || eligibility === 'UNCLEAR') score -= 15;
  if (eligibility === 'ACCREDITED_ONLY') score -= 10;
  if (!exemption || exemption === 'UNKNOWN') score -= 15;
  if (exemption === 'REG_D') score -= 10;
  if (!securityType || securityType === 'UNKNOWN') score -= 10;
  if (!fees || /unknown|unclear|not clearly/i.test(fees)) score -= 8;
  if (!valuation || /unknown|unclear|not disclosed/i.test(valuation)) score -= 8;

  return Math.max(0, Math.min(100, Math.round(score)));
}

function mapSourceToEvidenceType(source: WatchlistSource): EvidenceClaimInput['sourceType'] {
  if (source.sourceType === 'SEC_EDGAR') return 'FORM_C';
  if (source.sourceType === 'MANUAL') return 'USER_NOTE';
  return 'CAMPAIGN_PAGE';
}

function evidenceClaimsFromSuggestions(
  source: WatchlistSource,
  suggestions: ImportFieldSuggestion[]
): EvidenceClaimInput[] {
  return suggestions
    .filter((suggestion) => suggestion.confidence !== 'LOW')
    .slice(0, 8)
    .map((suggestion) => ({
      claim: `${String(suggestion.fieldName)}: ${suggestion.suggestedValue}`,
      sourceType: mapSourceToEvidenceType(source),
      sourceText: suggestion.sourceSnippet,
      evidenceStrength: suggestion.confidence === 'HIGH' ? 'STRONG' : 'MEDIUM',
      verified: false,
      notes: `Generated by Deal Scout from ${getSourceLabel(source.sourceType)} source ${source.id}.`
    }));
}

function findMatchingDeal(source: WatchlistSource, deals: Deal[]): Deal | undefined {
  if (source.dealId) {
    const linkedDeal = deals.find((deal) => deal.id === source.dealId);
    if (linkedDeal) return linkedDeal;
  }

  const sourceCompany = source.companyName.toLowerCase();
  if (sourceCompany) {
    const byCompany = deals.find((deal) => deal.companyName.toLowerCase() === sourceCompany);
    if (byCompany) return byCompany;
  }

  if (source.url) {
    return deals.find((deal) => deal.offeringUrl && deal.offeringUrl === source.url);
  }

  return undefined;
}

function latestSnapshotForSource(state: ScoutState, sourceId: number): DealSnapshot | undefined {
  return state.snapshots.find((snapshot) => snapshot.sourceId === sourceId);
}

function compareSnapshots(
  previous: DealSnapshot | undefined,
  current: DealSnapshot,
  rawText: string
): string[] {
  if (!previous) return ['Initial snapshot captured'];

  const changes: string[] = [];
  if (previous.amountRaised !== current.amountRaised) changes.push('Amount raised changed');
  if (previous.investorCount !== current.investorCount) changes.push('Investor count changed');
  if (previous.deadline !== current.deadline) changes.push('Deadline changed');
  if (previous.minimumInvestment !== current.minimumInvestment) changes.push('Minimum investment changed');
  if (previous.valuationOrCap !== current.valuationOrCap) changes.push('Terms changed');
  if (previous.offeringExemption !== current.offeringExemption) changes.push('Offering exemption changed');
  if (previous.securityType !== current.securityType) changes.push('Security type changed');
  if (current.redFlagCount > previous.redFlagCount) changes.push('New risk language detected');
  if (Math.abs(current.dataConfidence - previous.dataConfidence) >= 10) changes.push('Data confidence changed');
  if (/form\s+c-a|amendment|new document|offering circular/i.test(rawText)) {
    changes.push('New document language detected');
  }
  if (/live offering|now accepting investments|invest now/i.test(rawText) && /reservation|reserve/i.test(rawText)) {
    changes.push('Reservation became or may be becoming a live offering');
  }
  if (previous.rawTextHash !== current.rawTextHash && changes.length === 0) {
    changes.push('Source text changed');
  }

  return Array.from(new Set(changes));
}

function buildSnapshot(
  state: ScoutState,
  source: WatchlistSource,
  rawText: string,
  parseResult: SourceParseResult,
  deal: Deal | undefined
): DealSnapshot {
  const now = new Date().toISOString();
  const nextId = state.snapshots.reduce((maxId, snapshot) => Math.max(maxId, snapshot.id), 0) + 1;
  const amountRaised = parseMoney(suggestionValue(parseResult.fieldSuggestions, 'amountRaised'));
  const minimumInvestment = parseMoney(suggestionValue(parseResult.fieldSuggestions, 'minimumInvestment'));
  const securitySuggestion = suggestionValue(parseResult.fieldSuggestions, 'securityType') as SecurityType;
  const exemptionSuggestion = suggestionValue(parseResult.fieldSuggestions, 'offeringExemption') as DealSnapshot['offeringExemption'];
  const eligibilitySuggestion = suggestionValue(parseResult.fieldSuggestions, 'investorEligibility') as DealSnapshot['investorEligibility'];
  const baseSnapshot: DealSnapshot = {
    id: nextId,
    dealId: deal?.id ?? source.dealId ?? 0,
    sourceId: source.id,
    checkedAt: now,
    amountRaised: amountRaised ?? deal?.amountRaised,
    investorCount: parseInvestorCount(rawText),
    deadline: parseDeadline(rawText),
    minimumInvestment: minimumInvestment ?? deal?.minimumInvestment,
    valuationOrCap: suggestionValue(parseResult.fieldSuggestions, 'valuationOrCap') || deal?.valuationOrCap || '',
    securityType: SECURITY_TYPE_ORDER.includes(securitySuggestion) ? securitySuggestion : deal?.securityType ?? 'UNKNOWN',
    offeringExemption:
      exemptionSuggestion === 'REG_CF' ||
      exemptionSuggestion === 'REG_A' ||
      exemptionSuggestion === 'REG_D' ||
      exemptionSuggestion === 'OTHER'
        ? exemptionSuggestion
        : deal?.offeringExemption ?? 'UNKNOWN',
    investorEligibility:
      eligibilitySuggestion === 'NON_ACCREDITED' ||
      eligibilitySuggestion === 'ACCREDITED_ONLY' ||
      eligibilitySuggestion === 'UNCLEAR'
        ? eligibilitySuggestion
        : deal?.investorEligibility ?? 'UNCLEAR',
    dataConfidence: deal ? getDataConfidenceScore(deal) : estimateConfidenceFromParse(parseResult),
    redFlagCount: deal ? Math.max(getRedFlagCount(deal), parseResult.riskSuggestions.length) : parseResult.riskSuggestions.length,
    rawTextHash: parseResult.rawTextHash,
    notableChanges: [],
    createdAt: now
  };

  return {
    ...baseSnapshot,
    notableChanges: compareSnapshots(latestSnapshotForSource(state, source.id), baseSnapshot, rawText)
  };
}

function snapshotText(snapshot: DealSnapshot | undefined): string {
  if (!snapshot) return 'No snapshot yet';

  return [
    snapshot.minimumInvestment ? `Min ${formatCurrency(snapshot.minimumInvestment)}` : '',
    snapshot.valuationOrCap ? `Valuation/cap ${snapshot.valuationOrCap}` : '',
    snapshot.amountRaised ? `Raised ${formatCurrency(snapshot.amountRaised)}` : '',
    snapshot.deadline ? `Deadline ${snapshot.deadline}` : ''
  ]
    .filter(Boolean)
    .join(' | ');
}

export function getSourceTypeLabel(sourceType: WatchlistSourceType): string {
  return getSourceLabel(sourceType);
}

export function loadDealScoutState(): ScoutState {
  return loadScoutState();
}

export function addWatchlistSource(input: WatchlistSourceInput): WatchlistSource {
  return createScoutSource(input);
}

export function removeWatchlistSource(sourceId: number): void {
  deleteScoutSource(sourceId);
}

export function toggleWatchlistSource(sourceId: number): WatchlistSource {
  const source = loadScoutState().sources.find((item) => item.id === sourceId);
  if (!source) throw new Error(`Source ${sourceId} not found`);
  return updateScoutSource(sourceId, { enabled: !source.enabled });
}

export function saveDealScoutPreferences(preferences: ScoutPreferences): ScoutState {
  return saveScoutPreferences(preferences);
}

export async function fetchSource(source: WatchlistSource): Promise<SourceFetchResult> {
  if (!source.enabled) {
    return {
      source,
      rawText: '',
      structuredFields: {},
      status: 'SKIPPED'
    };
  }

  const pastedText = source.pastedText.trim();
  if (source.sourceType === 'MANUAL' || pastedText.length >= 40) {
    if (pastedText.length < 20 && source.notes.trim().length < 20) {
      return {
        source,
        rawText: '',
        structuredFields: {},
        status: 'NEEDS_MANUAL_PASTE',
        error: 'Manual source needs pasted page, filing, or notes text before it can be parsed.'
      };
    }

    return {
      source,
      rawText: pastedText || source.notes,
      structuredFields: {},
      status: 'OK'
    };
  }

  if (!source.url) {
    return {
      source,
      rawText: '',
      structuredFields: {},
      status: 'NEEDS_MANUAL_PASTE',
      error: 'No public URL or pasted text is available for this source.'
    };
  }

  try {
    const response = await fetch(source.url, {
      method: 'GET',
      credentials: 'omit',
      redirect: 'follow'
    });

    if (!response.ok) {
      return {
        source,
        rawText: '',
        structuredFields: {},
        status: 'ERROR',
        error: `Public fetch failed with HTTP ${response.status}. Paste page text manually if allowed.`
      };
    }

    return {
      source,
      rawText: await response.text(),
      structuredFields: {},
      status: 'OK'
    };
  } catch (error) {
    return {
      source,
      rawText: '',
      structuredFields: {},
      status: 'NEEDS_MANUAL_PASTE',
      error: error instanceof Error
        ? `Browser fetch could not read this public page: ${error.message}. Paste allowed text manually.`
        : 'Browser fetch could not read this public page. Paste allowed text manually.'
    };
  }
}

export function parseSource(source: WatchlistSource, rawText: string): SourceParseResult {
  const parsed = parseDealText({
    importMode: 'LAZY',
    title: source.companyName || source.url || `Source ${source.id}`,
    sourceUrl: source.url,
    rawText
  });

  return {
    fieldSuggestions: parsed.fieldSuggestions,
    evidenceClaims: evidenceClaimsFromSuggestions(source, parsed.fieldSuggestions),
    riskSuggestions: parsed.suggestedRedFlags,
    sections: parsed.sections,
    rawTextHash: hashText(rawText),
    snapshotData: {
      amountRaised: parseMoney(suggestionValue(parsed.fieldSuggestions, 'amountRaised')),
      investorCount: parseInvestorCount(rawText),
      deadline: parseDeadline(rawText),
      minimumInvestment: parseMoney(suggestionValue(parsed.fieldSuggestions, 'minimumInvestment')),
      valuationOrCap: suggestionValue(parsed.fieldSuggestions, 'valuationOrCap')
    }
  };
}

export async function runDealScoutOnce(): Promise<ScoutRunResult> {
  const state = loadScoutState();
  const deals = getDeals();
  const now = new Date().toISOString();
  const updatedSources: WatchlistSource[] = [];
  const newSnapshots: DealSnapshot[] = [];
  let checked = 0;
  let errors = 0;
  let skipped = 0;

  for (const source of state.sources) {
    const fetchResult = await fetchSource(source);
    const checkedAt = fetchResult.status === 'SKIPPED' ? source.lastCheckedAt : now;
    const lastError = fetchResult.error ?? '';
    const updatedSource: WatchlistSource = {
      ...source,
      lastCheckedAt: checkedAt,
      lastStatus: fetchResult.status,
      lastError,
      updatedAt: now
    };

    updatedSources.push(updatedSource);

    if (fetchResult.status === 'SKIPPED') {
      skipped += 1;
      continue;
    }

    checked += 1;
    if (fetchResult.status !== 'OK') {
      errors += 1;
      continue;
    }

    const parseResult = parseSource(source, fetchResult.rawText);
    newSnapshots.push(
      buildSnapshot(state, source, fetchResult.rawText, parseResult, findMatchingDeal(source, deals))
    );
  }

  const result: ScoutRunResult = {
    checked,
    errors,
    skipped,
    snapshotsCreated: newSnapshots.length
  };

  saveScoutState({
    ...state,
    sources: [
      ...updatedSources,
      ...state.sources.filter((source) => !updatedSources.some((updated) => updated.id === source.id))
    ],
    snapshots: [...newSnapshots, ...state.snapshots].sort((a, b) => b.checkedAt.localeCompare(a.checkedAt)),
    lastRunAt: now,
    logs: [
      {
        id: state.logs.reduce((maxId, log) => Math.max(maxId, log.id), 0) + 1,
        createdAt: now,
        level: errors ? 'WARN' as const : 'INFO' as const,
        message: `Deal Scout checked ${checked} source(s), created ${newSnapshots.length} snapshot(s), and saw ${errors} error(s).`
      },
      ...state.logs
    ].slice(0, 40),
    updatedAt: now
  });

  return result;
}

function preferencesAllowCandidate(
  candidate: {
    sector: string;
    minimumInvestment?: number;
    redFlagCount: number;
    investorEligibility: DealSnapshot['investorEligibility'];
    offeringExemption: DealSnapshot['offeringExemption'];
    securityType: SecurityType;
  },
  preferences: ScoutPreferences
): boolean {
  const excluded = parseList(preferences.excludedSectors);
  const sector = candidate.sector.toLowerCase();

  if (excluded.some((item) => sector.includes(item))) return false;
  if (
    typeof preferences.maxMinimumInvestment === 'number' &&
    typeof candidate.minimumInvestment === 'number' &&
    candidate.minimumInvestment > preferences.maxMinimumInvestment
  ) {
    return false;
  }
  if (candidate.redFlagCount > preferences.maxRedFlags) return false;
  if (preferences.requireNonAccreditedEligibility && candidate.investorEligibility !== 'NON_ACCREDITED') {
    return false;
  }
  if (
    preferences.requireRegCfOrRegA &&
    candidate.offeringExemption !== 'REG_CF' &&
    candidate.offeringExemption !== 'REG_A'
  ) {
    return false;
  }
  if (
    preferences.preferredSecurityTypes.length &&
    !preferences.preferredSecurityTypes.includes(candidate.securityType)
  ) {
    return false;
  }

  return true;
}

function scoreThemeFit(text: string, preferences: ScoutPreferences): { score: number; reasons: string[] } {
  const themes = parseList(preferences.preferredThemes);
  if (!themes.length) return { score: 10, reasons: ['No preferred theme filter set'] };

  const normalized = text.toLowerCase();
  const matched = themes.filter((theme) => normalized.includes(theme));
  return {
    score: Math.min(20, matched.length * 10),
    reasons: matched.length ? matched.map((theme) => `Matches theme: ${theme}`) : ['No preferred theme match']
  };
}

function scoreAccessibility(
  eligibility: DealSnapshot['investorEligibility'],
  exemption: DealSnapshot['offeringExemption']
): { score: number; reason: string } {
  if (eligibility === 'NON_ACCREDITED' && (exemption === 'REG_CF' || exemption === 'REG_A')) {
    return { score: 20, reason: 'Non-accredited access with Reg CF/Reg A structure' };
  }
  if (eligibility === 'NON_ACCREDITED') return { score: 16, reason: 'Non-accredited eligibility indicated' };
  if (eligibility === 'UNCLEAR') return { score: 8, reason: 'Eligibility needs confirmation' };
  return { score: 0, reason: 'Accredited-only access indicated' };
}

function scoreTraction(deal: Deal | undefined, text: string): number {
  if (deal?.revenueStatus === 'REVENUE') return 15;
  if (deal?.revenueStatus === 'EARLY_REVENUE') return 10;
  if (/revenue|arr|mrr|contract|customer|pilot|partnership/i.test(text)) return 8;
  if (deal?.revenueStatus === 'PRE_REVENUE') return 2;
  return 4;
}

function scoreValuation(deal: Deal | undefined, snapshot: DealSnapshot | undefined): number {
  if (deal?.redFlags.highValuation) return 0;
  const valuationText = deal?.valuationOrCap || snapshot?.valuationOrCap || '';
  const valuation = parseMoney(valuationText);
  if (!valuation) return 5;
  if (valuation <= 25_000_000) return 10;
  if (valuation <= 75_000_000) return 7;
  if (valuation <= 150_000_000) return 4;
  return 2;
}

function scoreUrgency(deal: Deal | undefined, snapshot: DealSnapshot | undefined): number {
  if (snapshot?.notableChanges.length) return 10;
  if (deal?.review?.nextReviewDate && deal.review.nextReviewDate <= new Date().toISOString().slice(0, 10)) return 8;
  if (deal?.nextMilestone) return 6;
  if (snapshot?.deadline) return 5;
  return 0;
}

function getCandidateScore(
  deal: Deal | undefined,
  source: WatchlistSource | undefined,
  snapshot: DealSnapshot | undefined,
  preferences: ScoutPreferences
): { score: number; reasons: string[] } {
  const themeText = [
    deal?.companyName,
    deal?.sector,
    deal?.shortDescription,
    deal?.thesis,
    source?.companyName,
    source?.notes,
    source?.pastedText
  ].join(' ');
  const theme = scoreThemeFit(themeText, preferences);
  const eligibility = snapshot?.investorEligibility ?? deal?.investorEligibility ?? 'UNCLEAR';
  const exemption = snapshot?.offeringExemption ?? deal?.offeringExemption ?? 'UNKNOWN';
  const access = scoreAccessibility(eligibility, exemption);
  const confidence = snapshot?.dataConfidence ?? (deal ? getDataConfidenceScore(deal) : 20);
  const traction = scoreTraction(deal, themeText);
  const valuation = scoreValuation(deal, snapshot);
  const redFlagCount = snapshot?.redFlagCount ?? (deal ? getRedFlagCount(deal) : 0);
  const urgency = scoreUrgency(deal, snapshot);
  const score = Math.max(
    0,
    Math.min(
      100,
      Math.round(
        theme.score +
          access.score +
          Math.min(20, Math.round(confidence * 0.2)) +
          traction +
          valuation +
          urgency -
          Math.min(15, redFlagCount * 4)
      )
    )
  );

  return {
    score,
    reasons: [...theme.reasons, access.reason, `Data confidence ${confidence}/100`, `${redFlagCount} red flag(s)`]
  };
}

function getLatestSnapshot(
  snapshots: DealSnapshot[],
  deal: Deal | undefined,
  source: WatchlistSource | undefined
): DealSnapshot | undefined {
  return snapshots.find(
    (snapshot) =>
      (deal && snapshot.dealId === deal.id) ||
      (source && snapshot.sourceId === source.id)
  );
}

function getCandidateRedFlags(
  deal: Deal | undefined,
  snapshot: DealSnapshot | undefined,
  source: WatchlistSource | undefined
): string[] {
  if (!deal) {
    const sourceRisks = source?.pastedText
      ? parseSource(source, source.pastedText).riskSuggestions.map((risk) => risk.label)
      : [];
    const uniqueRisks = Array.from(new Set(sourceRisks));

    return uniqueRisks.length
      ? uniqueRisks
      : snapshot?.redFlagCount
        ? [`${snapshot.redFlagCount} risk suggestion(s) from latest source text`]
        : [];
  }

  return Object.entries(deal.redFlags)
    .filter(([, checked]) => checked)
    .map(([key]) => redFlagLabel(key as RedFlagKey));
}

function buildCandidate(
  deal: Deal | undefined,
  source: WatchlistSource | undefined,
  snapshot: DealSnapshot | undefined,
  preferences: ScoutPreferences
): ReviewCandidate | null {
  const sector = deal?.sector ?? '';
  const redFlagCount = snapshot?.redFlagCount ?? (deal ? getRedFlagCount(deal) : 0);
  const investorEligibility = snapshot?.investorEligibility ?? deal?.investorEligibility ?? 'UNCLEAR';
  const offeringExemption = snapshot?.offeringExemption ?? deal?.offeringExemption ?? 'UNKNOWN';
  const securityType = snapshot?.securityType ?? deal?.securityType ?? 'UNKNOWN';
  const minimumInvestment = snapshot?.minimumInvestment ?? deal?.minimumInvestment;

  if (
    !preferencesAllowCandidate(
      { sector, minimumInvestment, redFlagCount, investorEligibility, offeringExemption, securityType },
      preferences
    )
  ) {
    return null;
  }

  const score = getCandidateScore(deal, source, snapshot, preferences);
  const keyTerms = [
    `Eligibility: ${formatInvestorEligibility(investorEligibility)}`,
    `Exemption: ${formatOfferingExemption(offeringExemption)}`,
    `Security: ${formatSecurityType(securityType)}`,
    minimumInvestment ? `Minimum: ${formatCurrency(minimumInvestment)}` : 'Minimum: unknown',
    snapshot?.valuationOrCap || deal?.valuationOrCap ? `Valuation/cap: ${snapshot?.valuationOrCap || deal?.valuationOrCap}` : 'Valuation/cap: unknown'
  ];
  const strongestEvidence =
    deal?.evidenceClaims.find((claim) => claim.evidenceStrength === 'STRONG')?.claim ??
    deal?.evidenceClaims[0]?.claim ??
    snapshotText(snapshot);

  return {
    id: deal ? `deal-${deal.id}` : `source-${source?.id ?? 0}`,
    companyName: deal?.companyName || source?.companyName || 'Unlabeled source',
    platformOrSource: deal?.platform || (source ? getSourceLabel(source.sourceType) : 'Unknown'),
    sector: deal?.sector || 'Unknown',
    sourceUrl: source?.url || deal?.offeringUrl || '',
    appLink: deal ? `#/deals/${deal.id}` : '#/scout',
    score: score.score,
    whyMatched: score.reasons,
    keyTerms,
    strongestEvidence,
    mainRedFlags: getCandidateRedFlags(deal, snapshot, source),
    notableChanges: snapshot?.notableChanges ?? [],
    suggestedNextStep: deal
      ? snapshot?.notableChanges.length
        ? 'Open the deal workspace and review the changed terms or risk language.'
        : 'Review offering documents, evidence, and red flags before deciding Pass / Watch / Invest Small.'
      : 'Paste allowed source text into Deal Text Import or create a deal record before making any decision.'
  };
}

export function getReviewCandidates(): ReviewCandidate[] {
  const state = loadScoutState();
  const deals = getDeals();
  const candidates: ReviewCandidate[] = [];

  deals.forEach((deal) => {
    const source = state.sources.find(
      (item) =>
        item.dealId === deal.id ||
        (item.companyName && item.companyName.toLowerCase() === deal.companyName.toLowerCase()) ||
        (item.url && item.url === deal.offeringUrl)
    );
    const candidate = buildCandidate(deal, source, getLatestSnapshot(state.snapshots, deal, source), state.preferences);
    if (candidate) candidates.push(candidate);
  });

  state.sources
    .filter((source) => !findMatchingDeal(source, deals))
    .forEach((source) => {
      const candidate = buildCandidate(undefined, source, getLatestSnapshot(state.snapshots, undefined, source), state.preferences);
      if (candidate) candidates.push(candidate);
    });

  return candidates.sort((a, b) => b.score - a.score).slice(0, 12);
}

export function generateScoutDigest(): ScoutDigest {
  const generatedAt = new Date().toISOString();
  const candidates = getReviewCandidates().slice(0, 5);
  const body = [
    'Subject: Weekly Startup Deal Scout - companies to consider researching',
    '',
    'This is a research shortlist, not financial advice. Review offering documents and risks before investing.',
    '',
    candidates.length
      ? candidates
          .map(
            (candidate, index) => `${index + 1}. ${candidate.companyName} (${candidate.platformOrSource})
Review-priority score: ${candidate.score}/100
Why it matched: ${candidate.whyMatched.join('; ')}
Key terms: ${candidate.keyTerms.join('; ')}
Strongest evidence: ${candidate.strongestEvidence || 'No strong evidence captured yet.'}
Main red flags: ${candidate.mainRedFlags.length ? candidate.mainRedFlags.join('; ') : 'None checked yet'}
Notable changes: ${candidate.notableChanges.length ? candidate.notableChanges.join('; ') : 'None from latest snapshot'}
Suggested next step: ${candidate.suggestedNextStep}
Open: ${candidate.sourceUrl || candidate.appLink}`
          )
          .join('\n\n')
      : 'No candidates matched the current Deal Scout preferences. Add sources, paste allowed page text, or loosen filters.',
    '',
    'Reminder: this email is for research triage only. It is not a recommendation to buy, sell, or invest.'
  ].join('\n');

  return {
    subject: 'Weekly Startup Deal Scout - deals to review',
    body,
    candidates,
    generatedAt
  };
}

export async function sendScoutDigestEmail(digest: ScoutDigest): Promise<EmailSendResult> {
  const mode = String(import.meta.env.VITE_DEAL_SCOUT_EMAIL_MODE ?? 'preview').toLowerCase();
  const endpoint = String(
    import.meta.env.VITE_DEAL_SCOUT_EMAIL_ENDPOINT ?? '/api/deal-scout/digest/send'
  );

  if (mode === 'preview') {
    console.info('Deal Scout email preview generated.', digest.subject);
    appendScoutLog('INFO', 'Deal Scout email preview generated. Sending is preview-only in development.');
    return {
      status: 'PREVIEW_ONLY',
      message: 'Preview generated. Email sending is disabled in preview mode.'
    };
  }

  if (mode !== 'server' && mode !== 'resend') {
    appendScoutLog('WARN', `Unknown email mode "${mode}" requested.`);
    return {
      status: 'NOT_CONFIGURED',
      message: `Unknown email mode "${mode}". Use preview, server, or resend.`
    };
  }

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        to: loadScoutState().preferences.emailRecipient || undefined,
        subject: digest.subject,
        text: digest.body,
        html: digestTextToHtml(digest.body)
      })
    });
    const payload = (await response.json().catch(() => null)) as
      | { ok?: boolean; id?: string; error?: string }
      | null;

    if (!response.ok || !payload?.ok) {
      const message = payload?.error || `server endpoint unavailable (${response.status})`;
      appendScoutLog('ERROR', `Deal Scout email failed: ${message}`);
      return {
        status: 'ERROR',
        message: `Failed: ${message}`
      };
    }

    appendScoutLog('INFO', `Deal Scout email sent through server endpoint. Resend id: ${payload.id || 'unknown'}.`);
    return {
      status: 'SENT',
      message: `Sent successfully${payload.id ? ` (${payload.id})` : ''}.`
    };
  } catch (error) {
    const message =
      error instanceof Error && error.message
        ? `server endpoint unavailable (${error.message})`
        : 'server endpoint unavailable';
    appendScoutLog('ERROR', `Deal Scout email failed: ${message}`);
    return {
      status: 'ERROR',
      message: `Failed: ${message}`
    };
  }
}

function digestTextToHtml(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

  return `<!doctype html>
<html>
  <body style="font-family: Arial, sans-serif; line-height: 1.5; color: #111827;">
    <h1 style="font-size: 20px;">Startup Deal Scout</h1>
    <p style="padding: 12px; background: #fff7ed; border: 1px solid #fed7aa;">
      This is a research shortlist, not financial advice. Review offering documents and risks before investing.
    </p>
    ${escaped
      .split(/\n{2,}/)
      .map((block) => `<p>${block.replace(/\n/g, '<br>')}</p>`)
      .join('\n')}
  </body>
</html>`;
}

export async function runDealScoutDigestJob(): Promise<{ run: ScoutRunResult; digest: ScoutDigest }> {
  const run = await runDealScoutOnce();
  const digest = generateScoutDigest();
  const state = loadScoutState();

  saveScoutState({
    ...state,
    lastDigestPreview: digest.body,
    updatedAt: new Date().toISOString()
  });

  return { run, digest };
}

export function getScoutDashboardSummary(): ScoutDashboardSummary {
  const state = loadScoutState();
  const sourcesWithErrors = state.sources.filter((source) => source.lastStatus === 'ERROR');
  const manualUpdateSources = state.sources.filter(
    (source) => source.lastStatus === 'NEEDS_MANUAL_PASTE' || (!source.url && !source.pastedText)
  );
  const notableSnapshots = state.snapshots
    .filter((snapshot) => snapshot.notableChanges.length)
    .slice(0, 8);

  return {
    sourceCount: state.sources.length,
    enabledSourceCount: state.sources.filter((source) => source.enabled).length,
    sourcesWithErrors,
    topCandidates: getReviewCandidates().slice(0, 5),
    notableSnapshots,
    manualUpdateSources,
    nextDigestLabel: `${state.preferences.weeklyDigestDay.toLowerCase()} at ${state.preferences.weeklyDigestTime}`
  };
}

export function getSecurityTypeOptions(): SecurityType[] {
  return SECURITY_TYPE_ORDER;
}
