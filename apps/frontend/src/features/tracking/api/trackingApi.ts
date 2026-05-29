import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

/** TransportStatus（domain-model.md / iteration_plan-5.md US17 / 9 値）。 */
export type TransportStatus =
  | 'NOT_RECEIVED'
  | 'RECEIVED'
  | 'LOADED'
  | 'IN_TRANSIT'
  | 'UNLOADED'
  | 'AWAITING_CLAIM'
  | 'DELIVERED'
  | 'MISROUTED'
  | 'EXCEPTION';

export interface TrackingSummary {
  trackingNumber: string;
  bookingId: string;
  currentStatus: TransportStatus;
  currentUnlocode: string | null;
  currentVoyageNumber: string | null;
  estimatedArrival: string | null;
  misrouted: boolean;
  lastEventAt: string | null;
  deliveredAt: string | null;
}

export interface TrackingEvent {
  eventId: number;
  occurredAt: string;
  recordedAt: string;
  eventType: string;
  transportStatus: TransportStatus | null;
  unlocode: string | null;
  voyageNumber: string | null;
  handlingType: string | null;
  source: 'SYSTEM' | 'MANUAL' | 'HANDLING' | null;
  description: string | null;
}

export interface UpdateTransportStatusRequest {
  toStatus: TransportStatus;
  unlocode?: string | null;
  voyageNumber?: string | null;
  occurredAt: string; // ISO LocalDateTime（YYYY-MM-DDTHH:mm 等）
  description?: string | null;
}

/**
 * 追跡管理一覧（US17 / IT5 2.4、ページネーション）。
 */
export async function fetchTrackingPage(page = 0, size = 20): Promise<PageResponse<TrackingSummary>> {
  const res = await fetch(`/api/v1/tracking?page=${page}&size=${size}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('追跡一覧の取得に失敗しました');
  return res.json();
}

export async function fetchTracking(trackingNumber: string): Promise<TrackingSummary> {
  const res = await fetch(`/api/v1/tracking/${trackingNumber}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('追跡情報の取得に失敗しました');
  return res.json();
}

export async function fetchTrackingEvents(trackingNumber: string): Promise<TrackingEvent[]> {
  const res = await fetch(`/api/v1/tracking/${trackingNumber}/events`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('追跡履歴の取得に失敗しました');
  return res.json();
}

export async function updateTrackingStatus(
  trackingNumber: string,
  req: UpdateTransportStatusRequest,
): Promise<void> {
  const res = await fetch(`/api/v1/tracking/${trackingNumber}/status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '状態更新に失敗しました' }));
    throw new Error(err.message ?? '状態更新に失敗しました');
  }
}

/**
 * 状態遷移ガード（バックエンド TransportStatusTransition と整合）。
 * 許可遷移のみ選択肢に出すために UI で使用する（IT5 2.4）。
 */
const ALLOWED_TRANSITIONS: Record<TransportStatus, TransportStatus[]> = {
  NOT_RECEIVED: ['RECEIVED', 'MISROUTED', 'EXCEPTION'],
  RECEIVED: ['LOADED', 'MISROUTED', 'EXCEPTION'],
  LOADED: ['IN_TRANSIT', 'MISROUTED', 'EXCEPTION'],
  IN_TRANSIT: ['UNLOADED', 'MISROUTED', 'EXCEPTION'],
  UNLOADED: ['LOADED', 'AWAITING_CLAIM', 'MISROUTED', 'EXCEPTION'],
  AWAITING_CLAIM: ['DELIVERED', 'EXCEPTION'],
  DELIVERED: [],
  MISROUTED: [],
  EXCEPTION: ['RECEIVED', 'LOADED', 'IN_TRANSIT'],
};

export function allowedNextStatuses(from: TransportStatus): TransportStatus[] {
  return ALLOWED_TRANSITIONS[from] ?? [];
}

export function transportStatusLabel(status: TransportStatus): string {
  switch (status) {
    case 'NOT_RECEIVED':
      return '未受領';
    case 'RECEIVED':
      return '受領済';
    case 'LOADED':
      return '積込済';
    case 'IN_TRANSIT':
      return '輸送中';
    case 'UNLOADED':
      return '荷降し済';
    case 'AWAITING_CLAIM':
      return '引取待ち';
    case 'DELIVERED':
      return '配送完了';
    case 'MISROUTED':
      return '誤配送';
    case 'EXCEPTION':
      return '例外発生';
    default:
      return status;
  }
}
