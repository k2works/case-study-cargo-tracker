export type BookingStatus =
  | 'PRELIMINARY'
  | 'ROUTE_PROPOSED'
  | 'CONFIRMED'
  | 'TRACKING_ISSUED'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'SETTLED'
  | 'CANCELLED'

export type CargoType = 'GENERAL' | 'REFRIGERATED' | 'HAZARDOUS' | 'HEAVY'

export interface Cargo {
  bookingId: string
  shipperId: number
  bookingStatus: BookingStatus
  cargoType: CargoType
  weightKg: number
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string | null
}

export interface CreateCargoRequest {
  shipperId: number
  cargoType: CargoType
  weightKg: number
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string | null
}
