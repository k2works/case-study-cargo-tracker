/**
 * 引取完了イベントの契約（共有カーネル）。
 * 発行側（Handling）と購読側（Billing / Booking）で型を 1 箇所に集約し、
 * ペイロードの手書き重複を排除する（IT6 Try T5）。イベント名文字列は変更しない。
 * 貨物状態「引取済」は配送完了を意味し、精算処理の開始条件となる（US16 受入基準 4）。
 */

/** 引取完了イベント名（EventEmitter2 のイベントキー） */
export const CARGO_CLAIMED_EVENT = 'handling.cargo-claimed';

/** 引取完了イベントのペイロード契約（Handling → Billing / Booking） */
export interface CargoClaimedPayload {
  bookingId: string;
  trackingNumber: string;
  claimedAt: Date;
}
