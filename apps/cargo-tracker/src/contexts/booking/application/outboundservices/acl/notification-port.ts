// 通知種別は共有契約（shared/contracts）を正とする（ADR-012）。後方互換のため再エクスポートする。
import { NotificationType } from '../../../../../shared/contracts/notification-type.js';
export { NotificationType };

export interface NotificationRequest {
  bookingId: string;
  notificationType: NotificationType;
  recipient: string;
}

/**
 * 通知システム ACL ポート（domain-model の NotificationPort）。
 * 荷主・荷受人への通知送信と送信記録の登録を担う。実配信はインフラアダプタが担当する。
 */
export interface NotificationPort {
  notify(request: NotificationRequest): Promise<void>;
}
