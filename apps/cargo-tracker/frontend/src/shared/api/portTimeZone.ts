import { BUSINESS_TIME_ZONE } from './businessDate';

/**
 * 港（UN/LOCODE）のタイムゾーン（non_functional.md:212 / IT9 引き継ぎ H.7）。
 *
 * <p><b>荷役の日時は港のローカル時刻で入力・表示する。</b> 業務タイムゾーン
 * （`Asia/Tokyo`）固定にすると、<b>海外港で最大 13 時間ずれる</b>——現場が
 * 「08:30 に降ろした」と入れたのに、履歴には 21:30 と出る。期限・請求の判定は
 * 業務タイムゾーンで行うので、<b>両者は別の概念として扱う</b>。</p>
 *
 * <p><b>知らない港は業務タイムゾーンで扱う。</b> 画面には併記があるので、
 * ずれていても「どちらの時刻か」は読める。港を足すときにここへ足す
 * ——足し忘れは `portTimeZone.canon.test.ts` が拾う（正典の港の一覧と突き合わせる）。</p>
 */
const PORT_TIME_ZONES: Record<string, string> = {
  JPTYO: 'Asia/Tokyo',
  JPYOK: 'Asia/Tokyo',
  JPOSA: 'Asia/Tokyo',
  USNYC: 'America/New_York',
  USLAX: 'America/Los_Angeles',
  SGSIN: 'Asia/Singapore',
  NLRTM: 'Europe/Amsterdam',
  DEHAM: 'Europe/Berlin',
  CNSHA: 'Asia/Shanghai',
  AUSYD: 'Australia/Sydney',
};

/** その港のタイムゾーン。知らない港は業務タイムゾーン。 */
export function portTimeZone(unLocode: string | null | undefined): string {
  if (!unLocode) {
    return BUSINESS_TIME_ZONE;
  }
  return PORT_TIME_ZONES[unLocode.toUpperCase()] ?? BUSINESS_TIME_ZONE;
}

/** タイムゾーンを持つ港の一覧（検査が空振りしていないことを見るために出す）。 */
export function portsWithTimeZone(): string[] {
  return Object.keys(PORT_TIME_ZONES);
}

function formatterFor(timeZone: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('ja-JP', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/**
 * 荷役の日時を港のローカル時刻で出し、業務タイムゾーンを併記する。
 *
 * <p><b>併記する理由。</b> 港のローカル時刻だけだと、東京から見ている追跡管理者が
 * 「いつの話か」を頭の中で足し引きすることになる。片方だけにすると、どちらかの
 * 側が必ず換算を強いられる。</p>
 *
 * <p>同じ時間帯の港では併記しない——同じ数字を 2 度並べても読み手の助けにならない。</p>
 */
export function formatPortDateTime(isoString: string, unLocode: string | null): string {
  const at = new Date(isoString);
  if (Number.isNaN(at.getTime())) {
    // 読めない値を握りつぶすと、画面に「Invalid Date」が出るより分かりにくい。
    return isoString;
  }
  const zone = portTimeZone(unLocode);
  const local = formatterFor(zone).format(at);
  if (zone === BUSINESS_TIME_ZONE) {
    return local;
  }
  return `${local}（JST ${formatterFor(BUSINESS_TIME_ZONE).format(at)}）`;
}

const OFFSET_FORMATTERS = new Map<string, Intl.DateTimeFormat>();

function offsetFormatterFor(timeZone: string): Intl.DateTimeFormat {
  const cached = OFFSET_FORMATTERS.get(timeZone);
  if (cached) {
    return cached;
  }
  const formatter = new Intl.DateTimeFormat('en-US', { timeZone, timeZoneName: 'longOffset' });
  OFFSET_FORMATTERS.set(timeZone, formatter);
  return formatter;
}

/**
 * その瞬間のその港の UTC からのずれ（分）。
 *
 * <p>固定値を書かない。夏時間を採る港（ニューヨーク・ロッテルダム）は年の半分だけ
 * ずれる——書いた瞬間は正しく、半年後に狂う。</p>
 */
function offsetMinutes(at: Date, timeZone: string): number {
  const name = offsetFormatterFor(timeZone).formatToParts(at)
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
 * 入力欄（datetime-local）の壁時計を、<b>その港の時刻として</b>絶対時刻へ。
 *
 * <p>現場は自分の腕時計を見て入れる。業務タイムゾーンとして送ると、時差の分
 * ずれた記録になり、<b>エラーは出ないまま履歴と予定の突き合わせが狂う</b>。</p>
 */
export function portLocalToInstant(local: string, unLocode: string | null): string {
  if (!local) {
    return '';
  }
  const zone = portTimeZone(unLocode);
  // 一度 UTC として読み、その瞬間のずれで引き戻す。ずれは日付をまたぐと
  // 変わりうるので、引き戻したあとの瞬間で決め直す（夏時間の切り替え日）。
  const naive = new Date(`${local}:00Z`);
  if (Number.isNaN(naive.getTime())) {
    return local;
  }
  const first = new Date(naive.getTime() - offsetMinutes(naive, zone) * 60_000);
  const at = new Date(naive.getTime() - offsetMinutes(first, zone) * 60_000);
  return `${at.toISOString().slice(0, 19)}Z`;
}
