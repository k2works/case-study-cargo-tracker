// バックエンド DTO（RegisterVoyageRequest / VoyageResponse / VoyageListResponse）に対応する型定義。

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'
export type VoyageStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface Movement {
  departureUnLocode: string
  arrivalUnLocode: string
  departureTime: string // ISO 形式（YYYY-MM-DDTHH:mm:ss）
  arrivalTime: string
}

export interface RegisterVoyageRequest {
  voyageNumber: string
  carrierCode: string
  carrierName: string
  shipName: string
  originUnLocode: string
  destinationUnLocode: string
  departureDate: string
  arrivalDate: string
  carrierMovements: Movement[]
  acceptedCargoTypes: CargoType[]
}

export interface VoyageResponse {
  voyageNumber: string
  status: string
}

export interface VoyageListItem {
  voyageNumber: string
  carrierCode: string
  carrierName: string
  shipName: string
  originUnLocode: string
  destinationUnLocode: string
  departureDate: string
  arrivalDate: string
  status: string
}
