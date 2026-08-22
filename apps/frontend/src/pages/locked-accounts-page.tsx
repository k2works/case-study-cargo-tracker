import { useLockedAccounts, useUnlockAccount } from '../features/admin/queries'
import { formatBusinessDateTime } from '../lib/business-time'

/**
 * ロックされたアカウントの解除（US32）。
 *
 * ロックされた本人には理由が表示されない（US31）ため、本人は自分で状況を確かめられない。
 * 管理者がここを見て解除する。
 */
export function LockedAccountsPage() {
  const { data: accounts, isPending, isError } = useLockedAccounts()
  const unlock = useUnlockAccount()

  if (isPending) {
    return <p className="text-gray-600">読み込んでいます…</p>
  }

  if (isError) {
    return (
      <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
        ロックされたアカウントを表示できませんでした。時間をおいて再度お試しください。
      </p>
    )
  }

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">アカウント管理</h1>

      <p className="text-sm text-gray-700">
        パスワードを 5 回続けて間違えたアカウントは 15 分間ロックされます。
        <strong>期限が過ぎたものは自動で解除される</strong>ため、ここには出ません。
      </p>

      {accounts.length === 0 ? (
        <p className="rounded border border-gray-200 bg-gray-50 p-4 text-gray-700">
          いまロックされているアカウントはありません。
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 text-left">
                <th className="py-2">利用者 ID</th>
                <th>氏名</th>
                <th>失敗回数</th>
                <th>ロック期限</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.username} className="border-b">
                  <td className="py-2 font-mono">{account.username}</td>
                  <td>{account.displayName}</td>
                  <td>{account.failedAttempts} 回</td>
                  {/* 日時は業務タイムゾーン（表示規約） */}
                  <td>{formatBusinessDateTime(account.lockedUntil)}</td>
                  <td>
                    <button
                      type="button"
                      onClick={() => unlock.mutate(account.username)}
                      disabled={unlock.isPending}
                      className="rounded bg-blue-600 px-3 py-1 text-white hover:bg-blue-700 disabled:opacity-50"
                    >
                      解除する
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-sm text-gray-600">
        解除すると<strong>失敗回数も 0 に戻り</strong>、その場でログインできるようになります。
        期限だけを消すと、次に 1 回間違えただけでまたロックされるためです。
        解除の操作は「誰が・いつ・どのアカウントを」が記録に残ります。
      </p>
    </div>
  )
}
