import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { SimulationsPage } from '../simulations-page'

const SCENARIO = {
  id: 'standard-transport',
  steps: [{ step: 'REGISTER_SHIPPER', label: '荷主登録', role: 'ROLE_SALES' }],
}

const RUN = {
  runId: 'SIM-20261116-0001',
  scenarioId: 'standard-transport',
  status: 'COMPLETED',
  startedBy: 'admin01',
  startedAt: '2026-11-16T01:00:00Z',
  finishedAt: '2026-11-16T01:00:12Z',
  failureReason: null,
  steps: [],
}

function renderPage() {
  loginAs(['ROLE_ADMIN'])
  return renderWithProviders(<SimulationsPage />, ['/admin/simulations'])
}

describe('業務シミュレーションの実行（US34）', () => {
  it('過去の実行を、状態と実行者つきで並べる', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
      http.get(API_PATHS.simulations, () => HttpResponse.json([RUN])),
    )
    renderPage()

    expect(await screen.findByText('SIM-20261116-0001')).toBeInTheDocument()
    expect(screen.getByText('完了')).toBeInTheDocument()
    expect(screen.getByText('admin01')).toBeInTheDocument()
  })

  it('一度も実行していないときは、その旨を出す', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
      http.get(API_PATHS.simulations, () => HttpResponse.json([])),
    )
    renderPage()

    expect(await screen.findByText(/まだ実行していません/)).toBeInTheDocument()
  })

  /**
   * **断るだけで終わらせない**（US34-5）。
   *
   * 実行中の ID へ行けなければ、指示した人はいま何が動いているかを確かめられない。
   */
  it('同じシナリオが実行中なら、実行中の実行へ行ける', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
      http.get(API_PATHS.simulations, () => HttpResponse.json([])),
      http.post(API_PATHS.simulations, () =>
        HttpResponse.json(
          { message: '同じシナリオが実行中です', runningRunId: 'SIM-20261116-0009' },
          { status: 409 },
        ),
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /実行する/ }))

    const link = await screen.findByRole('link', { name: /SIM-20261116-0009/ })
    expect(link).toHaveAttribute('href', '/admin/simulations/SIM-20261116-0009')
  })

  it('実行すると、その実行が一覧に現れる', async () => {
    let started = false
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
      http.get(API_PATHS.simulations, () => HttpResponse.json(started ? [RUN] : [])),
      http.post(API_PATHS.simulations, () => {
        started = true
        return HttpResponse.json(RUN, { status: 201 })
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /実行する/ }))

    // 取り直さないと、いま流した実行が出ず、指示した人はもう一度押す
    expect(await screen.findByText('SIM-20261116-0001')).toBeInTheDocument()
  })
})
