import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type { Itinerary, RoutingRequest } from '../types/itinerary'

export function useItineraries() {
  return useMutation({
    mutationFn: (request: RoutingRequest) =>
      apiClient.post<Itinerary[]>('/api/routing/v1/itineraries', request),
  })
}
