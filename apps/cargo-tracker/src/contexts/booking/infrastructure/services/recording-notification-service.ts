import { Logger } from '@nestjs/common';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type {
  NotificationPort,
  NotificationRequest,
} from '../../application/outboundservices/acl/notification-port.js';

/**
 * NotificationPort の記録付きスタブ実装（US12/US13/US14）。
 * 送信記録を notification_record に永続化する。実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingNotificationService implements NotificationPort {
  private readonly logger = new Logger(RecordingNotificationService.name);

  constructor(private readonly db: AppDatabase) {}

  async notify(request: NotificationRequest): Promise<void> {
    await this.db
      .insertInto('notification_record')
      .values({
        bookingId: request.bookingId,
        notificationType: request.notificationType,
        recipient: request.recipient,
      })
      .execute();
    this.logger.log(`通知記録: ${request.notificationType} → ${request.recipient}（${request.bookingId}）`);
  }
}
