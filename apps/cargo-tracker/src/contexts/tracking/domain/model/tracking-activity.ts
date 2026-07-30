import { TrackingStatus } from './tracking-status.js';
import { TrackingValidationError } from './tracking-validation-error.js';

/** 追跡イベント種別（荷役由来）と対応する追跡状態のマッピング。CUSTOMS は状態を変えない */
const EVENT_STATUS_MAP: Record<string, TrackingStatus | null> = {
  RECEIVE: TrackingStatus.RECEIVED,
  LOAD: TrackingStatus.LOADED,
  UNLOAD: TrackingStatus.UNLOADED,
  CUSTOMS: null,
  CLAIM: TrackingStatus.CLAIMED,
};

/** 追跡イベント（時系列で記録される追跡の出来事） */
export interface TrackingEvent {
  eventType: string;
  location: string;
  completionTime: Date;
  voyageNumber: string | null;
}

/**
 * 追跡レコード（集約ルート）。貨物の追跡情報全体を管理する。
 * 追跡番号発行（Booking イベント）で NOT_RECEIVED として作成され、
 * 荷役イベント・手動更新で状態が進む。状態は時系列の全イベントから導出する。
 */
export class TrackingActivity {
  private constructor(
    readonly id: number | null,
    readonly trackingNumber: string,
    readonly bookingId: string,
    private readonly _events: TrackingEvent[],
  ) {}

  static create(trackingNumber: string, bookingId: string): TrackingActivity {
    if (trackingNumber.trim().length === 0) {
      throw new TrackingValidationError('追跡番号は必須です');
    }
    if (bookingId.trim().length === 0) {
      throw new TrackingValidationError('予約 ID は必須です');
    }
    return new TrackingActivity(null, trackingNumber.trim(), bookingId, []);
  }

  static reconstruct(params: {
    id: number;
    trackingNumber: string;
    bookingId: string;
    events: TrackingEvent[];
  }): TrackingActivity {
    return new TrackingActivity(params.id, params.trackingNumber, params.bookingId, [...params.events]);
  }

  get events(): readonly TrackingEvent[] {
    return this._events;
  }

  /**
   * イベントを追加する。同一種別・同一完了時刻のイベントは重複追加しない（冪等）。
   * @returns 追加された場合 true、重複でスキップした場合 false
   */
  addEvent(event: TrackingEvent): boolean {
    if (!(event.eventType in EVENT_STATUS_MAP)) {
      throw new TrackingValidationError(`不正な追跡イベント種別: ${event.eventType}`);
    }
    const duplicated = this._events.some(
      (e) => e.eventType === event.eventType && e.completionTime.getTime() === event.completionTime.getTime(),
    );
    if (duplicated) {
      return false;
    }
    this._events.push(event);
    return true;
  }

  /** 現在の追跡状態。イベントなしは NOT_RECEIVED、CUSTOMS は直前状態を維持する */
  currentStatus(): TrackingStatus {
    const ordered = [...this._events].sort((a, b) => a.completionTime.getTime() - b.completionTime.getTime());
    let status: TrackingStatus = TrackingStatus.NOT_RECEIVED;
    for (const event of ordered) {
      const mapped = EVENT_STATUS_MAP[event.eventType];
      if (mapped !== null && mapped !== undefined) {
        status = mapped;
      }
    }
    return status;
  }
}
