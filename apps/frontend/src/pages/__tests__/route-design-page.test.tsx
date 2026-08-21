import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { RouteDesignPage } from '../route-design-page'

const BOOKING = {
  id: 1,
  bookingId: 'BKG-2026000001',
  shipperId: 1,
  shipperName: '丸紅商事',
  bookingStatus: 'PRELIMINARY',
  transportStatus: 'NOT_RECEIVED',
  routingStatus: 'ROUTING_REQUESTED',
  type: 'GENERAL',
  weightKg: 12000,
  quantity: 20,
  description: '電子部品',
  lengthCm: null,
  widthCm: null,
  heightCm: null,
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureDate: '2026-09-01',
  arrivalDeadline: '2026-09-30',
  hazardousClass: null,
  unNumber: null,
  properShippingName: null,
  minCelsius: null,
  maxCelsius: null,
}

const DIRECT = {
  rank: 1,
  direct: true,
  voyageNumbers: ['V0100'],
  departureTime: '2026-09-01T00:00:00Z',
  arrivalTime: '2026-09-15T03:00:00Z',
  transitDays: 14,
  transshipmentCount: 0,
  transitPorts: [],
  estimatedCost: 720000,
  legs: [
    {
      voyageNumber: 'V0100',
      fromUnLocode: 'JPTYO',
      fromName: 'Tokyo',
      toUnLocode: 'USLAX',
      toName: 'Los Angeles',
      departureTime: '2026-09-01T00:00:00Z',
      arrivalTime: '2026-09-15T03:00:00Z',
    },
  ],
}

const VIA_SHANGHAI = {
  rank: 2,
  direct: false,
  voyageNumbers: ['V0201', 'V0202'],
  departureTime: '2026-09-02T01:00:00Z',
  arrivalTime: '2026-09-18T00:00:00Z',
  transitDays: 16,
  transshipmentCount: 1,
  transitPorts: [{ unLocode: 'CNSHA', name: 'Shanghai' }],
  estimatedCost: 1060000,
  legs: [],
}

const APPLIED = {
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  arrivalDeadline: '2026-09-30T14:59:59Z',
  cargoType: 'GENERAL',
  maxTransshipments: 2,
}

function givenCandidates(candidates: unknown[], applied = APPLIED) {
  server.use(
    http.get(API_PATHS.routes, () =>
      HttpResponse.json({
        candidates,
        totalCount: candidates.length,
        appliedCriteria: applied,
      }),
    ),
  )
}

function renderPage() {
  loginAs(['ROLE_ROUTING'])
  return renderWithProviders(<RouteDesignPage />, ['/routing/design/BKG-2026000001'], undefined, {
    path: '/routing/design/:bookingId',
  })
}

describe('経路設計（経路候補の一覧）', () => {
  beforeEach(() => {
    server.use(
      http.get(`${API_PATHS.bookings}/BKG-2026000001`, () => HttpResponse.json(BOOKING)),
    )
    givenCandidates([DIRECT, VIA_SHANGHAI])
  })

  it('予約の条件を引き継いだ状態で開く（空のフォームを出さない）', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('2026-09-30')).toBeInTheDocument()
    expect(screen.getByText(/Tokyo/)).toBeInTheDocument()
    expect(screen.getByText(/Los Angeles/)).toBeInTheDocument()
    expect(screen.getByText(/一般貨物/)).toBeInTheDocument()
  })

  it('候補をサーバが返した推奨順のまま並べる', async () => {
    renderPage()

    const rows = await screen.findAllByRole('row')
    const body = rows.slice(1)
    expect(within(body[0]).getByText('V0100')).toBeInTheDocument()
    expect(within(body[1]).getByText(/V0201/)).toBeInTheDocument()
  })

  it('直行便であることが分かる', async () => {
    renderPage()

    const rows = await screen.findAllByRole('row')
    expect(within(rows[1]).getByText('直行')).toBeInTheDocument()
  })

  it('港は名前で示し、UN/LOCODE は併記する', async () => {
    renderPage()

    expect(await screen.findByText(/Shanghai/)).toBeInTheDocument()
    expect(screen.getByText(/CNSHA/)).toBeInTheDocument()
  })

  it('日時は業務タイムゾーンで表示する', async () => {
    renderPage()

    // 2026-09-15T03:00Z = 日本時間 09-15 12:00
    expect(await screen.findByText(/2026-09-15 12:00/)).toBeInTheDocument()
  })

  it('費用は概算であることを画面に書く', async () => {
    renderPage()

    // 表の見出しだけでなく、注記として「概算です」と書いてあることを見る
    expect(await screen.findByText(/正式な料金は精算時に確定します/)).toBeInTheDocument()
    expect(screen.getAllByText(/概算/).length).toBeGreaterThan(0)
  })

  it('確定が次のイテレーションであることを書き、押せない選択ボタンを置かない', async () => {
    renderPage()

    await screen.findAllByRole('row')
    expect(screen.queryByRole('button', { name: /選択/ })).not.toBeInTheDocument()
    expect(screen.getByText(/次のイテレーション/)).toBeInTheDocument()
  })

  it('候補の航海から航海詳細へ行ける', async () => {
    renderPage()

    const link = await screen.findByRole('link', { name: 'V0100' })
    expect(link).toHaveAttribute('href', '/routing/voyages/V0100')
  })

  describe('候補が 1 件も無かったとき', () => {
    beforeEach(() => {
      givenCandidates([], { ...APPLIED, cargoType: 'HAZARDOUS' })
    })

    it('何で絞ったかを示す', async () => {
      renderPage()

      expect(await screen.findByText(/見つかりませんでした/)).toBeInTheDocument()
      expect(screen.getByText(/危険物/)).toBeInTheDocument()
      // 貨物種別が効いていることに気づけないと、期限だけを緩め続ける
      expect(screen.getByText(/運べる船が限られます/)).toBeInTheDocument()
    })

    it('条件を緩める操作を置く（該当なしで終わらせない）', async () => {
      renderPage()

      expect(await screen.findByRole('button', { name: /到着期限を 1 週間延ばす/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /積み替えを 3 回まで許す/ })).toBeInTheDocument()
    })

    it('積み替えを緩めると、その条件で算出し直す', async () => {
      const requested: string[] = []
      server.use(
        http.get(API_PATHS.routes, ({ request }) => {
          requested.push(new URL(request.url).searchParams.get('maxTransshipments') ?? '')
          return HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED })
        }),
      )
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: /積み替えを 3 回まで許す/ }))

      await waitFor(() => expect(requested).toContain('3'))
    })

    it('航海スケジュールへの逃げ道を置く（そもそも便が無い可能性がある）', async () => {
      renderPage()

      expect(await screen.findByRole('link', { name: /航海スケジュールを見る/ })).toHaveAttribute(
        'href',
        '/routing/voyages',
      )
    })
  })

  it('期限は日付のまま送る（日時に変換しない）', async () => {
    let sentDeadline: string | null = null
    server.use(
      http.get(API_PATHS.routes, ({ request }) => {
        sentDeadline = new URL(request.url).searchParams.get('deadline')
        return HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED })
      }),
    )
    renderPage()

    await waitFor(() => expect(sentDeadline).toBe('2026-09-30'))
  })
})
