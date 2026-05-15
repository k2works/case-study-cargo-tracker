import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { routingApiClient } from '../../../../lib/api-client'
import type { RegisterVoyageRequest, VoyageListItem, VoyageResponse } from '../types/voyage'

const VOYAGES_KEY = ['voyages']

export function useVoyages() {
  return useQuery<VoyageListItem[]>({
    queryKey: VOYAGES_KEY,
    queryFn: () => routingApiClient.get<VoyageListItem[]>('/api/v1/voyages'),
  })
}

export function useRegisterVoyage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: RegisterVoyageRequest) =>
      routingApiClient.post<VoyageResponse>('/api/v1/voyages', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: VOYAGES_KEY })
    },
  })
}
