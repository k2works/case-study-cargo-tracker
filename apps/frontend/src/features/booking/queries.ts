import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  bookCargo,
  fetchBooking,
  fetchHazardClasses,
  fetchLocations,
  requestRouting,
  searchBookings,
  searchShippers,
} from './api'
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
function bookingListKey(type: CargoType | '', keyword: string, routingStatus: string) {
  return ['bookings', type, keyword, routingStatus] as const
}

export function useBookings(type: CargoType | '', keyword: string, routingStatus = '') {
  return useQuery({
    queryKey: bookingListKey(type, keyword, routingStatus),
    queryFn: () => searchBookings(type, keyword, routingStatus),
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

/** 危険物クラスの選択肢。自由入力にすると同じ分類が複数の字面で混ざる。 */
export function useHazardClasses() {
  return useQuery({
    queryKey: ['booking-hazard-classes'],
    queryFn: fetchHazardClasses,
    // 法定の分類であり、画面を開くたびに取り直す理由がない
    staleTime: 5 * 60 * 1000,
  })
}

export function useBooking(bookingId: string) {
  return useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => fetchBooking(bookingId),
  })
}

/**
 * 経路設計を依頼する（US06）。
 *
 * 依頼したら詳細も一覧も取り直す。取り直さないと、押した直後の画面が
 * 「まだ依頼していない」ままになり、押せたかどうかが分からない。
 */
export function useRequestRouting(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation<Booking, Error, void>({
    mutationFn: () => requestRouting(bookingId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['booking', bookingId] })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export function useBookCargo() {
  const queryClient = useQueryClient()
  return useMutation<Booking, Error, BookingRequest>({
    mutationFn: (request) => bookCargo(request),
    // 登録したら一覧を取り直す。既定の staleTime に頼ると、一覧の取得条件を
    // 変えた瞬間に「登録したのに出てこない」が戻ってくる
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}
