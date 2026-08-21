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

/** 経路候補の 1 区間（US08）。 */
export type RouteLeg = {
  voyageNumber: string
  fromUnLocode: string
  fromName: string
  toUnLocode: string
  toName: string
  departureTime: string
  arrivalTime: string
}

export type RoutePort = {
  unLocode: string
  name: string
}

/**
 * 経路候補（US08）。
 *
 * `rank` はサーバが決めた推奨順であり、画面は並べ替えない（ADR-018）。
 * `estimatedCost` は概算であって請求金額ではない（US21 で実料金に差し替える）。
 */
export type RouteCandidate = {
  rank: number
  direct: boolean
  voyageNumbers: string[]
  departureTime: string
  arrivalTime: string
  transitDays: number
  transshipmentCount: number
  transitPorts: RoutePort[]
  estimatedCost: number
  legs: RouteLeg[]
}

/** 実際に使われた条件。候補が無かったとき「何が効いているか」を示すために要る。 */
export type AppliedRouteCriteria = {
  originUnLocode: string
  originName: string
  destinationUnLocode: string
  destinationName: string
  arrivalDeadline: string
  cargoType: RoutingCargoType
  maxTransshipments: number
}

export type RouteCandidateList = {
  candidates: RouteCandidate[]
  totalCount: number
  appliedCriteria: AppliedRouteCriteria
}

/** 画面が指定する探索条件。期限は日付のまま送る（ADR-017）。 */
export type RouteSearchCriteria = {
  origin: string
  destination: string
  /** YYYY-MM-DD。**日時に変換しない。** サーバが業務タイムゾーンの当日終わりに直す */
  deadline: string
  cargoType: RoutingCargoType
  maxTransshipments: number
}
