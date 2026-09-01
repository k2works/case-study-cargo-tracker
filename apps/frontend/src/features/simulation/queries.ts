import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchActiveSession,
  fetchScenarios,
  fetchSimulationRun,
  fetchSimulationRuns,
  fetchSimulationSessions,
  startSimulation,
  startSimulationSession,
  stopSimulationSession,
} from './api'
import type { SimulationRun, SimulationSession, StartSessionInput } from './types'

export function useSimulationScenarios() {
  return useQuery({ queryKey: ['simulation-scenarios'], queryFn: fetchScenarios })
}

/**
 * 実行の一覧（TD-03）。
 *
 * **日付をキーに含める。**含めないと、日を変えても前の結果が返り続ける。
 */
export function useSimulationRuns(date = '') {
  return useQuery({
    queryKey: ['simulation-runs', date],
    queryFn: () => fetchSimulationRuns(date),
  })
}

/**
 * 過去のセッション（TD-03）。
 *
 * **停止した瞬間に種が画面から消えると、翌朝には再現の手立てが無い。**
 */
export function useSimulationSessions() {
  return useQuery({
    queryKey: ['simulation-sessions'],
    queryFn: fetchSimulationSessions,
  })
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

/**
 * 継続実行の状態と統計（US37-8）。
 *
 * **動いているあいだは繰り返し読む。** 一度きりだと、件数が増えていることも
 * 止まったことも画面に出ない——「動いているのに何も起きていない」ように見える。
 */
export function useActiveSimulationSession() {
  return useQuery({
    queryKey: ['simulation-session', 'active'],
    queryFn: fetchActiveSession,
    refetchInterval: (query) =>
      query.state.data?.session?.status === 'STOPPED' ? false : 5000,
  })
}

/** 継続実行を開始したら、状態と実行の一覧を取り直す。 */
export function useStartSimulationSession() {
  const queryClient = useQueryClient()
  return useMutation<SimulationSession, Error, StartSessionInput>({
    mutationFn: (input) => startSimulationSession(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['simulation-session'] })
      void queryClient.invalidateQueries({ queryKey: ['simulation-runs'] })
    },
  })
}

/** 停止したら状態を取り直す。**「停止処理中」を出すため**——止めたと止まったは違う。 */
export function useStopSimulationSession() {
  const queryClient = useQueryClient()
  return useMutation<SimulationSession, Error, string>({
    mutationFn: (sessionId) => stopSimulationSession(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['simulation-session'] })
    },
  })
}
