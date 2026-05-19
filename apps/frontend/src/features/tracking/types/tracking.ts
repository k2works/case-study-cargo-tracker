/**
 * US18 公開追跡照会レスポンス（trackingms `TrackingInfoResponse` と対応）。
 */
export type TrackingInfo = {
  trackingNumber: string
  currentStatus: TransportStatus
  currentLocation: { unlocode: string; portName: string | null } | null
  estimatedArrival: string | null
  deliveredAt: string | null
  misrouted: boolean
  validUntil: string | null
  events: TrackingEvent[]
}

export type TrackingEvent = {
  occurredAt: string
  type: string
  unlocode: string | null
  voyageNumber: string | null
  transportStatus: TransportStatus | null
  handlingType: string | null
  source: string | null
  description: string | null
}

export type TransportStatus =
  | 'NOT_RECEIVED'
  | 'RECEIVED'
  | 'LOADED'
  | 'IN_TRANSIT'
  | 'UNLOADED'
  | 'AWAITING_CLAIM'
  | 'DELIVERED'
  | 'MISROUTED'
  | 'EXCEPTION'

/** US18 公開照会 API のエラーコード。ADR-0013 のエラーレスポンスと対応。 */
export type TrackingErrorCode =
  | 'TOKEN_INVALID'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_TN_MISMATCH'
  | 'TRACKING_NOT_FOUND'

export type TrackingFetchError = {
  status: number
  errorCode: TrackingErrorCode | null
  message: string
}

/**
 * 追跡例外（US19/US20）。
 */
export type TrackingException = {
  exceptionId: string
  trackingNumber: string
  exceptionType: 'DELAY' | 'DAMAGE' | 'LOSS'
  occurredAt: string | null
  occurredUnlocode: string | null
  description: string
  responseStatus: 'PENDING' | 'RESOLVED' | 'ESCALATED'
  resolution: string | null
  resolvedAt: string | null
  escalated: boolean
  createdAt: string | null
}

/**
 * S16 追跡管理一覧の 1 行分（バックエンド {@code TrackingListItemResponse} に対応）。
 */
export type TrackingListItem = {
  trackingNumber: string
  bookingId: string
  currentStatus: TransportStatus
  currentUnlocode: string | null
  originUnlocode: string
  destinationUnlocode: string
  estimatedArrival: string | null
  deliveredAt: string | null
  misrouted: boolean
  lastEventAt: string | null
  updatedAt: string | null
}
