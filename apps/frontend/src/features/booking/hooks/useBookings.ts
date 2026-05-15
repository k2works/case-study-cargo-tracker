import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { bookingApiClient } from '../../../lib/api-client'
import type { BookCargoRequest, BookingListItem, BookingResponse } from '../types/booking'

const BOOKINGS_KEY = ['bookings']

export function useBookings() {
  return useQuery<BookingListItem[]>({
    queryKey: BOOKINGS_KEY,
    queryFn: () => bookingApiClient.get<BookingListItem[]>('/api/v1/bookings'),
  })
}

export function useBookCargo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BookCargoRequest) =>
      bookingApiClient.post<BookingResponse>('/api/v1/bookings', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: BOOKINGS_KEY })
    },
  })
}
