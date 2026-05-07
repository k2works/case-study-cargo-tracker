export interface CarrierMovement {
  departureLocationUnlocode: string
  arrivalLocationUnlocode: string
  departureDate: string
  arrivalDate: string
  seqNumber: number
}

export interface Voyage {
  id: number
  voyageNumber: string
  carrierMovements: CarrierMovement[]
}

export interface CreateVoyageRequest {
  voyageNumber: string
  carrierMovements: CarrierMovement[]
}

export interface UpdateVoyageRequest {
  carrierMovements: CarrierMovement[]
}
