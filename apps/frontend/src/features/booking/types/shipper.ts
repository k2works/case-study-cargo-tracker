export interface Shipper {
  id: number
  shipperCode: string
  shipperType: 'INDIVIDUAL' | 'CORPORATE'
  name: string
  email: string
  phone: string | null
  contractNumber: string | null
  discountRate: number | null
}

export interface RegisterShipperRequest {
  name: string
  email: string
  phone: string
  shipperType: 'INDIVIDUAL' | 'CORPORATE'
  contractNumber?: string
  discountRate?: number
}
