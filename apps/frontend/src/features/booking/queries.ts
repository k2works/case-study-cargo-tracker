import { useMutation, useQuery } from '@tanstack/react-query'
import { registerShipper, searchShippers } from './api'
import type { RegistrationOutcome } from './api'
import type { ShipperRequest } from './types'

/**
 * booking コンテキストのデータ取得。
 *
 * ADR-013 により、取得のフックはここに置き、`pages/` は呼ぶだけにする。
 * 画面ごとに useQuery を書くと、キャッシュキーが画面の数だけ散らばり、
 * 登録後に一覧が更新されない種類の不具合が画面単位で再発する。
 */

/** 一覧の取得に使うキャッシュキー。登録後の再取得もこれを使う。 */
export function shipperListKey(keyword: string) {
  return ['shippers', keyword] as const
}

export function useShippers(keyword: string) {
  return useQuery({
    queryKey: shipperListKey(keyword),
    queryFn: () => searchShippers(keyword),
  })
}

export function useRegisterShipper() {
  return useMutation<RegistrationOutcome, Error, ShipperRequest>({
    mutationFn: (request) => registerShipper(request),
  })
}
