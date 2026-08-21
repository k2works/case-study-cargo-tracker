import { API_PATHS } from '../../config/api'
import { ApiError, apiClient } from '../../lib/api-client'
import { businessDateEndInstant, businessDateStartInstant } from '../../lib/business-time'
import type {
  LocationOption,
  RouteCandidateList,
  RouteSearchCriteria,
  Voyage,
  VoyageDifference,
  VoyageList,
  VoyageRegistrationOutcome,
  VoyageRequest,
  VoyageSearchCriteria,
} from './types'

export function searchVoyages(criteria: VoyageSearchCriteria): Promise<VoyageList> {
  const params = new URLSearchParams()
  if (criteria.origin !== '') params.set('origin', criteria.origin)
  if (criteria.destination !== '') params.set('destination', criteria.destination)
  // 期間は日付で入力し、日時で送る。日付のまま送るとサーバが解釈できない
  if (criteria.departureFrom !== '') {
    params.set('departureFrom', businessDateStartInstant(criteria.departureFrom))
  }
  if (criteria.departureTo !== '') {
    params.set('departureTo', businessDateEndInstant(criteria.departureTo))
  }
  if (criteria.cargoType !== '') params.set('cargoType', criteria.cargoType)

  const query = params.toString()
  return apiClient.get<VoyageList>(
    query === '' ? API_PATHS.voyages : `${API_PATHS.voyages}?${query}`,
  )
}

/** 航海 1 件を取り出す（US25）。更新の画面が既存の内容を初期値にするために要る。 */
export function fetchVoyage(voyageNumber: string): Promise<Voyage> {
  return apiClient.get<Voyage>(`${API_PATHS.voyages}/${encodeURIComponent(voyageNumber)}`)
}

export function fetchVoyageLocations(): Promise<LocationOption[]> {
  return apiClient.get<LocationOption[]>(API_PATHS.voyageLocations)
}

/**
 * 航海を登録する。
 *
 * 409（同じ航海番号が既にある）は失敗ではなく、差分を見せて上書きを選ばせるための応答である。
 * 例外として投げると、呼び出し側が「登録できなかった」としか扱えない。
 */
export async function registerVoyage(request: VoyageRequest): Promise<VoyageRegistrationOutcome> {
  try {
    const voyage = await apiClient.post<Voyage>(API_PATHS.voyages, request)
    return { kind: 'registered', voyage }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && error.body !== undefined) {
      return { kind: 'exists', difference: error.body as VoyageDifference }
    }
    throw error
  }
}

/** 差分を確認したうえで上書きする。 */
export function updateVoyage(request: VoyageRequest): Promise<Voyage> {
  return apiClient.put<Voyage>(
    `${API_PATHS.voyages}/${encodeURIComponent(request.voyageNumber)}`,
    request,
  )
}

/**
 * 経路候補を算出する（US08・ADR-017）。
 *
 * 期限は**日付のまま送る**。日時に変換しない。業務上「9 月 30 日まで」は「30 日中に
 * 着けばよい」を意味し、その解釈はサーバが業務タイムゾーンで行う。画面側で変換すると
 * 規則が 2 か所に散り、時差の分だけ当日が消える事故が起きる（IT3 の教訓）。
 */
export function findRouteCandidates(criteria: RouteSearchCriteria): Promise<RouteCandidateList> {
  const params = new URLSearchParams({
    origin: criteria.origin,
    destination: criteria.destination,
    deadline: criteria.deadline,
    cargoType: criteria.cargoType,
    maxTransshipments: String(criteria.maxTransshipments),
  })
  return apiClient.get<RouteCandidateList>(`${API_PATHS.routes}?${params.toString()}`)
}
