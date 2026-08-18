import type {
  RadarAnalysis,
  RadarCompany,
  RadarAdminCompany,
  RadarAdminCompanyDetail,
  RadarAdminSession,
  RadarCompanyDetail,
  RadarCompanyFilters,
  RadarSource,
  RadarTrend,
  RadarSystemStatus,
  RadarFixtureResult,
  RadarJobResult
} from '../models/radar';

function resolveRadarApiBase(): string {
  const configured = (import.meta.env.VITE_RADAR_API_BASE_URL || '/api/radar').replace(/\/+$/, '');
  if (import.meta.env.PROD && /^https?:\/\//i.test(configured)
      && new URL(configured).origin !== window.location.origin) {
    return '/api/radar';
  }
  return configured;
}

const RADAR_API_BASE = resolveRadarApiBase();

export class RadarApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'RadarApiError';
    this.status = status;
  }
}

export function getRadarApiBase(): string {
  return RADAR_API_BASE;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${RADAR_API_BASE}${path}`, {
      ...options,
      headers,
      credentials: 'include',
      cache: 'no-store'
    });
  } catch {
    throw new RadarApiError('Radar server is unavailable. Check VITE_RADAR_API_BASE_URL and the backend deployment.', 0);
  }
  const text = await response.text();
  let payload: { error?: string } | null = null;
  if (text) {
    try {
      payload = JSON.parse(text) as { error?: string };
    } catch {
      payload = { error: text.slice(0, 300) };
    }
  }
  if (!response.ok) {
    throw new RadarApiError(payload?.error || `Radar request failed with HTTP ${response.status}.`, response.status);
  }
  return payload as T;
}

export function listRadarCompanies(filters: RadarCompanyFilters = {}): Promise<RadarCompany[]> {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '' && value !== null) params.set(key, String(value));
  });
  const query = params.size ? `?${params.toString()}` : '';
  return request<RadarCompany[]>(`/companies${query}`);
}

export function getRadarCompany(companyId: number): Promise<RadarCompanyDetail> {
  return request<RadarCompanyDetail>(`/companies/${companyId}`);
}

export function listRadarSources(): Promise<RadarSource[]> {
  return request<RadarSource[]>('/sources');
}

export function listRadarTrends(): Promise<RadarTrend[]> {
  return request<RadarTrend[]>('/trends');
}

export function getRadarAdminSession(): Promise<RadarAdminSession> {
  return request<RadarAdminSession>('/auth/session');
}

export function loginRadarAdmin(password: string): Promise<RadarAdminSession> {
  return request<RadarAdminSession>('/auth/login', { method: 'POST', body: JSON.stringify({ password }) });
}

export function logoutRadarAdmin(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' });
}

export function listRadarAdminCompanies(filters: RadarCompanyFilters & { watched?: boolean } = {}): Promise<RadarAdminCompany[]> {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '' && value !== null) params.set(key, String(value));
  });
  return request<RadarAdminCompany[]>(`/admin/companies${params.size ? `?${params.toString()}` : ''}`);
}

export function getRadarAdminCompany(companyId: number): Promise<RadarAdminCompanyDetail> {
  return request<RadarAdminCompanyDetail>(`/admin/companies/${companyId}`);
}

export function listRadarAdminSources(): Promise<Array<RadarSource & { url: string | null }>> {
  return request<Array<RadarSource & { url: string | null }>>('/admin/sources');
}

export function getRadarSystemStatus(): Promise<RadarSystemStatus> {
  return request<RadarSystemStatus>('/admin/status');
}

export function watchRadarCompany(companyId: number, notes = '', nextReviewAt: string | null = null): Promise<RadarAdminCompanyDetail> {
  return request<RadarAdminCompanyDetail>(`/companies/${companyId}/watch`, {
    method: 'PUT',
    body: JSON.stringify({ notes, nextReviewAt })
  });
}

export function unwatchRadarCompany(companyId: number): Promise<void> {
  return request<void>(`/companies/${companyId}/watch`, { method: 'DELETE' });
}

export function setRadarCompanyIgnored(companyId: number, ignored: boolean): Promise<RadarAdminCompany> {
  return request<RadarAdminCompany>(`/companies/${companyId}/ignore?ignored=${ignored}`, { method: 'PUT' });
}

export function runRadarDeepDive(companyId: number): Promise<RadarAnalysis> {
  return request<RadarAnalysis>(`/companies/${companyId}/deep-dive`, { method: 'POST' });
}

export function upsertRadarSource(input: {
  sourceKey: string;
  sourceType: string;
  name: string;
  url: string | null;
  enabled: boolean;
}): Promise<RadarSource & { url: string | null }> {
  return request<RadarSource & { url: string | null }>('/sources', { method: 'POST', body: JSON.stringify(input) });
}

export function addManualRadarCompany(input: {
  companyName: string;
  websiteUrl: string;
  description: string;
  sector: string;
  sourceUrl: string;
}): Promise<RadarAdminCompany> {
  return request<RadarAdminCompany>('/companies/manual', { method: 'POST', body: JSON.stringify(input) });
}

export function runRadarJob(jobType: string): Promise<RadarJobResult> {
  return request<RadarJobResult>(`/jobs/${encodeURIComponent(jobType)}`, { method: 'POST', body: '{}' });
}

export function seedRadarDemoFixture(): Promise<RadarFixtureResult> {
  return request<RadarFixtureResult>('/admin/fixtures/synthetic', { method: 'POST' });
}

export async function downloadRadarExport(): Promise<void> {
  const response = await fetch(`${RADAR_API_BASE}/admin/export`, {
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) {
    let message = `Radar export failed with HTTP ${response.status}.`;
    try {
      const payload = await response.json() as { error?: string };
      if (payload.error) message = payload.error;
    } catch {
      // Keep the HTTP fallback.
    }
    throw new RadarApiError(message, response.status);
  }
  const blob = await response.blob();
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'startup-radar-export.json';
  link.click();
  URL.revokeObjectURL(link.href);
}
