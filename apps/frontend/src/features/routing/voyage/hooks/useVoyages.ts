import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { routingApiClient } from '../../../../lib/api-client'
import type {
  RegisterVoyageRequest,
  UpdateVoyageScheduleRequest,
  VoyageListItem,
  VoyageResponse,
} from '../types/voyage'

const VOYAGES_KEY = ['voyages']

export function useVoyages() {
  return useQuery<VoyageListItem[]>({
    queryKey: VOYAGES_KEY,
    queryFn: () => routingApiClient.get<VoyageListItem[]>('/api/v1/voyages'),
  })
}

// US25: 編集画面で既存値を取得する
export function useVoyage(voyageNumber: string | undefined) {
  return useQuery<VoyageListItem>({
    queryKey: ['voyage', voyageNumber],
    queryFn: () => routingApiClient.get<VoyageListItem>(`/api/v1/voyages/${voyageNumber}`),
    enabled: !!voyageNumber,
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

// US25: 既存航海スケジュール更新
export function useUpdateVoyage(voyageNumber: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateVoyageScheduleRequest) =>
      routingApiClient.put<VoyageListItem>(`/api/v1/voyages/${voyageNumber}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: VOYAGES_KEY })
      queryClient.invalidateQueries({ queryKey: ['voyage', voyageNumber] })
    },
  })
}
