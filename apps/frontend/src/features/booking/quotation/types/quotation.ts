// バックエンド DTO に対応する型定義（US01）。

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'
export type QuotationStatus = 'DRAFT' | 'OFFERED' | 'ACCEPTED' | 'EXPIRED'

export interface HazardInfoDto {
  imoClass: string
  unNumber: string
  declaration: string
}

export interface CreateQuotationRequest {
  shipperId: number
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string // YYYY-MM-DD
  cargoType: CargoType
  weightKg: number
  hazardInfo?: HazardInfoDto | null
}

export interface CreateQuotationResponse {
  quotationId: string
  status: string
}

export interface RouteCandidateDto {
  candidateSeq: number
  estimatedDays: number
  estimatedCost: number
  estimatedCurrency: string
  itinerarySummary: string
  voyageNumbers: string
}

export interface QuotationResponse {
  quotationId: string
  shipperId: number
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: CargoType
  weightKg: number
  estimatedAmount: number | null
  estimatedCurrency: string | null
  validUntil: string
  status: QuotationStatus
  hazardImoClass?: string | null
  hazardUnNumber?: string | null
  hazardDeclaration?: string | null
  candidates: RouteCandidateDto[]
}
