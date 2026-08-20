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
