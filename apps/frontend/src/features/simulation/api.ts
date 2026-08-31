import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  SimulationActiveSession,
  SimulationRun,
  SimulationScenario,
  SimulationSession,
  StartSessionInput,
} from './types'

export function fetchScenarios(): Promise<SimulationScenario[]> {
  return apiClient.get<SimulationScenario[]>(API_PATHS.simulationScenarios)
}

export function fetchSimulationRuns(): Promise<SimulationRun[]> {
  return apiClient.get<SimulationRun[]>(API_PATHS.simulations)
}

export function fetchSimulationRun(runId: string): Promise<SimulationRun> {
  return apiClient.get<SimulationRun>(API_PATHS.simulationRun(runId))
}

/**
 * シナリオを実行する（US34）。
 *
 * 誰が始めたかは**サーバが利用者ヘッダから取る**。画面から送らない——送ると、
 * 画面を書き換えて別人の名前で記録できてしまう。
 */
export function startSimulation(scenarioId: string): Promise<SimulationRun> {
  return apiClient.post<SimulationRun>(API_PATHS.simulations, { scenarioId })
}

/**
 * 継続実行を開始する（US37-4）。
 *
 * 種を省くとサーバが作って**必ず記録する**——記録しないと、指定しなかった実行だけが
 * 再現できない。実運用では指定しない方が普通である。
 */
export function startSimulationSession(
  input: StartSessionInput,
): Promise<SimulationSession> {
  return apiClient.post<SimulationSession>(API_PATHS.simulationSessions, input)
}

/**
 * 継続実行を停止する（US37-4）。
 *
 * **止まるのは新規の開始だけ**である。進行中の実行は最後まで走るため、
 * 応答は「停止処理中」を返しうる。
 */
export function stopSimulationSession(sessionId: string): Promise<SimulationSession> {
  return apiClient.delete<SimulationSession>(API_PATHS.simulationSession(sessionId))
}

/** 動いている継続実行と統計（US37-8）。動いていなくても統計は読める。 */
export function fetchActiveSession(): Promise<SimulationActiveSession> {
  return apiClient.get<SimulationActiveSession>(API_PATHS.simulationActiveSession)
}
