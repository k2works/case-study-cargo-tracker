/** 見積状態 */
export const EstimateStatus = {
  CREATED: 'CREATED',
  EXPIRED: 'EXPIRED',
} as const;
export type EstimateStatus = (typeof EstimateStatus)[keyof typeof EstimateStatus];

export const ESTIMATE_STATUS_LABELS: Record<EstimateStatus, string> = {
  CREATED: '作成済',
  EXPIRED: '期限切れ',
};

export function isEstimateStatus(value: string): value is EstimateStatus {
  return value === 'CREATED' || value === 'EXPIRED';
}
