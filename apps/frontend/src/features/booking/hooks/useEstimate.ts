import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type { Estimate, CreateEstimateRequest } from '../types/estimate'

export function useCreateEstimate() {
  return useMutation({
    mutationFn: (data: CreateEstimateRequest) =>
      apiClient.post<Estimate>('/api/booking/v1/estimates', data),
  })
}
