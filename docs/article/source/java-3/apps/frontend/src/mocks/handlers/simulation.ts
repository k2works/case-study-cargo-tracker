import type { SimulationSession } from '../../features/simulation/types'

/** 動いている継続実行。開始・停止で書き換わる。 */
let continuousSession: SimulationSession | null = null

/**
 * 業務シミュレーションのモック（US34・US35）。
 *
 * 本物と同じく、**工程の見出しと役割はサーバが返す**。画面に対訳表を持たせると、
 * 工程を足したときに画面が列挙の名前をそのまま出す。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { simulationRuns, simulationScenarios, simulationSessions } from '../data'

export const simulationHandlers = [
  http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(simulationScenarios)),

  /**
   * 実行の一覧（TD-03）。**日付での絞り込みも本物と同じ規則で行う**
   * ——モックが甘いと、絞れていない実装のまま緑になる。
   */
  http.get(API_PATHS.simulations, ({ request }) => {
    const date = new URL(request.url).searchParams.get('date')
    if (date === null || date === '') {
      return HttpResponse.json(simulationRuns)
    }
    return HttpResponse.json(
      simulationRuns.filter((run) => run.startedAt.startsWith(date)),
    )
  }),

  /** 過去のセッション（TD-03）。**停止したものも残る**。 */
  http.get(API_PATHS.simulationSessions, () => HttpResponse.json(simulationSessions)),

  http.get('/api/v1/simulations/:runId', ({ params }) => {
    const run = simulationRuns.find((candidate) => candidate.runId === params.runId)
    return run
      ? HttpResponse.json(run)
      : HttpResponse.json({ message: 'その実行は見つかりません' }, { status: 404 })
  }),

  /**
   * 実行の指示（US34）。
   *
   * **同じシナリオが実行中なら 409 で断り、実行中の ID を返す**（US34-5）。
   * 断るだけにすると、指示した人はいま何が動いているかを確かめられない。
   */
  http.post(API_PATHS.simulations, async ({ request }) => {
    const { scenarioId } = (await request.json()) as { scenarioId: string }
    // **本物より甘くしない。**本物は知らないシナリオを 400 で断る。
    // モックが受け付けると、画面が「断られたときの見え方」を一度も踏まない
    if (!simulationScenarios.some((scenario) => scenario.id === scenarioId)) {
      return HttpResponse.json(
        { message: `そのシナリオは実行できません: ${scenarioId}` },
        { status: 400 },
      )
    }
    const running = simulationRuns.find(
      (run) => run.scenarioId === scenarioId && run.status === 'RUNNING',
    )
    if (running) {
      return HttpResponse.json(
        { message: '同じシナリオが実行中です', runningRunId: running.runId },
        { status: 409 },
      )
    }
    const created = {
      ...simulationRuns[0],
      runId: `SIM-20261116-${String(simulationRuns.length + 1).padStart(4, '0')}`,
      startedAt: new Date().toISOString(),
    }
    simulationRuns.unshift(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  /**
   * 継続実行の状態と統計（US37）。
   *
   * **動いている状態も置く。** 動いていない状態だけを返すと、画面が
   * 「開始できる」形しか一度も踏まないまま緑になる。
   */
  http.get(API_PATHS.simulationActiveSession, () =>
    HttpResponse.json({
      session: continuousSession,
      statistics: {
        total: simulationRuns.length,
        succeeded: simulationRuns.filter((run) => run.status === 'COMPLETED').length,
        failed: simulationRuns.filter((run) => run.status === 'FAILED').length,
        running: simulationRuns.filter((run) => run.status === 'RUNNING').length,
        abandoned: 0,
        failuresByStep: [
          { step: 'ASSIGN_ROUTE', label: '経路割り当て', count: 1 },
        ],
      },
    }),
  ),

  http.post(API_PATHS.simulationSessions, async ({ request }) => {
    const body = (await request.json()) as { seed?: number }
    continuousSession = {
      sessionId: 'SES-20261207-0001',
      seed: body.seed ?? 20261207,
      intervalSeconds: 30,
      maxConcurrent: 3,
      exceptionRatio: 0.2,
      status: 'RUNNING',
      statusLabel: '実行中',
      startedBy: 'admin01',
      startedAt: new Date().toISOString(),
      stoppedAt: null,
    }
    return HttpResponse.json(continuousSession, { status: 201 })
  }),

  // **パスの組み立て関数を使わない。** encodeURIComponent が ':' を '%3A' に変えるため、
  // MSW がパス変数として解釈しない——ハンドラが一度も当たらないまま緑になる
  http.delete(`${API_PATHS.simulationSessions}/:sessionId`, () => {
    if (continuousSession === null) {
      return HttpResponse.json({ message: 'そのセッションはありません' }, { status: 404 })
    }
    // **止めたと止まったは違う**（ADR-031 決定 4）。進行中があるうちは停止処理中
    continuousSession = {
      ...continuousSession,
      status: 'STOPPING',
      statusLabel: '停止処理中',
    }
    return HttpResponse.json(continuousSession)
  }),
]
