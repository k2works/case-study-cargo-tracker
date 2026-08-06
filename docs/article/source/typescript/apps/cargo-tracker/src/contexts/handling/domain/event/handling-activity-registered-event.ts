import type { HandlingActivityRegisteredPayload } from '../../../../shared/contracts/handling-registered.contract.js';

// イベント名は共有契約（shared/contracts）を正とし、後方互換のため再エクスポートする。
export { HANDLING_ACTIVITY_REGISTERED_EVENT } from '../../../../shared/contracts/handling-registered.contract.js';

/**
 * 荷役作業登録イベント（Handling → Tracking / Booking）。
 * Tracking は貨物状態（RECEIVED / LOADED / UNLOADED / CLAIMED）を自動更新し、
 * Booking は misrouted のとき RoutingStatus を MISROUTED へ更新する。
 * コミット後発行・冪等リスナー（ADR-005/009）。ペイロード契約は shared/contracts に集約（Try T5）。
 */
export class HandlingActivityRegisteredEvent implements HandlingActivityRegisteredPayload {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string,
    readonly eventType: string,
    readonly location: string,
    readonly completionTime: Date,
    readonly voyageNumber: string | null,
    readonly misrouted: boolean,
  ) {}
}
