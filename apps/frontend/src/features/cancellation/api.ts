import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  ApproveCancellationRequest,
  CancellationOutcome,
  CancellationRequest,
  PendingCancellation,
  RejectCancellationRequest,
  RequestCancellationRequest,
} from './types'

/** 承認待ちの一覧（US30-4）。**件数の遷移先である**。 */
export function fetchPendingCancellations() {
  return apiClient.get<PendingCancellation[]>(API_PATHS.cancellations)
}

/** 1 つの予約のキャンセル申請。無ければ null。 */
export function fetchCancellation(bookingId: string) {
  return apiClient.get<CancellationRequest | null>(API_PATHS.cancellation(bookingId))
}

/** キャンセルを申請する（US30-1）。 */
export function requestCancellation(bookingId: string, request: RequestCancellationRequest) {
  return apiClient.post<CancellationOutcome>(API_PATHS.cancellation(bookingId), request)
}

/** 承認する（US30-5）。**陸揚げ地は必須**。 */
export function approveCancellation(bookingId: string, request: ApproveCancellationRequest) {
  return apiClient.put<CancellationRequest>(
    `${API_PATHS.cancellation(bookingId)}/approve`,
    request,
  )
}

/** 却下する（US30-7）。**予約は輸送中のまま維持される**。 */
export function rejectCancellation(bookingId: string, request: RejectCancellationRequest) {
  return apiClient.put<CancellationRequest>(
    `${API_PATHS.cancellation(bookingId)}/reject`,
    request,
  )
}
