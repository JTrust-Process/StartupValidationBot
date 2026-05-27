import type {
  DealSnapshot,
  DigestDay,
  ScoutLogEntry,
  ScoutPreferences,
  ScoutState,
  WatchlistSource,
  WatchlistSourceInput,
  WatchlistSourceStatus,
  WatchlistSourceType
} from '../models/scout';
import type { InvestorEligibility, OfferingExemption, SecurityType } from '../models/deal';

const SCOUT_STORAGE_KEY = 'startupDealOs.scout.v5';

const DEFAULT_PREFERENCES: ScoutPreferences = {
  preferredThemes: '',
  excludedSectors: '',
  maxRedFlags: 4,
  requireNonAccreditedEligibility: true,
  requireRegCfOrRegA: false,
  preferredSecurityTypes: [],
  weeklyDigestDay: 'FRIDAY',
  weeklyDigestTime: '09:00',
  emailRecipient: ''
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function asNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  return undefined;
}

function asSourceType(value: unknown): WatchlistSourceType {
  if (
    value === 'REPUBLIC' ||
    value === 'WEFUNDER' ||
    value === 'STARTENGINE' ||
    value === 'DEALMAKER' ||
    value === 'FUNDRISE' ||
    value === 'JARSY' ||
    value === 'ROSS_PRE_IPO' ||
    value === 'SEC_EDGAR' ||
    value === 'MANUAL' ||
    value === 'OTHER'
  ) {
    return value;
  }

  return 'MANUAL';
}

function asStatus(value: unknown): WatchlistSourceStatus {
  if (
    value === 'NEVER_CHECKED' ||
    value === 'OK' ||
    value === 'ERROR' ||
    value === 'SKIPPED' ||
    value === 'NEEDS_MANUAL_PASTE'
  ) {
    return value;
  }

  return 'NEVER_CHECKED';
}

function asDigestDay(value: unknown): DigestDay {
  if (
    value === 'MONDAY' ||
    value === 'TUESDAY' ||
    value === 'WEDNESDAY' ||
    value === 'THURSDAY' ||
    value === 'FRIDAY' ||
    value === 'SATURDAY' ||
    value === 'SUNDAY'
  ) {
    return value;
  }

  return 'FRIDAY';
}

function asSecurityType(value: unknown): SecurityType {
  if (
    value === 'SAFE' ||
    value === 'EQUITY' ||
    value === 'NOTE' ||
    value === 'REVENUE_SHARE' ||
    value === 'FUND_INTEREST' ||
    value === 'SPV' ||
    value === 'OTHER' ||
    value === 'UNKNOWN'
  ) {
    return value;
  }

  return 'UNKNOWN';
}

function asOfferingExemption(value: unknown): OfferingExemption {
  if (
    value === 'REG_CF' ||
    value === 'REG_A' ||
    value === 'REG_D' ||
    value === 'UNKNOWN' ||
    value === 'OTHER'
  ) {
    return value;
  }

  return 'UNKNOWN';
}

function asInvestorEligibility(value: unknown): InvestorEligibility {
  if (
    value === 'NON_ACCREDITED' ||
    value === 'ACCREDITED_ONLY' ||
    value === 'UNCLEAR'
  ) {
    return value;
  }

  return 'UNCLEAR';
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string');
}

function normalizeSecurityTypes(value: unknown): SecurityType[] {
  if (!Array.isArray(value)) return [];
  return value
    .map(asSecurityType)
    .filter((securityType, index, all) => all.indexOf(securityType) === index);
}

function normalizePreferences(value: unknown): ScoutPreferences {
  if (!isRecord(value)) return DEFAULT_PREFERENCES;

  return {
    preferredThemes: asString(value.preferredThemes),
    maxMinimumInvestment: asNumber(value.maxMinimumInvestment),
    excludedSectors: asString(value.excludedSectors),
    maxRedFlags: Math.max(0, Math.trunc(asNumber(value.maxRedFlags) ?? 4)),
    requireNonAccreditedEligibility: value.requireNonAccreditedEligibility !== false,
    requireRegCfOrRegA: value.requireRegCfOrRegA === true,
    preferredSecurityTypes: normalizeSecurityTypes(value.preferredSecurityTypes),
    weeklyDigestDay: asDigestDay(value.weeklyDigestDay),
    weeklyDigestTime: asString(value.weeklyDigestTime, '09:00'),
    emailRecipient: asString(value.emailRecipient)
  };
}

function normalizeSources(value: unknown): WatchlistSource[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((sourceValue, index): WatchlistSource | null => {
      if (!isRecord(sourceValue)) return null;
      const now = new Date().toISOString();

      return {
        id: Math.trunc(asNumber(sourceValue.id) ?? index + 1),
        sourceType: asSourceType(sourceValue.sourceType),
        dealId: asNumber(sourceValue.dealId),
        url: asString(sourceValue.url),
        companyName: asString(sourceValue.companyName),
        enabled: sourceValue.enabled !== false,
        lastCheckedAt: asString(sourceValue.lastCheckedAt),
        lastStatus: asStatus(sourceValue.lastStatus),
        notes: asString(sourceValue.notes),
        pastedText: asString(sourceValue.pastedText),
        lastError: asString(sourceValue.lastError),
        createdAt: asString(sourceValue.createdAt, now),
        updatedAt: asString(sourceValue.updatedAt, now)
      };
    })
    .filter((source): source is WatchlistSource => Boolean(source));
}

function normalizeSnapshots(value: unknown): DealSnapshot[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((snapshotValue, index): DealSnapshot | null => {
      if (!isRecord(snapshotValue)) return null;
      const now = new Date().toISOString();

      return {
        id: Math.trunc(asNumber(snapshotValue.id) ?? index + 1),
        dealId: Math.trunc(asNumber(snapshotValue.dealId) ?? 0),
        sourceId: Math.trunc(asNumber(snapshotValue.sourceId) ?? 0),
        checkedAt: asString(snapshotValue.checkedAt, now),
        amountRaised: asNumber(snapshotValue.amountRaised),
        investorCount: asNumber(snapshotValue.investorCount),
        deadline: asString(snapshotValue.deadline),
        minimumInvestment: asNumber(snapshotValue.minimumInvestment),
        valuationOrCap: asString(snapshotValue.valuationOrCap),
        securityType: asSecurityType(snapshotValue.securityType),
        offeringExemption: asOfferingExemption(snapshotValue.offeringExemption),
        investorEligibility: asInvestorEligibility(snapshotValue.investorEligibility),
        dataConfidence: Math.max(0, Math.min(100, Math.trunc(asNumber(snapshotValue.dataConfidence) ?? 0))),
        redFlagCount: Math.max(0, Math.trunc(asNumber(snapshotValue.redFlagCount) ?? 0)),
        rawTextHash: asString(snapshotValue.rawTextHash),
        notableChanges: asStringArray(snapshotValue.notableChanges),
        createdAt: asString(snapshotValue.createdAt, now)
      };
    })
    .filter((snapshot): snapshot is DealSnapshot => Boolean(snapshot))
    .sort((a, b) => b.checkedAt.localeCompare(a.checkedAt));
}

function normalizeLogs(value: unknown): ScoutLogEntry[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((logValue, index): ScoutLogEntry | null => {
      if (!isRecord(logValue)) return null;
      const level = logValue.level === 'ERROR' || logValue.level === 'WARN' ? logValue.level : 'INFO';

      return {
        id: Math.trunc(asNumber(logValue.id) ?? index + 1),
        createdAt: asString(logValue.createdAt, new Date().toISOString()),
        level,
        message: asString(logValue.message)
      };
    })
    .filter((log): log is ScoutLogEntry => Boolean(log))
    .slice(0, 40);
}

function getDefaultState(): ScoutState {
  const now = new Date().toISOString();
  return {
    version: 5,
    sources: [],
    snapshots: [],
    preferences: DEFAULT_PREFERENCES,
    lastRunAt: '',
    lastDigestPreview: '',
    logs: [],
    updatedAt: now
  };
}

export function loadScoutState(): ScoutState {
  const raw = window.localStorage.getItem(SCOUT_STORAGE_KEY);
  if (!raw) return getDefaultState();

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!isRecord(parsed)) return getDefaultState();

    return {
      version: 5,
      sources: normalizeSources(parsed.sources),
      snapshots: normalizeSnapshots(parsed.snapshots),
      preferences: normalizePreferences(parsed.preferences),
      lastRunAt: asString(parsed.lastRunAt),
      lastDigestPreview: asString(parsed.lastDigestPreview),
      logs: normalizeLogs(parsed.logs),
      updatedAt: asString(parsed.updatedAt, new Date().toISOString())
    };
  } catch {
    return getDefaultState();
  }
}

export function saveScoutState(state: ScoutState): void {
  window.localStorage.setItem(
    SCOUT_STORAGE_KEY,
    JSON.stringify({
      ...state,
      updatedAt: new Date().toISOString()
    })
  );
}

export function saveScoutPreferences(preferences: ScoutPreferences): ScoutState {
  const state = loadScoutState();
  const updatedState: ScoutState = {
    ...state,
    preferences,
    updatedAt: new Date().toISOString()
  };

  saveScoutState(updatedState);
  return updatedState;
}

export function createScoutSource(input: WatchlistSourceInput): WatchlistSource {
  const state = loadScoutState();
  const now = new Date().toISOString();
  const id = state.sources.reduce((maxId, source) => Math.max(maxId, source.id), 0) + 1;
  const source: WatchlistSource = {
    id,
    ...input,
    lastCheckedAt: '',
    lastStatus: 'NEVER_CHECKED',
    lastError: '',
    createdAt: now,
    updatedAt: now
  };

  saveScoutState({
    ...state,
    sources: [source, ...state.sources],
    updatedAt: now
  });
  return source;
}

export function updateScoutSource(sourceId: number, patch: Partial<WatchlistSource>): WatchlistSource {
  const state = loadScoutState();
  const existing = state.sources.find((source) => source.id === sourceId);
  if (!existing) throw new Error(`Source ${sourceId} not found`);

  const updatedSource: WatchlistSource = {
    ...existing,
    ...patch,
    id: existing.id,
    updatedAt: new Date().toISOString()
  };

  saveScoutState({
    ...state,
    sources: state.sources.map((source) => (source.id === sourceId ? updatedSource : source)),
    updatedAt: updatedSource.updatedAt
  });
  return updatedSource;
}

export function deleteScoutSource(sourceId: number): void {
  const state = loadScoutState();
  saveScoutState({
    ...state,
    sources: state.sources.filter((source) => source.id !== sourceId),
    snapshots: state.snapshots.filter((snapshot) => snapshot.sourceId !== sourceId),
    updatedAt: new Date().toISOString()
  });
}

export function appendScoutLog(level: ScoutLogEntry['level'], message: string): ScoutState {
  const state = loadScoutState();
  const now = new Date().toISOString();
  const id = state.logs.reduce((maxId, log) => Math.max(maxId, log.id), 0) + 1;
  const updatedState: ScoutState = {
    ...state,
    logs: [{ id, createdAt: now, level, message }, ...state.logs].slice(0, 40),
    updatedAt: now
  };

  saveScoutState(updatedState);
  return updatedState;
}
