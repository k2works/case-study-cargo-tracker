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
}

/**
 * 経路の状態の表示名。生の英字を出すと、利用者は自分の予約がどうなっているか読めない。
 *
 * 一覧と詳細で別々に持つと、片方だけ言葉を直したときに同じ状態が 2 つの名前で呼ばれる。
 */
export const ROUTING_STATUS_LABELS: Record<string, string> = {
  NOT_ROUTED: '未依頼',
  ROUTING_REQUESTED: '経路設計を依頼済み',
  ROUTED: '経路が決まりました',
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
