/**
 * 業務シミュレーションの実行（US34・US35）。
 *
 * **サーバが返すものだけを持つ。** 工程の見出しも役割も、画面で対訳表を持たない——
 * 工程を足したときに、画面が列挙の名前をそのまま出す。
 */
export type SimulationStep = {
  step: string
  /** 工程の名前（「予約登録」）。サーバが返す。 */
  label: string
  /** その工程を踏むロール。誰の操作として通ったかを示す。 */
  role: string
  outcome: 'SUCCEEDED' | 'FAILED'
  elapsedMs: number
  /** その工程が生んだ識別子（予約番号・追跡番号・精算書番号）。無ければ null。 */
  createdIdentifier: string | null
  /**
   * 何番号か（「予約番号」「追跡番号」）。**サーバが返す**——画面に対訳表を持たせると、
   * 工程を足したときに画面だけが古いままになる。識別子が無ければ null。
   */
  identifierKind: string | null
  /** 失敗の理由。成功なら null。「失敗しました」だけにしない。 */
  failureReason: string | null
  recordedAt: string | null
}

export type SimulationRun = {
  runId: string
  scenarioId: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  startedBy: string
  startedAt: string
  finishedAt: string | null
  failureReason: string | null
  steps: SimulationStep[]
}

export type SimulationScenario = {
  id: string
  steps: { step: string; label: string; role: string }[]
}

/** 二重実行を断られたとき。**実行中の ID を受け取る**——そこへ行けなければ次に進めない。 */
export type SimulationAlreadyRunning = {
  message: string
  runningRunId: string
}
