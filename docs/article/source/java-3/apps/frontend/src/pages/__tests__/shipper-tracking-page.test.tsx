import { screen, within } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import App from '../../App'
import { API_PATHS } from '../../config/api'
import {
  raiseExceptionForTest,
  resetTrackings,
  trackingHandlers,
} from '../../mocks/handlers/tracking'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'

describe('荷主向け貨物追跡（US33）', () => {
  beforeEach(() => {
    resetTrackings()
    server.use(...trackingHandlers)
  })

  it('荷主は自社貨物だけの一覧で、状態・現在地・到着予定を見られる', async () => {
    loginAs(['ROLE_SHIPPER'])
    renderWithProviders(<App />, ['/shipper/tracking'])

    expect(await screen.findByRole('heading', { name: '自分の貨物' })).toBeInTheDocument()

    const row = await screen.findByRole('row', { name: /TRK-20260823-0001/ })
    expect(within(row).getByText('受領待ち')).toBeInTheDocument()
    expect(within(row).getByText('Tokyo')).toBeInTheDocument()
    expect(within(row).getByText('2027-09-15')).toBeInTheDocument()
    expect(screen.queryByText('TRK-20260823-9001')).not.toBeInTheDocument()
  })

  it('例外が起きている自社貨物が分かる', async () => {
    raiseExceptionForTest(
      'TRK-20260823-0001',
      'DELAY',
      '台風により遅延',
      '2027-09-05T00:00:00.000Z',
    )
    loginAs(['ROLE_SHIPPER'])
    renderWithProviders(<App />, ['/shipper/tracking'])

    const row = await screen.findByRole('row', { name: /TRK-20260823-0001/ })
    expect(within(row).getByText('例外あり')).toBeInTheDocument()
  })

  it('自社貨物の詳細を開ける', async () => {
    loginAs(['ROLE_SHIPPER'])
    renderWithProviders(<App />, ['/shipper/tracking/TRK-20260823-0001'])

    expect(await screen.findByRole('heading', { name: 'TRK-20260823-0001' })).toBeInTheDocument()
    expect(screen.getByText('受領待ち')).toBeInTheDocument()
    expect(screen.getByText('Tokyo')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'これまでの経過' })).toBeInTheDocument()
  })

  it('他社貨物の追跡番号を指定しても、荷主向け詳細では見えない', async () => {
    loginAs(['ROLE_SHIPPER'])
    renderWithProviders(<App />, ['/shipper/tracking/TRK-20260823-9001'])

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '自社の貨物として確認できません',
    )
    expect(screen.queryByRole('heading', { name: 'TRK-20260823-9001' })).not.toBeInTheDocument()
  })

  it('紐付いていない利用者には問い合わせ先を出し、空一覧だけで終わらせない', async () => {
    server.use(
      http.get(API_PATHS.shipperTracking, () =>
        HttpResponse.json({
          linked: false,
          contactMessage: '営業担当またはシステム管理者へ紐付けを依頼してください。',
          cargos: [],
        }),
      ),
    )
    loginAs(['ROLE_SHIPPER'])
    renderWithProviders(<App />, ['/shipper/tracking'])

    expect(await screen.findByText(/紐付けがありません/)).toBeInTheDocument()
    expect(screen.getByText(/営業担当またはシステム管理者/)).toBeInTheDocument()
    expect(screen.queryByText('自社貨物はありません。')).not.toBeInTheDocument()
  })

  it('荷主以外は荷主専用追跡画面を開けない', async () => {
    loginAs(['ROLE_SALES'])
    renderWithProviders(<App />, ['/shipper/tracking'])

    expect(
      await screen.findByRole('heading', { name: 'この操作を行う権限がありません' }),
    ).toBeInTheDocument()
  })
})
