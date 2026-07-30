/** 荷役作業登録イベント名（EventEmitter2 のイベントキー） */
export const HANDLING_ACTIVITY_REGISTERED_EVENT = 'handling.registered';

/**
 * 荷役作業登録イベント（Handling → Tracking / Booking）。
 * Tracking は貨物状態（RECEIVED / LOADED / UNLOADED / CLAIMED）を自動更新し、
 * Booking は misrouted のとき RoutingStatus を MISROUTED へ更新する。
 * コミット後発行・冪等リスナー（ADR-005/009）。
 */
export class HandlingActivityRegisteredEvent {
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
