/**
 * 通知種別（共有契約・ADR-012）。
 * notification_record に記録される通知の種別を union 型で拘束し、各 BC のマジックリテラルを排除する。
 * 発行側（Booking / Handling / Tracking）はこの型を参照する。
 */
export const NotificationType = {
  /** 経路提案（US10） */
  ROUTE_PROPOSED: 'ROUTE_PROPOSED',
  /** 予約確定（US11） */
  BOOKING_CONFIRMED: 'BOOKING_CONFIRMED',
  /** 予約キャンセル */
  BOOKING_CANCELLED: 'BOOKING_CANCELLED',
  /** 追跡番号発行（US14） */
  TRACKING_ISSUED: 'TRACKING_ISSUED',
  /** 状態変更（US15/US17） */
  STATUS_CHANGED: 'STATUS_CHANGED',
  /** 例外発生（US19-3/US20-4） */
  EXCEPTION_REPORTED: 'EXCEPTION_REPORTED',
  /** 対応報告（US19-4/5・US20-5） */
  EXCEPTION_REPORT: 'EXCEPTION_REPORT',
  /** 管理職エスカレーション（US20-3） */
  ESCALATION: 'ESCALATION',
  /** 精算書発行（US23-2・荷主宛。本文に請求番号・請求金額・支払期限） */
  INVOICE_ISSUED: 'INVOICE_ISSUED',
  /** 未払い（US23-5・経理担当者宛。支払期限超過。本文に請求番号・期限・金額） */
  PAYMENT_OVERDUE: 'PAYMENT_OVERDUE',
} as const;

export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType];

const VALUES: ReadonlySet<string> = new Set(Object.values(NotificationType));

/** 文字列が通知種別のいずれかであるかを判定する型ガード */
export function isNotificationType(value: string): value is NotificationType {
  return VALUES.has(value);
}
