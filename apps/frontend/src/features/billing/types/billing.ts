export interface LineItemInput {
  description: string
  amountValue: number
}

export interface CalculateInvoiceRequest {
  bookingId: string
  shipperId?: string
  lineItems: LineItemInput[]
  discountRate?: number
}

export interface LineItemResponse {
  description: string
  amountValue: number
  currency: string
}

export interface InvoiceResponse {
  id: number
  invoiceNumber: string
  bookingId: string
  baseAmountValue: number
  discountRate: number
  discountAmountValue: number
  finalAmountValue: number
  currency: string
  paymentStatus: string
  issuedAt: string
  dueDate: string
  paidAt?: string
  lineItems: LineItemResponse[]
}
