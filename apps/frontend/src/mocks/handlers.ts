import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../config/api'
import {
  businessDateEndInstant,
  businessDateStartInstant,
  businessLocalToInstant,
  formatBusinessDate,
  formatBusinessDateTime,
} from '../lib/business-time'
import { ROUTING_CARGO_TYPE_LABELS, type RoutingCargoType } from '../features/routing/types'
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

type MockItineraryLeg = {
  voyageNumber: string
  loadUnLocode: string
  loadName: string
  unloadUnLocode: string
  unloadName: string
  loadTime: string
  unloadTime: string
}

type MockBooking = {
  id: number
  bookingId: string
  /** 割り当てられた旅程。決まっていなければ null（空配列にしない）。 */
  itinerary?: MockItineraryLeg[] | null
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
  /** 荷主へ通知した記録（US12-4）。通知していなければ null。 */
  routeNotifiedAt?: string | null
  routeNotifiedBy?: string | null
  /** 発行済みの追跡番号（US14）。未発行なら null。 */
  trackingNumber?: string | null
}

/** 地点マスタ（ADR-010）。到着期限の判断に使う業務タイムゾーンを持つ。 */
const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo', timeZone: 'Asia/Tokyo' },
  { unLocode: 'JPYOK', name: 'Yokohama', timeZone: 'Asia/Tokyo' },
  { unLocode: 'USLAX', name: 'Los Angeles', timeZone: 'America/Los_Angeles' },
  { unLocode: 'USNYC', name: 'New York', timeZone: 'America/New_York' },
  { unLocode: 'SGSIN', name: 'Singapore', timeZone: 'Asia/Singapore' },
]

/**
 * 引き渡し済みの予約を 1 件はじめから置く（US06）。
 *
 * 経路設計者の画面（待ち件数・絞り込み済み一覧）は、引き渡された予約が無いと何も確かめられない。
 * 営業がその場で作って渡す経路も通せるが、それだけだと「ログインし直したら消えた」ときに
 * 画面の不具合と区別がつかない。
 */
/** 一覧・詳細では荷主名を添える。社名で探せる一覧なのに名前が無いと、同名の別会社を見分けられない。 */
function withShipperName(booking: MockBooking) {
  return {
    ...booking,
    shipperName: shippers.find((shipper) => shipper.id === booking.shipperId)?.name ?? null,
    // 本物と同じく、経路が決まっていなければ null。空配列にすると画面が空の表を出す
    itinerary: booking.itinerary ?? null,
    // 本物は項目を必ず返す。省くと、画面の「通知した記録がある」の判定が undefined を
    // 見ることになり、モックだけが違う分岐を通る
    routeNotifiedAt: booking.routeNotifiedAt ?? null,
    routeNotifiedBy: booking.routeNotifiedBy ?? null,
    trackingNumber: booking.trackingNumber ?? null,
  }
}

/**
 * 追跡番号を採番する。
 *
 * 本物は DB のシーケンスが `TRK-yyyyMMdd-nnnn` を組み立てる（ADR-011 と同じ形）。
 * **形式を揃える。** 違う形式を返すと、桁数に依存した表示崩れが実物でだけ出る。
 */
let trackingNumberSequence = 0
function nextMockTrackingNumber(): string {
  trackingNumberSequence += 1
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
    .format(new Date())
    .replaceAll('-', '')
  return `TRK-${today}-${String(trackingNumberSequence).padStart(4, '0')}`
}


/**
 * 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる。
 *
 * 登録と編集で同じ検査を通す。片方だけ甘くすると、緩いほうの入口から壊れた値が入る。
 */
function invalidShipperMessage(
  body: Pick<MockShipper, 'type' | 'contractNumber' | 'discountRatePercent'>,
): string | null {
  if (body.type === 'CORPORATE' && (body.contractNumber ?? '').trim() === '') {
    return '法人荷主には契約番号が必要です'
  }
  if (
    body.type === 'INDIVIDUAL' &&
    (body.contractNumber !== null || body.discountRatePercent !== null)
  ) {
    return '契約番号と割引率は法人荷主にだけ設定できます'
  }
  if (
    body.discountRatePercent !== null &&
    (body.discountRatePercent < 0 || body.discountRatePercent > 30)
  ) {
    return `割引率は 0〜30% の範囲で指定してください: ${body.discountRatePercent}`
  }
  return null
}

/**
 * ロックされたアカウント（US32）。
 *
 * ロックされた利用者が 1 人もいないと、管理者の画面は空の一覧しか確かめられない。
 */
const lockedAccounts = [
  {
    username: 'sales02',
    displayName: '佐藤花子',
    failedAttempts: 5,
    lockedUntil: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
  },
]

const bookings: MockBooking[] = [
  {
    id: 1,
    bookingId: 'BKG-2026000001',
    shipperId: 1,
    bookingStatus: 'PRELIMINARY',
    transportStatus: 'NOT_RECEIVED',
    routingStatus: 'ROUTING_REQUESTED',
    type: 'GENERAL',
    weightKg: 1200,
    quantity: 20,
    description: '電子部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-09-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
  },
  /**
   * 経路が決まっていて、荷主へまだ通知していない予約（US12 の前提）。
   *
   * <p><strong>前提を種データとして置く。</strong>モックは画面の再読み込みで初期化されるため、
   * 利用者を切り替えて「経路設計者が確定 → 営業が通知」と辿ることができない。
   * かといって E2E に「条件が揃わなければスキップ」を書くと、前提が崩れた日に
   * 「緑だが何も確かめていない」状態になり、しかもそのことが誰にも見えない（IT5 の Try 2）。
   */
  {
    id: 2,
    bookingId: 'BKG-2026000002',
    shipperId: 1,
    bookingStatus: 'ROUTE_PROPOSED',
    transportStatus: 'NOT_RECEIVED',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 800,
    quantity: 10,
    description: '精密機器',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-09-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    itinerary: [
      {
        voyageNumber: 'V-SEED-1',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-18T00:00:00Z',
      },
    ],
  },
  /** 荷主の合意を得て確定済みの予約（US14 の前提）。経路設計者が追跡番号を発行する。 */
  {
    id: 3,
    bookingId: 'BKG-2026000003',
    shipperId: 1,
    bookingStatus: 'CONFIRMED',
    transportStatus: 'NOT_RECEIVED',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 950,
    quantity: 12,
    description: '自動車部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-09-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    itinerary: [
      {
        voyageNumber: 'V-SEED-2',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-18T00:00:00Z',
      },
    ],
  },
]
let bookingSequence = 3
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


/** 航海スケジュールのモック（IT3 / US24・US25・US07）。 */
type MockMovement = {
  departureUnLocode: string
  departureName: string
  arrivalUnLocode: string
  arrivalName: string
  departureTime: string
  arrivalTime: string
}

type MockVoyage = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  supportedCargoTypes: string[]
  originUnLocode: string
  originName: string
  destinationUnLocode: string
  destinationName: string
  departureTime: string
  arrivalTime: string
  movements: MockMovement[]
}

type MockVoyageRequest = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  supportedCargoTypes: string[]
  movements: {
    departureUnLocode: string
    arrivalUnLocode: string
    departureTime: string
    arrivalTime: string
  }[]
}

function locationName(unLocode: string): string {
  return LOCATIONS.find((location) => location.unLocode === unLocode)?.name ?? unLocode
}

function toMockVoyage(request: MockVoyageRequest): MockVoyage {
  const movements = request.movements.map((movement) => ({
    departureUnLocode: movement.departureUnLocode,
    departureName: locationName(movement.departureUnLocode),
    arrivalUnLocode: movement.arrivalUnLocode,
    arrivalName: locationName(movement.arrivalUnLocode),
    departureTime: movement.departureTime,
    arrivalTime: movement.arrivalTime,
  }))
  const last = movements[movements.length - 1]
  return {
    voyageNumber: request.voyageNumber,
    vesselName: request.vesselName,
    carrierName: request.carrierName,
    supportedCargoTypes: request.supportedCargoTypes,
    originUnLocode: movements[0].departureUnLocode,
    originName: movements[0].departureName,
    destinationUnLocode: last.arrivalUnLocode,
    destinationName: last.arrivalName,
    departureTime: movements[0].departureTime,
    arrivalTime: last.arrivalTime,
    movements,
  }
}

/** 差分。項目名・字面はサーバ実装（VoyageDifference）と同じにする。 */
function differenceOf(existing: MockVoyage, incoming: MockVoyage) {
  const port = (unLocode: string) => `${locationName(unLocode)} (${unLocode})`
  const ports = (voyage: MockVoyage) =>
    [
      port(voyage.movements[0].departureUnLocode),
      ...voyage.movements.map((m) => port(m.arrivalUnLocode)),
    ].join(' → ')
  // 全区間の出発・到着を丸ごと比べる。先頭の出発だけだと遅延の付け替えが差分に出ない
  const schedule = (voyage: MockVoyage) =>
    voyage.movements
      .map(
        (m) =>
          `${port(m.departureUnLocode)} ${formatBusinessDateTime(m.departureTime)} 発` +
          ` → ${port(m.arrivalUnLocode)} ${formatBusinessDateTime(m.arrivalTime)} 着`,
      )
      .join(' ／ ')
  const cargoTypes = (voyage: MockVoyage) =>
    (['GENERAL', 'HAZARDOUS', 'REFRIGERATED'] as RoutingCargoType[])
      .filter((cargoType) => voyage.supportedCargoTypes.includes(cargoType))
      .map((cargoType) => ROUTING_CARGO_TYPE_LABELS[cargoType])
      .join('、')
  const pairs: [string, string, string][] = [
    ['船名', existing.vesselName, incoming.vesselName],
    ['運送会社', existing.carrierName, incoming.carrierName],
    ['対応できる貨物種別', cargoTypes(existing), cargoTypes(incoming)],
    ['寄港地', ports(existing), ports(incoming)],
    ['日程', schedule(existing), schedule(incoming)],
  ]
  return pairs
    .filter(([, before, after]) => before !== after)
    .map(([item, before, after]) => ({ item, before, after }))
}


/** 積み替えに要する最低時間（ミリ秒）。サーバの TransitPath.MINIMUM_TRANSSHIPMENT と同じ 6 時間。 */
const MOCK_MINIMUM_TRANSSHIPMENT_MS = 6 * 60 * 60 * 1000

type MockLeg = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  fromUnLocode: string
  fromName: string
  toUnLocode: string
  toName: string
  departureTime: string
  arrivalTime: string
}

/** その航海で from から乗って降りられる区間を、寄港の順序どおりに挙げる。 */
function mockDeparturesFrom(voyage: MockVoyage, from: string, readyAt: string | null): MockLeg[] {
  const ports = [
    voyage.movements[0].departureUnLocode,
    ...voyage.movements.map((movement) => movement.arrivalUnLocode),
  ]
  const legs: MockLeg[] = []
  ports.forEach((port, loadOrder) => {
    if (port !== from || loadOrder >= voyage.movements.length) {
      return
    }
    const departure = voyage.movements[loadOrder].departureTime
    if (
      readyAt !== null &&
      new Date(departure).getTime() - new Date(readyAt).getTime() < MOCK_MINIMUM_TRANSSHIPMENT_MS
    ) {
      return
    }
    for (let unloadOrder = loadOrder + 1; unloadOrder <= voyage.movements.length; unloadOrder += 1) {
      const arrival = voyage.movements[unloadOrder - 1].arrivalTime
      const to = ports[unloadOrder]
      if (to === from) {
        continue
      }
      legs.push({
        voyageNumber: voyage.voyageNumber,
        vesselName: voyage.vesselName,
        carrierName: voyage.carrierName,
        fromUnLocode: from,
        fromName: LOCATIONS.find((location) => location.unLocode === from)?.name ?? from,
        toUnLocode: to,
        toName: LOCATIONS.find((location) => location.unLocode === to)?.name ?? to,
        departureTime: departure,
        arrivalTime: arrival,
      })
    }
  })
  return legs
}

/** 深さ優先で経路を挙げる。一度出た港へは戻らない（ADR-018）。 */
function findMockRoutes(
  voyages: MockVoyage[],
  from: string,
  destination: string,
  deadline: string,
  maxTransshipments: number,
  /** 荷物が出せるようになる時刻。本物と同じく、これより前に出る便には積めない（US10） */
  earliestDeparture: string | null = null,
  readyAt: string | null = null,
  visited: string[] = [],
  arrivedOn: string | null = null,
): MockLeg[][] {
  if (visited.length > maxTransshipments) {
    return []
  }
  const found: MockLeg[][] = []
  for (const voyage of voyages) {
    // 同じ船に乗り直すのは積み替えではない（サーバの TransitPathFinder と同じ規則）
    if (voyage.voyageNumber === arrivedOn) {
      continue
    }
    for (const leg of mockDeparturesFrom(voyage, from, readyAt)) {
      if (leg.arrivalTime > deadline) {
        continue
      }
      if (earliestDeparture !== null && leg.departureTime < earliestDeparture) {
        continue
      }
      if (leg.toUnLocode === destination) {
        found.push([leg])
        continue
      }
      if (visited.includes(leg.toUnLocode) || leg.toUnLocode === from) {
        continue
      }
      const rest = findMockRoutes(
        voyages,
        leg.toUnLocode,
        destination,
        deadline,
        maxTransshipments,
        earliestDeparture,
        leg.arrivalTime,
        [...visited, from],
        leg.voyageNumber,
      )
      rest.forEach((tail) => found.push([leg, ...tail]))
    }
  }
  return found
}

/** 推奨順（直行優先 → 到着の早い順 → 積み替えの少ない順）と費用の概算（ADR-018）。 */
function toMockCandidate(legs: MockLeg[], rank: number) {
  const departureTime = legs[0].departureTime
  const arrivalTime = legs[legs.length - 1].arrivalTime
  const transitDays = Math.floor(
    (new Date(arrivalTime).getTime() - new Date(departureTime).getTime()) / (24 * 60 * 60 * 1000),
  )
  // 積み替え港の待ち時間。本物と同じく、前の区間の到着から次の区間の出発まで
  const transitPorts = legs.slice(1).map((leg, index) => ({
    unLocode: leg.fromUnLocode,
    name: leg.fromName,
    layoverMinutes: Math.round(
      (new Date(leg.departureTime).getTime() - new Date(legs[index].arrivalTime).getTime()) / 60000,
    ),
  }))
  return {
    rank,
    direct: legs.length === 1,
    voyageNumbers: legs.map((leg) => leg.voyageNumber),
    departureTime,
    arrivalTime,
    transitDays,
    transshipmentCount: legs.length - 1,
    transitPorts,
    estimatedCost: 200000 * legs.length + 30000 * transitDays + 50000 * (transitPorts.length + 2),
    legs,
  }
}

/**
 * 動作確認用の航海を最初から置く（IT4 / US08）。
 *
 * 経路候補の画面は、探索の材料が無いと何も確かめられない。その場で登録する経路も
 * 通せるが、モックは画面を読み直すと消えるため、それだけだと「読み直したら候補が
 * 消えた」ときに画面の不具合と区別がつかない（引き渡し済みの予約を置いたのと同じ理由）。
 *
 * 直行 1 本と、シンガポールで積み替える 2 本。**直行のほうが遅く着く**ようにしてある。
 * 推奨順が「直行を最優先」であることを、順序が入れ替わる形で確かめられる。
 */
function seedVoyage(
  voyageNumber: string,
  legs: [string, string, number, number][],
): MockVoyage {
  const movements = legs.map(([from, to, departureDays, arrivalDays]) => ({
    departureUnLocode: from,
    departureName: LOCATIONS.find((location) => location.unLocode === from)?.name ?? from,
    arrivalUnLocode: to,
    arrivalName: LOCATIONS.find((location) => location.unLocode === to)?.name ?? to,
    departureTime: daysFromNow(departureDays),
    arrivalTime: daysFromNow(arrivalDays),
  }))
  return {
    voyageNumber,
    vesselName: `${voyageNumber} 丸`,
    carrierName: 'デモ海運',
    supportedCargoTypes: ['GENERAL', 'REFRIGERATED'],
    originUnLocode: movements[0].departureUnLocode,
    originName: movements[0].departureName,
    destinationUnLocode: movements[movements.length - 1].arrivalUnLocode,
    destinationName: movements[movements.length - 1].arrivalName,
    departureTime: movements[0].departureTime,
    arrivalTime: movements[movements.length - 1].arrivalTime,
    movements,
  }
}

/** 今日から n 日後の 09:00（業務タイムゾーン）を UTC の ISO 8601 で返す。 */
function daysFromNow(days: number): string {
  const at = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
  return businessLocalToInstant(`${formatBusinessDate(at)}T09:00`)
}

const voyages: MockVoyage[] = [
  // 直行。遅く着くが積み替えが無い。**途中で横浜に寄る**ので、航海詳細で
  // 寄港地と区間ごとの時刻を確かめられる（1 区間の便だと詳細画面の意味が伝わらない）
  seedVoyage('DEMO-DIRECT', [
    ['JPTYO', 'JPYOK', 10, 11],
    ['JPYOK', 'USLAX', 12, 28],
  ]),
  // 積み替え。早く着くが 1 回積み替える
  seedVoyage('DEMO-LEG1', [['JPTYO', 'SGSIN', 11, 14]]),
  seedVoyage('DEMO-LEG2', [['SGSIN', 'USLAX', 15, 26]]),
]


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

  http.get(`${API_PATHS.shippers}/:id`, ({ params }) => {
    const found = shippers.find((s) => s.id === Number(params.id))
    return found === undefined
      ? HttpResponse.json({ message: '指定された荷主が見つかりません' }, { status: 404 })
      : HttpResponse.json(found)
  }),

  // 編集（US02 / #550）。重複の問いかけは無い。すでにどの荷主かが分かっているため
  http.put(`${API_PATHS.shippers}/:id`, async ({ params, request }) => {
    const found = shippers.find((s) => s.id === Number(params.id))
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された荷主が見つかりません' }, { status: 404 })
    }

    const body = (await request.json()) as MockShipper
    // 種別は変えられない（本物と同じ規則）。黙って無視すると、原因と無関係な
    // 「契約番号が必要です」が返り、利用者は何度直しても通らない
    if (body.type !== found.type) {
      return HttpResponse.json(
        { message: '荷主種別は変更できません。種別が違うなら、それは別の荷主です' },
        { status: 400 },
      )
    }
    const invalid = invalidShipperMessage(body)
    if (invalid !== null) {
      return HttpResponse.json({ message: invalid }, { status: 400 })
    }

    // 荷主コードと id は変わらない。変わると、予約から見た荷主が別人になる
    found.name = body.name
    found.email = body.email
    found.address = body.address
    found.phone = body.phone ?? null
    found.contractNumber = body.contractNumber ?? null
    found.discountRatePercent = body.discountRatePercent ?? null
    return HttpResponse.json(found)
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
      : HttpResponse.json(withShipperName(found))
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
    return HttpResponse.json(withShipperName(found))
  }),

  // 条件協議の差し戻し（US10・ADR-020 決定 7）。本物と同じ規則で拒む
  http.post(`${API_PATHS.bookings}/:bookingId/consultation-request`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.routingStatus !== 'ROUTING_REQUESTED') {
      return HttpResponse.json(
        { message: '経路設計を依頼された予約だけが、条件の協議を営業へ戻せます' },
        { status: 409 },
      )
    }
    found.routingStatus = 'CONSULTATION_REQUESTED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 経路の割り当て（US09・ADR-019）。
   *
   * 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる。
   * 認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
   * Gateway だからである。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/route`, async ({ params, request }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }

    const body = (await request.json()) as { legs?: MockLeg[]; maxTransshipments?: number }
    const legs = body.legs ?? []
    if (legs.length === 0) {
      return HttpResponse.json(
        { message: '割り当てる経路の区間を指定してください' },
        { status: 400 },
      )
    }
    // ADR-020 決定 1・4: 引き渡された予約か、すでに経路が決まった予約にだけ割り当てられる。
    // 本物は「それ以外」を拒む。ここで NOT_ROUTED だけを見ると、差し戻し中の予約を
    // モックだけが通し、実物でだけ 409 になる
    if (found.routingStatus !== 'ROUTING_REQUESTED' && found.routingStatus !== 'ROUTED') {
      return HttpResponse.json(
        { message: '経路設計を依頼された予約にだけ経路を割り当てられます' },
        { status: 409 },
      )
    }
    // ADR-021 決定 3: 確定したあとは差し替えられない。差し替えを許すと、
    // 「確定から経路設計へ戻せない」を裏口から破ることになる
    if (found.bookingStatus === 'CONFIRMED' || found.bookingStatus === 'TRACKING_ISSUED') {
      return HttpResponse.json(
        {
          message:
            '確定した予約の経路は差し替えられません。変更が必要なら担当者に相談してください',
        },
        { status: 409 },
      )
    }

    // ADR-019 決定 2: 選んだ経路がいまも算出できるかを確かめる。
    // 確かめずに通すと、欠航した航海の旅程が予約に入る
    const deadlineInstant = businessDateEndInstant(found.arrivalDeadline)
    const now = new Date().toISOString()
    const usable = voyages.filter(
      (voyage) => voyage.supportedCargoTypes.includes(found.type) && voyage.departureTime >= now,
    )
    const stillAvailable = findMockRoutes(
      usable,
      found.originUnLocode,
      found.destinationUnLocode,
      deadlineInstant,
      body.maxTransshipments ?? 2,
      found.departureDate === null ? null : businessDateStartInstant(found.departureDate),
    ).some(
      (candidate) =>
        candidate.length === legs.length &&
        candidate.every(
          (leg, index) =>
            leg.voyageNumber === legs[index].voyageNumber &&
            leg.fromUnLocode === (legs[index] as unknown as { loadUnLocode: string }).loadUnLocode &&
            leg.toUnLocode ===
              (legs[index] as unknown as { unloadUnLocode: string }).unloadUnLocode &&
            // 本物は時刻まで含めて等価判定する。ここで見ないと、時刻がずれた旅程が
            // 画面テストでは通り、実物でだけ 409 になる
            leg.departureTime === (legs[index] as unknown as { loadTime: string }).loadTime &&
            leg.arrivalTime === (legs[index] as unknown as { unloadTime: string }).unloadTime,
        ),
    )
    if (!stillAvailable) {
      return HttpResponse.json(
        {
          message:
            '選んだ経路はもう使えません。航海スケジュールが変わっている可能性があります。経路をもう一度探してください',
        },
        { status: 409 },
      )
    }

    // 地点の名称はサーバがマスタから引く（画面が送った名称を信じない）
    found.itinerary = legs.map((leg) => {
      const load = (leg as unknown as { loadUnLocode: string }).loadUnLocode
      const unload = (leg as unknown as { unloadUnLocode: string }).unloadUnLocode
      return {
        voyageNumber: leg.voyageNumber,
        loadUnLocode: load,
        loadName: LOCATIONS.find((location) => location.unLocode === load)?.name ?? load,
        unloadUnLocode: unload,
        unloadName: LOCATIONS.find((location) => location.unLocode === unload)?.name ?? unload,
        loadTime: (leg as unknown as { loadTime: string }).loadTime,
        unloadTime: (leg as unknown as { unloadTime: string }).unloadTime,
      }
    })
    found.routingStatus = 'ROUTED'
    found.bookingStatus = 'ROUTE_PROPOSED'
    // 差し替えたら通知の記録は消える。残すと、画面は「通知しました」と出したまま
    // 経路だけが変わり、営業は変わったことに気づかない
    found.routeNotifiedAt = null
    found.routeNotifiedBy = null
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 予約の日程の訂正（US06 の訂正）。
   *
   * 本物（`Cargo#reviseSchedule`）の条件を読み比べて写した。**引き渡す前か、営業へ戻された
   * 予約だけ**が直せる。経路設計者が組んでいる最中に条件が変わると、出来上がった経路が
   * 条件を満たさなくなる。直せるのは日程だけである。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/schedule`, async ({ params, request }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (
      found.routingStatus !== 'NOT_ROUTED' &&
      found.routingStatus !== 'CONSULTATION_REQUESTED'
    ) {
      return HttpResponse.json(
        { message: '経路設計に引き渡す前か、営業へ戻された予約だけを直せます' },
        { status: 409 },
      )
    }
    const body = (await request.json()) as {
      departureDate?: string | null
      arrivalDeadline?: string | null
    }
    if (body.arrivalDeadline === null || (body.arrivalDeadline ?? '') === '') {
      return HttpResponse.json({ message: '到着期限は必須です' }, { status: 400 })
    }
    // 本物は目的地の暦で「今日」を決める（ADR-010）。モックも同じ地点の暦で判断する
    const destinationZone =
      LOCATIONS.find((location) => location.unLocode === found.destinationUnLocode)?.timeZone
      ?? 'Asia/Tokyo'
    if (body.arrivalDeadline < todayAt(destinationZone)) {
      return HttpResponse.json(
        { message: `到着期限に過去の日付は指定できません: ${body.arrivalDeadline}` },
        { status: 400 },
      )
    }
    if (
      (body.departureDate ?? '') !== '' &&
      (body.departureDate as string) > body.arrivalDeadline
    ) {
      return HttpResponse.json(
        { message: '希望出発日が到着期限より後になっています' },
        { status: 400 },
      )
    }
    found.departureDate = (body.departureDate ?? '') === '' ? null : (body.departureDate as string)
    found.arrivalDeadline = body.arrivalDeadline
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 荷主への通知（US12・ADR-021 決定 1・決定 2）。
   *
   * 本物（`Cargo#notifyShipper`）の条件を読み比べて写した。**経路が決まった予約か、
   * すでに通知した予約**だけが通知でき、再通知は記録を最新で上書きする。
   * ここを緩くすると、画面は「動く」まま本物でだけ 409 になる。
   */
  http.post(`${API_PATHS.bookings}/:bookingId/route-notification`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_PROPOSED' && found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '経路が決まった予約だけを荷主へ通知できます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'ROUTE_NOTIFIED'
    // 記録は最新で上書きする（ADR-021 決定 2。履歴は US19 まで持たない）
    found.routeNotifiedAt = new Date().toISOString()
    found.routeNotifiedBy = 'sales01'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 予約の確定（US13-2・ADR-021 決定 1）。
   *
   * **通知していない予約は確定できない。** 確定は「荷主の合意を得た」という業務上の
   * 事実であり、提示していない条件で合意は成り立たない。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/confirm`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '荷主へ通知した予約だけを確定できます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'CONFIRMED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 経路設計へ戻す（US13-4・ADR-021 決定 3・決定 4）。
   *
   * **経路の状態も戻す。** `bookingStatus` だけ戻しても経路設計者の作業待ちに現れず、
   * 荷主が変更を希望したことが誰にも伝わらない。**旅程は消さない**（見直しの起点になる）。
   * **確定したあとは戻せない**（決定 3）。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/return-to-routing`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '荷主へ通知した予約だけを経路設計へ戻せます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'ROUTE_PROPOSED'
    found.routingStatus = 'ROUTING_REQUESTED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 追跡番号の発行（US14）。
   *
   * **確定した予約にだけ発行でき、二重には発行しない**（番号が変わると、荷主に伝えた
   * 番号で追えなくなる）。形式は本物と同じ `TRK-yyyyMMdd-nnnn` にする。
   * 形式が違うと、画面の表示崩れが実物でだけ出る。
   */
  http.post(`${API_PATHS.bookings}/:bookingId/tracking-number`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'CONFIRMED') {
      return HttpResponse.json(
        { message: '確定した予約にだけ追跡番号を発行できます' },
        { status: 409 },
      )
    }
    // null も未設定も「未発行」。項目を省いた種データが 409 になった（画面で踏んだのと同じ形）
    if ((found.trackingNumber ?? null) !== null) {
      return HttpResponse.json(
        { message: 'この予約はすでに追跡番号を発行しています' },
        { status: 409 },
      )
    }
    found.trackingNumber = nextMockTrackingNumber()
    found.bookingStatus = 'TRACKING_ISSUED'
    // 貨物はまだ動いていない（US14-3）
    found.transportStatus = 'NOT_RECEIVED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * ロックされたアカウント（US32-1）。
   *
   * 本物と同じく<strong>期限切れは含めない</strong>。含めると、管理者は要らない作業をする。
   * パスワードもメールアドレスも返さない（本物が返さないものをモックが返すと、
   * 画面がそれに依存しても気づけない）。
   */
  http.get(API_PATHS.lockedAccounts, () =>
    HttpResponse.json(
      lockedAccounts.filter((account) => new Date(account.lockedUntil) > new Date()),
    ),
  ),

  /** ロックの解除（US32-2）。解除した管理者はサーバが利用者ヘッダから取る。 */
  http.post('/api/v1/admin/accounts/:username/unlock', ({ params }) => {
    const index = lockedAccounts.findIndex((account) => account.username === params.username)
    if (index < 0) {
      return HttpResponse.json({ message: '指定されたアカウントが見つかりません' }, { status: 404 })
    }
    const [removed] = lockedAccounts.splice(index, 1)
    // 失敗回数も 0 に戻す。期限だけ消すと、次の 1 回でまたロックされる
    return HttpResponse.json({ ...removed, failedAttempts: 0, lockedUntil: null })
  }),

  http.get(API_PATHS.bookings, ({ request }) => {
    const params = new URL(request.url).searchParams
    const type = params.get('type')
    const routingStatus = params.get('routingStatus')
    const bookingStatus = params.get('bookingStatus')
    const keyword = (params.get('keyword') ?? '').trim().toLowerCase()

    const matched = bookings.filter((booking) => {
      if (type !== null && booking.type !== type) {
        return false
      }
      // 経路設計待ちだけを見る絞り込み（US06）
      if (routingStatus !== null && booking.routingStatus !== routingStatus) {
        return false
      }
      // 追跡番号の発行待ちだけを見る絞り込み（US13-3）。本物と同じ条件で絞る
      if (bookingStatus !== null && booking.bookingStatus !== bookingStatus) {
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

    const invalid = invalidShipperMessage(body)
    if (invalid !== null) {
      return HttpResponse.json({ message: invalid }, { status: 400 })
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

  // ---- 航海スケジュール（IT3 / US24・US25・US07）----

  http.get(API_PATHS.voyageLocations, () =>
    HttpResponse.json(LOCATIONS.map(({ unLocode, name }) => ({ unLocode, name }))),
  ),

  http.get(API_PATHS.voyages, ({ request }) => {
    const params = new URL(request.url).searchParams
    const origin = params.get('origin')
    const destination = params.get('destination')
    const departureFrom = params.get('departureFrom')
    const departureTo = params.get('departureTo')
    const cargoType = params.get('cargoType')

    const matched = voyages.filter((voyage) => {
      const ports = [
        voyage.movements[0].departureUnLocode,
        ...voyage.movements.map((movement) => movement.arrivalUnLocode),
      ]
      // 寄港の順序で絞る。同じ港に寄ることと、その向きに運べることは別である
      if (origin !== null && destination !== null) {
        const from = ports.indexOf(origin)
        const to = ports.lastIndexOf(destination)
        if (from < 0 || to < 0 || from >= to) {
          return false
        }
      } else if (origin !== null && !ports.slice(0, -1).includes(origin)) {
        return false
      } else if (destination !== null && !ports.slice(1).includes(destination)) {
        return false
      }
      if (departureFrom !== null && voyage.departureTime < departureFrom) {
        return false
      }
      if (departureTo !== null && voyage.departureTime > departureTo) {
        return false
      }
      if (cargoType !== null && !voyage.supportedCargoTypes.includes(cargoType)) {
        return false
      }
      return true
    })

    const limit = 50
    return HttpResponse.json({
      voyages: matched.slice(0, limit),
      totalCount: matched.length,
      limit,
      truncated: matched.length > limit,
    })
  }),

  // ---- 経路候補算出（IT4 / US08。ADR-017・ADR-018）----

  /**
   * 動作確認用の経路探索。
   *
   * **本物より甘くしない。** 期限を日付として業務タイムゾーンの当日終わりまでで判断し、
   * 貨物種別・積み替えの上限・積み替えの最低時間・推奨順・費用の式まで、サーバと同じ
   * 規則をなぞる。ここを緩めると、モックでだけ通る経路が画面に出て、実バックエンドで
   * 消える（IT3 で同じ形の欠陥が実バックエンドでだけ落ちた）。
   *
   * 規則そのものはサーバのドメインが正であり、ここは写し。実物との食い違いは
   * `real-backend.spec.ts` が捕まえる。
   */
  http.get(API_PATHS.routes, ({ request }) => {
    const params = new URL(request.url).searchParams
    const origin = params.get('origin') ?? ''
    const destination = params.get('destination') ?? ''
    const deadline = params.get('deadline') ?? ''
    const cargoType = params.get('cargoType') ?? 'GENERAL'
    const maxTransshipments = Number(params.get('maxTransshipments') ?? '2')

    // 本物が断る入力は、ここでも断る。通してしまうと「モックでは動くのに実物で 400」になる。
    // 認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
    // Gateway だからである。ロールの検査は実バックエンドの検査が受け持つ
    const originLocation = LOCATIONS.find((location) => location.unLocode === origin)
    const destinationLocation = LOCATIONS.find((location) => location.unLocode === destination)
    if (originLocation === undefined) {
      return HttpResponse.json({ message: '出発地が見つかりません' }, { status: 400 })
    }
    if (destinationLocation === undefined) {
      return HttpResponse.json({ message: '目的地が見つかりません' }, { status: 400 })
    }
    if (origin === destination) {
      return HttpResponse.json({ message: '出発地と目的地は同じにできません' }, { status: 400 })
    }
    if (deadline === '') {
      return HttpResponse.json({ message: '到着期限を指定してください' }, { status: 400 })
    }
    if (params.get('cargoType') === null) {
      return HttpResponse.json({ message: '貨物種別を指定してください' }, { status: 400 })
    }
    if (!Number.isInteger(maxTransshipments) || maxTransshipments < 0) {
      return HttpResponse.json({ message: '積み替えの上限は 0 以上にしてください' }, { status: 400 })
    }
    if (maxTransshipments > 3) {
      return HttpResponse.json({ message: '積み替えの上限は 3 回までにしてください' }, { status: 400 })
    }

    // 期限は日付。業務タイムゾーンのその日の終わりまでに着けばよい（ADR-017 決定 3）
    const deadlineInstant = businessDateEndInstant(deadline)
    // 出発希望日は、業務タイムゾーンでのその日の始まりが境目（US10）
    const earliestDeparture = params.get('earliestDeparture')
    const earliestDepartureInstant =
      earliestDeparture === null ? null : businessDateStartInstant(earliestDeparture)
    const now = new Date().toISOString()
    const usable = voyages.filter(
      (voyage) => voyage.supportedCargoTypes.includes(cargoType) && voyage.departureTime >= now,
    )

    const candidates = findMockRoutes(
      usable,
      origin,
      destination,
      deadlineInstant,
      maxTransshipments,
      earliestDepartureInstant,
    ).sort((a, b) => {
      const direct = Number(b.length === 1) - Number(a.length === 1)
      if (direct !== 0) return direct
      const arrival = a[a.length - 1].arrivalTime.localeCompare(b[b.length - 1].arrivalTime)
      if (arrival !== 0) return arrival
      return a.length - b.length
    })

    return HttpResponse.json({
      candidates: candidates.map((legs, index) => toMockCandidate(legs, index + 1)),
      totalCount: candidates.length,
      appliedCriteria: {
        originUnLocode: origin,
        originName: originLocation.name,
        destinationUnLocode: destination,
        destinationName: destinationLocation.name,
        arrivalDeadline: deadlineInstant,
        cargoType,
        maxTransshipments,
        earliestDeparture: earliestDepartureInstant,
      },
    })
  }),

  http.get(`${API_PATHS.voyages}/:voyageNumber`, ({ params }) => {
    const found = voyages.find((voyage) => voyage.voyageNumber === params.voyageNumber)
    return found === undefined
      ? HttpResponse.json({ message: '指定された航海が見つかりません' }, { status: 404 })
      : HttpResponse.json(found)
  }),

  http.post(API_PATHS.voyages, async ({ request }) => {
    const body = (await request.json()) as MockVoyageRequest
    const existing = voyages.find((voyage) => voyage.voyageNumber === body.voyageNumber)
    if (existing !== undefined) {
      // 重複は失敗ではなく、上書きするかを選ばせるための応答である
      const incoming = toMockVoyage(body)
      const changes = differenceOf(existing, incoming)
      return HttpResponse.json(
        {
          message:
            changes.length === 0
              ? '同じ航海番号のスケジュールが既に登録されています。変更はありません'
              : '同じ航海番号のスケジュールが既に登録されています',
          hasChanges: changes.length > 0,
          existing,
          changes,
        },
        { status: 409 },
      )
    }
    const created = toMockVoyage(body)
    voyages.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put(`${API_PATHS.voyages}/:voyageNumber`, async ({ request, params }) => {
    const body = (await request.json()) as MockVoyageRequest
    const index = voyages.findIndex((voyage) => voyage.voyageNumber === params.voyageNumber)
    if (index < 0) {
      return HttpResponse.json({ message: '指定された航海が見つかりません' }, { status: 404 })
    }
    const updated = toMockVoyage(body)
    voyages[index] = updated
    return HttpResponse.json(updated)
  }),
]
