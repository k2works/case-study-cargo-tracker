import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchLockedAccounts, unlockAccount } from './api'
import type { LockedAccount } from './types'

export function useLockedAccounts() {
  return useQuery({
    queryKey: ['locked-accounts'],
    queryFn: fetchLockedAccounts,
  })
}

/**
 * 解除したら一覧を取り直す。
 *
 * 取り直さないと、解除した行が残ったままになり、管理者は同じ行をもう一度押す。
 */
export function useUnlockAccount() {
  const queryClient = useQueryClient()
  return useMutation<LockedAccount, Error, string>({
    mutationFn: (username) => unlockAccount(username),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locked-accounts'] })
    },
  })
}
