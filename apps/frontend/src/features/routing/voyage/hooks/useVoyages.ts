import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { routingApiClient } from '../../../../lib/api-client'
import type {
  RegisterVoyageRequest,
  UpdateVoyageScheduleRequest,
  VoyageListItem,
  VoyageResponse,
} from '../types/voyage'

const VOYAGES_KEY = ['voyages']

export interface VoyageSearchCriteria {
  origin?: string
  destination?: string
  departureFrom?: string
  departureTo?: string
  cargoType?: string
}

function buildVoyagesUrl(criteria?: VoyageSearchCriteria): string {
  if (!criteria) return '/api/v1/voyages'
  const params = new URLSearchParams()
  if (criteria.origin) params.set('origin', criteria.origin)
  if (criteria.destination) params.set('destination', criteria.destination)
  if (criteria.departureFrom) params.set('departureFrom', criteria.departureFrom)
  if (criteria.departureTo) params.set('departureTo', criteria.departureTo)
  if (criteria.cargoType) params.set('cargoType', criteria.cargoType)
  const qs = params.toString()
  return qs ? `/api/v1/voyages?${qs}` : '/api/v1/voyages'
}

export function useVoyages(criteria?: VoyageSearchCriteria) {
  return useQuery<VoyageListItem[]>({
    queryKey: [...VOYAGES_KEY, criteria],
    queryFn: () => routingApiClient.get<VoyageListItem[]>(buildVoyagesUrl(criteria)),
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
