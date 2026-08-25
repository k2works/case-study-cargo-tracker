import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  CustomsDeclarationDetail,
  CustomsSearchCriteria,
  CustomsSearchResult,
  CustomsStatusChoice,
  OverdueCustomsSummary,
  RegisterCustomsDeclarationRequest,
  UpdateCustomsStatusRequest,
} from './types'

/** 通関状態の選択肢。**画面が一覧を持たない**。 */
export function fetchCustomsStatuses() {
  return apiClient.get<CustomsStatusChoice[]>(`${API_PATHS.customs}/statuses`)
}

/** 申告の一覧・検索（US29-7）。 */
export function fetchCustomsDeclarations(criteria: CustomsSearchCriteria) {
  const params = new URLSearchParams()
  if (criteria.bookingId !== '') params.set('bookingId', criteria.bookingId)
  if (criteria.trackingNumber !== '') params.set('trackingNumber', criteria.trackingNumber)
  
  if (criteria.status !== '') params.set('status', criteria.status)
  if (criteria.unsettledOnly) params.set('unsettledOnly', 'true')
  const query = params.toString()
  return apiClient.get<CustomsSearchResult>(
    query === '' ? API_PATHS.customs : `${API_PATHS.customs}?${query}`,
  )
}

/** 申告の詳細（US29-8）。状態変更履歴を伴う。 */
export function fetchCustomsDeclaration(declarationId: number) {
  return apiClient.get<CustomsDeclarationDetail>(`${API_PATHS.customs}/${declarationId}`)
}

/** 申告の登録（US29-1）。 */
export function registerCustomsDeclaration(request: RegisterCustomsDeclarationRequest) {
  return apiClient.post<CustomsDeclarationDetail>(API_PATHS.customs, request)
}

/** 状態の更新（US29-2）。**理由が空なら、サーバが断る**。 */
export function updateCustomsStatus(declarationId: number, request: UpdateCustomsStatusRequest) {
  return apiClient.put<CustomsDeclarationDetail>(
    `${API_PATHS.customs}/${declarationId}/status`,
    request,
  )
}

/** 留置 3 日超の件数（US29-6）。 */
export function fetchOverdueCustoms() {
  return apiClient.get<OverdueCustomsSummary>(`${API_PATHS.customs}/overdue`)
}
