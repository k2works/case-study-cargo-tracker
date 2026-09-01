import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  calculateCharge,
  confirmPayment,
  fetchOverdueInvoices,
  voidInvoice,
  fetchChargeCalculation,
  fetchInvoice,
  fetchInvoices,
  fetchUnbilledBookings,
} from './api'
import type {
  CalculateChargeRequest,
  ConfirmPaymentRequest,
  InvoiceSearchCriteria,
} from './types'

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

/**
 * 発行済みの精算書の一覧・検索（US38）。
 *
 * **条件をキーに含める。**含めないと、条件を変えても前の結果が返り続ける。
 */
export function useInvoices(criteria: InvoiceSearchCriteria = {}) {
  return useQuery({
    queryKey: ['invoices', criteria.keyword ?? '', criteria.issuedMonth ?? ''],
    queryFn: () => fetchInvoices(criteria),
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

/**
 * 支払期限を過ぎた請求書（受入基準 23-5 の代替）。
 *
 * **ダッシュボードの件数もこれを使う。** 件数と一覧が別の問い合わせから来ると、
 * 「N 件あります」を押した先が空になることが起きる。
 */
export function useOverdueInvoices() {
  return useQuery({
    queryKey: ['overdue-invoices'],
    queryFn: fetchOverdueInvoices,
  })
}

/**
 * 入金を確認する（受入基準 23-3・23-4）。
 *
 * 確認すると**期限超過の一覧からも消える**ので、両方を取り直す。
 */
export function useConfirmPayment(invoiceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ConfirmPaymentRequest) => confirmPayment(invoiceId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['overdue-invoices'] })
      // 予約が「精算済」になる（受入基準 23-4）。予約の画面も取り直す
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['booking'] })
    },
  })
}

/**
 * 請求書を取り消す（赤伝）。
 *
 * 取り消すと**その予約は再び「料金未算出」に戻る**ので、待ち行列も取り直す。
 */
export function useVoidInvoice(invoiceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reason: string) => voidInvoice(invoiceId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['overdue-invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['unbilled-bookings'] })
    },
  })
}
