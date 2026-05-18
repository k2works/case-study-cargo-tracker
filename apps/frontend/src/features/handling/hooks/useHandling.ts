import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { handlingApiClient } from '../../../lib/api-client'
import type {
  HandlingActivityRecord,
  HandlingActivityResponse,
  RegisterHandlingActivityRequest,
} from '../types/handling'

const HANDLING_KEY = ['handling']

export function useHandlingActivities(trackingNumber: string | undefined) {
  return useQuery<HandlingActivityRecord[]>({
    queryKey: [...HANDLING_KEY, 'activities', trackingNumber ?? 'all'],
    queryFn: () =>
      handlingApiClient.get<HandlingActivityRecord[]>(
        `/api/v1/handling/activities/${trackingNumber}`,
      ),
    enabled: !!trackingNumber,
  })
}

export function useRegisterHandlingActivity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: RegisterHandlingActivityRequest) =>
      handlingApiClient.post<HandlingActivityResponse>('/api/v1/handling/activities', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: HANDLING_KEY })
    },
  })
}
