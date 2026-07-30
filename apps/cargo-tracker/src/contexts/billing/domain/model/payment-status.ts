/** 支払い状態（PENDING / CONFIRMED / OVERDUE / REFUNDED） */
export const PaymentStatus = {
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  OVERDUE: 'OVERDUE',
  REFUNDED: 'REFUNDED',
} as const;
export type PaymentStatus = (typeof PaymentStatus)[keyof typeof PaymentStatus];

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: '未払い',
  CONFIRMED: '入金確認済',
  OVERDUE: '支払期限超過',
  REFUNDED: '返金済',
};

export function isPaymentStatus(value: string): value is PaymentStatus {
  return Object.values(PaymentStatus).includes(value as PaymentStatus);
}
