import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

export interface ShipperView {
  readonly shipperId: string;
  readonly shipperCode: string;
  readonly shipperType: 'INDIVIDUAL' | 'CORPORATE';
  /** 鍵を破棄した荷主は null になる（ADR-0003）。画面は「（削除済み）」と出す。 */
  readonly name: string | null;
  readonly email: string | null;
  readonly phone: string | null;
  readonly address: string | null;
  readonly contractNumber: string | null;
  readonly discountRate: string | null;
}

export interface RegisterShipperInput {
  readonly name: string;
  readonly shipperType: 'INDIVIDUAL' | 'CORPORATE';
  readonly email: string;
  readonly phone?: string;
  readonly address?: string;
  readonly contractNumber?: string;
  readonly discountRate?: string;
}

export function fetchShippers(): Promise<Pending<{ items: ShipperView[] }>> {
  return queryClient('/booking/shippers?page=0&size=200');
}

export function fetchShipper(shipperId: string): Promise<Pending<ShipperView>> {
  return queryClient(`/booking/shippers/${encodeURIComponent(shipperId)}`);
}

export function registerShipper(input: RegisterShipperInput): Promise<{ shipperId: string }> {
  return commandClient('/booking/shippers', input);
}

/** 削除済みの個人情報の見せ方。ここに集めて、画面ごとに書き分けない。 */
export const REDACTED = '（削除済み）';

export function display(value: string | null): string {
  return value ?? REDACTED;
}
