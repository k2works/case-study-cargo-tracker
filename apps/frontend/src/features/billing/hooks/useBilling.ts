import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type { CalculateInvoiceRequest, InvoiceResponse } from '../types/billing'

export function useCalculateInvoice() {
  return useMutation({
    mutationFn: (data: CalculateInvoiceRequest) =>
      apiClient.post<InvoiceResponse>('/api/billing/v1/invoices/calculate', data),
  })
}

export function useConfirmInvoice() {
  return useMutation({
    mutationFn: (invoiceId: number) =>
      apiClient.post<InvoiceResponse>(`/api/billing/v1/invoices/${invoiceId}/confirm`, {}),
  })
}
