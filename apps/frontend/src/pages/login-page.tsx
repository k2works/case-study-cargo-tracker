import type React from 'react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { DEMO_LOGIN } from '../config/demo-login'
import { login } from '../features/auth/api'
import { ApiError } from '../lib/api-client'
import { useAuthStore } from '../stores/auth-store'

/**
 * ログイン失敗時に画面へ出す唯一の文言。
 *
 * 認証情報誤り・アカウントロック中・無効化アカウントを区別して表示すると、
 * 「その利用者 ID は存在する」ことを攻撃者に教えてしまう（US31）。
 * ロック発生の通知はメール等の帯域外で行い、画面には出さない。
 */
const FAILURE_MESSAGE = '利用者 ID またはパスワードが正しくありません'

/**
 * 認証以外の理由で失敗したときの文言。
 *
 * 繋がらないだけなのに「ID かパスワードが違う」と言われると、利用者は正しい情報を
 * 何度も打ち直すことになる。同一文言にすべきなのは「認証の失敗どうし」であって、
 * 通信の失敗まで混ぜる理由はない（アカウントの存在有無とは無関係のため漏れもしない）。
 */
const UNAVAILABLE_MESSAGE =
    'サーバーに接続できませんでした。時間をおいて再度お試しください（続く場合は管理者にご連絡ください）'

export function LoginPage() {
  // 開発環境では動作確認の利用者を事前入力する（既定は無効。ADR-006）
  const [userId, setUserId] = useState(DEMO_LOGIN.userId)
  const [password, setPassword] = useState(DEMO_LOGIN.password)
  const [failure, setFailure] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const setSession = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    setSubmitting(true)

    try {
      const result = await login({ userId, password })
      setSession(result)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/dashboard', { replace: true })
    } catch (error) {
      // 認証の失敗（401）は理由を区別しない。サーバー側の監査ログにだけ残る。
      // それ以外は認証の問題ではないので、そう伝える
      const isAuthenticationFailure = error instanceof ApiError && error.status === 401
      setFailure(isAuthenticationFailure ? FAILURE_MESSAGE : UNAVAILABLE_MESSAGE)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-bold text-gray-900">CargoTracker</h1>
      <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

      {DEMO_LOGIN.enabled && (
        <section className="mt-6 space-y-3">
          {/* 事前入力されていることを隠さない。気づかないまま本番同様の画面だと思われるのが最も危ない */}
          <p className="rounded border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-gray-800">
            <strong>開発環境</strong>です。動作確認用の利用者で事前入力しています。
          </p>

          <div className="rounded border bg-white p-4">
            <h2 className="text-sm font-semibold text-gray-900">動作確認用の利用者</h2>
            <p className="mt-1 text-sm text-gray-600">
              <strong>パスワードは共通</strong>で <code>{DEMO_LOGIN.password}</code> です。
              利用者 ID を選ぶと入力欄に反映されます。
            </p>

            <ul className="mt-3 divide-y text-sm">
              {DEMO_LOGIN.accounts.map((account) => (
                <li key={account.userId} className="flex gap-3 py-2">
                  <button
                    type="button"
                    onClick={() => {
                      setUserId(account.userId)
                      setPassword(DEMO_LOGIN.password)
                      setFailure(null)
                    }}
                    className="w-32 shrink-0 text-left text-blue-700 underline"
                  >
                    {account.userId}
                  </button>
                  <span className={account.canLogIn ? 'text-gray-700' : 'text-gray-500'}>
                    {account.description}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

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

        {failure !== null && (
          <p role="alert" className="text-sm text-red-700">
            {failure}
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
