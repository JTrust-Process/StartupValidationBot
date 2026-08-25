import type { Deal } from '../models/deal';

const API_ROOT = '/api/deal-workspaces';

export class DealWorkspaceApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'DealWorkspaceApiError';
    this.status = status;
  }
}

async function request<T>(path = '', options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${API_ROOT}${path}`, {
      ...options,
      headers,
      credentials: 'include',
      cache: 'no-store'
    });
  } catch {
    throw new DealWorkspaceApiError('Deal workspace server is unavailable.', 0);
  }

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(text) as unknown;
    } catch {
      payload = { error: text.slice(0, 300) };
    }
  }
  if (!response.ok) {
    const detail = payload && typeof payload === 'object'
      ? String((payload as { error?: string; detail?: string; message?: string }).error
          || (payload as { detail?: string }).detail
          || (payload as { message?: string }).message
          || '')
      : '';
    throw new DealWorkspaceApiError(detail || `Deal workspace request failed with HTTP ${response.status}.`, response.status);
  }
  return payload as T;
}

export function listDealWorkspaces(): Promise<Deal[]> {
  return request<Deal[]>();
}

export function getDealWorkspace(id: number): Promise<Deal> {
  return request<Deal>(`/${id}`);
}

export function createDealWorkspace(deal: Deal): Promise<Deal> {
  return request<Deal>('', { method: 'POST', body: JSON.stringify(deal) });
}

export function updateDealWorkspace(deal: Deal): Promise<Deal> {
  return request<Deal>(`/${deal.id}`, { method: 'PUT', body: JSON.stringify(deal) });
}

export function deleteDealWorkspace(id: number): Promise<void> {
  return request<void>(`/${id}`, { method: 'DELETE' });
}
