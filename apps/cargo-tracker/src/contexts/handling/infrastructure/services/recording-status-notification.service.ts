import { Logger } from '@nestjs/common';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { HandlingNotificationPort } from '../../application/outboundservices/acl/handling-notification-port.js';
import type { ShipperContactPort } from '../../application/outboundservices/acl/shipper-contact-port.js';

/**
 * HandlingNotificationPort の記録付きスタブ実装（US15 受入基準 5）。
 * 荷主メールは ShipperContactPort 経由で解決し、送信記録を notification_record に登録する。
 * 宛先解決の生 JOIN はポート裏へ隠蔽した（IT6 Try T4）。実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingStatusNotificationService implements HandlingNotificationPort {
  private readonly logger = new Logger(RecordingStatusNotificationService.name);

  constructor(
    private readonly db: AppDatabase,
    private readonly contacts: ShipperContactPort,
  ) {}

  async notifyStatusChange(bookingId: string, eventType: string): Promise<void> {
    const email = await this.contacts.findEmailByBookingId(bookingId);
    if (email === null) {
      this.logger.warn(`状態変更通知の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.db
      .insertInto('notification_record')
      .values({ bookingId, notificationType: 'STATUS_CHANGED', recipient: email })
      .execute();
    this.logger.log(`状態変更通知記録: ${eventType} → ${email}（${bookingId}）`);
  }
}
