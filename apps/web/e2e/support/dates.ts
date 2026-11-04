/** F9：E2E の日付は、業務のタイムゾーン（Asia/Tokyo）で作る。`toISOString()` は UTC の CI で 1 日ずれる。 */
const BUSINESS_ZONE = 'Asia/Tokyo';

export function businessDate(offsetDays = 0, now = new Date()): string {
  const base = new Date(now.getTime() + offsetDays * 24 * 60 * 60 * 1000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(base);
}
