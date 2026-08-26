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
import { bookings, shippers } from '../data'
import type { MockBooking } from '../data'

/** 1 区間・1,000kg・一般貨物のときの運賃（[ADR-027] 決定 1）。 */
const BASE_FARE = 50_000

/** 貨物種別係数（正典の値をそのまま使う）。 */
const CARGO_TYPE_FACTORS: Record<string, number> = {
  GENERAL: 1.0,
  HAZARDOUS: 1.8,
  REFRIGERATED: 1.5,
}

/** 重量係数の下限。運ぶ手間は重量に比例しない——置かないと軽量の貨物が 0 円に近づく。 */
const MIN_WEIGHT_FACTOR = 0.1

/** 消費税率（決定 8）。**業務として扱うのは US23**。 */
const TAX_RATE = 0.1

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
  taxAmount: { value: number; currency: string }
  totalAmount: { value: number; currency: string }
  paymentStatus: string
  issuedAt: string
  dueDate: string | null
}

/** 発行済みの精算書。**確定操作でだけ増える**（決定 3）。 */
export const invoices: MockInvoice[] = []

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
  const legCount = booking.itinerary?.length ?? 0
  const weightFactor = Math.max(weightFactorOf(booking.weightKg), MIN_WEIGHT_FACTOR)
  return {
    baseFare: yen(BASE_FARE),
    legCount,
    legFactor: legCount,
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
  return invoices.some((invoice) => invoice.bookingId === bookingId)
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
  const taxAmount = yen(beforeTax * TAX_RATE)

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
    taxRate: TAX_RATE,
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
    const taxAmount = yen(beforeTax * TAX_RATE)

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
      taxRate: TAX_RATE,
      taxAmount,
      totalAmount: yen(beforeTax + taxAmount.value),
      // **発行の時点では未入金**（決定 3）。入金の確認は US23
      paymentStatus: 'PENDING',
      issuedAt: new Date().toISOString(),
      dueDate: null,
    }
    invoices.push(invoice)
    return HttpResponse.json(invoice, { status: 201 })
  }),
]
