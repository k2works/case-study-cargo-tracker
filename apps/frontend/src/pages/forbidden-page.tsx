import { Link } from 'react-router-dom'
import { useAuthStore } from '../stores/auth-store'

/** 権限のない画面へアクセスしたときに表示する。戻り先を必ず置き、行き止まりにしない。 */
export function ForbiddenPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())

  return (
    <main className="mx-auto max-w-xl p-8">
      <h1 className="text-2xl font-bold text-gray-900">この操作を行う権限がありません</h1>
      <p className="mt-2 text-gray-600">
        担当外の画面です。必要な場合は管理者に権限の付与を依頼してください。
      </p>

      <p className="mt-6">
        {isAuthenticated ? (
          <Link to="/dashboard" className="text-blue-700 underline">
            ダッシュボードへ戻る
          </Link>
        ) : (
          <Link to="/login" className="text-blue-700 underline">
            ログインへ戻る
          </Link>
        )}
      </p>
    </main>
  )
}
