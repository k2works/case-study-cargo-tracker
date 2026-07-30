import type { CargoClaimedPayload } from '../../../../shared/contracts/cargo-claimed.contract.js';

export { CARGO_CLAIMED_EVENT } from '../../../../shared/contracts/cargo-claimed.contract.js';

/**
 * 引取完了イベント（Handling → Billing / Booking）。
 * 貨物状態「引取済」は配送完了を意味し、精算処理の開始条件となる（US16 受入基準 4）。
 * 契約（CargoClaimedPayload）を実装し、購読側と型を共有する（IT7 グループ 3）。
 */
export class CargoClaimedEvent implements CargoClaimedPayload {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string,
    readonly claimedAt: Date,
  ) {}
}
