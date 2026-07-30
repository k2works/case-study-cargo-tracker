import { TrackingValidationError } from './tracking-validation-error.js';
import { ExceptionType, escalates, isExceptionType } from './exception-type.js';
import type { TrackingStatus } from './tracking-status.js';

const UNLOCODE_PATTERN = /^[A-Z]{5}$/;

/** 対応報告（US19-4/5・US20-5）の入力内容 */
export interface ExceptionReport {
  /** 新到着予定日（遅延時。任意） */
  newEstimatedArrival: Date | null;
  /** 対応方針・補償方針 */
  notes: string;
}

/**
 * 追跡例外イベント（集約内エンティティ）。
 * 遅延・破損・紛失・税関保留の例外記録を保持し、対応報告・解決の履歴を持つ。
 * statusBeforeException は例外発生前の追跡状態を封入し、解決時の復帰先とする（履歴再導出禁止）。
 */
export class TrackingExceptionEvent {
  private constructor(
    private _id: number | null,
    readonly exceptionType: ExceptionType,
    readonly location: string,
    readonly occurredAt: Date,
    readonly description: string | null,
    readonly escalationFlag: boolean,
    readonly statusBeforeException: TrackingStatus,
    private _resolvedAt: Date | null,
    private _resolutionNotes: string | null,
    private _reportedAt: Date | null,
    private _newEstimatedArrival: Date | null,
    private _reportNotes: string | null,
  ) {}

  /**
   * 新規例外を生成する。escalationFlag は例外種別から自動導出する（LOST のみ true）。
   * @param statusBeforeException 例外発生前の追跡状態（解決時の復帰先）
   */
  static create(params: {
    exceptionType: string;
    location: string;
    occurredAt: Date;
    description: string | null;
    statusBeforeException: TrackingStatus;
  }): TrackingExceptionEvent {
    if (!isExceptionType(params.exceptionType)) {
      throw new TrackingValidationError(`不正な例外種別: ${params.exceptionType}`);
    }
    if (!UNLOCODE_PATTERN.test(params.location)) {
      throw new TrackingValidationError(`不正な UN/LOCODE: ${params.location}（5 文字の英字コードが必要です）`);
    }
    if (Number.isNaN(params.occurredAt.getTime())) {
      throw new TrackingValidationError('発生日時が不正です');
    }
    return new TrackingExceptionEvent(
      null,
      params.exceptionType,
      params.location,
      params.occurredAt,
      params.description,
      escalates(params.exceptionType),
      params.statusBeforeException,
      null,
      null,
      null,
      null,
      null,
    );
  }

  static reconstruct(params: {
    id: number;
    exceptionType: ExceptionType;
    location: string;
    occurredAt: Date;
    description: string | null;
    escalationFlag: boolean;
    statusBeforeException: TrackingStatus;
    resolvedAt: Date | null;
    resolutionNotes: string | null;
    reportedAt: Date | null;
    newEstimatedArrival: Date | null;
    reportNotes: string | null;
  }): TrackingExceptionEvent {
    return new TrackingExceptionEvent(
      params.id,
      params.exceptionType,
      params.location,
      params.occurredAt,
      params.description,
      params.escalationFlag,
      params.statusBeforeException,
      params.resolvedAt,
      params.resolutionNotes,
      params.reportedAt,
      params.newEstimatedArrival,
      params.reportNotes,
    );
  }

  get id(): number | null {
    return this._id;
  }

  get resolvedAt(): Date | null {
    return this._resolvedAt;
  }

  get resolutionNotes(): string | null {
    return this._resolutionNotes;
  }

  get reportedAt(): Date | null {
    return this._reportedAt;
  }

  get newEstimatedArrival(): Date | null {
    return this._newEstimatedArrival;
  }

  get reportNotes(): string | null {
    return this._reportNotes;
  }

  get isResolved(): boolean {
    return this._resolvedAt !== null;
  }

  get isReported(): boolean {
    return this._reportedAt !== null;
  }

  /** リポジトリが挿入後の採番 ID を割り当てる（集約内部用） */
  assignId(id: number): void {
    this._id = id;
  }

  /** 対応報告を記録する（新到着予定日・対応方針） */
  report(report: ExceptionReport): void {
    this._reportedAt = new Date();
    this._newEstimatedArrival = report.newEstimatedArrival;
    this._reportNotes = report.notes;
  }

  /** 例外を解決する。既に解決済みなら何もしない（冪等） */
  resolve(notes: string | null): void {
    if (this._resolvedAt !== null) {
      return;
    }
    this._resolvedAt = new Date();
    this._resolutionNotes = notes;
  }
}
