import { authHeader } from '../../../shared/api/auth';

/** 経路設計待ちリストの 1 件（route_design_request、US06/US08）。 */
export interface RouteDesignRequest {
  bookingId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string; // YYYY-MM-DD
  cargoType: string;
  status: string;
  requestedAt: string;
}

/** 経路候補を構成する輸送区間。 */
export interface RouteLeg {
  voyageNumber: string;
  loadUnlocode: string;
  unloadUnlocode: string;
  loadTime: string;
  unloadTime: string;
}

/** 経路候補（US08、推奨順）。 */
export interface RouteCandidate {
  sequence: number;
  legs: RouteLeg[];
  voyageNumbers: string[];
  ports: string[];
  estimatedDays: number;
  estimatedCost: number;
  currency: string;
  direct: boolean;
  recommended: boolean;
}

/** 経路設計待ちリストを取得する（US08）。到着期限の昇順に並べ替える（レビュー L4）。 */
export async function fetchRouteDesignRequests(): Promise<RouteDesignRequest[]> {
  const res = await fetch('/api/v1/routes/design-requests', { headers: authHeader() });
  if (!res.ok) throw new Error('経路設計待ちリストの取得に失敗しました');
  const list: RouteDesignRequest[] = await res.json();
  return [...list].sort((a, b) => a.arrivalDeadline.localeCompare(b.arrivalDeadline));
}

/** 経路設計依頼を 1 件取得する（US08）。 */
export async function fetchRouteDesignRequest(bookingId: string): Promise<RouteDesignRequest> {
  const res = await fetch(`/api/v1/routes/design-requests/${bookingId}`, { headers: authHeader() });
  if (!res.ok) throw new Error('経路設計依頼の取得に失敗しました');
  return res.json();
}

/**
 * 経路候補を算出する（US08）。期限内到達可能な候補がない場合は空配列。
 * {@code overrideDeadline}（YYYY-MM-DD）を指定すると、その到着期限で再算出する（US10 条件調整）。
 */
export async function calculateRoutes(
  bookingId: string,
  overrideDeadline?: string
): Promise<RouteCandidate[]> {
  const hasOverride = overrideDeadline != null && overrideDeadline !== '';
  const res = await fetch(`/api/v1/routes/${bookingId}/calculate`, {
    method: 'POST',
    headers: hasOverride ? { 'Content-Type': 'application/json', ...authHeader() } : authHeader(),
    body: hasOverride ? JSON.stringify({ arrivalDeadline: overrideDeadline }) : undefined,
  });
  if (!res.ok) throw new Error('経路候補の算出に失敗しました');
  return res.json();
}

/** 経路候補を選択して確定する（US09）。 */
export async function selectRoute(bookingId: string, sequence: number): Promise<RouteCandidate> {
  const res = await fetch(`/api/v1/routes/${bookingId}/select`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ sequence }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '経路の確定に失敗しました' }));
    throw new Error(err.message ?? '経路の確定に失敗しました');
  }
  return res.json();
}

/** 確定経路を予約に紐付ける（US11、RouteConfirmedEvent 発行）。 */
export async function confirmRoute(bookingId: string, sequence: number): Promise<void> {
  const res = await fetch(`/api/v1/routes/${bookingId}/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ sequence }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '経路の予約紐付けに失敗しました' }));
    throw new Error(err.message ?? '経路の予約紐付けに失敗しました');
  }
}
