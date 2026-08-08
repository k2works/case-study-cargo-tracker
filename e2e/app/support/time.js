/**
 * 画面に入れる日付・日時を**業務のタイムゾーン**で組み立てる。
 *
 * **実行機のタイムゾーンを使わない。** アプリは `app.business-zone`（既定 Asia/Tokyo）で
 * 「今日」を判断する（`BusinessClockConfiguration`）。テストを回す機械がそれと違うと、
 * 時差の分だけ日付がずれ、**「作業日時が追跡番号の発行日より前です」で落ちる**。
 *
 * 開発機（JST）では `toISOString()`（UTC）が 00:00〜09:00 だけ落ち、
 * CI（UTC）では 1 日中落ちる。**どちらも「時差」という同じ原因**である。
 * 環境で挙動が変わらないよう、業務のタイムゾーンを明示して組み立てる。
 */

/** 業務のタイムゾーン。アプリの既定（`app.business-zone`）と揃える。 */
const BUSINESS_ZONE = process.env.E2E_BUSINESS_ZONE ?? 'Asia/Tokyo';

/**
 * 業務のタイムゾーンにおける「今」の各部を取り出す。
 * @param {number} offsetDays ずらす日数
 * @returns {Record<string, string>} year/month/day/hour/minute
 */
function partsInBusinessZone(offsetDays) {
  const at = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
  return Object.fromEntries(
    formatter.formatToParts(at).map((part) => [part.type, part.value]),
  );
}

/**
 * `YYYY-MM-DDTHH:mm` 形式の日時（datetime-local 用）。
 * @param {number} [offsetDays] ずらす日数
 * @returns {string} 業務のタイムゾーンでの日時
 */
export function localDateTime(offsetDays = 0) {
  const p = partsInBusinessZone(offsetDays);
  // **24 時は 00 時として扱う。** hour12: false は 24 時制で 24 を返すことがある
  const hour = p.hour === '24' ? '00' : p.hour;
  return `${p.year}-${p.month}-${p.day}T${hour}:${p.minute}`;
}

/**
 * `YYYY-MM-DD` 形式の日付（date 用）。
 * @param {number} [offsetDays] ずらす日数
 * @returns {string} 業務のタイムゾーンでの日付
 */
export function localDate(offsetDays = 0) {
  return localDateTime(offsetDays).slice(0, 10);
}
