import { Logger } from '@nestjs/common';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { TrackingNotificationPort } from '../../application/outboundservices/acl/tracking-notification-port.js';

/**
 * TrackingNotificationPort の記録付きスタブ実装（US17 受入基準 4）。
 * 荷主メールを shipper テーブル直読で解決し、送信記録を notification_record に登録する。
 * 実配信（メール/SMS）は運用フェーズで差し替える。共有 DB 直読は ADR-008 の統制盲点に留意。
 */
export class RecordingTrackingNotificationService implements TrackingNotificationPort {
  private readonly logger = new Logger(RecordingTrackingNotificationService.name);

  constructor(private readonly db: AppDatabase) {}

  async notifyStatusChange(bookingId: string, status: string): Promise<void> {
    const row = await this.db
      .selectFrom('cargo')
      .innerJoin('shipper', 'shipper.id', 'cargo.shipperId')
      .select('shipper.email as email')
      .where('cargo.bookingId', '=', bookingId)
      .executeTakeFirst();
    if (row === undefined) {
      this.logger.warn(`状態変更通知の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.db
      .insertInto('notification_record')
      .values({ bookingId, notificationType: 'STATUS_CHANGED', recipient: row.email })
      .execute();
    this.logger.log(`状態変更通知記録: ${status} → ${row.email}（${bookingId}）`);
  }
}
