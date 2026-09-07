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
  /** 重複の問いかけに「続ける」と答えたか。省略は続行の意思なし。 */
  readonly acknowledgedDuplicate?: boolean;
}

/**
 * 荷主の一覧（S10）。**名前で絞り込める**。
 *
 * <p>一覧は荷主コード順で上限があるので、新しく採った荷主ほど後ろに回る。
 * 件数が上限を超えると 1 ページ目には出ず、<b>予約登録の選択肢にも出ない</b>ので、
 * 登録したその日からその荷主の予約が取れなくなる（IT8 のクラスタで実測）。</p>
 */
export function fetchShippers(
  q = '',
): Promise<Pending<{ items: ShipperView[]; total: number }>> {
  const query = new URLSearchParams({ page: '0', size: '200' });
  if (q.trim() !== '') {
    query.set('q', q.trim());
  }
  return queryClient(`/booking/shippers?${query.toString()}`);
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
