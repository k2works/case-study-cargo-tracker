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
  /** 旅程の区間数。 */
  legCount: number
  /** 区間係数（区間ごとの地域係数の合計）。**距離の代わり**（決定 1 の改訂）。 */
  legFactor: number
  /** 旅程で最も重い地域区分。運んでいなければ `null`。 */
  region: string | null
  /** 地域区分の表示名。**「なぜ 1 区間で 30 万円か」はこれが無いと読めない。** */
  regionLabel: string | null
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
  /** 輸出免税か（決定 8 の改訂）。 */
  taxExempt: boolean
  taxAmount: Money
  /** 調整を入れる前の合計。 */
  totalAmount: Money
}

/**
 * 請求書の検索結果（US38）。
 *
 * **件数と合計を一覧と一緒に受け取る。**画面で足し上げると、上限で切った瞬間に
 * 「見えている分だけの合計」に化ける。
 */
export type InvoiceSearchResult = {
  invoices: Invoice[]
  /** 条件に合う総件数（上限で切る前）。 */
  totalCount: number
  /** **取り消し済みを除いた**合計金額。 */
  totalAmount: number
  currency: string
  limit: number
  /** 上限で切ったか。**黙って切ると「一覧に出ていないから無い」と読まれる**。 */
  truncated: boolean
}

/** 請求書を探す条件（US38）。 */
export type InvoiceSearchCriteria = {
  /** 請求番号・荷主名・予約番号のいずれかに含まれる語。 */
  keyword?: string
  /** 発行月（`yyyy-MM`）。 */
  issuedMonth?: string
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
  /**
   * 輸出免税か（決定 8 の改訂）。
   *
   * **税区分として画面に出す**——「消費税 ¥0」だけでは、免税なのか計算漏れなのか
   * 読めない。荷主から問われたときに答えられない。
   */
  taxExempt: boolean
  taxAmount: Money
  totalAmount: Money
  paymentStatus: PaymentStatus
  paymentStatusLabel: string
  issuedAt: string
  dueDate: string | null
  /** 入金の記録（受入基準 23-3）。**未入金なら `null`**。 */
  payment: Payment | null
  /** 取り消した日時（赤伝）。取り消していなければ `null`。 */
  voidedAt: string | null
  /** 取り消した理由。**残らないと、二重発行の失敗と区別できない。** */
  voidReason: string | null
}

/**
 * 入金の記録（受入基準 23-3）。
 *
 * **根拠ごと持つ。**「入金済」だけでは、いつ・いくら・どの振込かを誰も追えない。
 */
export type Payment = {
  amount: Money
  paidAt: string
  method: string
  methodLabel: string
  transactionReference: string | null
}

/** 入金の確認（経理担当者が手で入れる）。 */
export type ConfirmPaymentRequest = {
  amountValue: number
  paidAt: string
  method: string
  transactionReference: string | null
}

/** 入金の方法。**サーバの `PaymentMethod` と同じ値を持つ。** */
export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  BANK_TRANSFER: '銀行振込',
  PROMISSORY_NOTE: '手形',
  OFFSET: '相殺',
  OTHER: 'その他',
}

/** 確定の要求。**調整はここでまとめて送る**（決定 3）。 */
export type CalculateChargeRequest = {
  adjustments: { description: string; amountValue: number }[]
}
