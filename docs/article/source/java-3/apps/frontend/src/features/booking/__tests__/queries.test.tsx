import { screen, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../../config/api'
import { server } from '../../../test/msw/server'
import { createTestQueryClient, loginAs, renderWithProviders } from '../../../test/render'
import { useBookCargo, useBookings, useLocations } from '../queries'
import type { BookingRequest } from '../types'

/**
 * 取得のフック（ADR-013 で `features/` に置くと決めた層）。
 *
 * 画面から間接的にしか確かめないと、キャッシュキーの取り違えが
 * 「別の条件の結果が出る」という形で画面側の不具合に見える。
 */

function Bookings({ type, keyword }: { type: '' | 'HAZARDOUS'; keyword: string }) {
  const { data, isPending } = useBookings(type, keyword)
  if (isPending) {
    return <p>読み込み中</p>
  }
  return <p>件数: {data?.totalCount}</p>
}

function Locations() {
  const { data = [] } = useLocations()
  return <ul>{data.map((l) => <li key={l.unLocode}>{l.name}</li>)}</ul>
}

function BookButton({ request }: { request: BookingRequest }) {
  const { mutate, data } = useBookCargo()
  return (
    <div>
      <button type="button" onClick={() => mutate(request)}>
        登録
      </button>
      {data !== undefined && <p>{data.bookingId}</p>}
    </div>
  )
}

describe('予約の取得フック', () => {
  beforeEach(() => {
    loginAs(['ROLE_SALES'])
  })

  it('条件ごとに別のキャッシュを引く', async () => {
    const counts: Record<string, number> = { '': 3, HAZARDOUS: 1 }
    server.use(
      http.get(API_PATHS.bookings, ({ request }) => {
        const type = new URL(request.url).searchParams.get('type') ?? ''
        return HttpResponse.json({
          bookings: [],
          totalCount: counts[type],
          limit: 100,
          truncated: false,
        })
      }),
    )

    // 同じ QueryClient を使う。毎回作り直すと、キーを取り違えた実装でも
    // キャッシュが空なので取り直され、テストが常に通ってしまう
    const client = createTestQueryClient()

    const { unmount } = renderWithProviders(<Bookings type="" keyword="" />, ['/'], client)
    await waitFor(() => expect(screen.getByText('件数: 3')).toBeInTheDocument())
    unmount()

    // キーが同じだと、絞り込んだのに前の結果が一瞬出る。取り直しを待ってから
    // 確かめると、キーを取り違えた実装でも最後には正しい件数になるため判別しない。
    // 「取りに行くところから始まる」ことを、描画直後に確かめる
    renderWithProviders(<Bookings type="HAZARDOUS" keyword="" />, ['/'], client)
    expect(screen.getByText('読み込み中')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('件数: 1')).toBeInTheDocument())
  })

  it('地点の選択肢を取る', async () => {
    server.use(
      http.get(API_PATHS.bookingLocations, () =>
        HttpResponse.json([{ unLocode: 'JPTYO', name: 'Tokyo' }]),
      ),
    )

    renderWithProviders(<Locations />)

    await waitFor(() => expect(screen.getByText('Tokyo')).toBeInTheDocument())
  })

  const request = {
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
  } as BookingRequest

  it('登録すると採番された予約を返す', async () => {
    server.use(
      http.post(API_PATHS.bookings, () =>
        HttpResponse.json({ bookingId: 'BKG-2026000042' }, { status: 201 }),
      ),
    )

    renderWithProviders(<BookButton request={request} />)
    screen.getByRole('button', { name: '登録' }).click()

    await waitFor(() => expect(screen.getByText('BKG-2026000042')).toBeInTheDocument())
  })

  it('登録したら一覧を取り直す', async () => {
    let listed = 0
    server.use(
      http.get(API_PATHS.bookings, () => {
        listed += 1
        return HttpResponse.json({
          bookings: [],
          totalCount: listed,
          limit: 100,
          truncated: false,
        })
      }),
      http.post(API_PATHS.bookings, () =>
        HttpResponse.json({ bookingId: 'BKG-2026000043' }, { status: 201 }),
      ),
    )

    // 一覧と登録が同じキャッシュを共有する状態。登録しても一覧を無効化しないと、
    // 一覧に戻った利用者には「登録したのに出てこない」と見える
    const client = createTestQueryClient()
    renderWithProviders(
      <div>
        <Bookings type="" keyword="" />
        <BookButton request={{ ...request, weightKg: 200 } as BookingRequest} />
      </div>,
      ['/'],
      client,
    )
    await waitFor(() => expect(screen.getByText('件数: 1')).toBeInTheDocument())

    screen.getByRole('button', { name: '登録' }).click()

    await waitFor(() => expect(screen.getByText('件数: 2')).toBeInTheDocument())
  })
})
