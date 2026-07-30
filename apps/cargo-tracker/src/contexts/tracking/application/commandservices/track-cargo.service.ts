import { Logger } from '@nestjs/common';
import { TrackingActivity, type TrackingEvent } from '../../domain/model/tracking-activity.js';
import type { TrackingActivityRepository } from '../../domain/repository/tracking-activity-repository.js';
import type { TrackingNotificationPort } from '../outboundservices/acl/tracking-notification-port.js';

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
    if (activity === null) {
      activity = TrackingActivity.create(params.trackingNumber, params.bookingId);
    }
    activity.addEvent(params.event);
    await this.activities.save(activity);
  }

  /** 手動更新（US17）。追跡イベントを履歴に記録し、荷主へ状態変更を通知する */
  async addManualEvent(trackingNumber: string, event: TrackingEvent): Promise<void> {
    const activity = await this.activities.findByTrackingNumber(trackingNumber);
    if (activity === null) {
      throw new TrackingActivityNotFoundError(trackingNumber);
    }
    activity.addEvent(event);
    await this.activities.save(activity);
    // コミット後副作用: 失敗はコマンド失敗として扱わない（ADR-009）
    try {
      await this.notifier.notifyStatusChange(activity.bookingId, activity.currentStatus());
    } catch (error) {
      this.logger.error(`手動更新の荷主通知に失敗: ${String(error)}`);
    }
  }
}
