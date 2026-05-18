import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { handlingApiClient } from '../../../lib/api-client'
import type {
  CargoSnapshotResponse,
  CargoStatusHistoryRecord,
  CargoStatusUpdateResponse,
  HandlingActivityRecord,
  HandlingActivityResponse,
  RegisterHandlingActivityRequest,
  UpdateCargoStatusRequest,
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

// US17: 貨物状態手動更新
export function useCargoSnapshot(trackingNumber: string | undefined) {
  return useQuery<CargoSnapshotResponse>({
    queryKey: [...HANDLING_KEY, 'snapshot', trackingNumber ?? 'none'],
    queryFn: () =>
      handlingApiClient.get<CargoSnapshotResponse>(
        `/api/v1/handling/activities/${trackingNumber}/snapshot`,
      ),
    enabled: !!trackingNumber,
  })
}

export function useStatusHistory(trackingNumber: string | undefined) {
  return useQuery<CargoStatusHistoryRecord[]>({
    queryKey: [...HANDLING_KEY, 'status-history', trackingNumber ?? 'none'],
    queryFn: () =>
      handlingApiClient.get<CargoStatusHistoryRecord[]>(
        `/api/v1/handling/activities/${trackingNumber}/status-history`,
      ),
    enabled: !!trackingNumber,
  })
}

export function useUpdateCargoStatus(trackingNumber: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateCargoStatusRequest) =>
      handlingApiClient.put<CargoStatusUpdateResponse>(
        `/api/v1/handling/activities/${trackingNumber}/status`,
        data,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: HANDLING_KEY })
    },
  })
}
