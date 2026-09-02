import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchHandlingHistory,
  fetchHandlingLocations,
  fetchHandlingTypes,
  registerHandlingActivity,
} from './api'
import type { HandlingActivityRequest } from './types'

export function useHandlingTypes() {
  return useQuery({ queryKey: ['handling', 'types'], queryFn: fetchHandlingTypes })
}

export function useHandlingLocations() {
  return useQuery({ queryKey: ['handling', 'locations'], queryFn: fetchHandlingLocations })
}

/**
 * 1 つの貨物の荷役履歴。
 *
 * 追跡番号が決まるまでは引かない。**記録できたかどうかとは切り離す**——追跡管理者は
 * 記録できないので、記録の成否に紐づけると履歴を一度も見られない。
 */
export function useHandlingHistory(trackingNumber: string | null) {
  return useQuery({
    queryKey: ['handling', 'history', trackingNumber],
    queryFn: () => fetchHandlingHistory(trackingNumber as string),
    enabled: trackingNumber !== null && trackingNumber !== '',
  })
}

/**
 * 荷役作業を記録する。
 *
 * 記録したら履歴を取り直す。**同じ貨物に続けて記録する**のが荷役の実際の使い方であり、
 * 直前の作業が履歴に出ないと、作業員は登録できたのか分からない。
 */
export function useRegisterHandlingActivity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: HandlingActivityRequest) => registerHandlingActivity(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['handling', 'history'] })
    },
  })
}
