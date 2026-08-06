import type { NotificationType } from '../../../../../shared/contracts/notification-type.js';

/** 精算通知の登録要求（本文に請求番号・金額・期限を載せる。ADR-012） */
export interface BillingNotificationRequest {
  bookingId: string;
  notificationType: NotificationType;
  recipient: string;
  /** 人間可読な本文（請求番号・請求金額・支払期限など） */
  body: string;
}

/**
 * 精算通知ポート（US23・出力ポート）。
 * 精算書発行（INVOICE_ISSUED・荷主宛）・未払い（PAYMENT_OVERDUE・経理宛）の通知記録を担う。
 * notification_record への書き込みは共有アダプタへ委譲し、Billing は他 BC のドメイン型に依存しない。
 */
export interface BillingNotificationPort {
  notify(request: BillingNotificationRequest): Promise<void>;
}
