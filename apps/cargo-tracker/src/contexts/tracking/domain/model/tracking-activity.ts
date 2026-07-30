import { TrackingStatus, transportPhaseRank } from './tracking-status.js';
import { TrackingValidationError } from './tracking-validation-error.js';
import { TrackingExceptionEvent, type ExceptionReport } from './tracking-exception.js';

/**
 * 追跡イベント種別と対応する追跡状態のマッピング。CUSTOMS は状態を変えない。
 * RECEIVE〜CLAIM は荷役由来（HandlingActivityRegisteredEvent）、
 * DEPARTURE（出港）/ ARRIVAL（入港）は荷役では捕捉できない状態変化の手動更新用（US17）。
 * EXCEPTION / UNKNOWN への遷移は IT6（例外処理）で導出予定。
 */
const EVENT_STATUS_MAP: Record<string, TrackingStatus | null> = {
  RECEIVE: TrackingStatus.RECEIVED,
  LOAD: TrackingStatus.LOADED,
  UNLOAD: TrackingStatus.UNLOADED,
  CUSTOMS: null,
  CLAIM: TrackingStatus.CLAIMED,
  DEPARTURE: TrackingStatus.ONBOARD_CARRIER,
  ARRIVAL: TrackingStatus.AWAITING_CLAIM,
};

const UNLOCODE_PATTERN = /^[A-Z]{5}$/;

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
    private readonly _exceptions: TrackingExceptionEvent[],
  ) {}

  static create(trackingNumber: string, bookingId: string): TrackingActivity {
    if (trackingNumber.trim().length === 0) {
      throw new TrackingValidationError('追跡番号は必須です');
    }
    if (bookingId.trim().length === 0) {
      throw new TrackingValidationError('予約 ID は必須です');
    }
    return new TrackingActivity(null, trackingNumber.trim(), bookingId, [], []);
  }

  static reconstruct(params: {
    id: number;
    trackingNumber: string;
    bookingId: string;
    events: TrackingEvent[];
    exceptions?: TrackingExceptionEvent[];
  }): TrackingActivity {
    return new TrackingActivity(
      params.id,
      params.trackingNumber,
      params.bookingId,
      [...params.events],
      [...(params.exceptions ?? [])],
    );
  }

  get events(): readonly TrackingEvent[] {
    return this._events;
  }

  get exceptions(): readonly TrackingExceptionEvent[] {
    return this._exceptions;
  }

  /**
   * イベントを追加する。同一種別・同一完了時刻のイベントは重複追加しない（冪等）。
   * @returns 追加された場合 true、重複でスキップした場合 false
   */
  addEvent(event: TrackingEvent): boolean {
    if (!(event.eventType in EVENT_STATUS_MAP)) {
      throw new TrackingValidationError(`不正な追跡イベント種別: ${event.eventType}`);
    }
    if (!UNLOCODE_PATTERN.test(event.location)) {
      throw new TrackingValidationError(`不正な UN/LOCODE: ${event.location}（5 文字の英字コードが必要です）`);
    }
    if (Number.isNaN(event.completionTime.getTime())) {
      throw new TrackingValidationError('完了日時が不正です');
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

  /**
   * イベント由来の追跡状態（例外を考慮しない素の導出）。
   * イベントなしは NOT_RECEIVED、CUSTOMS は直前状態を維持する。
   */
  private eventDerivedStatus(): TrackingStatus {
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

  /**
   * 現在の表示用追跡状態。
   * 未解決の例外があれば EXCEPTION を優先する。全て解決済みなら最後に解決した例外の
   * 「発生前状態」と「イベント由来状態」のうち輸送フェーズ順で進んでいる方へ復帰する
   * （例外対応中に到着した荷降し等のイベントを失わない）。例外がなければイベント由来状態。
   */
  currentStatus(): TrackingStatus {
    if (this.hasActiveException()) {
      return TrackingStatus.EXCEPTION;
    }
    const derived = this.eventDerivedStatus();
    if (this._exceptions.length > 0) {
      const lastResolved = [...this._exceptions]
        .filter((e) => e.resolvedAt !== null)
        // 解決時刻の昇順。同時刻は id 併用で決定化する（復帰先を一意にする）
        .sort((a, b) => {
          const byTime = a.resolvedAt!.getTime() - b.resolvedAt!.getTime();
          return byTime !== 0 ? byTime : (a.id ?? 0) - (b.id ?? 0);
        })
        .at(-1);
      if (lastResolved !== undefined) {
        const before = lastResolved.statusBeforeException;
        return transportPhaseRank(derived) > transportPhaseRank(before) ? derived : before;
      }
    }
    return derived;
  }

  /** 未解決の例外を持つか */
  hasActiveException(): boolean {
    return this._exceptions.some((e) => !e.isResolved);
  }

  /**
   * 例外を登録する（US19/US20）。未解決の同種例外が既にあれば追加しない（冪等）。
   * statusBeforeException には登録時点のイベント由来状態を封入する（解決時の復帰先）。
   * @returns 追加された場合は生成した例外、冪等スキップ時は null
   */
  addException(params: {
    exceptionType: string;
    location: string;
    occurredAt: Date;
    description: string | null;
  }): TrackingExceptionEvent | null {
    const duplicated = this._exceptions.some((e) => e.exceptionType === params.exceptionType && !e.isResolved);
    if (duplicated) {
      return null;
    }
    const exception = TrackingExceptionEvent.create({
      exceptionType: params.exceptionType,
      location: params.location,
      occurredAt: params.occurredAt,
      description: params.description,
      statusBeforeException: this.eventDerivedStatus(),
    });
    this._exceptions.push(exception);
    return exception;
  }

  /** ID で例外を取得する（未存在は null） */
  findException(exceptionId: number): TrackingExceptionEvent | null {
    return this._exceptions.find((e) => e.id === exceptionId) ?? null;
  }

  /** 例外に対応報告を記録する（US19-4/5・US20-5）。未存在時はエラー */
  reportException(exceptionId: number, report: ExceptionReport): TrackingExceptionEvent {
    const exception = this.findException(exceptionId);
    if (exception === null) {
      throw new TrackingValidationError(`例外が見つかりません: ${exceptionId}`);
    }
    exception.report(report);
    return exception;
  }

  /** 例外を解決する。発生前状態へ復帰する（未解決例外が残る場合は EXCEPTION 維持） */
  resolveException(exceptionId: number, resolutionNotes: string | null): TrackingExceptionEvent {
    const exception = this.findException(exceptionId);
    if (exception === null) {
      throw new TrackingValidationError(`例外が見つかりません: ${exceptionId}`);
    }
    exception.resolve(resolutionNotes);
    return exception;
  }
}
