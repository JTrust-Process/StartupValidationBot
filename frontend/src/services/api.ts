import type {
  CreateDealRequest,
  DealResponse,
  DecisionRequest,
  DeepDiligenceRequest,
  QuickScreenRequest,
  ReviewRequest
} from '../models/api';

const API_BASE_URL = 'http://localhost:8080/api';

async function request<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();

  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export async function getDeals(): Promise<DealResponse[]> {
  return request<DealResponse[]>(`${API_BASE_URL}/deals`);
}

export async function getDealById(dealId: number): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}`);
}

export async function createDeal(payload: CreateDealRequest): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export async function updateDeal(
  dealId: number,
  payload: CreateDealRequest
): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function deleteDeal(dealId: number): Promise<void> {
  return request<void>(`${API_BASE_URL}/deals/${dealId}`, {
    method: 'DELETE'
  });
}

export async function saveQuickScreen(
  dealId: number,
  payload: QuickScreenRequest
): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}/quick-screen`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function saveDecision(
  dealId: number,
  payload: DecisionRequest
): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}/decision`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function saveDeepDiligence(
  dealId: number,
  payload: DeepDiligenceRequest
): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}/deep-diligence`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export async function saveReview(
  dealId: number,
  payload: ReviewRequest
): Promise<DealResponse> {
  return request<DealResponse>(`${API_BASE_URL}/deals/${dealId}/review`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}