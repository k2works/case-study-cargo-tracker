/**
 * 荷役作業登録イベントの契約（共有カーネル）。
 * 発行側（Handling）と購読側（Tracking / Booking）で型を 1 箇所に集約し、
 * ペイロードの手書き重複を排除する（IT6 Try T5）。イベント名文字列は変更しない。
 */

/** 荷役作業登録イベント名（EventEmitter2 のイベントキー） */
export const HANDLING_ACTIVITY_REGISTERED_EVENT = 'handling.registered';

/** 荷役作業登録イベントのペイロード契約（Handling → Tracking / Booking） */
export interface HandlingActivityRegisteredPayload {
  bookingId: string;
  trackingNumber: string;
  eventType: string;
  location: string;
  completionTime: Date;
  voyageNumber: string | null;
  misrouted: boolean;
}
