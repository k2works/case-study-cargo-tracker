import { Logger } from '@nestjs/common';
import { NotificationType } from '../../../../shared/contracts/notification-type.js';
import type { NotificationRecorder } from '../../../../shared/infrastructure/notification/notification-recorder.js';
import type { HandlingNotificationPort } from '../../application/outboundservices/acl/handling-notification-port.js';
import type { ShipperContactPort } from '../../application/outboundservices/acl/shipper-contact-port.js';

/**
 * HandlingNotificationPort の記録付きスタブ実装（US15 受入基準 5）。
 * 荷主メールは ShipperContactPort 経由で解決し、送信記録は共有アダプタ NotificationRecorder へ委譲する
 * （所有集約・ADR-012）。実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingStatusNotificationService implements HandlingNotificationPort {
  private readonly logger = new Logger(RecordingStatusNotificationService.name);

  constructor(
    private readonly recorder: NotificationRecorder,
    private readonly contacts: ShipperContactPort,
  ) {}

  async notifyStatusChange(bookingId: string, eventType: string): Promise<void> {
    const email = await this.contacts.findEmailByBookingId(bookingId);
    if (email === null) {
      this.logger.warn(`状態変更通知の宛先が解決できません（予約: ${bookingId}）`);
      return;
    }
    await this.recorder.record({
      bookingId,
      notificationType: NotificationType.STATUS_CHANGED,
      recipient: email,
      body: `状態変更: ${eventType}`,
    });
  }
}
