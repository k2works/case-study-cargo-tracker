export interface BookingSummary {
  bookingId: string
  origin: string
  destination: string
  status: string
  arrivalDeadline: string
}

export interface TrackingInfo {
  trackingNumber: string
  origin: string
  destination: string
  transportStatus: string
  lastKnownLocation: string
  lastUpdated: string
}
