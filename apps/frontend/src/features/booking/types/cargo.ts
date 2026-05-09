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
  legs: LegInfo[]
}

export interface LegInfo {
  voyageNumber: string
  loadLocationUnlocode: string
  unloadLocationUnlocode: string
  loadTime: string
  unloadTime: string
}

export interface HazmatInfo {
  unCode: string
  hazardClass: string
  packingGroup: string
}

export interface TemperatureInfo {
  minTemperature: number
  maxTemperature: number
  unit: string
}

export interface CreateCargoRequest {
  shipperId: number
  cargoType: CargoType
  weightKg: number
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string | null
  hazmatInfo?: HazmatInfo
  temperatureInfo?: TemperatureInfo
}
