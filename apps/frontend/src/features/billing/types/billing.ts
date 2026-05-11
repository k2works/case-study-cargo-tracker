export interface LineItemInput {
  description: string
  amountValue: number
}

export interface CalculateInvoiceRequest {
  bookingId: string
  shipperId?: string
  lineItems: LineItemInput[]
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
  finalAmountValue: number
  currency: string
  paymentStatus: string
  issuedAt: string
  dueDate: string
  lineItems: LineItemResponse[]
}
