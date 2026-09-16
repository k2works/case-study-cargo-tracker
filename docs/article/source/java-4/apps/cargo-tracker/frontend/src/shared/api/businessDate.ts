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

const OFFSET_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: BUSINESS_TIME_ZONE,
  timeZoneName: 'longOffset',
});

/**
 * その瞬間の業務タイムゾーンの UTC からのずれ（分）。
 *
 * <p>固定値を書かない。夏時間を採る地域へ業務タイムゾーンを変えたときに、
 * 年の半分だけずれる形になる。</p>
 */
function offsetMinutes(at: Date): number {
  const name = OFFSET_FORMATTER.formatToParts(at)
    .find((part) => part.type === 'timeZoneName')?.value ?? 'GMT';
  const matched = /GMT([+-])(\d{2}):(\d{2})/.exec(name);
  if (!matched) {
    // GMT ちょうどのときは符号も数字も付かない。
    return 0;
  }
  const sign = matched[1] === '-' ? -1 : 1;
  return sign * (Number(matched[2]) * 60 + Number(matched[3]));
}

/**
 * 入力欄（datetime-local）の壁時計を絶対時刻へ。
 *
 * <p><b>入れた時刻は業務タイムゾーンの時刻である。</b> UTC として送ると、時差の分
 * ずれた航海が登録され、そのまま経路候補の所要日数になる（エラーは出ない）。</p>
 */
export function businessLocalToInstant(local: string): string {
  if (!local) {
    return '';
  }
  // 一度 UTC として読み、その瞬間のずれで引き戻す。ずれは日付をまたぐと
  // 変わりうるので、引き戻したあとの瞬間で決め直す。
  const naive = new Date(`${local}:00Z`);
  if (Number.isNaN(naive.getTime())) {
    return local;
  }
  const first = new Date(naive.getTime() - offsetMinutes(naive) * 60_000);
  const at = new Date(naive.getTime() - offsetMinutes(first) * 60_000);
  return `${at.toISOString().slice(0, 19)}Z`;
}

/** 絶対時刻を入力欄（datetime-local）の壁時計へ。送るときと同じ見方で戻す。 */
export function instantToBusinessLocal(instant: string): string {
  if (!instant) {
    return '';
  }
  const at = new Date(instant);
  if (Number.isNaN(at.getTime())) {
    return instant;
  }
  const shifted = new Date(at.getTime() + offsetMinutes(at) * 60_000);
  return shifted.toISOString().slice(0, 16);
}
