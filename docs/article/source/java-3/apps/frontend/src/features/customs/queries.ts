import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchCustomsDeclaration,
  fetchCustomsDeclarations,
  fetchCustomsStatuses,
  fetchOverdueCustoms,
  registerCustomsDeclaration,
  updateCustomsStatus,
} from './api'
import type {
  CustomsSearchCriteria,
  RegisterCustomsDeclarationRequest,
  UpdateCustomsStatusRequest,
} from './types'

export function useCustomsStatuses() {
  return useQuery({ queryKey: ['customs', 'statuses'], queryFn: fetchCustomsStatuses })
}

export function useCustomsDeclarations(criteria: CustomsSearchCriteria) {
  return useQuery({
    queryKey: ['customs', 'list', criteria],
    queryFn: () => fetchCustomsDeclarations(criteria),
  })
}

export function useCustomsDeclaration(declarationId: number | null) {
  return useQuery({
    queryKey: ['customs', 'detail', declarationId],
    queryFn: () => fetchCustomsDeclaration(declarationId as number),
    enabled: declarationId !== null,
  })
}

/** 留置 3 日超の件数（US29-6）。**件数から対象一覧へ辿れること**（横断規約）。 */
export function useOverdueCustoms() {
  return useQuery({ queryKey: ['customs', 'overdue'], queryFn: fetchOverdueCustoms })
}

export function useRegisterCustomsDeclaration() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: RegisterCustomsDeclarationRequest) =>
      registerCustomsDeclaration(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customs'] })
    },
  })
}

export function useUpdateCustomsStatus(declarationId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateCustomsStatusRequest) =>
      updateCustomsStatus(declarationId, request),
    onSuccess: () => {
      // 状態が変われば、一覧の警告表示も件数も変わる。まとめて引き直す
      void queryClient.invalidateQueries({ queryKey: ['customs'] })
    },
  })
}
