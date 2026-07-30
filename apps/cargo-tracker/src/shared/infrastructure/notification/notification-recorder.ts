import { Logger } from '@nestjs/common';
import type { AppDatabase } from '../database/database.js';
import type { NotificationType } from '../../contracts/notification-type.js';

/** 通知記録の登録パラメータ（ADR-012） */
export interface NotificationRecordParams {
  bookingId: string;
  notificationType: NotificationType;
  recipient: string;
  /** 人間可読な本文（新到着予定日・対応方針・例外種別など）。省略時は本文なし */
  body?: string | null;
}

/**
 * 通知記録の単一書き込みアダプタ（共有インフラが所有・ADR-012）。
 * notification_record への insert を 1 箇所に集約し、各 BC の通知ポート実装はこのアダプタへ委譲する。
 * 実配信（メール/SMS）は body を素材に運用フェーズで差し替える。
 */
export class NotificationRecorder {
  private readonly logger = new Logger(NotificationRecorder.name);

  constructor(private readonly db: AppDatabase) {}

  async record(params: NotificationRecordParams): Promise<void> {
    await this.db
      .insertInto('notification_record')
      .values({
        bookingId: params.bookingId,
        notificationType: params.notificationType,
        recipient: params.recipient,
        body: params.body ?? null,
      })
      .execute();
    this.logger.log(
      `通知記録: ${params.notificationType} → ${params.recipient}（${params.bookingId}）`,
    );
  }
}
