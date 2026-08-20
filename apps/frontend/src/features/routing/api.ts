import { API_PATHS } from '../../config/api'
import { ApiError, apiClient } from '../../lib/api-client'
import type {
  LocationOption,
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
  if (criteria.departureFrom !== '') params.set('departureFrom', criteria.departureFrom)
  if (criteria.departureTo !== '') params.set('departureTo', criteria.departureTo)
  if (criteria.cargoType !== '') params.set('cargoType', criteria.cargoType)

  const query = params.toString()
  return apiClient.get<VoyageList>(
    query === '' ? API_PATHS.voyages : `${API_PATHS.voyages}?${query}`,
  )
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
