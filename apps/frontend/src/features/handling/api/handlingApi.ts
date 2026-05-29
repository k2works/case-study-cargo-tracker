import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

export type HandlingType = 'RECEIVE' | 'LOAD' | 'UNLOAD' | 'CLAIM' | 'CUSTOMS';

export interface HandlingActivity {
  activityId: string;
  bookingId: string | null;
  trackingNumber: string;
  originUnlocode: string | null;
  destinationUnlocode: string | null;
  cargoType: string | null;
  handlingType: HandlingType;
  occurredAt: string;
  recordedAt: string;
  unlocode: string;
  voyageNumber: string | null;
  handlerId: string;
  unexpected: boolean;
}

export interface ClaimVerificationRequest {
  consigneeName: string;
  signatureRef?: string | null;
  confirmationCode?: string | null;
  verifiedAt: string;
}

export interface RegisterHandlingActivityRequest {
  activityId?: string | null;
  trackingNumber: string;
  handlingType: HandlingType;
  occurredAt: string;
  unlocode: string;
  voyageNumber?: string | null;
  handlerId: string;
  claimVerification?: ClaimVerificationRequest | null;
}

export interface RegisterHandlingActivityResponse {
  activityId: string;
}

export async function registerHandling(
  req: RegisterHandlingActivityRequest,
): Promise<RegisterHandlingActivityResponse> {
  const res = await fetch('/api/v1/handling', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '登録に失敗しました' }));
    throw new Error(err.message ?? '登録に失敗しました');
  }
  return res.json();
}

export async function fetchHandlingPage(
  page = 0,
  size = 20,
): Promise<PageResponse<HandlingActivity>> {
  const res = await fetch(`/api/v1/handling?page=${page}&size=${size}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷役履歴の取得に失敗しました');
  return res.json();
}

export async function fetchHandlingByTrackingNumber(
  trackingNumber: string,
): Promise<HandlingActivity[]> {
  const res = await fetch(
    `/api/v1/handling?trackingNumber=${encodeURIComponent(trackingNumber)}`,
    { headers: authHeader() },
  );
  if (!res.ok) throw new Error('荷役履歴の取得に失敗しました');
  const page = (await res.json()) as PageResponse<HandlingActivity>;
  return page.items;
}

export function handlingTypeLabel(type: HandlingType): string {
  switch (type) {
    case 'RECEIVE':
      return '受領';
    case 'LOAD':
      return '積込';
    case 'UNLOAD':
      return '荷降し';
    case 'CLAIM':
      return '引取';
    case 'CUSTOMS':
      return '税関通過';
    default:
      return type;
  }
}

/** LOAD / UNLOAD では航海番号必須（バックエンドの不変条件と整合）。 */
export function requiresVoyageNumber(type: HandlingType): boolean {
  return type === 'LOAD' || type === 'UNLOAD';
}

/** CLAIM では荷受人確認必須（バックエンドの不変条件と整合）。 */
export function requiresClaimVerification(type: HandlingType): boolean {
  return type === 'CLAIM';
}
