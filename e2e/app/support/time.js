/**
 * 画面に入れる日付・日時を**現地（業務）時刻**で組み立てる。
 *
 * **`toISOString()` を使わない。** あれは UTC を返すため、業務時刻が JST の
 * 00:00〜09:00 のあいだだけ日付が 1 日前になる。アプリは業務のタイムゾーンで
 * 「今日」を判断するため、**日中しか動かさないと気づかない**ずれが生まれる
 * （「作業日時が追跡番号の発行日より前です」で落ちた）。
 */

/**
 * `YYYY-MM-DDTHH:mm` 形式の現地日時（datetime-local 用）。
 * @param {number} [offsetDays] ずらす日数
 * @returns {string} 現地日時
 */
export function localDateTime(offsetDays = 0) {
  const now = new Date();
  now.setDate(now.getDate() + offsetDays);
  const pad = (n) => String(n).padStart(2, '0');
  return (
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
    + `T${pad(now.getHours())}:${pad(now.getMinutes())}`
  );
}

/**
 * `YYYY-MM-DD` 形式の現地日付（date 用）。
 * @param {number} [offsetDays] ずらす日数
 * @returns {string} 現地日付
 */
export function localDate(offsetDays = 0) {
  return localDateTime(offsetDays).slice(0, 10);
}
