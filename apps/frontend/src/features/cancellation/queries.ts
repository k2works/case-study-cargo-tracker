import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveCancellation,
  fetchCancellation,
  fetchPendingCancellations,
  rejectCancellation,
  requestCancellation,
} from './api'
import type {
  ApproveCancellationRequest,
  RejectCancellationRequest,
  RequestCancellationRequest,
} from './types'

export function usePendingCancellations() {
  return useQuery({
    queryKey: ['cancellations', 'pending'],
    queryFn: fetchPendingCancellations,
  })
}

export function useCancellation(bookingId: string | null) {
  return useQuery({
    queryKey: ['cancellations', bookingId],
    queryFn: () => fetchCancellation(bookingId as string),
    enabled: bookingId !== null && bookingId !== '',
  })
}

export function useRequestCancellation(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: RequestCancellationRequest) =>
      requestCancellation(bookingId, request),
    onSuccess: () => {
      // 申請すると予約の状態も変わりうる（輸送開始前は即時に確定する）
      void queryClient.invalidateQueries({ queryKey: ['cancellations'] })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export function useApproveCancellation(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ApproveCancellationRequest) =>
      approveCancellation(bookingId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cancellations'] })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export function useRejectCancellation(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: RejectCancellationRequest) =>
      rejectCancellation(bookingId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cancellations'] })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}
