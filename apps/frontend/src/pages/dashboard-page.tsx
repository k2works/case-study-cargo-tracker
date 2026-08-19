import { Link } from 'react-router-dom'
import { NAVIGATION } from '../config/navigation'
import { useAuthStore } from '../stores/auth-store'
import type { Role } from '../types/role'

type Panel = {
  role: Role
  title: string
  /** その担当が「次に何をするか」への入口。件数だけ出しても仕事は進まない。 */
  actions: { label: string; to: string }[]
}

/**
 * ロール別のダッシュボード構成。
 *
 * IT1 では入口のみを置き、要対応件数は各ストーリーの実装時に足す（US02 は荷主登録のみ）。
 */
const PANELS: Panel[] = [
  {
    role: 'ROLE_SALES',
    title: '営業ダッシュボード',
    actions: [
      { label: '荷主を登録する', to: '/booking/shippers/new' },
      { label: '荷主を探す', to: '/booking/shippers' },
    ],
  },
  {
    role: 'ROLE_SHIPPER',
    title: '荷主ダッシュボード',
    actions: [{ label: '自分の貨物予約を見る', to: '/booking' }],
  },
  {
    role: 'ROLE_ROUTING',
    title: '経路設計ダッシュボード',
    actions: [{ label: '航海スケジュールを見る', to: '/routing/voyages' }],
  },
  {
    role: 'ROLE_HANDLER',
    title: '荷役ダッシュボード',
    actions: [{ label: '荷役作業を記録する', to: '/handling' }],
  },
  {
    role: 'ROLE_TRACKER',
    title: '追跡管理ダッシュボード',
    actions: [
      { label: '貨物の状態を確認する', to: '/tracking/manage' },
      { label: 'キャンセル申請を確認する', to: '/booking/cancellations' },
    ],
  },
  {
    role: 'ROLE_ACCOUNTANT',
    title: '経理ダッシュボード',
    actions: [{ label: '請求書を確認する', to: '/billing' }],
  },
]

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
