import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../lib/api-client'
import type {
  TrackingActivity,
  IssueTrackingNumberRequest,
  RecordHandlingActivityRequest,
  UpdateTrackingStatusRequest,
} from '../types/tracking'

const TRACKING_KEY = ['tracking']

/**
 * 追跡番号で追跡情報を取得する
 */
export function useTrackingActivity(trackingNumber: string) {
  return useQuery<TrackingActivity>({
    queryKey: [...TRACKING_KEY, trackingNumber],
    queryFn: () => apiClient.get<TrackingActivity>(`/api/tracking/v1/${trackingNumber}`),
    enabled: !!trackingNumber,
  })
}

/**
 * 追跡番号を発行する（予約 ID → 追跡番号）
 */
export function useIssueTrackingNumber() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: IssueTrackingNumberRequest) =>
      apiClient.post<TrackingActivity>('/api/tracking/v1/numbers', data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: [...TRACKING_KEY, data.trackingNumber] })
      queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

/**
 * 荷役作業を記録する
 */
export function useRecordHandlingActivity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: RecordHandlingActivityRequest) =>
      apiClient.post<TrackingActivity>('/api/handling/v1/activities', data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: [...TRACKING_KEY, data.trackingNumber] })
    },
  })
}

/**
 * 貨物状態を手動更新する
 */
export function useUpdateTrackingStatus(trackingNumber: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateTrackingStatusRequest) =>
      apiClient.put<TrackingActivity>(`/api/tracking/v1/${trackingNumber}/status`, data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: [...TRACKING_KEY, data.trackingNumber] })
    },
  })
}
