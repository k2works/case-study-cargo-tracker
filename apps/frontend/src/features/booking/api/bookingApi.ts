import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

export type { PageResponse } from '../../../shared/api/types';

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED';

export interface CargoSummary {
  bookingId: string;
  shipperId: string;
  trackingNumber: string | null;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string;
  cargoType: string;
  weightKg: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  quantity: number | null;
  productName: string | null;
  hazardImoClass: string | null;
  hazardUnNumber: string | null;
  hazardDeclaration: string | null;
  temperatureMinC: number | null;
  temperatureMaxC: number | null;
  bookingStatus: string;
  routingStatus: string;
  estimatedAmount: number | null;
  estimatedCurrency: string | null;
  routeNotifiedAt: string | null;
}

/** 確定旅程の輸送区間（US11/US12）。 */
export interface CargoLeg {
  legSeq: number;
  voyageNumber: string;
  loadUnlocode: string;
  unloadUnlocode: string;
  loadAt: string;
  unloadAt: string;
}

export interface BookCargoRequest {
  shipperId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: string; // YYYY-MM-DD
  cargoType: CargoType;
  weightKg: number;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
  quantity: number;
  productName: string;
  hazardImoClass?: string | null;
  hazardUnNumber?: string | null;
  hazardDeclaration?: string | null;
  temperatureMinC?: number | null;
  temperatureMaxC?: number | null;
}

export interface BookCargoResponse {
  bookingId: string;
}

/**
 * 予約一覧（ページネーション対応、IT2）。
 *
 * @param page 0 始まりのページ番号
 * @param size 1 ページあたり件数
 */
export async function fetchBookingsPage(page = 0, size = 20): Promise<PageResponse<CargoSummary>> {
  const res = await fetch(`/api/v1/bookings?page=${page}&size=${size}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('予約一覧の取得に失敗しました');
  return res.json();
}

export async function fetchBooking(bookingId: string): Promise<CargoSummary> {
  const res = await fetch(`/api/v1/bookings/${bookingId}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('予約の取得に失敗しました');
  return res.json();
}

export async function bookCargo(req: BookCargoRequest): Promise<BookCargoResponse> {
  const res = await fetch('/api/v1/bookings', {
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

/** 経路設計引き渡し（US06、仮受付 → 経路設計中）。 */
export async function handoffBooking(bookingId: string): Promise<void> {
  const res = await fetch(`/api/v1/bookings/${bookingId}/handoff`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('経路設計の依頼に失敗しました');
}

/** 予約確定（US13、経路提案中 → 予約確定）。 */
export async function confirmBooking(bookingId: string): Promise<void> {
  const res = await fetch(`/api/v1/bookings/${bookingId}/confirm`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('予約確定に失敗しました');
}

/** 予約キャンセル（US13）。 */
export async function cancelBooking(bookingId: string): Promise<void> {
  const res = await fetch(`/api/v1/bookings/${bookingId}/cancel`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('予約キャンセルに失敗しました');
}

/** 確定旅程を取得する（US11/US12、経路提案中以降）。 */
export async function fetchRoute(bookingId: string): Promise<CargoLeg[]> {
  const res = await fetch(`/api/v1/bookings/${bookingId}/route`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('確定経路の取得に失敗しました');
  return res.json();
}

/** 確定経路の荷主通知（US12、経路提案中のみ）。 */
export async function notifyRoute(bookingId: string): Promise<void> {
  const res = await fetch(`/api/v1/bookings/${bookingId}/notify-route`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷主への経路通知に失敗しました');
}
