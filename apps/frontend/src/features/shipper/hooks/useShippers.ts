import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { bookingApiClient } from '../../../lib/api-client'
import type { Shipper, RegisterShipperRequest } from '../types/shipper'

const SHIPPERS_KEY = ['shippers']

export function useShippers() {
  return useQuery<Shipper[]>({
    queryKey: SHIPPERS_KEY,
    queryFn: () => bookingApiClient.get<Shipper[]>('/api/v1/shippers'),
  })
}

export function useRegisterShipper() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: RegisterShipperRequest) =>
      bookingApiClient.post<Shipper>('/api/v1/shippers', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SHIPPERS_KEY })
    },
  })
}
