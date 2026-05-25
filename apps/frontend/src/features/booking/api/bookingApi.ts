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

/**
 * 予約一覧（後方互換、items のみ）。
 *
 * <p>新規コードは {@link fetchBookingsPage} を使用すること。</p>
 */
export async function fetchBookings(): Promise<CargoSummary[]> {
  const page = await fetchBookingsPage(0, 200);
  return page.items;
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
