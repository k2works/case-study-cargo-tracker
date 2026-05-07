import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type { Voyage, CreateVoyageRequest, UpdateVoyageRequest } from '../types/voyage'

const VOYAGES_KEY = ['voyages']

export function useVoyages() {
  return useQuery<Voyage[]>({
    queryKey: VOYAGES_KEY,
    queryFn: () => apiClient.get<Voyage[]>('/api/v1/voyages'),
  })
}

export function useVoyage(voyageNumber: string) {
  return useQuery<Voyage>({
    queryKey: [...VOYAGES_KEY, voyageNumber],
    queryFn: () => apiClient.get<Voyage>(`/api/v1/voyages/${voyageNumber}`),
    enabled: !!voyageNumber,
  })
}

export function useCreateVoyage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateVoyageRequest) =>
      apiClient.post<Voyage>('/api/v1/voyages', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: VOYAGES_KEY })
    },
  })
}

export function useUpdateVoyage(voyageNumber: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateVoyageRequest) =>
      apiClient.put<Voyage>(`/api/v1/voyages/${voyageNumber}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: VOYAGES_KEY })
    },
  })
}

export function useDeleteVoyage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (voyageNumber: string) =>
      apiClient.delete<void>(`/api/v1/voyages/${voyageNumber}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: VOYAGES_KEY })
    },
  })
}
