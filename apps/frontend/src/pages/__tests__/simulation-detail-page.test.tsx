import { screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { SimulationDetailPage } from '../simulation-detail-page'

const RUN = {
  runId: 'SIM-20261116-0002',
  scenarioId: 'standard-transport',
  status: 'FAILED',
  startedBy: 'admin01',
  startedAt: '2026-11-16T01:00:00Z',
  finishedAt: '2026-11-16T01:00:05Z',
  failureReason: '経路割り当て が失敗しました（503）',
  steps: [
    {
      step: 'ISSUE_TRACKING_NUMBER',
      label: '追跡番号発行',
      role: 'ROLE_ROUTING',
      outcome: 'SUCCEEDED',
      elapsedMs: 88,
      createdIdentifier: 'TRK-20261116-0001',
      failureReason: null,
      recordedAt: '2026-11-16T01:00:03Z',
    },
    {
      step: 'ASSIGN_ROUTE',
      label: '経路割り当て',
      role: 'ROLE_ROUTING',
      outcome: 'FAILED',
      elapsedMs: 42,
      createdIdentifier: null,
      failureReason: '経路候補が 0 件です（JPTYO → USLAX）。航海の登録を確かめる',
      recordedAt: '2026-11-16T01:00:05Z',
    },
  ],
}

function renderPage() {
  loginAs(['ROLE_ADMIN'])
  server.use(http.get('/api/v1/simulations/:runId', () => HttpResponse.json(RUN)))
  return renderWithProviders(
    <SimulationDetailPage />,
    ['/admin/simulations/SIM-20261116-0002'],
    undefined,
    { path: '/admin/simulations/:runId' },
  )
}

describe('業務シミュレーションの結果（US35）', () => {
  it('工程ごとに、踏んだロール・成否・所要時間を出す', async () => {
    renderPage()

    expect(await screen.findByText('追跡番号発行')).toBeInTheDocument()
    expect(screen.getAllByText('ROLE_ROUTING')).toHaveLength(2)
    expect(screen.getByText('88 ms')).toBeInTheDocument()
  })

  /** 「失敗しました」だけでは、候補が 0 件なのか接続先が違うのかを切り分けられない。 */
  it('止まった工程の理由をそのまま出す', async () => {
    renderPage()

    expect(await screen.findByText(/経路候補が 0 件です/)).toBeInTheDocument()
    expect(screen.getByText(/航海の登録を確かめる/)).toBeInTheDocument()
  })

  /**
   * **押した先で 403 にならない**（Phase 5.3）。
   *
   * 予約詳細は営業・経路設計者、精算書は経理にしか開かれていない。
   * システム管理者が押せる先は公開の追跡照会だけである。
   */
  it('追跡番号だけを追跡照会へ繋ぎ、他の識別子は文字のまま出す', async () => {
    renderPage()

    const link = await screen.findByRole('link', { name: 'TRK-20261116-0001' })
    expect(link).toHaveAttribute('href', '/tracking/TRK-20261116-0001')
  })
})
