import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  calculateCharge,
  fetchChargeCalculation,
  fetchInvoice,
  fetchInvoices,
  fetchUnbilledBookings,
} from './api'
import type { CalculateChargeRequest } from './types'

/**
 * billing コンテキストのデータ取得。
 *
 * 精算は「引取が終わってから」の作業であり、秒単位で変わるものではない。
 * 追跡のようなポーリングは置かない。
 */

/** 料金未算出の引取済予約（US21-1）。**ダッシュボードの件数もこれを使う**。 */
export function useUnbilledBookings() {
  return useQuery({
    queryKey: ['unbilled-bookings'],
    queryFn: fetchUnbilledBookings,
  })
}

/** 発行済みの精算書の一覧。 */
export function useInvoices() {
  return useQuery({
    queryKey: ['invoices'],
    queryFn: fetchInvoices,
  })
}

/** 発行済みの精算書 1 件。 */
export function useInvoice(invoiceId: string) {
  return useQuery({
    queryKey: ['invoice', invoiceId],
    queryFn: () => fetchInvoice(invoiceId),
    enabled: invoiceId !== '',
  })
}

/** 料金の算出結果（保存されない）。 */
export function useChargeCalculation(bookingId: string) {
  return useQuery({
    queryKey: ['charge-calculation', bookingId],
    queryFn: () => fetchChargeCalculation(bookingId),
    enabled: bookingId !== '',
  })
}

/**
 * 料金を確定して精算書を発行する。
 *
 * 発行すると**待ち行列から消える**ので、未算出の一覧と精算書の一覧の両方を取り直す。
 * 片方だけ無効化すると、確定した予約が待ち行列に残り続ける。
 */
export function useCalculateCharge(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CalculateChargeRequest) => calculateCharge(bookingId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['unbilled-bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}
