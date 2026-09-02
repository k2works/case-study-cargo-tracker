import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type { CreateEstimateRequest, Estimate, EstimateQuote } from './estimate-types'

/** 見積の一覧（**新しい順**）。 */
export function fetchEstimates(): Promise<Estimate[]> {
  return apiClient.get<Estimate[]>(API_PATHS.estimates)
}

/** 見積 1 件。 */
export function fetchEstimate(estimateId: string): Promise<Estimate> {
  return apiClient.get<Estimate>(API_PATHS.estimate(estimateId))
}

/**
 * 候補を探す（受入基準 01-2・01-3・01-5）。
 *
 * **保存しない。** 営業担当者は候補を見てから作成を決める。
 */
export function quoteEstimate(request: CreateEstimateRequest): Promise<EstimateQuote> {
  return apiClient.post<EstimateQuote>(API_PATHS.estimateQuotes, request)
}

/** 見積を作る（受入基準 01-4）。**見積番号が発行される。** */
export function createEstimate(request: CreateEstimateRequest): Promise<Estimate> {
  return apiClient.post<Estimate>(API_PATHS.estimates, request)
}
