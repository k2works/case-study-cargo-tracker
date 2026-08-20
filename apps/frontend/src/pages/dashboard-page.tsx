import { Link } from 'react-router-dom'
import { PANELS } from '../config/dashboard-panels'
import { NAVIGATION } from '../config/navigation'
import { useAuthStore } from '../stores/auth-store'

/**
 * その行動の画面が使えるか。
 *
 * <p>ナビゲーションの定義を正とする。ここで別々に判定すると、サイドバーは「準備中」なのに
 * ダッシュボードのリンクだけ押せて公開トップに飛ばされる状態が生まれる。
 * 利用者にはそれが「勝手にログアウトされた」ように見える。
 */
function isAvailable(to: string): boolean {
  return NAVIGATION.some((item) => item.available && to.startsWith(item.to))
}

export function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const panels = PANELS.filter((panel) => user?.roles.includes(panel.role))
  const hasAnyAvailableAction = panels.some((panel) => panel.actions.some((a) => isAvailable(a.to)))

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">ダッシュボード</h1>

      {panels.length === 0 && (
        <p className="text-gray-600">
          担当する業務がまだ割り当てられていません。管理者にお問い合わせください。
        </p>
      )}

      {panels.length > 0 && !hasAnyAvailableAction && (
        <p className="rounded border border-gray-300 bg-white p-4 text-gray-700">
          担当の画面は次のリリースで使えるようになります。準備が整うまでお待ちください。
        </p>
      )}

      {panels.map((panel) => (
        <section key={panel.role} className="rounded border bg-white p-6">
          <h2 className="text-lg font-semibold text-gray-900">{panel.title}</h2>
          <ul className="mt-4 space-y-2 text-sm">
            {panel.actions.map((action) => (
              <li key={action.to}>
                {isAvailable(action.to) ? (
                  <Link to={action.to} className="text-blue-700 underline">
                    {action.label}
                  </Link>
                ) : (
                  <span className="text-gray-400">
                    {action.label}
                    <span className="ml-2 text-xs">準備中</span>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
