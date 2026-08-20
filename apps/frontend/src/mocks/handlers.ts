import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../config/api'
import { MOCK_USERS } from './users'

type MockShipper = {
  id: number
  shipperCode: string
  type: 'INDIVIDUAL' | 'CORPORATE'
  name: string
  email: string
  address: string
  phone: string | null
  contractNumber: string | null
  discountRatePercent: number | null
}

/**
 * 初期データ。
 *
 * 実際の開発環境にはシードされた荷主がいる。モックだけが空だと、貨物予約の画面を
 * 開いても荷主が 1 件も選べず、画面を触って確かめられない。
 */
const SEED_SHIPPERS: MockShipper[] = [
  {
    id: 1,
    shipperCode: 'SHP-000001',
    type: 'CORPORATE',
    name: '丸紅商事株式会社',
    email: 'marubeni@example.com',
    address: '東京都千代田区 1-1-1',
    phone: '03-1234-5678',
    contractNumber: 'CN-2026-0001',
    discountRatePercent: 10,
  },
  {
    id: 2,
    shipperCode: 'SHP-000002',
    type: 'INDIVIDUAL',
    name: '山田太郎',
    email: 'yamada@example.com',
    address: '神奈川県横浜市 2-2-2',
    phone: null,
    contractNumber: null,
    discountRatePercent: null,
  },
]

const shippers: MockShipper[] = SEED_SHIPPERS.map((shipper) => ({ ...shipper }))
let sequence = SEED_SHIPPERS.length

type MockBooking = {
  id: number
  bookingId: string
  shipperId: number
  bookingStatus: string
  transportStatus: string
  routingStatus: string
  type: 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'
  weightKg: number
  quantity: number | null
  description: string | null
  lengthCm: number | null
  widthCm: number | null
  heightCm: number | null
  originUnLocode: string
  originName: string
  destinationUnLocode: string
  destinationName: string
  departureDate: string | null
  arrivalDeadline: string
  hazardousClass: string | null
  unNumber: string | null
  properShippingName: string | null
  minCelsius: number | null
  maxCelsius: number | null
}

/** 地点マスタ（ADR-010）。到着期限の判断に使う業務タイムゾーンを持つ。 */
const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo', timeZone: 'Asia/Tokyo' },
  { unLocode: 'JPYOK', name: 'Yokohama', timeZone: 'Asia/Tokyo' },
  { unLocode: 'USLAX', name: 'Los Angeles', timeZone: 'America/Los_Angeles' },
  { unLocode: 'USNYC', name: 'New York', timeZone: 'America/New_York' },
  { unLocode: 'SGSIN', name: 'Singapore', timeZone: 'Asia/Singapore' },
]

const bookings: MockBooking[] = []
let bookingSequence = 0
const BOOKING_LIMIT = 100

/** 目的地の暦での「今日」。UTC で判断すると、時差の分だけ受付が拒否される時間帯ができる。 */
function todayAt(timeZone: string) {
  return new Intl.DateTimeFormat('en-CA', { timeZone }).format(new Date())
}

/**
 * 連続失敗によるロック（US31）。本物と同じ回数・同じ応答で振る舞う。
 *
 * ここを実装しないと、画面が「5 回間違えると入れない」ことを一度も通らないまま
 * 「実装済み」になる。モックは仕様の写しであって、都合のよい相手ではない。
 */
const MAX_FAILED_ATTEMPTS = 5
const failedAttempts = new Map<string, number>()

function isLocked(userId: string) {
  return (failedAttempts.get(userId) ?? 0) >= MAX_FAILED_ATTEMPTS
}

export const handlers = [
  http.post(API_PATHS.login, async ({ request }) => {
    const { userId, password } = (await request.json()) as { userId: string; password: string }
    const user = MOCK_USERS[userId]

    const failure = HttpResponse.json(
      { message: '利用者 ID またはパスワードが正しくありません' },
      { status: 401 },
    )

    // ロック中は正しいパスワードでも入れない。理由は返さない（US31）
    if (isLocked(userId)) {
      return failure
    }

    // 失敗の理由は返さない。存在しない利用者・誤ったパスワード・無効化を同じ応答にする（US31）
    if (user === undefined || user.password !== password || !user.enabled) {
      failedAttempts.set(userId, (failedAttempts.get(userId) ?? 0) + 1)
      return failure
    }

    failedAttempts.delete(userId)

    return HttpResponse.json({
      token: `mock-token-${userId}`,
      userId,
      displayName: user.displayName,
      roles: user.roles,
    })
  }),

  http.get(API_PATHS.shippers, ({ request }) => {
    const keyword = new URL(request.url).searchParams.get('keyword')
    if (keyword === null || keyword.trim() === '') {
      return HttpResponse.json(shippers)
    }
    const lower = keyword.toLowerCase()
    return HttpResponse.json(
      shippers.filter(
        (s) => s.name.toLowerCase().includes(lower) || s.email.toLowerCase().includes(lower),
      ),
    )
  }),

  http.get(API_PATHS.bookingLocations, () =>
    HttpResponse.json(LOCATIONS.map(({ unLocode, name }) => ({ unLocode, name }))),
  ),

  // 分類の表示名はサーバが持つ（画面に対訳表を置くと直しが 2 箇所に分かれる）
  http.get(API_PATHS.bookingHazardClasses, () =>
    HttpResponse.json([
      { code: '1', label: '火薬類' },
      { code: '2', label: '高圧ガス' },
      { code: '3', label: '引火性液体' },
      { code: '4', label: '可燃性物質（可燃性固体・自然発火性物質・水反応可燃性物質）' },
      { code: '5', label: '酸化性物質・有機過酸化物' },
      { code: '6', label: '毒物・病毒をうつしやすい物質' },
      { code: '7', label: '放射性物質' },
      { code: '8', label: '腐食性物質' },
      { code: '9', label: '有害性物質' },
    ]),
  ),

  http.get(`${API_PATHS.bookings}/:bookingId`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    return found === undefined
      ? HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
      : HttpResponse.json(found)
  }),

  http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.routingStatus !== 'NOT_ROUTED') {
      return HttpResponse.json(
        { message: 'この予約はすでに経路設計を依頼しています' },
        { status: 409 },
      )
    }
    found.routingStatus = 'ROUTING_REQUESTED'
    return HttpResponse.json(found)
  }),

  http.get(API_PATHS.bookings, ({ request }) => {
    const params = new URL(request.url).searchParams
    const type = params.get('type')
    const routingStatus = params.get('routingStatus')
    const keyword = (params.get('keyword') ?? '').trim().toLowerCase()

    const matched = bookings.filter((booking) => {
      if (type !== null && booking.type !== type) {
        return false
      }
      // 経路設計待ちだけを見る絞り込み（US06）
      if (routingStatus !== null && booking.routingStatus !== routingStatus) {
        return false
      }
      if (keyword === '') {
        return true
      }
      const shipper = shippers.find((s) => s.id === booking.shipperId)
      return (
        booking.bookingId.toLowerCase().includes(keyword) ||
        (shipper?.name ?? '').toLowerCase().includes(keyword)
      )
    })

    // 新しい順。登録順だと、今入れた 1 件が常に最下部に沈む
    // 荷主名はサーバが結合して返す。モックだけが返さないと、画面の列が
    // モックのときだけ空欄になり「実装していない」ように見える
    const newestFirst = [...matched].reverse().map((booking) => ({
      ...booking,
      shipperName: shippers.find((s) => s.id === booking.shipperId)?.name ?? null,
    }))
    return HttpResponse.json({
      bookings: newestFirst.slice(0, BOOKING_LIMIT),
      totalCount: matched.length,
      limit: BOOKING_LIMIT,
      truncated: matched.length > BOOKING_LIMIT,
    })
  }),

  http.post(API_PATHS.bookings, async ({ request }) => {
    const body = (await request.json()) as Omit<
      MockBooking,
      'id' | 'bookingId' | 'bookingStatus' | 'transportStatus' | 'routingStatus'
      | 'originName' | 'destinationName'
    >

    // 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる
    const invalid = (message: string) => HttpResponse.json({ message }, { status: 400 })

    if (!shippers.some((s) => s.id === body.shipperId)) {
      return invalid(`指定された荷主が見つかりません: ${body.shipperId}`)
    }
    const origin = LOCATIONS.find((l) => l.unLocode === body.originUnLocode)
    const destination = LOCATIONS.find((l) => l.unLocode === body.destinationUnLocode)
    if (origin === undefined) {
      return invalid(`出発地が見つかりません: ${body.originUnLocode}`)
    }
    if (destination === undefined) {
      return invalid(`目的地が見つかりません: ${body.destinationUnLocode}`)
    }
    if (origin.unLocode === destination.unLocode) {
      return invalid(`出発地と目的地は同じにできません: ${origin.unLocode}`)
    }
    if (body.arrivalDeadline < todayAt(destination.timeZone)) {
      return invalid(`到着期限に過去の日付は指定できません: ${body.arrivalDeadline}`)
    }
    if (!(body.weightKg > 0)) {
      return invalid(`重量は 0 より大きい値で指定してください: ${body.weightKg}`)
    }

    const hasDeclaration = body.unNumber !== null || body.hazardousClass !== null
    const hasTemperature = body.minCelsius !== null || body.maxCelsius !== null
    if (body.type === 'HAZARDOUS' && !hasDeclaration) {
      return invalid('危険物には危険物申告が必要です')
    }
    if (body.type !== 'HAZARDOUS' && hasDeclaration) {
      return invalid('危険物申告は危険物にだけ設定できます')
    }
    if (body.type === 'REFRIGERATED' && !hasTemperature) {
      return invalid('冷凍・冷蔵貨物には保管温度の条件が必要です')
    }
    if (body.type !== 'REFRIGERATED' && hasTemperature) {
      return invalid('保管温度の条件は冷凍・冷蔵貨物にだけ設定できます')
    }
    if (body.type === 'HAZARDOUS' && !/^UN\d{4}$/.test(body.unNumber ?? '')) {
      return invalid(`UN 番号の形式が不正です（UN + 4 桁）: ${body.unNumber}`)
    }
    if (
      body.type === 'REFRIGERATED' &&
      (body.minCelsius === null || body.maxCelsius === null)
    ) {
      return invalid('保管温度の下限と上限はどちらも必須です')
    }
    if (
      body.minCelsius !== null &&
      body.maxCelsius !== null &&
      body.minCelsius > body.maxCelsius
    ) {
      return invalid(
        `保管温度の下限が上限を超えています: ${body.minCelsius} > ${body.maxCelsius}`,
      )
    }

    bookingSequence += 1
    const created: MockBooking = {
      ...body,
      id: bookingSequence,
      // 予約番号の形式は契約になる（ADR-011）
      bookingId: `BKG-2026${String(bookingSequence).padStart(6, '0')}`,
      bookingStatus: 'PRELIMINARY',
      transportStatus: 'NOT_RECEIVED',
      routingStatus: 'NOT_ROUTED',
      originName: origin.name,
      destinationName: destination.name,
    }
    bookings.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.post(API_PATHS.shippers, async ({ request }) => {
    const body = (await request.json()) as MockShipper & { registerAnyway: boolean }

    // 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる
    if (body.type === 'CORPORATE' && (body.contractNumber ?? '').trim() === '') {
      return HttpResponse.json({ message: '法人荷主には契約番号が必要です' }, { status: 400 })
    }
    if (body.type === 'INDIVIDUAL' && (body.contractNumber !== null || body.discountRatePercent !== null)) {
      return HttpResponse.json(
        { message: '契約番号と割引率は法人荷主にだけ設定できます' },
        { status: 400 },
      )
    }
    if (
      body.discountRatePercent !== null &&
      (body.discountRatePercent < 0 || body.discountRatePercent > 30)
    ) {
      return HttpResponse.json(
        { message: `割引率は 0〜30% の範囲で指定してください: ${body.discountRatePercent}` },
        { status: 400 },
      )
    }

    const existing = shippers.find((s) => s.email === body.email)

    if (existing !== undefined && !body.registerAnyway) {
      return HttpResponse.json(
        { message: '同じメールアドレスの荷主が既に登録されています', existing },
        { status: 409 },
      )
    }

    sequence += 1
    const created: MockShipper = {
      id: sequence,
      shipperCode: `SHP-${String(sequence).padStart(6, '0')}`,
      type: body.type,
      name: body.name,
      email: body.email,
      address: body.address,
      phone: body.phone ?? null,
      contractNumber: body.contractNumber ?? null,
      discountRatePercent: body.discountRatePercent ?? null,
    }
    shippers.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
