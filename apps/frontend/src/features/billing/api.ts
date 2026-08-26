import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  CalculateChargeRequest,
  ChargeCalculation,
  Invoice,
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

/** 発行済みの精算書の一覧。 */
export function fetchInvoices(): Promise<Invoice[]> {
  return apiClient.get<Invoice[]>(API_PATHS.invoices)
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
