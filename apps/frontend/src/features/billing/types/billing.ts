export type Invoice = {
  invoiceId: string
  bookingId: string
  shipperId: string
  basicAmount: number | null
  discountAmount: number | null
  totalAmount: number | null
  currency: string
  billingStatus: BillingStatus
  invoiceNumber: string | null
  paymentDue: string | null
  paidAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type BillingStatus =
  | 'PENDING'
  | 'CALCULATED'
  | 'INVOICED'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED'

export type CalculateChargeRequest = {
  baseAmount: number
  currencyCode: string
  discountRate: number
  operatorId: string
}

export type CalculateChargeResponse = {
  invoiceId: string
  status: string
}
