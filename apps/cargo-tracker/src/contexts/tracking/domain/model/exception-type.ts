/**
 * 追跡例外種別（domain-model が正）。
 * DELAY（遅延・US19）/ DAMAGE（破損・US20）/ LOST（紛失・US20）/ CUSTOMS_HOLD（税関保留・通関 HELD 自動登録）。
 */
export const ExceptionType = {
  DELAY: 'DELAY',
  DAMAGE: 'DAMAGE',
  LOST: 'LOST',
  CUSTOMS_HOLD: 'CUSTOMS_HOLD',
} as const;
export type ExceptionType = (typeof ExceptionType)[keyof typeof ExceptionType];

export const EXCEPTION_TYPE_LABELS: Record<ExceptionType, string> = {
  DELAY: '遅延',
  DAMAGE: '破損',
  LOST: '紛失',
  CUSTOMS_HOLD: '税関保留',
};

export function isExceptionType(value: string): value is ExceptionType {
  return Object.values(ExceptionType).includes(value as ExceptionType);
}

/**
 * 上位管理者へエスカレーションすべき例外種別か（domain-model ビジネスルール 3）。
 * 現在は LOST（紛失）のみ true。
 */
export function escalates(type: ExceptionType): boolean {
  return type === ExceptionType.LOST;
}
