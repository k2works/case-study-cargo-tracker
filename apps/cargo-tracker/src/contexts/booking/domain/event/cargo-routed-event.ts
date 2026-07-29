/** 経路確定イベント名（EventEmitter2 のイベントキー） */
export const CARGO_ROUTED_EVENT = 'cargo.routed';

/**
 * 経路確定イベント（Booking → Tracking）。
 * 旅程（CargoItinerary）確定後、経路・旅程情報を追跡コンテキストへ同期する。
 * IT4 では発行のみ（購読は Tracking Context 実装時）。
 */
export class CargoRoutedEvent {
  constructor(
    readonly bookingId: string,
    readonly voyageNumbers: string[],
  ) {}
}
