// バックエンド DTO（BookCargoRequest / BookingResponse / BookingListResponse）に対応する型定義。

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'

export interface Dimensions {
  lengthCm: number
  widthCm: number
  heightCm: number
}

export interface HazardInfo {
  imoClass: string
  unNumber: string
  declaration: string
}

export interface TemperatureCondition {
  minCelsius: number
  maxCelsius: number
}

export interface CargoSpec {
  cargoType: CargoType
  weightKg: number
  quantity: number
  productName: string
  dimensions: Dimensions
  hazardInfo?: HazardInfo
  temperatureCondition?: TemperatureCondition
}

export interface RouteSpec {
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string // ISO 形式（YYYY-MM-DD）
}

export interface BookCargoRequest {
  shipperId: number
  cargoSpec: CargoSpec
  routeSpec: RouteSpec
}

export interface BookingResponse {
  bookingId: string
  bookingStatus: string
}

export interface BookingListItem {
  bookingId: string
  shipperId: number
  trackingNumber: string | null
  cargoType: CargoType
  productName: string
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  bookingStatus: string
  routingStatus: string | null
  createdAt: string | null
}
