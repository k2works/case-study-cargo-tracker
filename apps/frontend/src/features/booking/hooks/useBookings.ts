import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type { Cargo, CreateCargoRequest } from '../types/cargo'

const BOOKINGS_KEY = ['bookings']

export function useBookings() {
  return useQuery<Cargo[]>({
    queryKey: BOOKINGS_KEY,
    queryFn: () => apiClient.get<Cargo[]>('/api/booking/v1/cargos'),
  })
}

export function useBooking(bookingId: string) {
  return useQuery<Cargo>({
    queryKey: [...BOOKINGS_KEY, bookingId],
    queryFn: () => apiClient.get<Cargo>(`/api/booking/v1/cargos/${bookingId}`),
    enabled: !!bookingId,
  })
}

export function useCreateBooking() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateCargoRequest) =>
      apiClient.post<Cargo>('/api/booking/v1/cargos', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: BOOKINGS_KEY })
    },
  })
}

export interface AssignRouteRequest {
  legs: {
    voyageNumber: string
    loadLocationUnlocode: string
    unloadLocationUnlocode: string
    loadTime: string
    unloadTime: string
  }[]
}

export function useAssignRoute(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: AssignRouteRequest) =>
      apiClient.put<Cargo>(`/api/booking/v1/cargos/${bookingId}/route`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...BOOKINGS_KEY, bookingId] })
    },
  })
}
