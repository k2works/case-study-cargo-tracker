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

  async notifyStatusChange(bookingId: string, status: string): Promise<void> {
    const email = await this.contacts.findEmailByBookingId(bookingId);
    if (email === null) {
      this.logger.warn(`状態変更通知の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.db
      .insertInto('notification_record')
      .values({ bookingId, notificationType: 'STATUS_CHANGED', recipient: email })
      .execute();
    this.logger.log(`状態変更通知記録: ${status} → ${email}（${bookingId}）`);
  }
}
