import { Logger } from '@nestjs/common';
import { type Clock, systemClock } from '../../../../shared/infrastructure/clock/clock.js';
import { TrackingValidationError } from '../../domain/model/tracking-validation-error.js';
import { TrackingActivity, type TrackingEvent } from '../../domain/model/tracking-activity.js';
import type { TrackingActivityRepository } from '../../domain/repository/tracking-activity-repository.js';
import type { TrackingNotificationPort } from '../outboundservices/acl/tracking-notification-port.js';

/** 手動更新（US17）で記録できるイベント種別。CLAIM/CUSTOMS は専用経路のみ */
const MANUAL_EVENT_TYPES = new Set(['RECEIVE', 'LOAD', 'UNLOAD', 'DEPARTURE', 'ARRIVAL']);

export class TrackingActivityNotFoundError extends Error {
  constructor(trackingNumber: string) {
    super(`追跡番号が見つかりません: ${trackingNumber}`);
    this.name = 'TrackingActivityNotFoundError';
  }
}

/**
 * 追跡レコード操作ユースケース（US15 イベント購読・US17 手動更新・Try T6）。
 * - createIfAbsent: 追跡番号発行イベントから NOT_RECEIVED の追跡レコードを作成（冪等）
 * - applyHandlingEvent: 荷役イベントから貨物状態を自動更新（冪等・未存在時は遅延作成）
 * - addManualEvent: 追跡管理者の手動更新（履歴記録・荷主通知）
 */
export class TrackCargoService {
  private readonly logger = new Logger(TrackCargoService.name);

  constructor(
    private readonly activities: TrackingActivityRepository,
    private readonly notifier: TrackingNotificationPort,
    private readonly now: Clock = systemClock,
  ) {}

  /** 追跡番号発行時に NOT_RECEIVED の追跡レコードを作成する。既存なら何もしない（冪等） */
  async createIfAbsent(trackingNumber: string, bookingId: string): Promise<void> {
    const existing = await this.activities.findByTrackingNumber(trackingNumber);
    if (existing !== null) {
      return;
    }
    await this.activities.save(TrackingActivity.create(trackingNumber, bookingId));
  }

  /** 荷役イベントを追跡レコードへ反映する。追跡レコード未存在時は bookingId から遅延作成する */
  async applyHandlingEvent(params: {
    trackingNumber: string;
    bookingId: string;
    event: TrackingEvent;
  }): Promise<void> {
    let activity = await this.activities.findByTrackingNumber(params.trackingNumber);
    activity ??= TrackingActivity.create(params.trackingNumber, params.bookingId);
    activity.addEvent(params.event);
    await this.activities.save(activity);
  }

  /**
   * 手動更新（US17）。追跡イベントを履歴に記録し、荷主へ状態変更を通知する。
   * CLAIM（引取）は「通関 CLEARED + 荷受人確認」の不変条件を持つため荷役登録経路（US16）に限定し、
   * 手動更新からは受け付けない（迂回防止）。CUSTOMS も通関申告コマンドの管轄のため対象外。
   * @returns 追加された場合 true、重複（同種別・同時刻）でスキップした場合 false
   */
  async addManualEvent(trackingNumber: string, event: TrackingEvent): Promise<boolean> {
    if (!MANUAL_EVENT_TYPES.has(event.eventType)) {
      throw new TrackingValidationError(
        `手動更新で記録できないイベント種別です: ${event.eventType}（引取は荷役登録から行ってください）`,
      );
    }
    const activity = await this.activities.findByTrackingNumber(trackingNumber);
    if (activity === null) {
      throw new TrackingActivityNotFoundError(trackingNumber);
    }
    const added = activity.addEvent(event, this.now());
    if (!added) {
      return false;
    }
    await this.activities.save(activity);
    // コミット後副作用: 失敗はコマンド失敗として扱わない（ADR-009）
    try {
      await this.notifier.notifyStatusChange(activity.bookingId, activity.currentStatus());
    } catch (error) {
      this.logger.error(`手動更新の荷主通知に失敗: ${String(error)}`);
    }
    return true;
  }
}
