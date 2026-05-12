export interface RouteCandidate {
  voyageNumber: string
  transitPort: string | null
  transitDays: number
  estimatedCost: number
  rank: number
}

export interface Estimate {
  estimateId: string
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string
  cargoType: string
  weightKg: number
  status: 'CREATED' | 'CANDIDATES_AVAILABLE' | 'NO_ROUTES'
  candidates: RouteCandidate[]
}

export interface CreateEstimateRequest {
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string
  cargoType: string
  weightKg: number
}
