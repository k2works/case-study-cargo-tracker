export interface RouteCandidateInput {
  itinerarySummary: string;
  estimatedDays: number;
  estimatedCost: number;
  estimatedCurrency: string;
}

export interface CreateQuotationRequest {
  shipperId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string; // YYYY-MM-DD
  cargoType: string;
  weightKg: number;
  productName?: string;
  validUntil: string; // YYYY-MM-DD
  candidates: RouteCandidateInput[];
}

export interface QuotationCandidate {
  candidateSeq: number;
  estimatedDays: number;
  estimatedCost: number;
  estimatedCurrency: string;
  itinerarySummary: string;
}

export interface Quotation {
  quotationId: string;
  shipperId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string;
  cargoType: string;
  weightKg: number | null;
  estimatedAmount: number | null;
  estimatedCurrency: string | null;
  validUntil: string;
  status: string;
  candidates: QuotationCandidate[];
}

export interface PageResponse<T> {
  items: T[];
  totalCount: number;
  page: number;
  size: number;
}

function authHeader(): Record<string, string> {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * 輸送見積を作成する（US01）。
 *
 * <p>ルート候補（US07 航海検索結果から選択）を渡すと、サーバー側で最安費用を概算金額とする。</p>
 */
export async function createQuotation(req: CreateQuotationRequest): Promise<{ quotationId: string }> {
  const res = await fetch('/api/v1/quotes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '見積の作成に失敗しました' }));
    throw new Error(err.message ?? '見積の作成に失敗しました');
  }
  return res.json();
}

/**
 * 見積一覧（ページネーション対応、US01 / ADR-0008）。
 */
export async function fetchQuotationsPage(page = 0, size = 20): Promise<PageResponse<Quotation>> {
  const res = await fetch(`/api/v1/quotes?page=${page}&size=${size}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('見積一覧の取得に失敗しました');
  return res.json();
}

/**
 * 見積詳細を取得する（US01）。
 */
export async function fetchQuotation(quotationId: string): Promise<Quotation> {
  const res = await fetch(`/api/v1/quotes/${quotationId}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('見積の取得に失敗しました');
  return res.json();
}
