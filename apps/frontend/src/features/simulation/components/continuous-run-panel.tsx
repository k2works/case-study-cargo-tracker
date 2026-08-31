import { useState } from 'react'
import {
  useActiveSimulationSession,
  useStartSimulationSession,
  useStopSimulationSession,
} from '../queries'
import type { SimulationSession, SimulationStatistics } from '../types'

/** 既定の設定。**画面が持つのは初期値だけ**で、上限の判定はサーバが行う。 */
const DEFAULTS = { intervalSeconds: 30, maxConcurrent: 3, exceptionRatio: 0.2 }

/**
 * 継続実行の開始・停止・状態と統計（US37）。
 *
 * 1 件を押す実行（US34）とは**手番が違う**ため分けている。押した人がその場で
 * 結果を見る操作と、しばらく回し続けて様子を見る操作は、見たいものが違う。
 */
export function ContinuousRunPanel() {
  const { data } = useActiveSimulationSession()
  const start = useStartSimulationSession()
  const stop = useStopSimulationSession()
  const [seed, setSeed] = useState('')

  const session = data?.session ?? null
  const statistics = data?.statistics
  // 停止処理中も「動いている」——進行中の実行が残っている
  const active = session !== null && session.status !== 'STOPPED'

  return (
    <section className="space-y-4 rounded border border-gray-200 p-4">
      <h2 className="text-lg font-bold text-gray-900">継続実行</h2>

      <p className="text-sm text-gray-700">
        {'バックエンドが乱数でシナリオと入力を選び、一定間隔で実行し続けます。'}
        <strong>使った種は必ず記録されます</strong>
        {'——同じ種を指定すると、同じ並びを再現できます。'}
      </p>

      {active && session ? (
        <RunningSession onStop={() => stop.mutate(session.sessionId)} session={session} />
      ) : (
        <StartForm
          onSeedChange={setSeed}
          onStart={() =>
            start.mutate({
              ...DEFAULTS,
              ...(seed.trim() === '' ? {} : { seed: Number(seed) }),
            })
          }
          pending={start.isPending}
          seed={seed}
        />
      )}

      {start.isError ? (
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          継続実行を開始できませんでした。{start.error.message}
        </p>
      ) : null}

      {statistics ? <Statistics statistics={statistics} /> : null}
    </section>
  )
}

function StartForm({
  onSeedChange,
  onStart,
  pending,
  seed,
}: Readonly<{
  onSeedChange: (value: string) => void
  onStart: () => void
  pending: boolean
  seed: string
}>) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className="text-sm text-gray-600">
        {DEFAULTS.intervalSeconds} 秒ごと・同時 {DEFAULTS.maxConcurrent} 本・例外{' '}
        {Math.round(DEFAULTS.exceptionRatio * 100)}%
      </span>
      <label className="text-sm text-gray-700" htmlFor="seed">
        種（省略可）
      </label>
      <input
        className="w-40 rounded border border-gray-300 px-2 py-1 font-mono"
        id="seed"
        inputMode="numeric"
        onChange={(event) => onSeedChange(event.target.value)}
        value={seed}
      />
      <button
        className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-400"
        disabled={pending}
        onClick={onStart}
        type="button"
      >
        {pending ? '開始しています…' : '継続実行を開始する'}
      </button>
    </div>
  )
}

/**
 * 動いているセッション。
 *
 * **種をそのまま出す。** 記録していても読めなければ、落ちた実行を再現できない。
 */
function RunningSession({
  onStop,
  session,
}: Readonly<{ onStop: () => void; session: SimulationSession }>) {
  return (
    <div className="space-y-3">
      <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <Field label="セッション" value={session.sessionId} />
        <Field label="状態" value={session.statusLabel} />
        <Field label="種" value={String(session.seed)} />
        <Field
          label="設定"
          value={`${session.intervalSeconds} 秒ごと・同時 ${session.maxConcurrent} 本・例外 ${Math.round(
            session.exceptionRatio * 100,
          )}%`}
        />
      </dl>
      <button
        className="rounded border border-gray-400 px-4 py-2 disabled:bg-gray-200"
        disabled={session.status === 'STOPPING'}
        onClick={onStop}
        type="button"
      >
        停止する
      </button>
      {session.status === 'STOPPING' ? (
        <p className="text-sm text-gray-700">
          {'新しい実行は始まりません。'}
          <strong>進行中の実行は最後まで走ります</strong>
          {'——途中で止めると、業務データが中途半端な状態で残るためです。'}
        </p>
      ) : null}
    </div>
  )
}

function Field({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div>
      <dt className="text-gray-500">{label}</dt>
      <dd className="font-mono text-gray-900">{value}</dd>
    </div>
  )
}

/**
 * 統計（US37-8）。
 *
 * **失敗した工程の分布まで出す。** 件数だけでは「たくさん落ちている」としか
 * 分からず、直す場所が決まらない。
 */
function Statistics({ statistics }: Readonly<{ statistics: SimulationStatistics }>) {
  return (
    <div className="space-y-3">
      <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <Field label="実行件数" value={String(statistics.total)} />
        <Field label="成功" value={String(statistics.succeeded)} />
        <Field label="失敗" value={String(statistics.failed)} />
        <Field label="実行中" value={String(statistics.running)} />
      </dl>

      {statistics.failuresByStep.length > 0 ? (
        <table aria-label="失敗した工程" className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-300 text-left">
              <th className="py-1">工程</th>
              <th className="py-1">失敗</th>
            </tr>
          </thead>
          <tbody>
            {statistics.failuresByStep.map((failure) => (
              <tr className="border-b border-gray-100" key={failure.step}>
                <td className="py-1">{failure.label}</td>
                <td className="py-1">{failure.count} 件</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="text-sm text-gray-600">失敗した工程はありません。</p>
      )}
    </div>
  )
}
