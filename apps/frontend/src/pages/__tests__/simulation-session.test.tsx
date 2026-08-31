import { screen, waitFor, within } from '@testing-library/react'
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

const SESSION = {
  sessionId: 'SES-20261207-0001',
  seed: 20261207,
  intervalSeconds: 30,
  maxConcurrent: 3,
  exceptionRatio: 0.2,
  status: 'RUNNING',
  statusLabel: '実行中',
  startedBy: 'admin01',
  startedAt: '2026-12-07T01:00:00Z',
  stoppedAt: null,
}

const STATISTICS = {
  total: 12,
  succeeded: 9,
  failed: 2,
  running: 1,
  failuresByStep: [
    { step: 'ASSIGN_ROUTE', label: '経路割り当て', count: 2 },
    { step: 'DECLARE_CUSTOMS', label: '通関申告', count: 1 },
  ],
}

function renderPage(active: unknown) {
  loginAs(['ROLE_ADMIN'])
  server.use(
    http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
    http.get(API_PATHS.simulations, () => HttpResponse.json([])),
    http.get(API_PATHS.simulationActiveSession, () => HttpResponse.json(active)),
  )
  return renderWithProviders(<SimulationsPage />, ['/admin/simulations'])
}

describe('継続実行（US37）', () => {
  it('動いていないときは、開始できる状態で出る', async () => {
    renderPage({ session: null, statistics: { ...STATISTICS, total: 0 } })

    expect(
      await screen.findByRole('button', { name: '継続実行を開始する' }),
    ).toBeEnabled()
  })

  it('動いているときは、種と設定と状態が読める', async () => {
    renderPage({ session: SESSION, statistics: STATISTICS })

    // **種を出す**——落ちた実行を再現するために読み取る
    expect(await screen.findByText('20261207')).toBeInTheDocument()
    expect(screen.getByText('SES-20261207-0001')).toBeInTheDocument()
    // 「実行中」は統計の見出しにも出るため、状態の欄を名指しで見る
    const status = screen.getByText('状態').parentElement
    expect(status).not.toBeNull()
    expect(within(status as HTMLElement).getByText('実行中')).toBeInTheDocument()
  })

  it('統計に、失敗した工程の分布が出る', async () => {
    renderPage({ session: SESSION, statistics: STATISTICS })

    const distribution = await screen.findByRole('table', { name: '失敗した工程' })
    // 件数だけでは直す場所が決まらない。どの工程で落ちているかを出す
    expect(within(distribution).getByText('経路割り当て')).toBeInTheDocument()
    expect(within(distribution).getByText('通関申告')).toBeInTheDocument()
  })

  it('開始すると、設定と種をそのまま送る', async () => {
    let sent: Record<string, unknown> | null = null
    renderPage({ session: null, statistics: { ...STATISTICS, total: 0 } })
    server.use(
      http.post(API_PATHS.simulationSessions, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(SESSION, { status: 201 })
      }),
    )

    await userEvent.click(
      await screen.findByRole('button', { name: '継続実行を開始する' }),
    )

    // 状態の取り直しは「動いていない」を返し続けるため、送った内容そのものを待つ
    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent).toMatchObject({ intervalSeconds: 30, maxConcurrent: 3 })
  })

  /** **止めたと止まったは違う**（ADR-031 決定 4）。 */
  it('停止すると、停止処理中が出る', async () => {
    renderPage({ session: SESSION, statistics: STATISTICS })
    server.use(
      http.delete(API_PATHS.simulationSession('SES-20261207-0001'), () =>
        HttpResponse.json({ ...SESSION, status: 'STOPPING', statusLabel: '停止処理中' }),
      ),
      http.get(API_PATHS.simulationActiveSession, () =>
        HttpResponse.json({
          session: { ...SESSION, status: 'STOPPING', statusLabel: '停止処理中' },
          statistics: STATISTICS,
        }),
      ),
    )

    await userEvent.click(await screen.findByRole('button', { name: '停止する' }))

    expect(await screen.findByText('停止処理中')).toBeInTheDocument()
  })

  /** 種を指定して同じ並びを流し直せる（US37-3）。 */
  it('種を指定して開始できる', async () => {
    let sent: Record<string, unknown> | null = null
    renderPage({ session: null, statistics: { ...STATISTICS, total: 0 } })
    server.use(
      http.post(API_PATHS.simulationSessions, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(SESSION, { status: 201 })
      }),
    )

    await userEvent.type(await screen.findByLabelText('種（省略可）'), '20261207')
    await userEvent.click(screen.getByRole('button', { name: '継続実行を開始する' }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent).toMatchObject({ seed: 20261207 })
  })
})
