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

/**
 * 公開追跡照会レスポンス（US18 / ADR-0013）。
 * Filter 検証成功時に summary + events を一括返却。
 */
export interface PublicTrackingPayload {
  summary: TrackingSummary;
  events: TrackingEvent[];
}

/**
 * 公開追跡照会の結果型（US18 / ADR-0013）。
 * 200 / 403 / 404 / その他エラーを明示的にハンドリングするため discriminated union を使う。
 */
export type PublicTrackingResult =
  | { type: 'success'; payload: PublicTrackingPayload }
  | { type: 'forbidden'; message: string }
  | { type: 'not_found' }
  | { type: 'error'; message: string };

/**
 * 公開追跡照会（US18）。token クエリパラメータで JWT を渡し、
 * バックエンドの PublicTrackingTokenFilter で検証される。403 / 404 を明示的に分岐する。
 */
export async function fetchPublicTracking(
  trackingNumber: string,
  token: string,
): Promise<PublicTrackingResult> {
  if (!token || token.trim() === '') {
    return { type: 'forbidden', message: 'トークンが指定されていません' };
  }
  try {
    const res = await fetch(
      `/api/v1/public/tracking/${trackingNumber}?token=${encodeURIComponent(token)}`,
    );
    if (res.status === 403) {
      return { type: 'forbidden', message: 'リンクの有効期限が切れています。担当者に再発行を依頼してください' };
    }
    if (res.status === 404) {
      return { type: 'not_found' };
    }
    if (!res.ok) {
      return { type: 'error', message: `照会に失敗しました（HTTP ${res.status}）` };
    }
    const payload = (await res.json()) as PublicTrackingPayload;
    return { type: 'success', payload };
  } catch (e) {
    return { type: 'error', message: e instanceof Error ? e.message : '通信エラー' };
  }
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

// --- IT6 タスク 2.3 / 2.4：US19 / US20 例外管理 ---

/** 追跡例外種別（US19 / US20）。 */
export type ExceptionType = 'DELAY' | 'DAMAGE' | 'LOSS';

/** 例外対応状態（US19 / US20）。 */
export type ResponseStatus = 'REPORTED' | 'RESPONDING' | 'RESOLVED';

export interface TrackingExceptionView {
  exceptionId: string;
  trackingNumber: string;
  type: ExceptionType;
  occurredAt: string;
  occurredUnlocode: string | null;
  description: string;
  responseStatus: ResponseStatus;
  resolution: string | null;
  resolvedAt: string | null;
  escalated: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RegisterExceptionRequestBody {
  type: ExceptionType;
  occurredAt: string;
  occurredUnlocode: string | null;
  description: string;
}

/**
 * 例外を登録する（US19 / US20）。LOSS の場合は escalated=true でレスポンスされる。
 */
export async function registerException(
  trackingNumber: string,
  body: RegisterExceptionRequestBody,
): Promise<TrackingExceptionView> {
  const res = await fetch(`/api/v1/tracking/${trackingNumber}/exceptions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '例外登録に失敗しました' }));
    throw new Error(err.message ?? '例外登録に失敗しました');
  }
  return res.json();
}

/**
 * 例外を解決済みに遷移させる（US19 受入基準 4/5、US20 受入基準 5）。
 */
export async function resolveException(
  trackingNumber: string,
  exceptionId: string,
  resolution: string,
): Promise<void> {
  const res = await fetch(
    `/api/v1/tracking/${trackingNumber}/exceptions/${exceptionId}/resolve`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', ...authHeader() },
      body: JSON.stringify({ resolution }),
    },
  );
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '例外解決に失敗しました' }));
    throw new Error(err.message ?? '例外解決に失敗しました');
  }
}

export async function fetchExceptionsByTrackingNumber(
  trackingNumber: string,
): Promise<TrackingExceptionView[]> {
  const res = await fetch(`/api/v1/tracking/${trackingNumber}/exceptions`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('例外一覧の取得に失敗しました');
  return res.json();
}

export async function fetchAllExceptions(
  responseStatus: ResponseStatus | 'ALL' = 'ALL',
  page = 0,
  size = 20,
): Promise<PageResponse<TrackingExceptionView>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (responseStatus !== 'ALL') {
    params.set('responseStatus', responseStatus);
  }
  const res = await fetch(`/api/v1/tracking/exceptions?${params.toString()}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('例外横断一覧の取得に失敗しました');
  return res.json();
}

/** 例外種別ラベル。 */
export function exceptionTypeLabel(type: ExceptionType): string {
  switch (type) {
    case 'DELAY':
      return '遅延';
    case 'DAMAGE':
      return '破損';
    case 'LOSS':
      return '紛失';
    default:
      return type;
  }
}

/** 対応状態ラベル。 */
export function responseStatusLabel(status: ResponseStatus): string {
  switch (status) {
    case 'REPORTED':
      return '未対応';
    case 'RESPONDING':
      return '対応中';
    case 'RESOLVED':
      return '解決済';
    default:
      return status;
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
  // H5: MISROUTED から正常状態への救済動線（再経路設計 / 緊急輸送による復帰）
  MISROUTED: ['RECEIVED', 'LOADED', 'IN_TRANSIT'],
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
