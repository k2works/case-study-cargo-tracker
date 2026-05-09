/**
 * 追跡アクティビティ型定義
 */
export interface TrackingActivityEvent {
  eventType: string
  locationUnlocode: string
  eventTime: string
  voyageNumber: string | null
}

export interface TrackingActivity {
  trackingNumber: string
  bookingId: string
  transportStatus: TrackingStatus
  events: TrackingActivityEvent[]
}

export type TrackingStatus =
  | 'NOT_RECEIVED'
  | 'RECEIVED'
  | 'LOADED'
  | 'ONBOARD_CARRIER'
  | 'UNLOADED'
  | 'AWAITING_CLAIM'
  | 'CLAIMED'
  | 'EXCEPTION'
  | 'UNKNOWN'

export interface IssueTrackingNumberRequest {
  bookingId: string
}

export interface RecordHandlingActivityRequest {
  trackingNumber: string
  eventType: string
  locationUnlocode: string
  eventTime: string
  voyageNumber?: string
  consigneeConfirmation?: string
}

export interface UpdateTrackingStatusRequest {
  newStatus: string
}
