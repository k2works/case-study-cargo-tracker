/** 航海が運べる貨物種別。予約側の CargoType とは意味が違う（船が何を運べるか）。 */
export type RoutingCargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'

export const ROUTING_CARGO_TYPE_LABELS: Record<RoutingCargoType, string> = {
  GENERAL: '一般貨物',
  HAZARDOUS: '危険物',
  REFRIGERATED: '冷凍・冷蔵',
}

export type VoyageMovement = {
  departureUnLocode: string
  departureName: string
  arrivalUnLocode: string
  arrivalName: string
  departureTime: string
  arrivalTime: string
}

export type Voyage = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  supportedCargoTypes: RoutingCargoType[]
  originUnLocode: string
  originName: string
  destinationUnLocode: string
  destinationName: string
  departureTime: string
  arrivalTime: string
  movements: VoyageMovement[]
}

export type VoyageList = {
  voyages: Voyage[]
  totalCount: number
  limit: number
  /** 上限で切ったか。黙って切ると「これで全部だ」と読まれる。 */
  truncated: boolean
}

export type VoyageMovementRequest = {
  departureUnLocode: string
  arrivalUnLocode: string
  departureTime: string
  arrivalTime: string
}

export type VoyageRequest = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  supportedCargoTypes: RoutingCargoType[]
  movements: VoyageMovementRequest[]
}

export type VoyageSearchCriteria = {
  origin: string
  destination: string
  departureFrom: string
  departureTo: string
  cargoType: RoutingCargoType | ''
}

/** 既にある航海との差分（US25）。何が変わるか分からないまま上書きさせない。 */
export type VoyageChange = {
  item: string
  before: string
  after: string
}

export type VoyageDifference = {
  message: string
  hasChanges: boolean
  existing: Voyage
  changes: VoyageChange[]
}

export type LocationOption = {
  unLocode: string
  name: string
}

/**
 * 登録の結果。
 *
 * 同じ航海番号が既にあるのは失敗ではなく、上書きするかを選ばせるための応答である。
 */
export type VoyageRegistrationOutcome =
  { kind: 'registered'; voyage: Voyage } | { kind: 'exists'; difference: VoyageDifference }
