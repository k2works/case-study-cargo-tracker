/**
 * 精算（US21・US22）。[ADR-027](../../../../docs/adr/027-transport-charge-calculation.md) に従う。
 */

/** 金額。**丸めはサーバが済ませている**（決定 2）。画面で計算しない。 */
export type Money = {
  /** 円。1 円単位に丸めたあとの値。 */
  value: number
  currency: string
}

/**
 * 支払いの状態。**バックエンドの `PaymentStatus` と同じ 4 値**（決定 3）。
 *
 * **本 IT で起こす遷移は「算出中 → `PENDING`」の 1 本だけ**である。残る 3 本は US23。
 * それでも 4 値すべて宣言するのは、**扱う場所すべてを回るため**——値を足したときに
 * 表示名を書き忘れると `tsc -b` が止まる（IT11 返済枠 0.7 と同じ形）。
 */
export type PaymentStatus = 'PENDING' | 'CONFIRMED' | 'OVERDUE' | 'REFUNDED'

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: '未入金',
  CONFIRMED: '入金済',
  OVERDUE: '支払期限超過',
  REFUNDED: '返金済',
}

/** 支払いの状態の表示名を引く。知らない値はそのまま返す。 */
export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status as PaymentStatus] ?? status
}

/**
 * 料金未算出の引取済予約（US21-1）。
 *
 * 精算管理の待ち行列であり、ダッシュボードの件数もここから来る。
 */
export type UnbilledBooking = {
  bookingId: string
  shipperName: string
  /** 法人か個人か。**個人には割引の欄を出さない**（22-3）。 */
  shipperType: 'CORPORATE' | 'INDIVIDUAL'
  originName: string
  destinationName: string
  /**
   * **最後に荷役があった日時**（IT11 レビュー 中）。引取の日時とは限らない。
   *
   * **古い順に並べる**——待たせている案件が上に来る。名前と中身を揃えないと、
   * 「引取日時」で並んでいるように読まれる。
   */
  lastHandlingAt: string | null
  /** 誤配の記録があるか（21-6 の根拠）。 */
  misrouted: boolean
  /** キャンセルされた予約か。キャンセル料の算定対象になる（US30-9）。 */
  cancelled: boolean
}

/**
 * 基本料金の根拠（[ADR-027] 決定 1）。
 *
 * **金額そのものより「なぜその金額か」が読めることを優先する**——経理担当者は
 * 請求の根拠を荷主に説明する。
 */
export type ChargeBasis = {
  /** 1 区間・1,000kg・一般貨物のときの運賃。 */
  baseFare: Money
  /** 旅程の区間数。**距離の代わり**（決定 1）。 */
  legCount: number
  legFactor: number
  weightKg: number
  weightFactor: number
  cargoType: string
  cargoTypeFactor: number
}

/** 明細 1 行（決定 6）。減額は負、加算は正。 */
export type LineItem = {
  description: string
  amount: Money
}

/**
 * 料金の算出結果（決定 3）。**保存されない。**
 *
 * 経理担当者が確定操作をするまで `Invoice` は存在しない。
 */
export type ChargeCalculation = {
  bookingId: string
  shipperName: string
  shipperType: 'CORPORATE' | 'INDIVIDUAL'
  /** 輸送実績（21-2）。**距離は持っていない**ため区間数で代替する。 */
  basis: ChargeBasis
  baseAmount: Money
  /**
   * 契約割引率（22-1）。**個人荷主では `null`**——0% ではない。
   *
   * 0% を出すと「割引が 0 だった」に読め、契約が無いことと区別できない
   * （[ADR-012] が `DiscountRate` について同じ判断をしている）。
   */
  discountRate: number | null
  discountAmount: Money | null
  /** 誤配の記録（21-6 の根拠）。無ければ `null`。 */
  misroute: { at: string; locationUnLocode: string; locationName: string | null } | null
  /** キャンセル料（US30-9）。キャンセルされていなければ `null`。 */
  cancellationFee: {
    bookingStatusAtCancel: string
    bookingStatusLabel: string
    feeRate: number
    amount: Money
  } | null
  taxRate: number
  taxAmount: Money
  /** 調整を入れる前の合計。 */
  totalAmount: Money
}

/** 発行済みの精算書。 */
export type Invoice = {
  invoiceId: string
  invoiceNumber: string
  bookingId: string
  shipperName: string
  basis: ChargeBasis
  baseAmount: Money
  discountRate: number | null
  discountAmount: Money | null
  lineItems: LineItem[]
  cancellationFee: { bookingStatusLabel: string; feeRate: number; amount: Money } | null
  taxRate: number
  taxAmount: Money
  totalAmount: Money
  paymentStatus: PaymentStatus
  issuedAt: string
  dueDate: string | null
}

/** 確定の要求。**調整はここでまとめて送る**（決定 3）。 */
export type CalculateChargeRequest = {
  adjustments: { description: string; amountValue: number }[]
}
