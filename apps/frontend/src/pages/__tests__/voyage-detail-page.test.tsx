import { screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { VoyageDetailPage } from '../voyage-detail-page'

const VOYAGE = {
  voyageNumber: 'V0100',
  vesselName: 'さくら丸',
  carrierName: '日本郵船',
  supportedCargoTypes: ['GENERAL', 'REFRIGERATED'],
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureTime: '2026-09-01T00:00:00Z',
  arrivalTime: '2026-09-18T03:00:00Z',
  movements: [
    {
      departureUnLocode: 'JPTYO',
      departureName: 'Tokyo',
      arrivalUnLocode: 'CNSHA',
      arrivalName: 'Shanghai',
      departureTime: '2026-09-01T00:00:00Z',
      arrivalTime: '2026-09-03T00:00:00Z',
    },
    {
      departureUnLocode: 'CNSHA',
      departureName: 'Shanghai',
      arrivalUnLocode: 'USLAX',
      arrivalName: 'Los Angeles',
      departureTime: '2026-09-04T00:00:00Z',
      arrivalTime: '2026-09-18T03:00:00Z',
    },
  ],
}

function renderPage(search = '') {
  loginAs(['ROLE_ROUTING'])
  return renderWithProviders(
    <VoyageDetailPage />,
    [`/routing/voyages/V0100${search}`],
    undefined,
    { path: '/routing/voyages/:voyageNumber' },
  )
}

describe('航海スケジュールの詳細', () => {
  beforeEach(() => {
    server.use(http.get(API_PATHS.voyageDetail('V0100'), () => HttpResponse.json(VOYAGE)))
  })

  it('途中の寄港地を区間ごとに見せる（一覧では分からない）', async () => {
    renderPage()

    expect((await screen.findAllByText(/Shanghai/)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/CNSHA/).length).toBeGreaterThan(0)
  })

  it('区間ごとの時刻を業務タイムゾーンで見せる', async () => {
    renderPage()

    // 2026-09-18T03:00Z = 日本時間 09-18 12:00
    expect(await screen.findByText('2026-09-18 12:00')).toBeInTheDocument()
  })

  it('運べる貨物種別を日本語で見せる', async () => {
    renderPage()

    expect(await screen.findByText(/一般貨物・冷凍・冷蔵/)).toBeInTheDocument()
  })

  it('一覧に戻れる', async () => {
    renderPage()

    expect(await screen.findByRole('link', { name: /一覧に戻る/ })).toHaveAttribute(
      'href',
      '/routing/voyages',
    )
  })

  it('見つからない航海はその旨を伝える', async () => {
    server.use(
      http.get(API_PATHS.voyageDetail('V0100'), () => new HttpResponse(null, { status: 404 })),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('見つかりません')
  })

  describe('経路設計から来たとき（残作業 4）', () => {
    it('条件ごと経路設計に戻れる', async () => {
      const from = '/routing/design/BKG-2026000001?deadline=2026-10-15'
      renderPage(`?from=${encodeURIComponent(from)}`)

      // 戻り先が一覧だけだと、どの予約を見ていたか分からない場所に出る
      expect(await screen.findByRole('link', { name: '経路設計に戻る' })).toHaveAttribute(
        'href',
        from,
      )
    })

    it('経路設計から来ていなければ、その導線は出さない', async () => {
      renderPage()

      await screen.findByRole('link', { name: '航海スケジュール一覧に戻る' })
      expect(screen.queryByRole('link', { name: '経路設計に戻る' })).not.toBeInTheDocument()
    })

    it('外部へ出る戻り先は使わない', async () => {
      renderPage(`?from=${encodeURIComponent('https://evil.example.com')}`)

      await screen.findByRole('link', { name: '航海スケジュール一覧に戻る' })
      expect(screen.queryByRole('link', { name: '経路設計に戻る' })).not.toBeInTheDocument()
    })
  })
})
