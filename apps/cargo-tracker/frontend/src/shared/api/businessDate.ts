/**
 * 業務日付。サーバの BusinessClock（Asia/Tokyo）に合わせる。
 *
 * <p>`toISOString()` は UTC を返すので、日本時間の朝 9 時より前は前日になる。
 * 画面で「今日」を作るときにこれを使うと、CI（UTC）だけでなく利用者の環境でも
 * 日付が 1 日ずれる時間帯ができる。</p>
 */
export const BUSINESS_TIME_ZONE = 'Asia/Tokyo';

const DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** 業務タイムゾーンでの日付（YYYY-MM-DD）。 */
export function businessDate(at: Date = new Date()): string {
  return DATE_FORMATTER.format(at);
}

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('ja-JP', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** 画面に出す日時。サーバから来る ISO 文字列を業務タイムゾーンで読む。 */
export function formatBusinessDateTime(isoString: string): string {
  const at = new Date(isoString);
  if (Number.isNaN(at.getTime())) {
    // 読めない値を握りつぶすと、画面に「Invalid Date」が出るより分かりにくい。
    return isoString;
  }
  return DATE_TIME_FORMATTER.format(at);
}
