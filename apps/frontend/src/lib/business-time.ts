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

/**
 * 業務タイムゾーンでの日時（`YYYY-MM-DDTHH:mm`）を UTC の ISO 8601 に変換する。
 *
 * 画面の日時入力（`datetime-local`）は端末のタイムゾーンを持たない文字列を返す。
 * `new Date(value).toISOString()` と書くと、端末の設定（CI では UTC）で解釈され、
 * 日本で入力した 09:00 が 09:00Z として送られる。業務の暦で解釈してから変換する。
 */
export function businessLocalToInstant(value: string): string {
  // **`Date` の解析に頼らない。** `new Date(':00Z')` は例外にならず、**2000 年**として
  // 読まれる。空欄のまま送ると「2000-01-01 の作業」が記録される——例外より悪い。
  // 画面の日時入力が返す形（YYYY-MM-DDTHH:mm[:ss]）だけを受け入れる
  if (!BUSINESS_LOCAL_PATTERN.test(value)) {
    throw new InvalidBusinessDateTimeError(value)
  }
  // いったん UTC として読み、その時点の業務タイムゾーンのずれを引く
  const asUtc = new Date(`${value}:00Z`)
  // **存在しない日を黙って繰り上げない。** `new Date('2027-02-30T09:00:00Z')` は
  // 3 月 2 日として読まれる。読み戻して一致するかで確かめる
  if (Number.isNaN(asUtc.getTime()) || !asUtc.toISOString().startsWith(value)) {
    throw new InvalidBusinessDateTimeError(value)
  }
  return new Date(asUtc.getTime() - businessOffsetMillis(asUtc)).toISOString()
}

/** `datetime-local` が返す形。秒は端末によって付くことがある。 */
const BUSINESS_LOCAL_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/

/**
 * 日時として読めない入力。
 *
 * 呼び出し側が「利用者に見せる誤り」として扱えるよう、専用の型にする。
 * `RangeError` のままだと、想定外の不具合と区別が付かない。
 */
export class InvalidBusinessDateTimeError extends Error {
  constructor(readonly value: string) {
    super('日時として読めません')
    this.name = 'InvalidBusinessDateTimeError'
  }
}

/** UTC の ISO 8601 を、画面の日時入力に入れられる業務タイムゾーンの文字列に戻す。 */
export function instantToBusinessLocal(isoInstant: string): string {
  const instant = new Date(isoInstant)
  const shifted = new Date(instant.getTime() + businessOffsetMillis(instant))
  return shifted.toISOString().slice(0, 16)
}

/**
 * 業務タイムゾーンでの日付（`YYYY-MM-DD`）を、その日の始まりの UTC 日時に変換する。
 *
 * サーバは期間を日時（Instant）で受け取る。日付のまま送ると解釈できず断られる。
 */
export function businessDateStartInstant(date: string): string {
  return businessLocalToInstant(`${date}T00:00`)
}

/**
 * 同じく、その日の終わり（23:59:59.999）の UTC 日時に変換する。
 *
 * サーバは翌日 00:00 の 1 ナノ秒前を期限とする（ADR-017 決定 3）。ここを 23:59:59.000 に
 * すると、その 1 秒の間に着く便だけがモックで落ちて実物で通る。写しは端をそろえる。
 */
export function businessDateEndInstant(date: string): string {
  const startOfNextMinute = new Date(businessLocalToInstant(`${date}T23:59`))
  return new Date(startOfNextMinute.getTime() + 59_999).toISOString()
}

/** 表示用。業務タイムゾーンでの日時を「YYYY-MM-DD HH:mm」で返す。 */
export function formatBusinessDateTime(isoInstant: string): string {
  return instantToBusinessLocal(isoInstant).replace('T', ' ')
}

/** その時点における業務タイムゾーンと UTC のずれ（ミリ秒）。夏時間のある地域でも正しく求まる。 */
function businessOffsetMillis(at: Date): number {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
  const parts = Object.fromEntries(formatter.formatToParts(at).map((part) => [part.type, part.value]))
  const asIfUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour === '24' ? '00' : parts.hour),
    Number(parts.minute),
    Number(parts.second),
  )
  return asIfUtc - at.getTime()
}
