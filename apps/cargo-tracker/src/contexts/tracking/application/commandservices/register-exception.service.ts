import { Logger } from '@nestjs/common';
import { type Clock, systemClock } from '../../../../shared/infrastructure/clock/clock.js';
import { TrackingValidationError } from '../../domain/model/tracking-validation-error.js';
import { ExceptionType } from '../../domain/model/exception-type.js';
import type { TrackingActivityRepository } from '../../domain/repository/tracking-activity-repository.js';
import type { TrackingNotificationPort } from '../outboundservices/acl/tracking-notification-port.js';
import { TrackingActivityNotFoundError } from './track-cargo.service.js';

/** 荷役作業員が手動登録できる例外種別（US20: 破損・紛失のみ） */
const HANDLER_ALLOWED_TYPES = new Set<string>([ExceptionType.DAMAGE, ExceptionType.LOST]);

/** ロールが登録を許されない例外種別を指定したとき */
export class ExceptionRegistrationForbiddenError extends Error {
  constructor(exceptionType: string) {
    super(`この例外種別は登録できません: ${exceptionType}`);
    this.name = 'ExceptionRegistrationForbiddenError';
  }
}

/**
 * 例外登録ユースケース（US19 遅延・US20 破損/紛失）。
 * 追跡番号で集約を特定 → addException（冪等）→ save → コミット後副作用で通知を記録する。
 * 荷役作業員（追跡管理者でない）は破損・紛失のみ登録できる（種別レベルの認可）。
 */
export class RegisterExceptionService {
  private readonly logger = new Logger(RegisterExceptionService.name);

  constructor(
    private readonly activities: TrackingActivityRepository,
    private readonly notifier: TrackingNotificationPort,
    private readonly now: Clock = systemClock,
  ) {}

  /**
   * 例外を登録する。
   * @returns 追加された場合 true、未解決の同種例外が既にあり冪等スキップした場合 false
   */
  async register(
    trackingNumber: string,
    input: {
      exceptionType: string;
      location: string;
      occurredAt: Date;
      description: string | null;
      declarationNumber?: string | null;
    },
    actor: { isTracker: boolean },
  ): Promise<boolean> {
    if (!actor.isTracker && !HANDLER_ALLOWED_TYPES.has(input.exceptionType)) {
      throw new ExceptionRegistrationForbiddenError(input.exceptionType);
    }
    const activity = await this.activities.findByTrackingNumber(trackingNumber);
    if (activity === null) {
      throw new TrackingActivityNotFoundError(trackingNumber);
    }
    const added = activity.addException(input, this.now());
    if (added === null) {
      return false;
    }
    await this.activities.save(activity);
    // コミット後副作用: 失敗はコマンド失敗として扱わない（ADR-009）
    try {
      await this.notifier.notifyException(activity.bookingId, added.exceptionType);
      if (added.escalationFlag) {
        await this.notifier.notifyEscalation(activity.bookingId, added.exceptionType, added.location);
      }
    } catch (error) {
      this.logger.error(`例外発生通知に失敗: ${String(error)}`);
    }
    return true;
  }

  /** 対応報告を送信する（US19-4/5・US20-5）。荷主へ EXCEPTION_REPORT を通知する */
  async report(
    trackingNumber: string,
    exceptionId: number,
    report: { newEstimatedArrival: Date | null; notes: string },
  ): Promise<void> {
    const activity = await this.activities.findByTrackingNumber(trackingNumber);
    if (activity === null) {
      throw new TrackingActivityNotFoundError(trackingNumber);
    }
    if (report.notes.trim().length === 0) {
      throw new TrackingValidationError('対応方針を入力してください');
    }
    const reported = activity.reportException(exceptionId, {
      newEstimatedArrival: report.newEstimatedArrival,
      notes: report.notes.trim(),
    });
    await this.activities.save(activity);
    try {
      await this.notifier.notifyExceptionReport(activity.bookingId, reported.exceptionType, {
        newEstimatedArrival: reported.newEstimatedArrival,
        notes: reported.reportNotes ?? report.notes.trim(),
      });
    } catch (error) {
      this.logger.error(`対応報告通知に失敗: ${String(error)}`);
    }
  }

  /** 例外を解決する（US19/US20）。発生前状態へ復帰する（未解決例外が残る場合は EXCEPTION 維持） */
  async resolve(trackingNumber: string, exceptionId: number, resolutionNotes: string | null): Promise<void> {
    const activity = await this.activities.findByTrackingNumber(trackingNumber);
    if (activity === null) {
      throw new TrackingActivityNotFoundError(trackingNumber);
    }
    activity.resolveException(exceptionId, resolutionNotes);
    await this.activities.save(activity);
  }
}
