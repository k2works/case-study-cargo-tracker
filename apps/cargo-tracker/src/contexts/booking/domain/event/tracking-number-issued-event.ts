/** 追跡番号発行イベント名（EventEmitter2 のイベントキー） */
export const TRACKING_NUMBER_ISSUED_EVENT = 'booking.tracking-issued';

/**
 * 追跡番号発行イベント（Booking → Tracking）。
 * 発行された追跡番号に対して Tracking Context が NOT_RECEIVED（受領待ち）の追跡レコードを作成する。
 * 採番は Booking 側の暫定判断（ADR-008）。コミット後発行・冪等リスナー（ADR-009）。
 */
export class TrackingNumberIssuedEvent {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string,
  ) {}
}
