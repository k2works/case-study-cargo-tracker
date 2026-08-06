/**
 * 入金確認完了イベントの契約（共有カーネル）。
 * 発行側（Billing）と購読側（Booking）で型を 1 箇所に集約し、ペイロードの手書き重複を排除する。
 * 入金確認は精算完了を意味し、予約を DELIVERED → SETTLED へ遷移させる（US23 受入基準 4）。
 */

/** 入金確認完了イベント名（EventEmitter2 のイベントキー） */
export const PAYMENT_CONFIRMED_EVENT = 'billing.payment-confirmed';

/** 入金確認完了イベントのペイロード契約（Billing → Booking） */
export interface PaymentConfirmedPayload {
  bookingId: string;
}
