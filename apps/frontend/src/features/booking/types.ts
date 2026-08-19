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
}

export type ShipperRequest = {
  type: ShipperType
  name: string
  email: string
  address: string
  phone: string | null
  /** 同じメールアドレスの荷主があっても新規で登録するか。 */
  registerAnyway: boolean
}

/** 同じメールアドレスの荷主が既にある場合の応答。エラーではなく問いかけ。 */
export type DuplicateShipper = {
  message: string
  existing: Shipper
}
