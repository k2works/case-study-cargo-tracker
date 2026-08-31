import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type { SimulationRun, SimulationScenario } from './types'

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
