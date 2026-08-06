import type { NotificationRecorder } from '../../../../shared/infrastructure/notification/notification-recorder.js';
import type {
  BillingNotificationPort,
  BillingNotificationRequest,
} from '../../application/outboundservices/acl/billing-notification-port.js';

/**
 * BillingNotificationPort の記録付き実装（US23）。
 * 送信記録は共有アダプタ NotificationRecorder へ委譲する（所有集約・ADR-012）。
 * 実配信（メール/SMS）は body を素材に運用フェーズで差し替える。
 */
export class RecordingBillingNotificationService implements BillingNotificationPort {
  constructor(private readonly recorder: NotificationRecorder) {}

  async notify(request: BillingNotificationRequest): Promise<void> {
    await this.recorder.record({
      bookingId: request.bookingId,
      notificationType: request.notificationType,
      recipient: request.recipient,
      body: request.body,
    });
  }
}
