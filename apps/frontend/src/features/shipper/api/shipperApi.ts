import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

export type { PageResponse } from '../../../shared/api/types';

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
  contractNumber?: string | null;
  discountRate?: number | null;
}

export interface RegisterShipperResponse {
  shipperId: string;
}

/**
 * 荷主一覧（ページネーション対応）。
 *
 * @param page 0 始まりのページ番号
 * @param size 1 ページあたり件数
 */
export async function fetchShippersPage(page = 0, size = 20): Promise<PageResponse<Shipper>> {
  const res = await fetch(`/api/v1/shippers?page=${page}&size=${size}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('荷主の取得に失敗しました');
  return res.json();
}

export async function fetchShippersByEmail(email: string): Promise<Shipper[]> {
  const res = await fetch(`/api/v1/shippers/search?email=${encodeURIComponent(email)}`, {
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
