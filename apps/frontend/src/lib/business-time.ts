/**
 * 業務タイムゾーン（Asia/Tokyo）で日付を扱うヘルパ。
 *
 * `toISOString()` を直接使うと CI（UTC）で 1 日ずれるため、日付を作る箇所は必ずここを通す。
 */
export const BUSINESS_TIME_ZONE = 'Asia/Tokyo'

/** 業務タイムゾーンでの「今日」を YYYY-MM-DD 形式で返す。 */
export function businessToday(now: Date = new Date()): string {
  return formatBusinessDate(now)
}

/** 任意の日時を業務タイムゾーンの YYYY-MM-DD 形式に整形する。 */
export function formatBusinessDate(value: Date): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
  return formatter.format(value)
}
