import { Logger } from '@nestjs/common';
import { NotificationType } from '../../../../shared/contracts/notification-type.js';
import { escalationRecipient } from '../../../../shared/infrastructure/notification/escalation-recipient.js';
import type { NotificationRecorder } from '../../../../shared/infrastructure/notification/notification-recorder.js';
import type {
  TrackingNotificationPort,
  ExceptionReportDetail,
} from '../../application/outboundservices/acl/tracking-notification-port.js';
import type { ShipperContactPort } from '../../application/outboundservices/acl/shipper-contact-port.js';

/** 新到着予定日をローカル日付（YYYY-MM-DD）で整形する。入力（datetime-local）と表示のズレを防ぐ */
function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * TrackingNotificationPort の記録付きスタブ実装（US17 受入基準 4）。
 * 荷主メールは ShipperContactPort 経由で解決し、送信記録は共有アダプタ NotificationRecorder へ委譲する
 * （所有集約・ADR-012）。実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingTrackingNotificationService implements TrackingNotificationPort {
  private readonly logger = new Logger(RecordingTrackingNotificationService.name);

  constructor(
    private readonly recorder: NotificationRecorder,
    private readonly contacts: ShipperContactPort,
  ) {}

  async notifyStatusChange(bookingId: string, status: string): Promise<void> {
    await this.recordToShipper(bookingId, NotificationType.STATUS_CHANGED, `状態変更: ${status}`);
  }

  async notifyException(bookingId: string, exceptionType: string): Promise<void> {
    await this.recordToShipper(bookingId, NotificationType.EXCEPTION_REPORTED, `例外発生: ${exceptionType}`);
  }

  async notifyExceptionReport(
    bookingId: string,
    exceptionType: string,
    detail: ExceptionReportDetail,
  ): Promise<void> {
    const arrival =
      detail.newEstimatedArrival !== null ? formatLocalDate(detail.newEstimatedArrival) : '未定';
    const body = `対応報告（${exceptionType}）: 新到着予定日=${arrival}, 対応方針=${detail.notes}`;
    await this.recordToShipper(bookingId, NotificationType.EXCEPTION_REPORT, body);
  }

  async notifyEscalation(bookingId: string, exceptionType: string, location: string): Promise<void> {
    const body = `エスカレーション: 例外種別=${exceptionType}, 発生地=${location}`;
    await this.recorder.record({
      bookingId,
      notificationType: NotificationType.ESCALATION,
      recipient: escalationRecipient(),
      body,
    });
    this.logger.log(`エスカレーション通知記録: ${exceptionType} → 管理職（${bookingId}）`);
  }

  /** 荷主宛の通知記録を登録する。宛先未解決時は警告ログのみ（送信スキップ） */
  private async recordToShipper(
    bookingId: string,
    notificationType: NotificationType,
    body: string,
  ): Promise<void> {
    const email = await this.contacts.findEmailByBookingId(bookingId);
    if (email === null) {
      this.logger.warn(`${notificationType} の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.recorder.record({ bookingId, notificationType, recipient: email, body });
  }
}
