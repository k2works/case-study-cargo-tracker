/**
 * 荷主への状態変更通知ポート（US15 受入基準 5）。
 * 宛先解決（荷主メール）と送信記録の登録はインフラアダプタが担う。
 * コミット後副作用のため、失敗はコマンド失敗として扱わない（ADR-009）。
 */
export interface HandlingNotificationPort {
  notifyStatusChange(bookingId: string, eventType: string): Promise<void>;
}
