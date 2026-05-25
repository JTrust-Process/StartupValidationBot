import type {
  DealDocumentType,
  DealDecision,
  EvidenceSourceType,
  EvidenceStrength,
  InvestorEligibility,
  LiquidityProfile,
  OfferingExemption,
  RevenueStatus,
  SecurityType,
  ThesisDirection
} from '../models/deal';

const DEAL_DECISION_LABELS: Record<DealDecision, string> = {
  PASS: 'Pass',
  WATCH: 'Watch',
  INVEST_SMALL: 'Invest Small'
};

const INVESTOR_ELIGIBILITY_LABELS: Record<InvestorEligibility, string> = {
  NON_ACCREDITED: 'Non-accredited',
  ACCREDITED_ONLY: 'Accredited only',
  UNCLEAR: 'Unclear'
};

const OFFERING_EXEMPTION_LABELS: Record<OfferingExemption, string> = {
  REG_CF: 'Reg CF',
  REG_A: 'Reg A',
  REG_D: 'Reg D',
  UNKNOWN: 'Unknown',
  OTHER: 'Other'
};

const SECURITY_TYPE_LABELS: Record<SecurityType, string> = {
  SAFE: 'SAFE',
  EQUITY: 'Equity',
  NOTE: 'Note',
  REVENUE_SHARE: 'Revenue Share',
  FUND_INTEREST: 'Fund Interest',
  SPV: 'SPV',
  OTHER: 'Other',
  UNKNOWN: 'Unknown'
};

const LIQUIDITY_LABELS: Record<LiquidityProfile, string> = {
  ILLIQUID: 'Illiquid',
  REDEMPTION_WINDOW: 'Redemption window',
  SECONDARY_POSSIBLE: 'Secondary possible',
  UNKNOWN: 'Unknown'
};

const REVENUE_STATUS_LABELS: Record<RevenueStatus, string> = {
  PRE_REVENUE: 'No revenue / pre-revenue',
  EARLY_REVENUE: 'Early revenue',
  REVENUE: 'Revenue',
  UNCLEAR: 'Unclear'
};

const THESIS_DIRECTION_LABELS: Record<ThesisDirection, string> = {
  STRONGER: 'Stronger',
  WEAKER: 'Weaker',
  UNCHANGED: 'Unchanged'
};

const EVIDENCE_SOURCE_TYPE_LABELS: Record<EvidenceSourceType, string> = {
  CAMPAIGN_PAGE: 'Campaign Page',
  FORM_C: 'Form C',
  FORM_CA: 'Form C-A',
  OFFERING_CIRCULAR: 'Offering Circular',
  SAFE_AGREEMENT: 'SAFE Agreement',
  SUBSCRIPTION_AGREEMENT: 'Subscription Agreement',
  INVESTOR_DECK: 'Investor Deck',
  FOUNDER_STATEMENT: 'Founder Statement',
  PRESS: 'Press',
  USER_NOTE: 'User Note',
  OTHER: 'Other'
};

const DEAL_DOCUMENT_TYPE_LABELS: Record<DealDocumentType, string> = {
  CAMPAIGN_PAGE: 'Campaign Page',
  FORM_C: 'Form C',
  FORM_CA: 'Form C-A',
  OFFERING_CIRCULAR: 'Offering Circular',
  SAFE_AGREEMENT: 'SAFE Agreement',
  SUBSCRIPTION_AGREEMENT: 'Subscription Agreement',
  INVESTOR_DECK: 'Investor Deck',
  PRESS: 'Press',
  USER_NOTE: 'User Note',
  OTHER: 'Other'
};

const EVIDENCE_STRENGTH_LABELS: Record<EvidenceStrength, string> = {
  STRONG: 'Strong',
  MEDIUM: 'Medium',
  WEAK: 'Weak',
  MISSING: 'Missing'
};

export function formatDealStatus(status: DealDecision | string): string {
  return DEAL_DECISION_LABELS[status as DealDecision] ?? formatEnumLike(status);
}

export function formatInvestorEligibility(value: InvestorEligibility | string): string {
  return INVESTOR_ELIGIBILITY_LABELS[value as InvestorEligibility] ?? formatEnumLike(value);
}

export function formatOfferingExemption(value: OfferingExemption | string): string {
  return OFFERING_EXEMPTION_LABELS[value as OfferingExemption] ?? formatEnumLike(value);
}

export function formatSecurityType(value: SecurityType | string): string {
  return SECURITY_TYPE_LABELS[value as SecurityType] ?? formatEnumLike(value);
}

export function formatLiquidity(value: LiquidityProfile | string): string {
  return LIQUIDITY_LABELS[value as LiquidityProfile] ?? formatEnumLike(value);
}

export function formatRevenueStatus(value: RevenueStatus | string): string {
  return REVENUE_STATUS_LABELS[value as RevenueStatus] ?? formatEnumLike(value);
}

export function formatThesisDirection(direction: ThesisDirection | string): string {
  return THESIS_DIRECTION_LABELS[direction as ThesisDirection] ?? formatEnumLike(direction);
}

export function formatEvidenceSourceType(value: EvidenceSourceType | string): string {
  return EVIDENCE_SOURCE_TYPE_LABELS[value as EvidenceSourceType] ?? formatEnumLike(value);
}

export function formatEvidenceStrength(value: EvidenceStrength | string): string {
  return EVIDENCE_STRENGTH_LABELS[value as EvidenceStrength] ?? formatEnumLike(value);
}

export function formatDealDocumentType(value: DealDocumentType | string): string {
  return DEAL_DOCUMENT_TYPE_LABELS[value as DealDocumentType] ?? formatEnumLike(value);
}

export function formatCurrency(value: number | null | undefined): string {
  if (typeof value !== 'number') return '-';

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  }).format(value);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).format(date);
}

function formatEnumLike(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}
