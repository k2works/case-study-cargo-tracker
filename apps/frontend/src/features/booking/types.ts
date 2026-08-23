export type ShipperType = 'INDIVIDUAL' | 'CORPORATE'

export const SHIPPER_TYPE_LABELS: Record<ShipperType, string> = {
  INDIVIDUAL: '個人',
  CORPORATE: '法人',
}

export type Shipper = {
  id: number
  shipperCode: string
  type: ShipperType
  name: string
  email: string
  address: string
  phone: string | null
  /** 法人のときだけ値を持つ。 */
  contractNumber: string | null
  /** 百分率（12.5 は 12.5%）。null は 0% ではなく「未設定」。 */
  discountRatePercent: number | null
}

export type ShipperRequest = {
  type: ShipperType
  name: string
  email: string
  address: string
  phone: string | null
  /** 法人のときだけ送る。個人で送るとサーバが拒否する。 */
  contractNumber: string | null
  /** 百分率。未入力は null（0% と区別する）。 */
  discountRatePercent: number | null
  /** 同じメールアドレスの荷主があっても新規で登録するか。 */
  registerAnyway: boolean
}

/** 同じメールアドレスの荷主が既にある場合の応答。エラーではなく問いかけ。 */
export type DuplicateShipper = {
  message: string
  existing: Shipper
}

export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED'

export const CARGO_TYPE_LABELS: Record<CargoType, string> = {
  GENERAL: '一般貨物',
  HAZARDOUS: '危険物',
  REFRIGERATED: '冷凍・冷蔵貨物',
}

export const BOOKING_STATUS_LABELS: Record<string, string> = {
  PRELIMINARY: '仮受付',
  /** 経路が決まり、荷主に提示できる状態。**確定ではない**（確定は荷主の合意を経た別の作業）。 */
  ROUTE_PROPOSED: '経路提案中',
  ROUTE_NOTIFIED: '荷主へ通知済',
  CONFIRMED: '確定済',
  TRACKING_ISSUED: '追跡番号発行済',
}

/**
 * 経路の状態の表示名。生の英字を出すと、利用者は自分の予約がどうなっているか読めない。
 *
 * 一覧と詳細で別々に持つと、片方だけ言葉を直したときに同じ状態が 2 つの名前で呼ばれる。
 */
export const ROUTING_STATUS_LABELS: Record<string, string> = {
  NOT_ROUTED: '未依頼',
  ROUTING_REQUESTED: '経路設計を依頼済み',
  ROUTED: '経路確定',
  CONSULTATION_REQUESTED: '条件協議中',
}

/**
 * 危険物クラスの選択肢。
 *
 * 法定の分類であり、画面で言葉を選べる項目ではない。対訳表を画面に置くと分類名の直しが
 * 2 箇所に分かれるため、表示名もサーバから受け取る。
 */
export type HazardClassOption = {
  code: string
  label: string
}

export type LocationOption = {
  unLocode: string
  name: string
}

export type Booking = {
  id: number
  bookingId: string
  shipperId: number
  /** 荷主の名前。営業担当者は社名で探すため、一覧にも出す。 */
  shipperName: string | null
  bookingStatus: string
  transportStatus: string
  routingStatus: string
  type: CargoType
  weightKg: number
  quantity: number | null
  description: string | null
  lengthCm: number | null
  widthCm: number | null
  heightCm: number | null
  originUnLocode: string
  originName: string
  destinationUnLocode: string
  destinationName: string
  departureDate: string | null
  arrivalDeadline: string
  hazardousClass: string | null
  unNumber: string | null
  properShippingName: string | null
  minCelsius: number | null
  maxCelsius: number | null
  /**
   * 割り当てられた旅程（US09）。経路が決まっていなければ null。
   *
   * 空の配列にしない。「区間が 0 件の旅程がある」と読め、画面が空の表を出す。
   */
  itinerary: ItineraryLeg[] | null
  /**
   * 荷主へ通知した日時（US12-4）。通知していなければ `null`。
   *
   * **メールは送られていない。** これが唯一の記録である（US19 の通知基盤まで）。
   */
  routeNotifiedAt: string | null
  routeNotifiedBy: string | null
  /** 発行済みの追跡番号（US14）。未発行なら `null`。 */
  trackingNumber: string | null
  /**
   * いまこの予約に対して行える操作。
   *
   * **判定はサーバの集約が持つ**（[ADR-021]）。画面が状態名を見比べて同じ判断を組み立てると、
   * 遷移の規則が集約・画面・モックの 3 か所に分かれ、片方だけ直る形になる。
   *
   * **権限は含まない。** ここが答えるのは「予約の状態として行えるか」だけで、
   * 「その利用者が行ってよいか」はロールで判断する。
   */
  availableActions: BookingAction[]
}

/**
 * 予約に対していま行える操作。サーバの `BookingAction` の写しである。
 *
 * 値が食い違うと、画面はボタンを出さないだけで**何も知らせずに操作を隠す**。
 * モックのハンドラも本物と同じ判定を通す（`deriveAvailableActions`）。
 */
export type BookingAction =
  | 'REQUEST_ROUTING'
  | 'ASSIGN_ROUTE'
  | 'REQUEST_CONSULTATION'
  | 'NOTIFY_SHIPPER'
  | 'CONFIRM'
  | 'RETURN_TO_ROUTING'
  | 'ISSUE_TRACKING_NUMBER'
  | 'REVISE_SCHEDULE'

/** その操作がいま行えるか。状態名の比較を画面に書かないための入口。 */
export function can(booking: Booking, action: BookingAction): boolean {
  return booking.availableActions?.includes(action) ?? false
}

/** 旅程の区間 1 本。港は名前まで受け取る（画面に対訳表を置かない）。 */
export type ItineraryLeg = {
  voyageNumber: string
  loadUnLocode: string
  loadName: string
  unloadUnLocode: string
  unloadName: string
  loadTime: string
  unloadTime: string
}

/** 一覧。上限で切られたことを黙っていると「全件見た」と受け取られる。 */
export type BookingList = {
  bookings: Booking[]
  totalCount: number
  limit: number
  truncated: boolean
}

export type BookingRequest = {
  shipperId: number
  type: CargoType
  weightKg: number
  quantity: number | null
  description: string | null
  lengthCm: number | null
  widthCm: number | null
  heightCm: number | null
  originUnLocode: string
  destinationUnLocode: string
  departureDate: string | null
  arrivalDeadline: string
  hazardousClass: string | null
  unNumber: string | null
  properShippingName: string | null
  minCelsius: number | null
  maxCelsius: number | null
}
