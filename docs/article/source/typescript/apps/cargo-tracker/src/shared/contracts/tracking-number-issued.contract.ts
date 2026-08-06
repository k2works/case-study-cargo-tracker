/**
 * 追跡番号発行イベントの契約（共有カーネル）。
 * 発行側（Booking）と購読側（Tracking）で型を 1 箇所に集約する（IT6 Try T5）。
 * イベント名文字列は変更しない。
 */

/** 追跡番号発行イベント名（EventEmitter2 のイベントキー） */
export const TRACKING_NUMBER_ISSUED_EVENT = 'booking.tracking-issued';

/** 追跡番号発行イベントのペイロード契約（Booking → Tracking） */
export interface TrackingNumberIssuedPayload {
  bookingId: string;
  trackingNumber: string;
}
