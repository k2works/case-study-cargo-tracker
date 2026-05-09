/**
 * 追跡アクティビティ型定義
 */
export interface TrackingActivityEvent {
  eventType: string
  locationUnlocode: string
  eventTime: string
  voyageNumber: string | null
}

export interface TrackingExceptionEvent {
  id?: number
  exceptionType: string
  occurredAt: string
  locationUnlocode: string | null
  reason: string | null
  escalationFlag: boolean
  responseContent: string | null
  newEstimatedArrival: string | null
  status: string
}

export interface TrackingActivity {
  trackingNumber: string
  bookingId: string
  transportStatus: TrackingStatus
  events: TrackingActivityEvent[]
  exceptions?: TrackingExceptionEvent[]
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

export interface RecordTrackingExceptionRequest {
  exceptionType: string
  occurredAt: string
  locationUnlocode?: string
  reason?: string
  escalationFlag?: boolean
}
