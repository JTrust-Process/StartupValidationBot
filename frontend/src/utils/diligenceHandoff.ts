import type { SecurityType } from '../models/deal';

export function diligenceSecurityType(value: string | null): SecurityType {
  switch (value?.trim().toLowerCase()) {
    case 'safe': case 'crowd safe': return 'SAFE';
    case 'convertible note': case 'debt': return 'NOTE';
    case 'common stock': case 'preferred stock': case 'equity': return 'EQUITY';
    case 'revenue share': return 'REVENUE_SHARE';
    case 'token': return 'OTHER';
    default: return 'UNKNOWN';
  }
}
