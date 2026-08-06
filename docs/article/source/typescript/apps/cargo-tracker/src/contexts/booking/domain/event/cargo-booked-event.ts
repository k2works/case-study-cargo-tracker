/** 貨物予約イベント名（EventEmitter2 のイベントキー） */
export const CARGO_BOOKED_EVENT = 'cargo.booked';

/**
 * 貨物予約完了イベント（Booking → Tracking）。
 * 新規予約後、追跡番号割り当て依頼を通知する（IT2 では発行のみ。購読は IT4 の Tracking 実装時）。
 */
export class CargoBookedEvent {
  constructor(
    readonly bookingId: string,
    readonly shipperId: number,
    readonly origin: string,
    readonly destination: string,
  ) {}
}
