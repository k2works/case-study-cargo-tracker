/** 通知種別（荷主への経路通知・確定・キャンセル・追跡番号発行） */
export const NotificationType = {
  ROUTE_PROPOSED: 'ROUTE_PROPOSED',
  BOOKING_CONFIRMED: 'BOOKING_CONFIRMED',
  BOOKING_CANCELLED: 'BOOKING_CANCELLED',
  TRACKING_ISSUED: 'TRACKING_ISSUED',
} as const;
export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType];

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
