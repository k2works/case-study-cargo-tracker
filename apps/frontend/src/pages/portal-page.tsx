import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

/**
 * 公開トップ。認証不要で開ける唯一の入口。
 *
 * ロール別の到達性は認証済み利用者にしか働かないため、まだログインしていない人のための
 * 入口を認証の外に置く。
 */
export function PortalPage() {
  const navigate = useNavigate()
  const [trackingNumber, setTrackingNumber] = useState('')

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = trackingNumber.trim()
    if (trimmed === '') {
      return
    }
    navigate(`/tracking/${encodeURIComponent(trimmed)}`)
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <h1 className="text-2xl font-bold text-gray-900">CargoTracker</h1>
      <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

      <section className="mt-8 rounded border bg-white p-6">
        <h2 className="text-lg font-semibold text-gray-900">貨物を追跡する</h2>
        <form onSubmit={submit} className="mt-4 flex gap-2">
          <label htmlFor="trackingNumber" className="sr-only">
            追跡番号
          </label>
          <input
            id="trackingNumber"
            type="text"
            value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)}
            placeholder="TRK-20260819-1234"
            className="flex-1 rounded border border-gray-300 px-3 py-2"
          />
          <button type="submit" className="rounded bg-blue-600 px-4 py-2 text-white">
            追跡する
          </button>
        </form>
        <p className="mt-2 text-sm text-gray-500">
          追跡番号があれば<strong>ログインなしで</strong>確認できます。
        </p>
      </section>

      <p className="mt-6">
        <Link to="/login" className="text-blue-700 underline">
          業務利用の方はログイン
        </Link>
      </p>
    </main>
  )
}
