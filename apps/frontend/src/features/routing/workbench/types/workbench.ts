// 経路設計ワークベンチ型定義（US08/US09/US11）

export interface RouteCandidatesResponse {
  candidates: CandidateItem[]
}

export interface CandidateItem {
  voyageNumbers: string[]
  via: string[]
  transitDays: number
}

export interface SelectRouteRequest {
  origin: string
  destination: string
  arrivalDeadline: string
  cargoType: string
  selectedIndex: number
}

export interface SelectedRouteResponse {
  legs: LegDto[]
}

export interface LegDto {
  voyageNumber: string
  loadUnlocode: string
  unloadUnlocode: string
  loadTime: string
  unloadTime: string
}

export interface AssignRouteRequest {
  legs: LegDto[]
}
