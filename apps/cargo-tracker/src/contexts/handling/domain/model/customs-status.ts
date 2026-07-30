/**
 * 通関状態（domain-model が正）。
 * PENDING（審査中）→ CLEARED（通関済）/ HELD（留置中）/ REJECTED（不可）。
 * 遷移規則は CustomsDeclaration 集約が保持する。
 */
export const CustomsStatus = {
  PENDING: 'PENDING',
  CLEARED: 'CLEARED',
  HELD: 'HELD',
  REJECTED: 'REJECTED',
} as const;
export type CustomsStatus = (typeof CustomsStatus)[keyof typeof CustomsStatus];

export const CUSTOMS_STATUS_LABELS: Record<CustomsStatus, string> = {
  PENDING: '審査中',
  CLEARED: '通関済',
  HELD: '留置中',
  REJECTED: '不可',
};

export function isCustomsStatus(value: string): value is CustomsStatus {
  return Object.values(CustomsStatus).includes(value as CustomsStatus);
}
