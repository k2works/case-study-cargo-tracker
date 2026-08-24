import { useState } from 'react'
import type { ExceptionTypeChoice, ManagedTracking } from '../types'

type Props = Readonly<{
  tracking: ManagedTracking
  exceptionTypes: ExceptionTypeChoice[]
  pending: boolean
  onBegin: () => void
  onRaise: (input: { exceptionType: string; description: string }) => void
  onResolve: (input: { resolutionNotes: string; newEstimatedArrival: string | null }) => void
}>

/**
 * 例外の起票と解決（US19・US20）。
 *
 * **起票と解決は同時に出さない。** 未解決の例外があるあいだは解決だけ、無いあいだは
 * 起票だけを出す。両方出すと、多重起票（サーバが 409 で断る）を押せる形になる
 * ——押せるのに断られる操作を出さない。
 */
export function ExceptionSection({
  tracking,
  exceptionTypes,
  pending,
  onBegin,
  onRaise,
  onResolve,
}: Props) {
  const [raising, setRaising] = useState(false)
  const [resolving, setResolving] = useState(false)
  const [exceptionType, setExceptionType] = useState('')
  const [description, setDescription] = useState('')
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [newEstimatedArrival, setNewEstimatedArrival] = useState('')

  /**
   * 遅延の解決には新しい到着予定日が要る（サーバの `TrackingActivity#resolveException` と
   * 同じ規則。IT9 返済枠 0.6）。
   *
   * **判定はサーバが返した種別で行う。** 画面が別の条件を持つと、サーバが断る入力を
   * 画面が通す（またはその逆）ようになる。
   */
  const requiresNewEstimate = tracking.activeException?.exceptionType === 'DELAY'


  function submitRaise(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    onRaise({ exceptionType, description })
    setRaising(false)
    setDescription('')
  }

  function submitResolve(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    onResolve({
      resolutionNotes,
      newEstimatedArrival: newEstimatedArrival === '' ? null : newEstimatedArrival,
    })
    setResolving(false)
    setResolutionNotes('')
    setNewEstimatedArrival('')
  }

  if (tracking.activeException === null) {
    return (
      <section className="space-y-4 rounded border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900">例外</h2>
        {raising ? (
          <form onSubmit={submitRaise} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label htmlFor="exceptionType" className="block text-sm font-medium text-gray-700">
                  例外の種別
                </label>
                {/* **選択肢はサーバが返す**（[ADR-024] 決定 11）。誤配・税関保留は自動で
                    起票されるため、ここには出ない——一覧に行だけ出て押せない形を作らない */}
                <select
                  id="exceptionType"
                  value={exceptionType}
                  onChange={(event) => setExceptionType(event.target.value)}
                  className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
                >
                  <option value="">選んでください</option>
                  {exceptionTypes.map((choice) => (
                    <option key={choice.exceptionType} value={choice.exceptionType}>
                      {choice.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="description" className="block text-sm font-medium text-gray-700">
                  発生状況
                </label>
                <input
                  id="description"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
                />
              </div>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={pending}
                className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
              >
                起票する
              </button>
              <button
                type="button"
                onClick={() => setRaising(false)}
                className="rounded border border-gray-300 px-4 py-2"
              >
                やめる
              </button>
            </div>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => {
              onBegin()
              setRaising(true)
            }}
            className="rounded border border-gray-300 px-4 py-2"
          >
            例外を起票する
          </button>
        )}
      </section>
    )
  }

  return (
    <section className="space-y-4 rounded border border-gray-200 p-4">
      <h2 className="text-lg font-semibold text-gray-900">例外</h2>
      {resolving ? (
        <form onSubmit={submitResolve} className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label htmlFor="resolutionNotes" className="block text-sm font-medium text-gray-700">
                対応内容
              </label>
              <input
                id="resolutionNotes"
                value={resolutionNotes}
                onChange={(event) => setResolutionNotes(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
            <div>
              <label
                htmlFor="newEstimatedArrival"
                className="block text-sm font-medium text-gray-700"
              >
                新しい到着予定日
                {requiresNewEstimate && <span className="text-red-600">（必須）</span>}
              </label>
              <input
                id="newEstimatedArrival"
                type="date"
                required={requiresNewEstimate}
                value={newEstimatedArrival}
                onChange={(event) => setNewEstimatedArrival(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
              {requiresNewEstimate && (
                <p className="mt-1 text-sm text-gray-600">
                  遅延の解決には、いつ着くのかが要ります。入れずに閉じると、遅れる前の
                  古い予定日が残り続けます。
                </p>
              )}
            </div>
          </div>
          <p className="text-sm text-gray-600">
            解決すると、<strong>例外が起きる前の状態に戻ります</strong>。
          </p>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={pending}
              className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
            >
              解決を記録する
            </button>
            <button
              type="button"
              onClick={() => setResolving(false)}
              className="rounded border border-gray-300 px-4 py-2"
            >
              やめる
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => {
            onBegin()
            setResolving(true)
          }}
          className="rounded border border-gray-300 px-4 py-2"
        >
          解決する
        </button>
      )}
    </section>
  )
}
