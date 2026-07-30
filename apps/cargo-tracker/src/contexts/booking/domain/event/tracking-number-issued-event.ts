import {
  TRACKING_NUMBER_ISSUED_EVENT,
  type TrackingNumberIssuedPayload,
} from '../../../../shared/contracts/tracking-number-issued.contract.js';

// イベント名は共有契約（shared/contracts）を正とし、後方互換のため再エクスポートする。
export { TRACKING_NUMBER_ISSUED_EVENT };

/**
 * 追跡番号発行イベント（Booking → Tracking）。
 * 発行された追跡番号に対して Tracking Context が NOT_RECEIVED（受領待ち）の追跡レコードを作成する。
 * 採番は Booking 側の暫定判断（ADR-008）。コミット後発行・冪等リスナー（ADR-009）。
 * ペイロード契約は shared/contracts に集約（Try T5）。
 */
export class TrackingNumberIssuedEvent implements TrackingNumberIssuedPayload {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string,
  ) {}
}
