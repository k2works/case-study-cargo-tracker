import { Logger } from '@nestjs/common';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { TrackingNotificationPort } from '../../application/outboundservices/acl/tracking-notification-port.js';
import type { ShipperContactPort } from '../../application/outboundservices/acl/shipper-contact-port.js';

/**
 * TrackingNotificationPort の記録付きスタブ実装（US17 受入基準 4）。
 * 荷主メールは ShipperContactPort 経由で解決し、送信記録を notification_record に登録する。
 * 宛先解決の生 JOIN はポート裏へ隠蔽した（IT6 Try T4）。実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingTrackingNotificationService implements TrackingNotificationPort {
  private readonly logger = new Logger(RecordingTrackingNotificationService.name);

  constructor(
    private readonly db: AppDatabase,
    private readonly contacts: ShipperContactPort,
  ) {}

  /** 管理職への通知宛先（実配信は運用フェーズで差し替える固定スタブ） */
  private static readonly MANAGEMENT_RECIPIENT = 'management@example.com';

  async notifyStatusChange(bookingId: string, status: string): Promise<void> {
    await this.recordToShipper(bookingId, 'STATUS_CHANGED', `状態変更通知: ${status}`);
  }

  async notifyException(bookingId: string, exceptionType: string): Promise<void> {
    await this.recordToShipper(bookingId, 'EXCEPTION_REPORTED', `例外発生通知: ${exceptionType}`);
  }

  async notifyExceptionReport(bookingId: string, exceptionType: string): Promise<void> {
    await this.recordToShipper(bookingId, 'EXCEPTION_REPORT', `対応報告通知: ${exceptionType}`);
  }

  async notifyEscalation(bookingId: string, exceptionType: string): Promise<void> {
    await this.db
      .insertInto('notification_record')
      .values({
        bookingId,
        notificationType: 'ESCALATION',
        recipient: RecordingTrackingNotificationService.MANAGEMENT_RECIPIENT,
      })
      .execute();
    this.logger.log(`エスカレーション通知記録: ${exceptionType} → 管理職（${bookingId}）`);
  }

  /** 荷主宛の通知記録を登録する。宛先未解決時は警告ログのみ（送信スキップ） */
  private async recordToShipper(bookingId: string, notificationType: string, message: string): Promise<void> {
    const email = await this.contacts.findEmailByBookingId(bookingId);
    if (email === null) {
      this.logger.warn(`${notificationType} の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.db.insertInto('notification_record').values({ bookingId, notificationType, recipient: email }).execute();
    this.logger.log(`${message} → ${email}（${bookingId}）`);
  }
}
