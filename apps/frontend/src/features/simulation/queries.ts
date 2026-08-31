import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchScenarios,
  fetchSimulationRun,
  fetchSimulationRuns,
  startSimulation,
} from './api'
import type { SimulationRun } from './types'

export function useSimulationScenarios() {
  return useQuery({ queryKey: ['simulation-scenarios'], queryFn: fetchScenarios })
}

export function useSimulationRuns() {
  return useQuery({ queryKey: ['simulation-runs'], queryFn: fetchSimulationRuns })
}

export function useSimulationRun(runId: string | undefined) {
  return useQuery({
    queryKey: ['simulation-run', runId],
    queryFn: () => fetchSimulationRun(runId as string),
    enabled: Boolean(runId),
  })
}

/**
 * 実行したら一覧を取り直す。
 *
 * 取り直さないと、いま流した実行が一覧に出ず、指示した人はもう一度押す。
 */
export function useStartSimulation() {
  const queryClient = useQueryClient()
  return useMutation<SimulationRun, Error, string>({
    mutationFn: (scenarioId) => startSimulation(scenarioId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['simulation-runs'] })
    },
  })
}
