/**
 * 精算のモック（US21・US22・UC17）。
 *
 * <p><strong>本物と同じ式で計算する。</strong>モックだけが違う数字を返すと、画面は
 * 「動く」まま本番で別の金額を出す。IT9 の通関ガードで踏んだ形と同じである
 * ——甘いモックは、モック自身の欠陥ではなく<strong>画面が本物の規則を満たして
 * いないこと</strong>を隠す。
 *
 * <p>写した本物の規則（[ADR-027](../../../../docs/adr/027-transport-charge-calculation.md)）:
 * <ul>
 *   <li>決定 1 — 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数。
 *       <strong>距離は使わない</strong>（持っていない）
 *   <li>決定 2 — 端数は 1 円単位で四捨五入し、<strong>丸めたあとの値</strong>を返す
 *   <li>決定 3 — 算出結果は保存しない。確定操作で初めて精算書ができる
 *   <li>決定 4 — 発行した精算書の金額は動かない。二重請求も断る
 *   <li>決定 5 — 対象は引取済（DELIVERED）とキャンセル済み（CANCELLED）の予約
 *   <li>決定 8 — 消費税は既定 10%
 * </ul>
 */
import { HttpResponse, http } from 'msw'

import { API_PATHS } from '../../config/api'
import { roundYen } from '../../features/billing/money'
// **表示名を写さない。**画面が使っているものをそのまま使う。写すと、値を足したときに
// 片方だけ古くなり、モックだけ英字を返す（`Record<...,string>` の索引は未登録を
// undefined で返すため、`?? 値そのもの` で埋めると気づけない）
import {
  BOOKING_STATUS_LABELS,
  CARGO_TYPE_LABELS,
  type BookingStatus,
  type CargoType,
} from '../../features/booking/types'
import { LOCATIONS, bookings, shippers } from '../data'
import { PAYMENT_METHOD_LABELS } from '../../features/billing/types'
import { businessToday } from '../../lib/business-time'
import type { MockBooking } from '../data'

/** 1 区間・1,000kg・一般貨物のときの運賃（[ADR-027] 決定 1）。 */
const BASE_FARE = 50_000

/** 貨物種別係数（正典の値をそのまま使う）。 */
const CARGO_TYPE_FACTORS: Record<string, number> = {
  GENERAL: 1.0,
  HAZARDOUS: 1.8,
  REFRIGERATED: 1.5,
}

/** 地域係数（正典の値をそのまま使う。[ADR-027] 決定 1 の改訂）。 */
const REGION_FACTORS: Record<string, number> = {
  DOMESTIC: 1.0,
  NEAR_SEA: 2.5,
  OCEAN: 6.0,
}

const REGION_LABELS: Record<string, string> = {
  DOMESTIC: '国内',
  NEAR_SEA: '近海',
  OCEAN: '遠洋',
}

/** 地点の地域区分。**知らない港は断る**——既定値に倒すと、その港だけ安く請求される。 */
function regionOf(unLocode: string) {
  const location = LOCATIONS.find((item) => item.unLocode === unLocode)
  if (location === undefined) {
    throw new Error(`扱いを決めていない地点です: ${unLocode}`)
  }
  return location.region
}

/** 重量係数の下限。運ぶ手間は重量に比例しない——置かないと軽量の貨物が 0 円に近づく。 */
const MIN_WEIGHT_FACTOR = 0.1

/** 消費税率（決定 8）。 */
const TAX_RATE = 0.1

/**
 * 税率（決定 8 の改訂）。**国が異なれば輸出免税。**
 *
 * 分からなければ課税に倒す——免税に倒すと、国コードを引けない不具合が
 * 「消費税を取り忘れる」形で出る。
 */
function taxRateOf(booking: MockBooking) {
  const origin = LOCATIONS.find((item) => item.unLocode === booking.originUnLocode)
  const destination = LOCATIONS.find(
    (item) => item.unLocode === booking.destinationUnLocode,
  )
  if (origin === undefined || destination === undefined) {
    return TAX_RATE
  }
  return origin.countryCode === destination.countryCode ? TAX_RATE : 0
}

/**
 * キャンセル料の料率（正典のビジネスルール 6）。
 *
 * <p>輸送開始後は高くなる——船に載せてから降ろすには実費がかかる。
 */
const CANCELLATION_FEE_RATES: Record<string, number> = {
  PRELIMINARY: 0.0,
  ROUTE_PROPOSED: 0.0,
  ROUTE_NOTIFIED: 0.05,
  CONFIRMED: 0.1,
  TRACKING_ISSUED: 0.1,
  IN_TRANSIT: 0.3,
}


type MockInvoice = {
  invoiceId: string
  invoiceNumber: string
  bookingId: string
  shipperName: string
  basis: ReturnType<typeof basisOf>
  baseAmount: { value: number; currency: string }
  discountRate: number | null
  discountAmount: { value: number; currency: string } | null
  lineItems: { description: string; amount: { value: number; currency: string } }[]
  cancellationFee: {
    bookingStatusLabel: string
    feeRate: number
    amount: { value: number; currency: string }
  } | null
  taxRate: number
  /** 輸出免税か。**税区分として画面に出す**——「消費税 ¥0」だけでは計算漏れと読める。 */
  taxExempt: boolean
  taxAmount: { value: number; currency: string }
  totalAmount: { value: number; currency: string }
  paymentStatus: string
  paymentStatusLabel: string
  issuedAt: string
  dueDate: string | null
  payment: {
    amount: { value: number; currency: string }
    paidAt: string
    method: string
    methodLabel: string
    transactionReference: string | null
  } | null
  voidedAt: string | null
  voidReason: string | null
}

/** 支払いの状態の表示名。**サーバの `PaymentStatus.label()` と同じ。** */
const PAYMENT_STATUS_MOCK_LABELS: Record<string, string> = {
  PENDING: '未入金',
  CONFIRMED: '入金済',
  OVERDUE: '支払期限超過',
  REFUNDED: '返金済',
}

/**
 * 発行済みの精算書。**確定操作で増える**（決定 3）。
 *
 * <p><strong>期限を過ぎた 1 通をはじめから置く</strong>（受入基準 23-5 の代替）。
 * 経理担当者の画面は「期限を過ぎた請求に気づけること」を確かめる必要があるが、
 * 期限は発行日 + 30 日であり、その場で作った請求書では 30 日待つことになる。
 * 置かないと、**件数の導線が動くかどうかを誰も確かめられない**。
 */
export const invoices: MockInvoice[] = [
  {
    invoiceId: 'INV-2026000900',
    invoiceNumber: 'INV-2026000900',
    bookingId: 'BKG-2026000012',
    shipperName: '大洋物産株式会社',
    basis: {
      baseFare: { value: 50_000, currency: 'JPY' },
      legCount: 1,
      legFactor: 1,
      region: 'DOMESTIC',
      regionLabel: '国内',
      weightKg: 1000,
      weightFactor: 1,
      cargoType: 'GENERAL',
      cargoTypeFactor: 1,
    },
    baseAmount: { value: 50_000, currency: 'JPY' },
    discountRate: null,
    discountAmount: null,
    lineItems: [],
    cancellationFee: null,
    taxRate: 0.1,
    taxExempt: false,
    taxAmount: { value: 5_000, currency: 'JPY' },
    totalAmount: { value: 55_000, currency: 'JPY' },
    paymentStatus: 'PENDING',
    paymentStatusLabel: '未入金',
    issuedAt: '2026-06-05T00:00:00Z',
    // **過ぎている日付を直接書く。**「今日 − 30 日」で作ると、テストが
    // 「期限を過ぎているか」ではなく計算式を確かめることになる
    dueDate: '2026-07-05',
    payment: null,
    voidedAt: null,
    voidReason: null,
  },
]

let invoiceSequence = 0

/**
 * 円。**丸めるのはここ 1 か所だけ**（決定 2）。呼び出し側が丸めると結果が場所ごとに変わる。
 *
 * <p>向きは画面と同じ `roundYen` に委ねる——サーバの `HALF_UP` は 0 から遠いほうへ
 * 丸めるため、`Math.round` のままでは<strong>調整で小計が負になったとき</strong>
 * （大幅な減額・補償）にモックだけ 1 円ずれる。
 */
export function yen(value: number) {
  return { value: roundYen(value), currency: 'JPY' }
}

function shipperOf(booking: MockBooking) {
  return shippers.find((shipper) => shipper.id === booking.shipperId)
}

/** 基本料金の根拠（決定 1）。**距離ではなく区間数を使う。** */
export function basisOf(booking: MockBooking) {
  const legs = booking.itinerary ?? []
  const legCount = legs.length
  const weightFactor = Math.max(weightFactorOf(booking.weightKg), MIN_WEIGHT_FACTOR)
  // **両端の重いほうを採る**（決定 1 の改訂）。片端が国内でも、太平洋を渡れば
  // 遠洋の費用がかかる
  const legRegions = legs.map((leg) => {
    const load = regionOf(leg.loadUnLocode)
    const unload = regionOf(leg.unloadUnLocode)
    return REGION_FACTORS[load] >= REGION_FACTORS[unload] ? load : unload
  })
  const heaviest = legRegions.reduce<string | null>(
    (left, right) =>
      left === null || REGION_FACTORS[right] > REGION_FACTORS[left] ? right : left,
    null,
  )
  return {
    baseFare: yen(BASE_FARE),
    legCount,
    legFactor: legRegions.reduce((sum, region) => sum + REGION_FACTORS[region], 0),
    region: heaviest,
    regionLabel: heaviest === null ? null : REGION_LABELS[heaviest],
    weightKg: booking.weightKg,
    weightFactor,
    cargoType: booking.type,
    cargoTypeFactor: CARGO_TYPE_FACTORS[booking.type] ?? 1.0,
  }
}

/**
 * 重量係数（`TransportCharge.weightFactor` と同じ）。
 *
 * <p><strong>小数第 4 位で丸める。</strong>サーバは `scale 4` の `HALF_UP` で持つ。
 * 丸めずに掛けると、端数のある重量でモックだけ違う金額になる。
 *
 * <p><strong>丸めの境目そのものは一致させられない。</strong>サーバは `BigDecimal` で
 * 10 進数を正確に持つが、こちらは 2 進の浮動小数であり、1000.05 のような値は
 * そもそも正確に持てない。一致するのは境目でない値である——境目の 0.00005 単位が
 * 効くほどの重量差は業務上意味を持たない（重量は `NUMERIC(10,3)`）。
 */
function weightFactorOf(weightKg: number) {
  return Math.round((weightKg / 1000) * 10_000) / 10_000
}

export function baseAmountOf(basis: ReturnType<typeof basisOf>) {
  return yen(BASE_FARE * basis.legFactor * basis.weightFactor * basis.cargoTypeFactor)
}

/**
 * 精算の対象になる予約（決定 5）。
 *
 * <p>引取済（DELIVERED）とキャンセル済み（CANCELLED）。**輸送中の予約は対象にしない**
 * ——まだ運び終えておらず、請求する金額が決まらない。
 */
function billable(booking: MockBooking) {
  return booking.bookingStatus === 'DELIVERED' || booking.bookingStatus === 'CANCELLED'
}

/** すでに精算書が発行されている予約か（決定 4——二重請求を防ぐ）。 */
function alreadyInvoiced(bookingId: string) {
  // **取り消し済みは数えない**（[ADR-028] 決定 3）。数えると、取り消したあと
  // その予約に二度と請求できない
  return invoices.some(
    (invoice) => invoice.bookingId === bookingId && invoice.voidedAt === null,
  )
}

/**
 * 支払期限を過ぎているか（[ADR-028] 決定 5）。
 *
 * **列に書いて溜めない。**書き込む相手が無いため、書いた列は誰にも更新されず、
 * 期限超過が常に 0 件になる。**日付単位で比べる**——期限当日は超過ではない。
 */
function isOverdue(invoice: MockInvoice) {
  if (invoice.voidedAt !== null || invoice.paymentStatus === 'CONFIRMED') {
    return false
  }
  return invoice.dueDate !== null && businessToday() > invoice.dueDate
}

function cancellationFeeOf(booking: MockBooking) {
  if (booking.bookingStatus !== 'CANCELLED') {
    return null
  }
  // **申請した時点の状態で料率が決まる**（正典のビジネスルール 6）。
  // 承認された時点ではない——輸送中に申請したものは、承認が翌日でも輸送中の料率になる
  const statusAtCancel = booking.transportStatus === 'IN_TRANSIT' ? 'IN_TRANSIT' : 'CONFIRMED'
  const feeRate = CANCELLATION_FEE_RATES[statusAtCancel] ?? 0
  const basis = basisOf(booking)
  return {
    bookingStatusAtCancel: statusAtCancel,
    bookingStatusLabel: BOOKING_STATUS_LABELS[statusAtCancel as BookingStatus],
    feeRate,
    amount: yen(baseAmountOf(basis).value * feeRate),
  }
}

function calculationOf(booking: MockBooking) {
  const shipper = shipperOf(booking)
  const basis = basisOf(booking)
  const baseAmount = baseAmountOf(basis)
  // **個人荷主では null。0% ではない**（[ADR-012] と同じ判断）
  const discountRate =
    shipper?.type === 'CORPORATE' && shipper.discountRatePercent !== null
      ? shipper.discountRatePercent / 100
      : null
  const discountAmount = discountRate === null ? null : yen(baseAmount.value * discountRate)
  const cancellationFee = cancellationFeeOf(booking)

  const beforeTax =
    baseAmount.value - (discountAmount?.value ?? 0) + (cancellationFee?.amount.value ?? 0)
  const taxRate = taxRateOf(booking)
  const taxAmount = yen(beforeTax * taxRate)

  return {
    bookingId: booking.bookingId,
    shipperName: shipper?.name ?? '（不明）',
    shipperType: shipper?.type ?? 'INDIVIDUAL',
    basis: { ...basis, cargoTypeLabel: CARGO_TYPE_LABELS[booking.type as CargoType] },
    baseAmount,
    discountRate,
    discountAmount,
    misroute: booking.misroute ?? null,
    cancellationFee,
    taxRate,
    taxExempt: taxRate === 0,
    taxAmount,
    totalAmount: yen(beforeTax + taxAmount.value),
  }
}

/**
 * <p><strong>パスパラメータを含む経路は生の文字列で書く。</strong>
 * {@link API_PATHS} の関数は `encodeURIComponent` を通すため、`':bookingId'` を渡すと
 * `%3AbookingId` になり、MSW がパラメータとして認識しない——ハンドラが素通りして
 * <strong>実バックエンドへのプロキシに抜ける</strong>。他のハンドラも同じ書き方をしている。
 */
export const billingHandlers = [
  /** 料金を算出していない引取済の予約（US21-1）。**古い順**——待たせている案件が上に来る。 */
  http.get(API_PATHS.unbilledBookings, () => {
    const unbilled = bookings
      .filter((booking) => billable(booking) && !alreadyInvoiced(booking.bookingId))
      .map((booking) => ({
        bookingId: booking.bookingId,
        shipperName: shipperOf(booking)?.name ?? '（不明）',
        shipperType: shipperOf(booking)?.type ?? 'INDIVIDUAL',
        originName: booking.originName,
        destinationName: booking.destinationName,
        // **最後に荷役があった日時**（引取の日時とは限らない）。一覧の並びもこれで決まる
        lastHandlingAt: booking.lastHandlingAt ?? null,
        misrouted: booking.misroute !== null && booking.misroute !== undefined,
        cancelled: booking.bookingStatus === 'CANCELLED',
        sortKey: booking.lastHandlingAt ?? '',
      }))
      // **並びは最後の荷役日時で決める**（引取日時とは別）。待たせている案件が上に来る。
      // **日時を持たないもの（キャンセル）は最後に回す**——本物の SQL が
      // `CASE WHEN c.last_handling_at IS NULL THEN 1 ELSE 0 END` でそうしている。
      // 並べ替えないと、手引きのキャプチャが「引取が終わった順」の説明と食い違う
      .sort((a, b) => {
        const aNull = a.sortKey === '' ? 1 : 0
        const bNull = b.sortKey === '' ? 1 : 0
        if (aNull !== bNull) {
          return aNull - bNull
        }
        return (a.sortKey ?? '').localeCompare(b.sortKey ?? '')
      })
      .map(({ sortKey: _sortKey, ...rest }) => rest)
    return HttpResponse.json(unbilled)
  }),

  http.get(API_PATHS.invoices, () => HttpResponse.json(invoices)),

  /**
   * 支払期限を過ぎた請求書（受入基準 23-5 の代替）。
   *
   * **`/invoices/:invoiceId` より先に置く。**あとに置くと `overdue` が
   * 請求番号として読まれ、404 になる。
   */
  http.get(API_PATHS.overdueInvoices, () =>
    HttpResponse.json(invoices.filter(isOverdue)),
  ),

  /** 入金の確認（受入基準 23-3・23-4）。**予約も精算済になる**。 */
  http.post('/api/v1/billing/invoices/:invoiceId/payment', async ({ params, request }) => {
    const invoice = invoices.find((candidate) => candidate.invoiceId === params.invoiceId)
    if (invoice === undefined) {
      return HttpResponse.json({ message: '請求書が見つかりません' }, { status: 404 })
    }
    if (invoice.voidedAt !== null) {
      return HttpResponse.json(
        { message: '取り消した請求書に入金は確認できません' },
        { status: 409 },
      )
    }
    if (invoice.paymentStatus === 'CONFIRMED') {
      return HttpResponse.json({ message: 'すでに入金を確認しています' }, { status: 409 })
    }

    const body = (await request.json()) as {
      amountValue: number
      paidAt: string
      method: string
      transactionReference: string | null
    }
    if (!(body.amountValue > 0) || body.paidAt === '') {
      return HttpResponse.json({ message: '入金額と入金日を入力してください' }, { status: 400 })
    }

    invoice.paymentStatus = 'CONFIRMED'
    invoice.paymentStatusLabel = PAYMENT_STATUS_MOCK_LABELS.CONFIRMED
    invoice.payment = {
      amount: yen(body.amountValue),
      paidAt: body.paidAt,
      method: body.method,
      methodLabel: PAYMENT_METHOD_LABELS[body.method] ?? body.method,
      transactionReference: body.transactionReference,
    }

    // **予約も精算済になる**（受入基準 23-4）。本物では billingms が bookingms に
    // 知らせる——モックだけが閉じないと、画面をまたぐ 1 本を確かめられない
    const booking = bookings.find((candidate) => candidate.bookingId === invoice.bookingId)
    if (booking !== undefined) {
      booking.bookingStatus = 'SETTLED'
    }

    return HttpResponse.json(invoice)
  }),

  /** 請求書の取り消し（赤伝・[ADR-028] 決定 3）。**理由は必須**。 */
  http.post('/api/v1/billing/invoices/:invoiceId/void', async ({ params, request }) => {
    const invoice = invoices.find((candidate) => candidate.invoiceId === params.invoiceId)
    if (invoice === undefined) {
      return HttpResponse.json({ message: '請求書が見つかりません' }, { status: 404 })
    }
    if (invoice.voidedAt !== null) {
      return HttpResponse.json({ message: 'すでに取り消しています' }, { status: 409 })
    }
    if (invoice.paymentStatus === 'CONFIRMED') {
      return HttpResponse.json(
        { message: '入金済の請求書は取り消せません' },
        { status: 409 },
      )
    }

    const body = (await request.json()) as { reason: string }
    if (body.reason === undefined || body.reason.trim() === '') {
      return HttpResponse.json({ message: '取り消しの理由を入力してください' }, { status: 400 })
    }

    invoice.voidedAt = new Date().toISOString()
    invoice.voidReason = body.reason
    return HttpResponse.json(invoice)
  }),

  http.get('/api/v1/billing/invoices/:invoiceId', ({ params }) => {
    const found = invoices.find((invoice) => invoice.invoiceId === params.invoiceId)
    if (found === undefined) {
      return HttpResponse.json({ message: '精算書が見つかりません' }, { status: 404 })
    }
    return HttpResponse.json(found)
  }),

  /** 料金の算出結果（決定 3）。**保存しない。** */
  http.get('/api/v1/billing/calculations/:bookingId', ({ params }) => {
    const booking = bookings.find((candidate) => candidate.bookingId === params.bookingId)
    if (booking === undefined) {
      return HttpResponse.json({ message: '予約が見つかりません' }, { status: 404 })
    }
    if (!billable(booking)) {
      // **まだ運び終えていない予約は断る**（決定 5）。画面で出し分けるだけでは守れない
      return HttpResponse.json(
        { message: '引取が終わっていない予約の料金は算出できません' },
        { status: 409 },
      )
    }
    if (alreadyInvoiced(booking.bookingId)) {
      return HttpResponse.json(
        { message: 'この予約にはすでに精算書が発行されています' },
        { status: 409 },
      )
    }
    return HttpResponse.json(calculationOf(booking))
  }),

  /** 料金を確定して精算書を発行する（US21-4・US21-5）。 */
  http.post('/api/v1/billing/:bookingId/calculate', async ({ params, request }) => {
    const booking = bookings.find((candidate) => candidate.bookingId === params.bookingId)
    if (booking === undefined) {
      return HttpResponse.json({ message: '予約が見つかりません' }, { status: 404 })
    }
    if (!billable(booking)) {
      return HttpResponse.json(
        { message: '引取が終わっていない予約の料金は算出できません' },
        { status: 409 },
      )
    }
    // **二重請求を断る**（決定 4・正典のビジネスルール 5）。画面が押させないだけでは守れない
    if (alreadyInvoiced(booking.bookingId)) {
      return HttpResponse.json(
        { message: 'この予約にはすでに精算書が発行されています' },
        { status: 409 },
      )
    }

    const body = (await request.json()) as {
      adjustments?: { description: string; amountValue: number }[]
    }
    const adjustments = body.adjustments ?? []
    if (adjustments.some((item) => item.description.trim() === '')) {
      // **根拠の無い調整を断る**（決定 6）。金額だけ残ると、あとから誰も理由を言えない
      return HttpResponse.json({ message: '調整の内容を入力してください' }, { status: 400 })
    }

    const calculation = calculationOf(booking)
    const adjustmentTotal = adjustments.reduce((sum, item) => sum + item.amountValue, 0)
    const beforeTax =
      calculation.baseAmount.value -
      (calculation.discountAmount?.value ?? 0) +
      (calculation.cancellationFee?.amount.value ?? 0) +
      adjustmentTotal
    const taxRate = calculation.taxRate
    const taxAmount = yen(beforeTax * taxRate)

    invoiceSequence += 1
    const invoice: MockInvoice = {
      invoiceId: `INV-2026${String(invoiceSequence).padStart(6, '0')}`,
      invoiceNumber: `INV-2026${String(invoiceSequence).padStart(6, '0')}`,
      bookingId: booking.bookingId,
      shipperName: calculation.shipperName,
      basis: calculation.basis,
      baseAmount: calculation.baseAmount,
      discountRate: calculation.discountRate,
      discountAmount: calculation.discountAmount,
      lineItems: adjustments.map((item) => ({
        description: item.description,
        amount: yen(item.amountValue),
      })),
      cancellationFee:
        calculation.cancellationFee === null
          ? null
          : {
              bookingStatusLabel: calculation.cancellationFee.bookingStatusLabel,
              feeRate: calculation.cancellationFee.feeRate,
              amount: calculation.cancellationFee.amount,
            },
      taxRate,
      taxExempt: taxRate === 0,
      taxAmount,
      totalAmount: yen(beforeTax + taxAmount.value),
      // **発行の時点では未入金**（決定 3）。入金の確認は US23
      paymentStatus: 'PENDING',
      paymentStatusLabel: PAYMENT_STATUS_MOCK_LABELS.PENDING,
      issuedAt: new Date().toISOString(),
      // 支払期限は発行日 + 30 日（受入基準 23-1）。**業務の暦で決める**
      dueDate: businessDatePlusDays(30),
      payment: null,
      voidedAt: null,
      voidReason: null,
    }
    invoices.push(invoice)
    return HttpResponse.json(invoice, { status: 201 })
  }),
]

/**
 * 業務の暦で「今日から N 日後」を返す。
 *
 * **`toISOString()` を使わない**——CI（UTC）で 1 日ずれる。
 */
function businessDatePlusDays(days: number) {
  const base = new Date(`${businessToday()}T00:00:00Z`)
  base.setUTCDate(base.getUTCDate() + days)
  return base.toISOString().slice(0, 10)
}
