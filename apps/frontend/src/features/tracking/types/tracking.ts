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
