import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { bookingApiClient } from '../../../../lib/api-client'
import type {
  CreateQuotationRequest,
  CreateQuotationResponse,
  QuotationResponse,
} from '../types/quotation'

const QUOTATIONS_KEY = ['quotations']

export function useQuotation(quotationId: string | undefined) {
  return useQuery<QuotationResponse>({
    queryKey: ['quotation', quotationId],
    queryFn: () =>
      bookingApiClient.get<QuotationResponse>(`/api/v1/quotations/${quotationId}`),
    enabled: !!quotationId,
  })
}

export function useCreateQuotation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateQuotationRequest) =>
      bookingApiClient.post<CreateQuotationResponse>('/api/v1/quotations', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUOTATIONS_KEY })
    },
  })
}
