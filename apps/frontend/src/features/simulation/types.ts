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

/**
 * 継続実行のセッション（US37）。
 *
 * **種を持つ**——落ちた実行を再現するために、画面から読み取って指定する。
 */
export type SimulationSession = {
  sessionId: string
  /** 使った乱数の種。同じ種を指定すると、同じ並びが再現される。 */
  seed: number
  intervalSeconds: number
  maxConcurrent: number
  exceptionRatio: number
  status: 'RUNNING' | 'STOPPING' | 'STOPPED'
  /** 状態の見出し。**サーバが返す**——画面に対訳表を持たせない。 */
  statusLabel: string
  startedBy: string
  startedAt: string
  stoppedAt: string | null
}

/** 失敗した工程の件数。**どこで落ちているか**が分かって初めて直す場所が決まる。 */
export type SimulationStepFailure = {
  step: string
  label: string
  count: number
}

export type SimulationStatistics = {
  total: number
  succeeded: number
  failed: number
  running: number
  /**
   * 止まったきりの件数。
   *
   * **実行中と分ける。** 配備や再起動で途中終了した実行が「実行中」に残ると、
   * 管理者は止めてよいのかまだ待つのかを判断できない。
   */
  abandoned: number
  failuresByStep: SimulationStepFailure[]
}

/** 動いていなければ session は null。統計はいつでも読める。 */
export type SimulationActiveSession = {
  session: SimulationSession | null
  statistics: SimulationStatistics
}

/** 継続実行の開始で送る設定（US37-2）。種を省くとサーバが作って記録する。 */
export type StartSessionInput = {
  intervalSeconds: number
  maxConcurrent: number
  exceptionRatio: number
  seed?: number
}
