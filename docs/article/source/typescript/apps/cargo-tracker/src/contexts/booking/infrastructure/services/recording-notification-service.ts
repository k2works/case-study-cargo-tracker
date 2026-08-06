import type { NotificationRecorder } from '../../../../shared/infrastructure/notification/notification-recorder.js';
import type {
  NotificationPort,
  NotificationRequest,
} from '../../application/outboundservices/acl/notification-port.js';

/**
 * NotificationPort の記録付きスタブ実装（US12/US13/US14）。
 * 送信記録は共有アダプタ NotificationRecorder へ委譲する（所有集約・ADR-012）。
 * 実配信（メール/SMS）は運用フェーズで差し替える。
 */
export class RecordingNotificationService implements NotificationPort {
  constructor(private readonly recorder: NotificationRecorder) {}

  async notify(request: NotificationRequest): Promise<void> {
    await this.recorder.record({
      bookingId: request.bookingId,
      notificationType: request.notificationType,
      recipient: request.recipient,
    });
  }
}
