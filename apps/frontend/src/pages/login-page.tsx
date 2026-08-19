import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login } from '../features/auth/api'
import { useAuthStore } from '../stores/auth-store'

/**
 * ログイン失敗時に画面へ出す唯一の文言。
 *
 * 認証情報誤り・アカウントロック中・無効化アカウントを区別して表示すると、
 * 「その利用者 ID は存在する」ことを攻撃者に教えてしまう（US31）。
 * ロック発生の通知はメール等の帯域外で行い、画面には出さない。
 */
const FAILURE_MESSAGE = '利用者 ID またはパスワードが正しくありません'

export function LoginPage() {
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')
  const [failed, setFailed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const setSession = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFailed(false)
    setSubmitting(true)

    try {
      const result = await login({ userId, password })
      setSession(result)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/dashboard', { replace: true })
    } catch {
      // 失敗の理由はサーバー側の監査ログに残る。画面では区別しない。
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-bold text-gray-900">CargoTracker</h1>
      <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-4 rounded border bg-white p-6">
        <div>
          <label htmlFor="userId" className="block text-sm font-medium text-gray-700">
            利用者 ID
          </label>
          <input
            id="userId"
            type="text"
            autoComplete="username"
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
            required
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-gray-700">
            パスワード
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        {failed && (
          <p role="alert" className="text-sm text-red-700">
            {FAILURE_MESSAGE}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
        >
          ログイン
        </button>
      </form>

      <p className="mt-6 text-sm text-gray-600">
        追跡番号をお持ちの方は{' '}
        <Link to="/" className="text-blue-700 underline">
          追跡照会はこちら
        </Link>
        （ログイン不要）
      </p>
    </main>
  )
}
