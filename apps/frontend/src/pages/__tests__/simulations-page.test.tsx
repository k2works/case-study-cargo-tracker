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

describe('シナリオを選ぶ（US36）', () => {
  const SCENARIOS = [
    SCENARIO,
    {
      id: 'misroute',
      steps: [
        { step: 'REGISTER_SHIPPER', label: '荷主登録', role: 'ROLE_SALES' },
        {
          step: 'RECORD_MISROUTED_HANDLING',
          label: '予定外の港での荷役記録',
          role: 'ROLE_HANDLER',
        },
      ],
    },
  ]

  it('例外シナリオを選べる', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(SCENARIOS)),
      http.get(API_PATHS.simulations, () => HttpResponse.json([])),
    )
    renderPage()

    // 選択肢が届くのを待つ。ラベルは最初から出ているので、そこで待つと早すぎる
    expect(await screen.findByRole('option', { name: /誤配/ })).toBeInTheDocument()
    expect(screen.getByLabelText('シナリオ')).toBeInTheDocument()
  })

  it('選んだシナリオで実行を指示する', async () => {
    let requested: string | null = null
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(SCENARIOS)),
      http.get(API_PATHS.simulations, () => HttpResponse.json([])),
      http.post(API_PATHS.simulations, async ({ request }) => {
        requested = ((await request.json()) as { scenarioId: string }).scenarioId
        return HttpResponse.json({ ...RUN, scenarioId: requested }, { status: 201 })
      }),
    )
    renderPage()

    await screen.findByRole('option', { name: /誤配/ })
    await userEvent.selectOptions(screen.getByLabelText('シナリオ'), 'misroute')
    await userEvent.click(screen.getByRole('button', { name: /実行する/ }))

    await screen.findByText(/実行しています…|SIM-/)
    expect(requested).toBe('misroute')
  })

  /** 選んだシナリオの工程数を出す。**シナリオごとに違う**——標準輸送だけの数を出さない。 */
  it('選んだシナリオの工程数を出す', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(SCENARIOS)),
      http.get(API_PATHS.simulations, () => HttpResponse.json([])),
    )
    renderPage()

    expect(await screen.findByText('1 工程')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('シナリオ'), 'misroute')

    expect(await screen.findByText('2 工程')).toBeInTheDocument()
  })
})

describe('進んだ工程の分母（US36-4）', () => {
  const SCENARIOS = [
    { id: 'standard-transport', steps: Array.from({ length: 14 }, (_, i) => ({
      step: `S${i}`, label: `工程${i}`, role: 'ROLE_SALES' })) },
    { id: 'misroute', steps: Array.from({ length: 18 }, (_, i) => ({
      step: `M${i}`, label: `工程${i}`, role: 'ROLE_SALES' })) },
  ]

  /**
   * **分母は行ごとの実行のシナリオから取る。**
   *
   * 選択中のシナリオを全行に使うと、正常に完了した誤配が「18 / 14 工程」と出る
   * ——毎朝ありもしない障害を追うことになる。
   */
  it('完了した実行は、そのシナリオの工程数を分母にする', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(SCENARIOS)),
      http.get(API_PATHS.simulations, () =>
        HttpResponse.json([
          {
            ...RUN,
            runId: 'SIM-20261207-0001',
            scenarioId: 'misroute',
            status: 'COMPLETED',
            steps: Array.from({ length: 18 }, () => ({
              step: 'X', label: '工程', role: 'ROLE_SALES', outcome: 'SUCCEEDED',
              elapsedMs: 1, createdIdentifier: null, identifierKind: null,
              failureReason: null, recordedAt: null,
            })),
          },
        ]),
      ),
    )
    renderPage()

    // 標準輸送（14 工程）を選んだ状態でも、誤配の行は 18 / 18 と出る
    expect(await screen.findByText('18 / 18 工程')).toBeInTheDocument()
  })
})

describe('失敗した工程での絞り込み', () => {
  const FAILED_AT_ASSIGN = {
    ...RUN,
    runId: 'SIM-20261207-0002',
    status: 'FAILED',
    steps: [
      {
        step: 'ASSIGN_ROUTE', label: '経路割り当て', role: 'ROLE_ROUTING',
        outcome: 'FAILED', elapsedMs: 1, createdIdentifier: null,
        identifierKind: null, failureReason: '候補が 0 件', recordedAt: null,
      },
    ],
  }

  /**
   * **押した先が絞られていなければ、繋いだ意味が無い。**
   * 統計から来た管理者は、その工程で落ちた実行だけを見たい。
   */
  it('failedStep を指定すると、その工程で落ちた実行だけが出る', async () => {
    server.use(
      http.get(API_PATHS.simulationScenarios, () => HttpResponse.json([SCENARIO])),
      http.get(API_PATHS.simulations, () => HttpResponse.json([RUN, FAILED_AT_ASSIGN])),
    )
    loginAs(['ROLE_ADMIN'])
    renderWithProviders(<SimulationsPage />, ['/admin/simulations?failedStep=ASSIGN_ROUTE'])

    expect(await screen.findByText('SIM-20261207-0002')).toBeInTheDocument()
    expect(screen.queryByText('SIM-20261116-0001')).not.toBeInTheDocument()
    // **絞っていることを画面に出す**——出さないと「1 件しかない」と読まれる
    expect(screen.getByText(/で止まった実行だけを表示/)).toBeInTheDocument()
  })
})
