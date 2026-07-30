/** 引取完了イベント名（EventEmitter2 のイベントキー） */
export const CARGO_CLAIMED_EVENT = 'handling.cargo-claimed';

/**
 * 引取完了イベント（Handling → Billing）。
 * 貨物状態「引取済」は配送完了を意味し、精算処理の開始条件となる（US16 受入基準 4）。
 * IT5 では発行のみ。Billing の購読（請求書発行）は IT7 で実装する。
 */
export class CargoClaimedEvent {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string,
    readonly claimedAt: Date,
  ) {}
}
