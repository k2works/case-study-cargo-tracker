import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { BookingListPage } from '../booking-list-page'

/**
 * 貨物予約の一覧（US04）。
 *
 * 一覧は「登録した直後に入ったか確かめる」使い方をされる。新しい順であること、
 * 上限で切ったことを黙っていないことが、一覧そのものの信用に効く。
 */

function booking(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    bookingId: 'BKG-2026000001',
    shipperId: 1,
    bookingStatus: 'PRELIMINARY',
    transportStatus: 'NOT_RECEIVED',
    routingStatus: 'NOT_ROUTED',
    type: 'GENERAL',
    weightKg: 1000,
    quantity: null,
    description: null,
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    originUnLocode: 'JPTYO',
    originName: 'Tokyo',
    destinationUnLocode: 'USLAX',
    destinationName: 'Los Angeles',
    departureDate: null,
    arrivalDeadline: '2027-09-20',
    hazardousClass: null,
    unNumber: null,
    properShippingName: null,
    minCelsius: null,
    maxCelsius: null,
    ...overrides,
  }
}

function respondWith(bookings: unknown[], extra: Record<string, unknown> = {}) {
  server.use(
    http.get(API_PATHS.bookings, () =>
      HttpResponse.json({
        bookings,
        totalCount: bookings.length,
        limit: 100,
        truncated: false,
        ...extra,
      }),
    ),
  )
}

function renderPage(entry = '/booking') {
  loginAs(['ROLE_SALES'])
  return renderWithProviders(<BookingListPage />, [entry])
}

describe('貨物予約の一覧', () => {
  it('予約番号・状態・経路・期限を並べる', async () => {
    respondWith([booking()])
    renderPage()

    expect(await screen.findByRole('cell', { name: 'BKG-2026000001' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '仮受付' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Tokyo' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Los Angeles' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2027-09-20' })).toBeInTheDocument()
  })

  it('危険物・冷凍は一覧で見分けられる', async () => {
    // 取り違えると事故になるため、種別が一目で分かることが要る
    respondWith([
      booking({ id: 1, type: 'HAZARDOUS' }),
      booking({ id: 2, bookingId: 'BKG-2026000002', type: 'REFRIGERATED' }),
    ])
    renderPage()

    expect(await screen.findByRole('cell', { name: '危険物' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '冷凍・冷蔵貨物' })).toBeInTheDocument()
  })

  it('登録直後は採番された予約番号を知らせる', async () => {
    respondWith([booking()])
    renderPage('/booking?registered=BKG-2026000001')

    expect(await screen.findByText(/を発行しました/)).toHaveTextContent('BKG-2026000001')
  })

  it('上限で切ったことを黙っていない', async () => {
    // 黙って切ると「全件見た」と受け取られる
    respondWith([booking()], { totalCount: 250, truncated: true })
    renderPage()

    expect(await screen.findByText(/新しい 100 件のみ表示/)).toBeInTheDocument()
    expect(screen.getByText(/250 件/)).toBeInTheDocument()
  })

  it('条件に合う予約が無ければ、次にできることを示す', async () => {
    respondWith([])
    renderPage()

    expect(await screen.findByText(/条件に合う予約がありません/)).toBeInTheDocument()
  })

  it('種別で絞り込むと条件をサーバへ渡す', async () => {
    let requested: string | null = null
    server.use(
      http.get(API_PATHS.bookings, ({ request }) => {
        requested = new URL(request.url).searchParams.get('type')
        return HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false })
      }),
    )
    renderPage()

    await userEvent.selectOptions(await screen.findByLabelText('貨物種別'), 'HAZARDOUS')

    await waitFor(() => expect(requested).toBe('HAZARDOUS'))
  })

  it('予約番号や荷主名でも絞り込める', async () => {
    let requested: string | null = null
    server.use(
      http.get(API_PATHS.bookings, ({ request }) => {
        requested = new URL(request.url).searchParams.get('keyword')
        return HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false })
      }),
    )
    renderPage()

    await userEvent.type(await screen.findByLabelText('予約番号または荷主の名前'), '丸紅')
    await userEvent.click(screen.getByRole('button', { name: '検索' }))

    await waitFor(() => expect(requested).toBe('丸紅'))
  })

  it('新規登録への導線がある', async () => {
    respondWith([])
    renderPage()

    expect(await screen.findByRole('link', { name: '新規登録' })).toHaveAttribute(
      'href',
      '/booking/new',
    )
  })
})
