import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  CalculateChargeRequest,
  ConfirmPaymentRequest,
  ChargeCalculation,
  Invoice,
  InvoiceSearchCriteria,
  InvoiceSearchResult,
  UnbilledBooking,
} from './types'

/**
 * 料金を算出していない引取済の予約（US21-1）。
 *
 * **経理担当者が仕事を始める場所である。** 他に気づく手段は無い。
 */
export function fetchUnbilledBookings(): Promise<UnbilledBooking[]> {
  return apiClient.get<UnbilledBooking[]>(API_PATHS.unbilledBookings)
}

/**
 * 発行済みの精算書の一覧・検索（US38）。
 *
 * **条件を渡さなければ、これまでどおりの一覧である。**入口を分けると、一覧と検索で
 * 結果の形が食い違う余地が生まれる。
 */
export function fetchInvoices(criteria: InvoiceSearchCriteria = {}): Promise<InvoiceSearchResult> {
  const params = new URLSearchParams()
  if (criteria.keyword !== undefined && criteria.keyword.trim() !== '') {
    params.set('keyword', criteria.keyword.trim())
  }
  if (criteria.issuedMonth !== undefined && criteria.issuedMonth !== '') {
    params.set('issuedMonth', criteria.issuedMonth)
  }
  const query = params.toString()
  return apiClient.get<InvoiceSearchResult>(
    query === '' ? API_PATHS.invoices : `${API_PATHS.invoices}?${query}`,
  )
}

/** 発行済みの精算書 1 件。 */
export function fetchInvoice(invoiceId: string): Promise<Invoice> {
  return apiClient.get<Invoice>(API_PATHS.invoice(invoiceId))
}

/**
 * 料金の算出結果を取る（[ADR-027] 決定 3）。
 *
 * **保存されない。** サーバが毎回計算して返す。画面はこの値を出すだけで、
 * 金額の計算をしない（決定 2——丸めが 2 か所に分かれると、画面と保存値が食い違う）。
 */
export function fetchChargeCalculation(bookingId: string): Promise<ChargeCalculation> {
  return apiClient.get<ChargeCalculation>(API_PATHS.chargeCalculation(bookingId))
}

/**
 * 料金を確定して精算書を発行する（US21-4・US21-5）。
 *
 * **調整はここでまとめて送る。** 算出中は保存しないため、画面が積んだ明細を
 * 確定の瞬間に渡す。
 */
export function calculateCharge(
  bookingId: string,
  request: CalculateChargeRequest,
): Promise<Invoice> {
  return apiClient.post<Invoice>(API_PATHS.calculateCharge(bookingId), request)
}

/**
 * 支払期限を過ぎた請求書（受入基準 23-5 の代替）。
 *
 * **未払い通知のメールは無い。** 経理担当者はこの一覧でしか気づけない。
 */
export function fetchOverdueInvoices(): Promise<Invoice[]> {
  return apiClient.get<Invoice[]>(API_PATHS.overdueInvoices)
}

/**
 * 入金を確認する（受入基準 23-3・23-4）。
 *
 * **決済機関とは連携していない。** 経理担当者が通帳や入金明細を見て入れる。
 */
export function confirmPayment(
  invoiceId: string,
  request: ConfirmPaymentRequest,
): Promise<Invoice> {
  return apiClient.post<Invoice>(API_PATHS.confirmPayment(invoiceId), request)
}

/**
 * 請求書を取り消す（赤伝・[ADR-028] 決定 3）。
 *
 * **理由は必須。** 残らないと、あとから見て「二重発行の失敗」と区別できない。
 */
export function voidInvoice(invoiceId: string, reason: string): Promise<Invoice> {
  return apiClient.post<Invoice>(API_PATHS.voidInvoice(invoiceId), { reason })
}
