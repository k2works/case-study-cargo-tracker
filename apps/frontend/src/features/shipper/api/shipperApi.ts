export type ShipperType = 'INDIVIDUAL' | 'CORPORATE';

export interface Shipper {
  shipperId: string;
  shipperType: string;
  name: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  countryCode: string;
  postalCode: string | null;
  email: string;
  phone: string;
  contractNumber: string | null;
  discountRate: number | null;
  active: boolean | null;
}

export interface RegisterShipperRequest {
  shipperType: ShipperType;
  name: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  countryCode: string;
  postalCode?: string | null;
  email: string;
  phone: string;
}

export interface RegisterShipperResponse {
  shipperId: string;
}

function authHeader(): Record<string, string> {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function fetchShippers(): Promise<Shipper[]> {
  const res = await fetch('/api/v1/shippers', {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷主の取得に失敗しました');
  return res.json();
}

export async function fetchShippersByEmail(email: string): Promise<Shipper[]> {
  const res = await fetch(`/api/v1/shippers?email=${encodeURIComponent(email)}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷主の検索に失敗しました');
  return res.json();
}

export async function fetchShipper(shipperId: string): Promise<Shipper> {
  const res = await fetch(`/api/v1/shippers/${shipperId}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷主の取得に失敗しました');
  return res.json();
}

export async function registerShipper(req: RegisterShipperRequest): Promise<RegisterShipperResponse> {
  const res = await fetch('/api/v1/shippers', {
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
