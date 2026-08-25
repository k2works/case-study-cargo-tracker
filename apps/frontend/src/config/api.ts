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
