import { useMutation, useQuery } from '@tanstack/react-query'
import { bookCargo, fetchLocations, searchBookings, searchShippers } from './api'
import type { Booking, BookingRequest, CargoType } from './types'

/**
 * booking コンテキストのデータ取得。
 *
 * ADR-013 により、取得のフックはここに置き、`pages/` は呼ぶだけにする。
 * 画面ごとに useQuery を書くと、キャッシュキーが画面の数だけ散らばり、
 * 登録後に一覧が更新されない種類の不具合が画面単位で再発する。
 */

/** 一覧の取得に使うキャッシュキー。登録後の再取得もこれを使う。 */
function shipperListKey(keyword: string) {
  return ['shippers', keyword] as const
}

export function useShippers(keyword: string) {
  return useQuery({
    queryKey: shipperListKey(keyword),
    queryFn: () => searchShippers(keyword),
  })
}


/** 一覧の取得に使うキャッシュキー。登録後の再取得もこれを使う。 */
function bookingListKey(type: CargoType | '', keyword: string) {
  return ['bookings', type, keyword] as const
}

export function useBookings(type: CargoType | '', keyword: string) {
  return useQuery({
    queryKey: bookingListKey(type, keyword),
    queryFn: () => searchBookings(type, keyword),
  })
}

/** 地点の選択肢。UN/LOCODE を画面に直接入力させないために取る。 */
export function useLocations() {
  return useQuery({
    queryKey: ['booking-locations'],
    queryFn: fetchLocations,
    // 地点マスタはめったに変わらない。画面を開くたびに取り直す理由がない
    staleTime: 5 * 60 * 1000,
  })
}

export function useBookCargo() {
  return useMutation<Booking, Error, BookingRequest>({
    mutationFn: (request) => bookCargo(request),
  })
}
