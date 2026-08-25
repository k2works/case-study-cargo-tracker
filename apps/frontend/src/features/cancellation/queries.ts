import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveCancellation,
  fetchCancellation,
  fetchCancellationHistory,
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

/**
 * キャンセル申請の履歴（US30-10）。
 *
 * **最新の 1 件では足りない。** 却下されて再申請すると、前回の却下理由が
 * 予約詳細から消える——「なぜ一度断られたか」は、次に荷主と話す営業が必要とする。
 */
export function useCancellationHistory(bookingId: string | null) {
  return useQuery({
    queryKey: ['cancellations', bookingId, 'history'],
    queryFn: () => fetchCancellationHistory(bookingId as string),
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
