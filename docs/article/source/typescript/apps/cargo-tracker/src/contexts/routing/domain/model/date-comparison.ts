function toUtcDateKey(date: Date): number {
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

/** Routing Context の期限判定は時刻ではなく日付単位で扱う */
export function isSameOrBeforeDate(actual: Date, deadline: Date): boolean {
  return toUtcDateKey(actual) <= toUtcDateKey(deadline);
}

export function isBeforeDate(actual: Date, baseline: Date): boolean {
  return toUtcDateKey(actual) < toUtcDateKey(baseline);
}
