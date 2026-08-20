import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchVoyageLocations, registerVoyage, searchVoyages, updateVoyage } from './api'
import type {
  Voyage,
  VoyageRegistrationOutcome,
  VoyageRequest,
  VoyageSearchCriteria,
} from './types'

function voyageListKey(criteria: VoyageSearchCriteria) {
  return ['voyages', criteria] as const
}

export function useVoyages(criteria: VoyageSearchCriteria) {
  return useQuery({
    queryKey: voyageListKey(criteria),
    queryFn: () => searchVoyages(criteria),
  })
}

/** 地点の選択肢。UN/LOCODE を画面に直接入力させないために取る。 */
export function useVoyageLocations() {
  return useQuery({
    queryKey: ['voyage-locations'],
    queryFn: fetchVoyageLocations,
    // 地点マスタはめったに変わらない
    staleTime: 5 * 60 * 1000,
  })
}

export function useRegisterVoyage() {
  const queryClient = useQueryClient()
  return useMutation<VoyageRegistrationOutcome, Error, VoyageRequest>({
    mutationFn: (request) => registerVoyage(request),
    onSuccess: (outcome) => {
      // 差分の確認で止まっている間は一覧を取り直さない（まだ何も変わっていない）
      if (outcome.kind === 'registered') {
        void queryClient.invalidateQueries({ queryKey: ['voyages'] })
      }
    },
  })
}

export function useUpdateVoyage() {
  const queryClient = useQueryClient()
  return useMutation<Voyage, Error, VoyageRequest>({
    mutationFn: (request) => updateVoyage(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['voyages'] })
    },
  })
}
