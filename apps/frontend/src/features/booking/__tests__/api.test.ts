import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../../config/api'
import { server } from '../../../test/msw/server'
import { useAuthStore } from '../../../stores/auth-store'
import { bookCargo, fetchLocations, searchBookings } from '../api'
import type { Booking, BookingRequest } from '../types'

/**
 * 予約 API の呼び出し。
 *
 * 絞り込み条件の組み立ては、画面から見ると「検索しても絞られない」としてしか現れず、
 * 原因がサーバか画面かの区別がつかない。ここで URL の形を直接確かめる。
 */
describe('予約 API', () => {
  beforeEach(() => {
    useAuthStore.getState().login({
      token: 'test-token',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })
  })

  describe('一覧の取得', () => {
    it('条件が無ければクエリを付けない', async () => {
      let requested = ''
      server.use(
        http.get(API_PATHS.bookings, ({ request }) => {
          requested = new URL(request.url).search
          return HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false })
        }),
      )

      await searchBookings('', '')

      expect(requested).toBe('')
    })

    it('種別とキーワードをクエリに載せる', async () => {
      let requested = new URLSearchParams()
      server.use(
        http.get(API_PATHS.bookings, ({ request }) => {
          requested = new URL(request.url).searchParams
          return HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false })
        }),
      )

      await searchBookings('HAZARDOUS', '  BKG-2026000001  ')

      expect(requested.get('type')).toBe('HAZARDOUS')
      // 前後の空白を残すと、貼り付けた予約番号で 1 件も見つからない
      expect(requested.get('keyword')).toBe('BKG-2026000001')
    })

    it('空白だけのキーワードは条件にしない', async () => {
      let requested = new URLSearchParams()
      server.use(
        http.get(API_PATHS.bookings, ({ request }) => {
          requested = new URL(request.url).searchParams
          return HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false })
        }),
      )

      await searchBookings('', '   ')

      expect(requested.has('keyword')).toBe(false)
    })

    it('上限で切られたことを含めて返す', async () => {
      server.use(
        http.get(API_PATHS.bookings, () =>
          HttpResponse.json({ bookings: [], totalCount: 250, limit: 100, truncated: true }),
        ),
      )

      const result = await searchBookings('', '')

      // 黙って切ると「全件見た」と受け取られる
      expect(result.truncated).toBe(true)
      expect(result.totalCount).toBe(250)
    })
  })

  describe('地点の選択肢', () => {
    it('地点の一覧を返す', async () => {
      server.use(
        http.get(API_PATHS.bookingLocations, () =>
          HttpResponse.json([{ unLocode: 'JPTYO', name: 'Tokyo' }]),
        ),
      )

      await expect(fetchLocations()).resolves.toEqual([{ unLocode: 'JPTYO', name: 'Tokyo' }])
    })
  })

  describe('予約の登録', () => {
    const request: BookingRequest = {
      shipperId: 1,
      type: 'GENERAL',
      weightKg: 100,
      quantity: null,
      description: null,
      lengthCm: null,
      widthCm: null,
      heightCm: null,
      originUnLocode: 'JPTYO',
      destinationUnLocode: 'USLAX',
      departureDate: null,
      arrivalDeadline: '2027-09-20',
      hazardousClass: null,
      unNumber: null,
      properShippingName: null,
      minCelsius: null,
      maxCelsius: null,
    }

    it('採番された予約を返す', async () => {
      server.use(
        http.post(API_PATHS.bookings, () =>
          HttpResponse.json({ bookingId: 'BKG-2026000001' } as Booking, { status: 201 }),
        ),
      )

      await expect(bookCargo(request)).resolves.toMatchObject({ bookingId: 'BKG-2026000001' })
    })

    it('理由を添えた拒否は例外として伝える（呼び出し側が理由を見せられるように）', async () => {
      server.use(
        http.post(API_PATHS.bookings, () =>
          HttpResponse.json({ message: '指定された荷主が見つかりません: 999' }, { status: 400 }),
        ),
      )

      await expect(bookCargo(request)).rejects.toMatchObject({ status: 400 })
    })
  })
})
