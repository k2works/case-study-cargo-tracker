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
}

export interface BookCargoResponse {
  bookingId: string;
}

function authHeader(): Record<string, string> {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function fetchBookings(): Promise<CargoSummary[]> {
  const res = await fetch('/api/v1/bookings', {
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
