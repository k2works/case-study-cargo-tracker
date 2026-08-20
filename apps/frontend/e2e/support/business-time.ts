/**
 * E2E で使う日時は業務タイムゾーンで作る。
 *
 * `toISOString()` を直接使うと CI（UTC）で 1 日ずれ、日中は緑・夜間は赤という
 * 再現しにくい落ち方をする。生成はこのヘルパに集約する。
 */
const BUSINESS_TIME_ZONE = 'Asia/Tokyo'

/** 今日から days 日後の、業務タイムゾーンでの `YYYY-MM-DDTHH:mm`。 */
export function businessLocalDateTime(days: number, time: string): string {
  const target = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
  return `${formatter.format(target)}T${time}`
}
