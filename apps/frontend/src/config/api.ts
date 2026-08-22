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
  billing: '/api/v1/billing',
  /** ロックされたアカウントの管理（US32）。システム管理者のみ。 */
  lockedAccounts: '/api/v1/admin/accounts/locked',
  unlockAccount: (username: string) =>
    `/api/v1/admin/accounts/${encodeURIComponent(username)}/unlock`,
} as const
