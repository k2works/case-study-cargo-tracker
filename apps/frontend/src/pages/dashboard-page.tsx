import { Link } from 'react-router-dom'
import { PANELS } from '../config/dashboard-panels'
import { resolveNavigationItem } from '../config/navigation'
import { useAuthStore } from '../stores/auth-store'
import { useBookings } from '../features/booking/queries'

/**
 * その行動の画面が使えるか。
 *
 * <p>ナビゲーションの定義を正とする。ここで別々に判定すると、サイドバーは「準備中」なのに
 * ダッシュボードのリンクだけ押せて公開トップに飛ばされる状態が生まれる。
 * 利用者にはそれが「勝手にログアウトされた」ように見える。
 */
function isAvailable(to: string): boolean {
  return resolveNavigationItem(to)?.available === true
}

/**
 * 経路の状態別に「いま何件たまっているか」を出す。
 *
 * メール通知の仕組みが無いため、担当者はこの表示で自分の仕事に気づく。ただし件数を
 * 出すだけでは仕事は進まないため、対象の一覧への入口を同じパネルの行動に置いている。
 */
function RoutingBacklogNotice({
  routingStatus,
  message,
}: Readonly<{ routingStatus: string; message: (count: number) => string }>) {
  const { data } = useBookings('', '', routingStatus)

  if (data === undefined || data.totalCount === 0) {
    return null
  }

  return (
    <p className="mt-2 rounded border border-yellow-300 bg-yellow-50 px-3 py-2 text-sm text-yellow-900">
      {message(data.totalCount)}
    </p>
  )
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
          {panel.role === 'ROLE_ROUTING' && (
            <RoutingBacklogNotice
              routingStatus="ROUTING_REQUESTED"
              message={(count) => `経路設計を待っている予約が ${count} 件あります。`}
            />
          )}
          {/* 引き渡し忘れは営業側にしか直せない。経路設計者の待ち行列には現れない（#553） */}
          {panel.role === 'ROLE_SALES' && (
            <RoutingBacklogNotice
              routingStatus="NOT_ROUTED"
              message={(count) => `まだ経路設計を依頼していない予約が ${count} 件あります。`}
            />
          )}
          {/* 経路設計者から戻ってきた予約。荷主と条件を話せるのは営業だけであり、
              気づかないと予約が止まったままになる（ADR-020 決定 7） */}
          {panel.role === 'ROLE_SALES' && (
            <RoutingBacklogNotice
              routingStatus="CONSULTATION_REQUESTED"
              message={(count) => `条件の協議を求められている予約が ${count} 件あります。`}
            />
          )}
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
