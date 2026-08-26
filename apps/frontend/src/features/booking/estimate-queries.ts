import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createEstimate,
  fetchEstimate,
  fetchEstimates,
  quoteEstimate,
} from './estimate-api'
import type { CreateEstimateRequest } from './estimate-types'

/** 見積の一覧。 */
export function useEstimates() {
  return useQuery({
    queryKey: ['estimates'],
    queryFn: fetchEstimates,
  })
}

/** 見積 1 件。 */
export function useEstimate(estimateId: string) {
  return useQuery({
    queryKey: ['estimate', estimateId],
    queryFn: () => fetchEstimate(estimateId),
    enabled: estimateId !== '',
  })
}

/**
 * 候補を探す（受入基準 01-2）。
 *
 * **保存しないので、一覧を取り直す必要はない。**
 */
export function useQuoteEstimate() {
  return useMutation({
    mutationFn: (request: CreateEstimateRequest) => quoteEstimate(request),
  })
}

/** 見積を作る（受入基準 01-4）。作ると一覧に現れる。 */
export function useCreateEstimate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateEstimateRequest) => createEstimate(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['estimates'] })
    },
  })
}
