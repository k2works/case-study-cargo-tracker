/**
 * 業務シミュレーションのモック（US34・US35）。
 *
 * 本物と同じく、**工程の見出しと役割はサーバが返す**。画面に対訳表を持たせると、
 * 工程を足したときに画面が列挙の名前をそのまま出す。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { simulationRuns, simulationScenarios } from '../data'

export const simulationHandlers = [
  http.get(API_PATHS.simulationScenarios, () => HttpResponse.json(simulationScenarios)),

  http.get(API_PATHS.simulations, () => HttpResponse.json(simulationRuns)),

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
]
