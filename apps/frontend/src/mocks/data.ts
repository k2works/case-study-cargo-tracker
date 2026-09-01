/**
 * モックの状態と、複数のハンドラが共有する道具。
 *
 * <p><strong>状態はここ 1 か所に置く。</strong>ハンドラのファイルごとに配列を持つと、
 * 予約を作ったのに一覧に出ない、といった食い違いが起きる。
 *
 * <p>本物と同じ規則で拒むのがモックの役目である。モックだけが甘いと、画面は「動く」まま
 * 本番で落ちる。<strong>規則を写すときは、本物の該当箇所を開いて条件を読み比べる</strong>
 * （IT5 の Try 4）。
 */
import { ROUTING_CARGO_TYPE_LABELS, type RoutingCargoType } from '../features/routing/types'
import { formatBusinessDateTime } from '../lib/business-time'

export type MockShipper = {
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
export const SEED_SHIPPERS: MockShipper[] = [
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

export const shippers: MockShipper[] = SEED_SHIPPERS.map((shipper) => ({ ...shipper }))
/**
 * 採番のカウンタ。
 *
 * <p><strong>オブジェクトで持つ。</strong>`let` を export すると、読み込む側は値の写しを
 * 受け取るため、ハンドラ側で増やしても他のファイルからは増えて見えない。
 */
export const sequenceState = { shipper: SEED_SHIPPERS.length, booking: 0, trackingNumber: 0 }

export type MockItineraryLeg = {
  voyageNumber: string
  loadUnLocode: string
  loadName: string
  unloadUnLocode: string
  unloadName: string
  loadTime: string
  unloadTime: string
}

export type MockBooking = {
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
  /**
   * 最後の荷役があった地点（[ADR-025] 決定 1・4）。
   *
   * 荷役のイベントを購読して更新する。**陸揚げ地の候補「現在地の港」はこれを使う**
   * ——同じ事実を trackingms から取りに行くと 2 ホップ先の伝聞になる。
   */
  lastHandlingLocationUnLocode?: string | null
  lastHandlingLocationName?: string | null
  /** 誤配が起きた事実（US28）。**再設計しても消さない**。 */
  misroute?: {
    at: string
    locationUnLocode: string
    locationName?: string | null
  } | null
  lastHandlingAt?: string | null
}

/**
 * 地点マスタ（ADR-010・ADR-014）。到着期限の判断に使う業務タイムゾーンを持つ。
 *
 * **本物の種データ（bookingms の V3）と同じ 10 件にする。** モックだけが狭いと、
 * 画面は少ない選択肢で動くまま、本番では出るはずの港が「出ていない」と報告される
 * （IT9 で 5 件しか無く、旅程が参照している CNSHA すら入っていなかった）。
 *
 * **国コードと地域区分も持つ**（IT12）。前者は輸出免税の判定、後者は区間係数に効く
 * （[ADR-027] 決定 1・8 の改訂）。持たないと、モックだけが国内の運賃を返し、
 * 画面は「動く」まま本番と違う金額を出す。
 */
export const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo', timeZone: 'Asia/Tokyo', countryCode: 'JP', region: 'DOMESTIC' },
  { unLocode: 'JPYOK', name: 'Yokohama', timeZone: 'Asia/Tokyo', countryCode: 'JP', region: 'DOMESTIC' },
  { unLocode: 'JPOSA', name: 'Osaka', timeZone: 'Asia/Tokyo', countryCode: 'JP', region: 'DOMESTIC' },
  { unLocode: 'USLAX', name: 'Los Angeles', timeZone: 'America/Los_Angeles', countryCode: 'US', region: 'OCEAN' },
  { unLocode: 'USNYC', name: 'New York', timeZone: 'America/New_York', countryCode: 'US', region: 'OCEAN' },
  { unLocode: 'CNSHA', name: 'Shanghai', timeZone: 'Asia/Shanghai', countryCode: 'CN', region: 'NEAR_SEA' },
  { unLocode: 'SGSIN', name: 'Singapore', timeZone: 'Asia/Singapore', countryCode: 'SG', region: 'NEAR_SEA' },
  { unLocode: 'DEHAM', name: 'Hamburg', timeZone: 'Europe/Berlin', countryCode: 'DE', region: 'OCEAN' },
  { unLocode: 'NLRTM', name: 'Rotterdam', timeZone: 'Europe/Amsterdam', countryCode: 'NL', region: 'OCEAN' },
  { unLocode: 'AUMEL', name: 'Melbourne', timeZone: 'Australia/Melbourne', countryCode: 'AU', region: 'OCEAN' },
]

/**
 * 引き渡し済みの予約を 1 件はじめから置く（US06）。
 *
 * 経路設計者の画面（待ち件数・絞り込み済み一覧）は、引き渡された予約が無いと何も確かめられない。
 * 営業がその場で作って渡す経路も通せるが、それだけだと「ログインし直したら消えた」ときに
 * 画面の不具合と区別がつかない。
 */
/** 一覧・詳細では荷主名を添える。社名で探せる一覧なのに名前が無いと、同名の別会社を見分けられない。 */
/**
 * いま行える操作を、本物と同じ規則で導く。
 *
 * **本物の該当箇所は `Cargo` の `canXxx()` である。** 条件を写すときは集約を開いて読み比べる。
 * ここが甘いと、画面はボタンを出すのに本番では 409 で断られる（あるいはその逆で、
 * **本番では押せる操作がモックでは現れない**）。
 *
 * 遷移の可否を画面に書かないための入口でもある。画面が状態名を見比べると、規則が
 * 集約・画面・モックの 3 か所に分かれ、片方だけ直る形になる（IT7 返済枠 0.7）。
 */
export function mockAvailableActions(booking: MockBooking): string[] {
  const actions: string[] = []
  if (
    booking.bookingStatus === 'PRELIMINARY' &&
    booking.routingStatus !== 'ROUTING_REQUESTED' &&
    booking.routingStatus !== 'ROUTED'
  ) {
    actions.push('REQUEST_ROUTING')
  }
  if (
    (booking.routingStatus === 'ROUTING_REQUESTED' || booking.routingStatus === 'ROUTED') &&
    booking.bookingStatus !== 'CONFIRMED' &&
    booking.bookingStatus !== 'TRACKING_ISSUED'
  ) {
    actions.push('ASSIGN_ROUTE')
  }
  if (booking.routingStatus === 'ROUTING_REQUESTED') {
    actions.push('REQUEST_CONSULTATION')
  }
  if (
    booking.routingStatus === 'ROUTED' &&
    (booking.bookingStatus === 'ROUTE_PROPOSED' || booking.bookingStatus === 'ROUTE_NOTIFIED')
  ) {
    actions.push('NOTIFY_SHIPPER')
  }
  if (booking.bookingStatus === 'ROUTE_NOTIFIED') {
    actions.push('CONFIRM', 'RETURN_TO_ROUTING')
  }
  if (booking.bookingStatus === 'CONFIRMED' && !booking.trackingNumber) {
    actions.push('ISSUE_TRACKING_NUMBER')
  }
  // キャンセルの申請（US30-1）。**輸送開始前でも輸送中でも申請はできる**——
  // 違うのは、その場で確定するか承認を待つかである（US30-2・US30-3）。
  // すでにキャンセルされた予約には出さない（押した先で 409 になる）
  if (booking.bookingStatus !== 'CANCELLED' && booking.bookingStatus !== 'DELIVERED') {
    actions.push('REQUEST_CANCELLATION')
  }
  // 誤配のあとの組み直し（US28-4）。**通常の割り当てとは別に出す**——後者は
  // 現在地が出発地であり、判断の前提が違う。
  // **判定は状態で行う**（本物の `Cargo#isMisrouted` と同じ）。記録は組み直しても
  // 消えないので、記録の有無で出すと済んだ操作の入口が残り続ける
  if (booking.routingStatus === 'MISROUTED') {
    actions.push('REASSIGN_ROUTE')
  }
  if (
    booking.routingStatus === 'NOT_ROUTED' ||
    booking.routingStatus === 'CONSULTATION_REQUESTED'
  ) {
    actions.push('REVISE_SCHEDULE')
  }
  return actions
}

export function withShipperName(booking: MockBooking) {
  return {
    ...booking,
    availableActions: mockAvailableActions(booking),
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
export function nextMockTrackingNumber(): string {
  sequenceState.trackingNumber += 1
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' })
    .format(new Date())
    .replaceAll('-', '')
  return `TRK-${today}-${String(sequenceState.trackingNumber).padStart(4, '0')}`
}


/**
 * 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる。
 *
 * 登録と編集で同じ検査を通す。片方だけ甘くすると、緩いほうの入口から壊れた値が入る。
 */
export function invalidShipperMessage(
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
export const lockedAccounts = [
  {
    username: 'sales02',
    displayName: '佐藤花子',
    failedAttempts: 5,
    lockedUntil: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
  },
]

export const bookings: MockBooking[] = [
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
  /**
   * 追跡番号を発行済みの予約（US15・US16 の前提）。荷役作業員がこの番号から作業を記録する。
   *
   * **種データで前提を用意する。** 前提が無いと、荷役の E2E は「予約 → 引き渡し → 経路 →
   * 通知 → 確定 → 発行」を毎回通すことになり、荷役と関係のない場所で落ちて原因が読めない。
   * かといって「条件が揃わなければスキップ」にすると、**通っていないことに気づけない**。
   *
   * 経由港を持つのは、予定外の作業（[ADR-023] 決定 3）を確かめられるようにするためである。
   */
  {
    id: 4,
    bookingId: 'BKG-2026000004',
    shipperId: 1,
    bookingStatus: 'TRACKING_ISSUED',
    transportStatus: 'NOT_RECEIVED',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 4200,
    quantity: 30,
    description: '産業機械',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-10-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0001',
    itinerary: [
      {
        voyageNumber: 'V-SEED-3',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'CNSHA',
        unloadName: 'Shanghai',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-08T00:00:00Z',
      },
      {
        voyageNumber: 'V-SEED-4',
        loadUnLocode: 'CNSHA',
        loadName: 'Shanghai',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-10T00:00:00Z',
        unloadTime: '2027-09-25T00:00:00Z',
      },
    ],
  },

  /**
   * 輸送中の予約（US30）。
   *
   * **輸送中でないと、キャンセルは申請したその場で確定する**（US30-2）。承認の画面を
   * 確かめるには、船に載っている貨物が要る。荷役のイベントで輸送中を知る仕組み
   * （[ADR-025] 決定 1）が入るまで、種データとして置く。
   *
   * 最後の荷役地点（Shanghai）を持たせているのは、**陸揚げ地の候補「現在地の港」**を
   * 確かめられるようにするためである。
   */
  {
    id: 5,
    bookingId: 'BKG-2026000005',
    shipperId: 1,
    bookingStatus: 'IN_TRANSIT',
    transportStatus: 'IN_TRANSIT',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 5600,
    quantity: 40,
    description: '自動車部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-10-25',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0002',
    lastHandlingLocationUnLocode: 'CNSHA',
    lastHandlingLocationName: 'Shanghai',
    lastHandlingAt: '2027-09-08T00:00:00Z',
    itinerary: [
      {
        voyageNumber: 'V-SEED-3',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'CNSHA',
        unloadName: 'Shanghai',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-08T00:00:00Z',
      },
      {
        voyageNumber: 'V-SEED-4',
        loadUnLocode: 'CNSHA',
        loadName: 'Shanghai',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-10T00:00:00Z',
        unloadTime: '2027-09-25T00:00:00Z',
      },
    ],
  },

  /**
   * 誤配が起きている予約（US28・IT10）。
   *
   * <p>予定は東京 → 上海 → ロサンゼルスだったが、<strong>シンガポールで荷降しされた</strong>。
   * 経路の状況は `MISROUTED` で、誤配の事実（いつ・どこで）を持つ。
   *
   * <p>この 2 つを<strong>別に持つ</strong>のが要点である——再設計して `ROUTED` へ戻っても、
   * 誤配の記録は残る（料金調整の根拠。受入基準 28-8）。
   */
  {
    id: 6,
    bookingId: 'BKG-2026000006',
    shipperId: 1,
    bookingStatus: 'IN_TRANSIT',
    transportStatus: 'IN_TRANSIT',
    routingStatus: 'MISROUTED',
    type: 'GENERAL',
    weightKg: 3200,
    quantity: 15,
    description: '産業機械部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-10-25',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0003',
    lastHandlingLocationUnLocode: 'SGSIN',
    lastHandlingLocationName: 'Singapore',
    lastHandlingAt: '2027-09-09T00:00:00Z',
    misroute: {
      at: '2027-09-09T00:00:00Z',
      locationUnLocode: 'SGSIN',
      locationName: 'Singapore',
    },
    itinerary: [
      {
        voyageNumber: 'V-SEED-3',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'CNSHA',
        unloadName: 'Shanghai',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-08T00:00:00Z',
      },
      {
        voyageNumber: 'V-SEED-4',
        loadUnLocode: 'CNSHA',
        loadName: 'Shanghai',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-10T00:00:00Z',
        unloadTime: '2027-09-25T00:00:00Z',
      },
    ],
  },

  /**
   * 引取が終わった予約（US21）。**法人荷主・2 区間**。
   *
   * <p>精算の対象がここから始まる。IT10 まで種データに引取済の予約が 1 件も無く、
   * <strong>経理担当者は仕事を始める相手を持っていなかった</strong>。
   *
   * <p>2 区間にしてあるのは、区間係数が効くことを確かめるためである
   * （[ADR-027] 決定 1）——1 区間だと係数 1.0 になり、掛け忘れても同じ金額になる。
   */
  {
    id: 7,
    bookingId: 'BKG-2026000007',
    shipperId: 1,
    bookingStatus: 'DELIVERED',
    transportStatus: 'DELIVERED',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 4200,
    quantity: 30,
    description: '産業機械',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-10-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0007',
    lastHandlingLocationUnLocode: 'USLAX',
    lastHandlingLocationName: 'Los Angeles',
    lastHandlingAt: '2027-09-26T00:00:00Z',
    itinerary: [
      {
        voyageNumber: 'V-SEED-3',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'CNSHA',
        unloadName: 'Shanghai',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-08T00:00:00Z',
      },
      {
        voyageNumber: 'V-SEED-4',
        loadUnLocode: 'CNSHA',
        loadName: 'Shanghai',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-10T00:00:00Z',
        unloadTime: '2027-09-25T00:00:00Z',
      },
    ],
  },

  /**
   * 引取が終わった予約（US22-3）。**個人荷主・1 区間**。
   *
   * <p>個人荷主には契約割引が無い。<strong>割引率 0% ではなく「契約が無い」</strong>
   * （[ADR-012] が `DiscountRate` について同じ判断をしている）——0% を出すと
   * 「割引が 0 だった」に読め、契約が無いことと区別できない。
   */
  {
    id: 8,
    bookingId: 'BKG-2026000008',
    shipperId: 2,
    bookingStatus: 'DELIVERED',
    transportStatus: 'DELIVERED',
    routingStatus: 'ROUTED',
    type: 'REFRIGERATED',
    weightKg: 800,
    quantity: 12,
    description: '冷凍食品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'SGSIN',
    destinationName: 'Singapore',
    departureDate: null,
    arrivalDeadline: '2027-09-30',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: -18,
    maxCelsius: -10,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0008',
    lastHandlingLocationUnLocode: 'SGSIN',
    lastHandlingLocationName: 'Singapore',
    lastHandlingAt: '2027-09-20T00:00:00Z',
    itinerary: [
      {
        voyageNumber: 'V-SEED-5',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'SGSIN',
        unloadName: 'Singapore',
        loadTime: '2027-09-05T00:00:00Z',
        unloadTime: '2027-09-19T00:00:00Z',
      },
    ],
  },

  /**
   * 誤配のあと組み直して、引取まで終わった予約（US21-6・US28-8）。
   *
   * <p><strong>誤配の記録は解決しても消えない</strong>（[ADR-026] 決定 3）。
   * IT10 はそこまでで終わっており、<strong>読む相手がいなかった</strong>。
   * 経理担当者がここで初めてその記録を読む。
   */
  {
    id: 9,
    bookingId: 'BKG-2026000009',
    shipperId: 1,
    bookingStatus: 'DELIVERED',
    transportStatus: 'DELIVERED',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 2500,
    quantity: 18,
    description: '自動車部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-09-30',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0009',
    lastHandlingLocationUnLocode: 'USLAX',
    lastHandlingLocationName: 'Los Angeles',
    lastHandlingAt: '2027-10-02T00:00:00Z',
    misroute: {
      at: '2027-09-09T00:00:00Z',
      locationUnLocode: 'SGSIN',
      locationName: 'Singapore',
    },
    itinerary: [
      {
        voyageNumber: 'V-SEED-6',
        loadUnLocode: 'SGSIN',
        loadName: 'Singapore',
        unloadUnLocode: 'USLAX',
        unloadName: 'Los Angeles',
        loadTime: '2027-09-12T00:00:00Z',
        unloadTime: '2027-10-01T00:00:00Z',
      },
    ],
  },

  /**
   * 輸送中にキャンセルが承認された予約（US30-9）。
   *
   * <p>IT9 は画面に「キャンセル料は算定していません」と書いた。算定する場所が
   * 無かったからである。<strong>本 IT でその一文が消える。</strong>
   */
  {
    id: 10,
    bookingId: 'BKG-2026000010',
    shipperId: 1,
    bookingStatus: 'CANCELLED',
    transportStatus: 'IN_TRANSIT',
    routingStatus: 'ROUTED',
    type: 'GENERAL',
    weightKg: 1500,
    quantity: 8,
    description: '電子部品',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-10-15',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    routeNotifiedAt: '2026-08-22T02:00:00Z',
    routeNotifiedBy: 'sales01',
    trackingNumber: 'TRK-20260823-0010',
    lastHandlingLocationUnLocode: 'CNSHA',
    lastHandlingLocationName: 'Shanghai',
    lastHandlingAt: '2027-09-08T00:00:00Z',
    itinerary: [
      {
        voyageNumber: 'V-SEED-3',
        loadUnLocode: 'JPTYO',
        loadName: 'Tokyo',
        unloadUnLocode: 'CNSHA',
        unloadName: 'Shanghai',
        loadTime: '2027-09-02T00:00:00Z',
        unloadTime: '2027-09-08T00:00:00Z',
      },
    ],
  },

]

/**
 * 採番の初期値を<strong>シードから導く</strong>。
 *
 * <p>件数を書き写すと、シードを 1 件足したときに新規登録の番号が既存とぶつかる。
 * IT9 で輸送中の予約を足したところ、新しく登録した予約が種データと同じ番号になり、
 * <strong>一覧で別の予約を開いていた</strong>——原因と無関係なテストが赤くなる。
 */
sequenceState.booking = bookings.length
sequenceState.trackingNumber = bookings.filter(
  (booking) => booking.trackingNumber !== null && booking.trackingNumber !== undefined,
).length
export const BOOKING_LIMIT = 100

/** 目的地の暦での「今日」。UTC で判断すると、時差の分だけ受付が拒否される時間帯ができる。 */
export function todayAt(timeZone: string) {
  return new Intl.DateTimeFormat('en-CA', { timeZone }).format(new Date())
}

/**
 * 連続失敗によるロック（US31）。本物と同じ回数・同じ応答で振る舞う。
 *
 * ここを実装しないと、画面が「5 回間違えると入れない」ことを一度も通らないまま
 * 「実装済み」になる。モックは仕様の写しであって、都合のよい相手ではない。
 */
export const MAX_FAILED_ATTEMPTS = 5
export const failedAttempts = new Map<string, number>()

export function isLocked(userId: string) {
  return (failedAttempts.get(userId) ?? 0) >= MAX_FAILED_ATTEMPTS
}


/** 航海スケジュールのモック（IT3 / US24・US25・US07）。 */
export type MockMovement = {
  departureUnLocode: string
  departureName: string
  arrivalUnLocode: string
  arrivalName: string
  departureTime: string
  arrivalTime: string
}

export type MockVoyage = {
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

export type MockVoyageRequest = {
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

export function locationName(unLocode: string): string {
  return LOCATIONS.find((location) => location.unLocode === unLocode)?.name ?? unLocode
}

export function toMockVoyage(request: MockVoyageRequest): MockVoyage {
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
export function differenceOf(existing: MockVoyage, incoming: MockVoyage) {
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

type SimulationRun = {
  runId: string
  scenarioId: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  startedBy: string
  startedAt: string
  finishedAt: string | null
  failureReason: string | null
  steps: {
    step: string
    label: string
    role: string
    outcome: 'SUCCEEDED' | 'FAILED'
    elapsedMs: number
    createdIdentifier: string | null
    /** 何番号か。サーバが返す（画面に対訳表を持たせない）。 */
    identifierKind: string | null
    failureReason: string | null
    recordedAt: string | null
  }[]
}

/**
 * 業務シミュレーションのシナリオ（US34）。
 *
 * **工程の見出しと役割はサーバが持つ**。ここで持つのは、本物が返すものの写しである。
 */
export const simulationScenarios = [
  {
    id: 'standard-transport',
    steps: [
      { step: 'REGISTER_SHIPPER', label: '荷主登録', role: 'ROLE_SALES' },
      { step: 'REGISTER_BOOKING', label: '予約登録', role: 'ROLE_SALES' },
      { step: 'REQUEST_ROUTING', label: '経路設計依頼', role: 'ROLE_SALES' },
      { step: 'REGISTER_VOYAGE', label: '航海登録', role: 'ROLE_ROUTING' },
      { step: 'ASSIGN_ROUTE', label: '経路割り当て', role: 'ROLE_ROUTING' },
      { step: 'NOTIFY_ROUTE', label: '経路通知', role: 'ROLE_SALES' },
      { step: 'CONFIRM_BOOKING', label: '予約確定', role: 'ROLE_SALES' },
      { step: 'ISSUE_TRACKING_NUMBER', label: '追跡番号発行', role: 'ROLE_ROUTING' },
      { step: 'RECORD_HANDLING', label: '荷役記録', role: 'ROLE_HANDLER' },
      { step: 'DECLARE_CUSTOMS', label: '通関申告', role: 'ROLE_HANDLER' },
      { step: 'CLEAR_CUSTOMS', label: '通関完了', role: 'ROLE_TRACKER' },
      { step: 'RECORD_CLAIM', label: '引取記録', role: 'ROLE_HANDLER' },
      { step: 'CALCULATE_CHARGE', label: '料金算出', role: 'ROLE_ACCOUNTANT' },
      { step: 'SETTLE', label: '精算', role: 'ROLE_ACCOUNTANT' },
    ],
  },
  // **例外シナリオも置く**（US36）。正常系だけを置くと、画面がシナリオの選択を
  // 一度も踏まないまま緑になる
  {
    id: 'misroute',
    steps: [
      { step: 'REGISTER_SHIPPER', label: '荷主登録', role: 'ROLE_SALES' },
      { step: 'REGISTER_BOOKING', label: '予約登録', role: 'ROLE_SALES' },
      { step: 'REQUEST_ROUTING', label: '経路設計依頼', role: 'ROLE_SALES' },
      { step: 'REGISTER_VOYAGE', label: '航海登録', role: 'ROLE_ROUTING' },
      { step: 'ASSIGN_ROUTE', label: '経路割り当て', role: 'ROLE_ROUTING' },
      { step: 'NOTIFY_ROUTE', label: '経路通知', role: 'ROLE_SALES' },
      { step: 'CONFIRM_BOOKING', label: '予約確定', role: 'ROLE_SALES' },
      { step: 'ISSUE_TRACKING_NUMBER', label: '追跡番号発行', role: 'ROLE_ROUTING' },
      {
        step: 'RECORD_MISROUTED_HANDLING',
        label: '予定外の港での荷役記録',
        role: 'ROLE_HANDLER',
      },
      {
        step: 'REGISTER_RECOVERY_VOYAGE',
        label: '組み直し用の航海登録',
        role: 'ROLE_ROUTING',
      },
      { step: 'REDESIGN_ROUTE', label: '経路の組み直し', role: 'ROLE_ROUTING' },
      { step: 'RESOLVE_EXCEPTION', label: '例外の解決', role: 'ROLE_TRACKER' },
      { step: 'RECORD_HANDLING', label: '荷役記録', role: 'ROLE_HANDLER' },
      { step: 'DECLARE_CUSTOMS', label: '通関申告', role: 'ROLE_HANDLER' },
      { step: 'CLEAR_CUSTOMS', label: '通関完了', role: 'ROLE_TRACKER' },
      { step: 'RECORD_CLAIM', label: '引取記録', role: 'ROLE_HANDLER' },
      { step: 'CALCULATE_CHARGE', label: '料金算出', role: 'ROLE_ACCOUNTANT' },
      { step: 'SETTLE', label: '精算', role: 'ROLE_ACCOUNTANT' },
    ],
  },
  {
    id: 'cancellation',
    steps: [
      { step: 'REGISTER_SHIPPER', label: '荷主登録', role: 'ROLE_SALES' },
      { step: 'REGISTER_BOOKING', label: '予約登録', role: 'ROLE_SALES' },
      { step: 'REQUEST_ROUTING', label: '経路設計依頼', role: 'ROLE_SALES' },
      { step: 'REGISTER_VOYAGE', label: '航海登録', role: 'ROLE_ROUTING' },
      { step: 'ASSIGN_ROUTE', label: '経路割り当て', role: 'ROLE_ROUTING' },
      { step: 'NOTIFY_ROUTE', label: '経路通知', role: 'ROLE_SALES' },
      { step: 'CONFIRM_BOOKING', label: '予約確定', role: 'ROLE_SALES' },
      { step: 'ISSUE_TRACKING_NUMBER', label: '追跡番号発行', role: 'ROLE_ROUTING' },
      { step: 'RECORD_HANDLING', label: '荷役記録', role: 'ROLE_HANDLER' },
      { step: 'REQUEST_CANCELLATION', label: 'キャンセル申請', role: 'ROLE_SALES' },
      { step: 'APPROVE_CANCELLATION', label: 'キャンセル承認', role: 'ROLE_TRACKER' },
    ],
  },
]

/** 実行の履歴（US35）。**失敗した実行も残す**——巻き戻さないことがここに表れる。 */
export const simulationRuns: SimulationRun[] = [
  {
    // **失敗した実行も置く。**成功だけを置くと、画面が「止まった理由の見え方」を
    // 一度も踏まないまま緑になる
    runId: 'SIM-20261115-0002',
    scenarioId: 'standard-transport',
    status: 'FAILED',
    startedBy: 'admin01',
    startedAt: '2026-11-15T02:00:00Z',
    finishedAt: '2026-11-15T02:00:05Z',
    failureReason: '経路割り当て が失敗しました（経路候補が 0 件です）',
    steps: [
      {
        step: 'REGISTER_SHIPPER',
        label: '荷主登録',
        role: 'ROLE_SALES',
        outcome: 'SUCCEEDED',
        elapsedMs: 133,
        createdIdentifier: '41',
        identifierKind: '荷主 ID',
        failureReason: null,
        recordedAt: '2026-11-15T02:00:01Z',
      },
      {
        step: 'ASSIGN_ROUTE',
        label: '経路割り当て',
        role: 'ROLE_ROUTING',
        outcome: 'FAILED',
        elapsedMs: 42,
        createdIdentifier: null,
        identifierKind: null,
        failureReason: '経路候補が 0 件です（JPTYO → USLAX）。航海の登録を確かめる',
        recordedAt: '2026-11-15T02:00:05Z',
      },
    ],
  },
  {
    runId: 'SIM-20261116-0001',
    scenarioId: 'standard-transport',
    status: 'COMPLETED',
    startedBy: 'admin01',
    startedAt: '2026-11-16T01:00:00Z',
    finishedAt: '2026-11-16T01:00:12Z',
    failureReason: null,
    steps: [
      {
        step: 'REGISTER_SHIPPER',
        label: '荷主登録',
        role: 'ROLE_SALES',
        outcome: 'SUCCEEDED',
        elapsedMs: 120,
        createdIdentifier: '42',
        identifierKind: '荷主 ID',
        failureReason: null,
        recordedAt: '2026-11-16T01:00:01Z',
      },
      {
        step: 'ISSUE_TRACKING_NUMBER',
        label: '追跡番号発行',
        role: 'ROLE_ROUTING',
        outcome: 'SUCCEEDED',
        elapsedMs: 88,
        createdIdentifier: 'TRK-20261116-0001',
        identifierKind: '追跡番号',
        failureReason: null,
        recordedAt: '2026-11-16T01:00:08Z',
      },
    ],
  },
]
