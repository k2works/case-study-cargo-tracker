import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import type { Role } from '../../types/role'
import { BookingDetailPage } from '../booking-detail-page'

const BOOKING = {
  id: 1,
  bookingId: 'BKG-2026000001',
  shipperId: 1,
  shipperName: '丸紅商事株式会社',
  bookingStatus: 'PRELIMINARY',
  transportStatus: 'NOT_RECEIVED',
  routingStatus: 'NOT_ROUTED',
  type: 'GENERAL',
  weightKg: 1200,
  quantity: 20,
  description: '電子部品',
  lengthCm: null,
  widthCm: null,
  heightCm: null,
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureDate: null,
  arrivalDeadline: '2026-09-20',
  hazardousClass: null,
  unNumber: null,
  properShippingName: null,
  minCelsius: null,
  maxCelsius: null,
}

function renderPage(roles: Role[] = ['ROLE_SALES']) {
  loginAs(roles)
  return renderWithProviders(<BookingDetailPage />, ['/booking/BKG-2026000001'], undefined, {
    path: '/booking/:bookingId',
  })
}

describe('予約の詳細（US06）', () => {
  beforeEach(() => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () => HttpResponse.json(BOOKING)),
    )
  })

  /** 中身が見えないまま引き渡すと、経路設計者は不備に気づけないまま経路を組む。 */
  it('出発地・目的地・期限・貨物仕様を確認できる', async () => {
    renderPage()

    expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
    expect(screen.getByText(/Tokyo（JPTYO）/)).toBeInTheDocument()
    expect(screen.getByText(/Los Angeles（USLAX）/)).toBeInTheDocument()
    expect(screen.getByText('2026-09-20')).toBeInTheDocument()
    expect(screen.getByText('1200 kg')).toBeInTheDocument()
    expect(screen.getByText('電子部品')).toBeInTheDocument()
    expect(screen.getByText('丸紅商事株式会社')).toBeInTheDocument()
  })

  /** 生の英字を出すと、利用者は自分の予約がどうなっているか読めない。 */
  it('状態は日本語で示す', async () => {
    renderPage()

    expect(await screen.findByText('仮受付')).toBeInTheDocument()
    expect(screen.getByText('未依頼')).toBeInTheDocument()
  })

  it('営業担当者は経路設計を依頼できる', async () => {
    server.use(
      http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, () =>
        HttpResponse.json({ ...BOOKING, routingStatus: 'ROUTING_REQUESTED' }),
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '経路設計を依頼する' }))

    expect(await screen.findByText(/経路設計を依頼しました/)).toBeInTheDocument()
  })

  /**
   * 経路設計者は依頼のボタンを持たない。
   *
   * 立てられると、引き渡しの記録が「誰が渡したか」を表さなくなる。
   */
  it('経路設計者には依頼のボタンを出さない', async () => {
    renderPage(['ROLE_ROUTING'])

    await screen.findByText(/BKG-2026000001/)
    expect(screen.queryByRole('button', { name: '経路設計を依頼する' })).not.toBeInTheDocument()
  })

  it('引き渡し済みの予約には依頼のボタンを出さない', async () => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json({ ...BOOKING, routingStatus: 'ROUTING_REQUESTED' }),
      ),
    )
    renderPage()

    expect(await screen.findByText(/すでに引き渡し済みです/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '経路設計を依頼する' })).not.toBeInTheDocument()
  })

  /** 409 は入力の誤りではない。「入力を直してください」と伝えると利用者は直す先を探す。 */
  it('依頼できない状態のときは、その理由をそのまま見せる', async () => {
    server.use(
      http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, () =>
        HttpResponse.json(
          { message: 'この予約はすでに経路設計を依頼しています' },
          { status: 409 },
        ),
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '経路設計を依頼する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'この予約はすでに経路設計を依頼しています',
    )
  })

  it('見つからない予約は、一覧へ戻れる形で伝える', async () => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 }),
      ),
    )
    renderPage()

    expect(await screen.findByText(/予約を表示できませんでした/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '貨物予約の一覧に戻る' })).toBeInTheDocument()
  })

  /**
   * 状態軸の到達性（IT4）。
   *
   * 引き渡された予約からだけ経路設計へ行ける。引き渡されていない予約に入口を出すと、
   * サーバが同じ判定で詳細を絞っているため、押した先で 403 になる。
   */
  describe('経路設計への入口', () => {
    it('引き渡された予約からは経路設計へ行ける', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json({ ...BOOKING, routingStatus: 'ROUTING_REQUESTED' }),
        ),
      )
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByRole('link', { name: '経路を割り当て' })).toHaveAttribute(
        'href',
        '/routing/design/BKG-2026000001',
      )
    })

    it('引き渡されていない予約には入口を出さない', async () => {
      renderPage(['ROLE_ROUTING'])

      await screen.findByText(/BKG-2026000001/)
      expect(screen.queryByRole('link', { name: '経路を割り当て' })).not.toBeInTheDocument()
      expect(screen.getByText(/まだ経路設計に引き渡されていません/)).toBeInTheDocument()
    })

    it('営業担当者には経路設計の入口を出さない（経路を組むのは経路設計者の仕事）', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json({ ...BOOKING, routingStatus: 'ROUTING_REQUESTED' }),
        ),
      )
      renderPage(['ROLE_SALES'])

      await screen.findByText(/BKG-2026000001/)
      expect(screen.queryByRole('link', { name: '経路を割り当て' })).not.toBeInTheDocument()
    })
  })
})