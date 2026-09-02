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
  /** 船名。航海番号だけでは、どの船かを別画面で調べることになる（US09）。 */
  vesselName: string
  /** 運送会社。同じ区間でも会社によって扱いが変わるため、候補の選択に効く。 */
  carrierName: string
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
  /**
   * その港での待ち時間（分）。経由港だけが値を持つ。
   *
   * 所要日数の合計だけでは、どこでどれだけ止まるのかが分からない（US09）。
   */
  layoverMinutes: number | null
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
  /**
   * YYYY-MM-DD。荷物が出せるようになる日。指定が無ければ出発の早さでは絞らない。
   *
   * 予約の出発希望日を引き継ぐ。荷主が「9 月 1 日以降でないと倉庫に入らない」と
   * 言っているのに、それより前に出る便を候補に出すと、押さえても積むものがない。
   */
  earliestDeparture: string | null
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
  /**
   * YYYY-MM-DD。荷物が出せるようになる日。指定が無ければ出発の早さでは絞らない。
   *
   * 予約の出発希望日を引き継ぐ。荷主が「9 月 1 日以降でないと倉庫に入らない」と
   * 言っているのに、それより前に出る便を候補に出すと、押さえても積むものがない。
   */
  earliestDeparture: string | null
  /**
   * 誤配のあとの組み直しか（US28-4・[ADR-026] 決定 4）。
   *
   * **期限で候補を弾かないことをサーバに伝える。** サーバは既定で期限を超える候補を
   * 刈る。誤配した貨物は遅れているのが普通で、元の期限に間に合う便はまず残っていない
   * ——伝えなければ**候補が 1 本も出ず、組み直せない**。
   */
  reroute?: boolean
}

/**
 * 登録・訂正の入力欄 1 区間分。
 *
 * 画面と検証の両方が使うため、features 側に置く。画面の中に閉じ込めると、
 * 検証だけを切り出したときに型が付いてこない。
 */
export type MovementInput = {
  /** 並べ替え・削除しても入力欄が入れ替わらないための識別子。表示には使わない。 */
  key: string
  departureUnLocode: string
  arrivalUnLocode: string
  departureTime: string
  arrivalTime: string
}
