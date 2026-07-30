/**
 * 荷主への状態変更通知ポート（US17 受入基準 4）。
 * 宛先解決（荷主メール）と送信記録の登録はインフラアダプタが担う。
 * コミット後副作用のため、失敗はコマンド失敗として扱わない（ADR-009）。
 */
export interface TrackingNotificationPort {
  notifyStatusChange(bookingId: string, status: string): Promise<void>;
}
