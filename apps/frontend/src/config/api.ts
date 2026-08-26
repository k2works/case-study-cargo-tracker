/** API のベース URL。ローカルは Vite のプロキシ、コンテナ環境は Ingress / Gateway を指す。 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const API_PATHS = {
  login: '/api/v1/auth/login',
  bookings: '/api/v1/bookings',
  bookingLocations: '/api/v1/bookings/locations',
  bookingHazardClasses: '/api/v1/bookings/hazard-classes',
  shippers: '/api/v1/shippers',
  voyages: '/api/v1/voyages',
  voyageLocations: '/api/v1/voyages/locations',
  /** 経路候補算出（ADR-017）。単数の最適経路ではなく、推奨順の複数候補を返す。 */
  routes: '/api/v1/routes',
  voyageDetail: (voyageNumber: string) => `/api/v1/voyages/${encodeURIComponent(voyageNumber)}`,
  /** 公開追跡照会（認証不要）。業務 API と接頭辞を分けることで公開範囲を一目で分かるようにする。 */
  publicTracking: (trackingNumber: string) => `/api/v1/public/tracking/${trackingNumber}`,
  trackingManagement: '/api/v1/tracking/manage',
  /**
   * **料金を算出していない引取済の予約**（US21-1）。
   *
   * 経理担当者は他に気づく手段を持たない——メールの仕組みは無い。
   * ダッシュボードの件数も、精算管理の待ち行列も、ここから来る。
   */
  unbilledBookings: '/api/v1/billing/unbilled',
  /** 発行済みの精算書の一覧。 */
  invoices: '/api/v1/billing/invoices',
  /** 発行済みの精算書 1 件。 */
  invoice: (invoiceId: string) => `/api/v1/billing/invoices/${encodeURIComponent(invoiceId)}`,
  /** 支払期限を過ぎた請求書（受入基準 23-5 の代替）。 */
  overdueInvoices: '/api/v1/billing/invoices/overdue',
  /** 入金の確認（受入基準 23-3）。**経理担当者が手で入れる**——決済機関との連携先が無い。 */
  confirmPayment: (invoiceId: string) =>
    `/api/v1/billing/invoices/${encodeURIComponent(invoiceId)}/payment`,
  /** 請求書の取り消し（赤伝・[ADR-028] 決定 3）。 */
  voidInvoice: (invoiceId: string) =>
    `/api/v1/billing/invoices/${encodeURIComponent(invoiceId)}/void`,
  /**
   * 料金の算出結果（[ADR-027](../../../docs/adr/027-transport-charge-calculation.md) 決定 3）。
   *
   * **保存されない。** 算出中の精算書は存在せず、サーバが毎回計算して返す。
   * 下書きを持つと、下書きのまま忘れられた精算書が溜まる——それを見つける手段を
   * また作ることになる。
   */
  chargeCalculation: (bookingId: string) =>
    `/api/v1/billing/calculations/${encodeURIComponent(bookingId)}`,
  /**
   * 料金を確定して精算書を発行する（US21-4・US21-5）。
   *
   * **調整はここでまとめて送る。** 算出中は保存しないため、画面が積んだ明細を
   * 確定の瞬間に渡す。
   */
  calculateCharge: (bookingId: string) =>
    `/api/v1/billing/${encodeURIComponent(bookingId)}/calculate`,
  handling: '/api/v1/handling',
  customs: '/api/v1/customs',
  /**
   * 承認待ちのキャンセル申請（US30）。追跡管理者が見る。
   *
   * **`/api/v1/bookings/...` の下に置かない。** `/api/v1/bookings/{bookingId}` が
   * `cancellations` を予約 ID として拾ってしまう——モックでも本物（Spring の
   * パス変数）でも同じ衝突が起きる。実際 IT9 で先に置いてしまい、一覧が常に
   * 空になった。
   */
  cancellations: '/api/v1/cancellations',
  /**
   * **陸揚げ待ち**——承認済みで陸揚げ地が決まっている貨物（IT10 返済枠 0.3）。
   *
   * 作業指示は自動で作られない（[ADR-025] 決定 5）。荷役の担当者はここで
   * 自分の手番に気づく——連絡を待つだけだと、貨物は指定した港を通り過ぎる。
   */
  awaitingDischarge: '/api/v1/cancellations/awaiting-discharge',
  /** 1 つの予約のキャンセル申請（申請・承認・却下）。 */
  cancellation: (bookingId: string) =>
    `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation`,
  /**
   * 1 つの予約のキャンセル申請の**履歴**（US30-10）。
   *
   * 最新の 1 件を返す `cancellation` とは別に置く。却下されて再申請した予約では、
   * **前回の却下理由**が次の判断の材料になる。
   */
  cancellationHistory: (bookingId: string) =>
    `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellations`,
  billing: '/api/v1/billing',
  /** ロックされたアカウントの管理（US32）。システム管理者のみ。 */
  lockedAccounts: '/api/v1/admin/accounts/locked',
  unlockAccount: (username: string) =>
    `/api/v1/admin/accounts/${encodeURIComponent(username)}/unlock`,
} as const
