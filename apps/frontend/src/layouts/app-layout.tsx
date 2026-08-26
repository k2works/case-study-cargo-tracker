import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { NAVIGATION, resolveNavigationItem } from '../config/navigation'
import { useAuthStore } from '../stores/auth-store'
import { ROLE_LABELS } from '../types/role'

/** 認証済み画面の共通レイアウト。ナビゲーションはロールに応じて出し分ける。 */
export function AppLayout() {
  const user = useAuthStore((state) => state.user)
  const hasAnyRole = useAuthStore((state) => state.hasAnyRole)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const items = NAVIGATION.filter((item) => hasAnyRole(item.roles))
  // いま開いている画面に対応する項目（最長一致）。**ここで判定を書き直さない**
  const current = resolveNavigationItem(useLocation().pathname)?.to

  function handleLogout() {
    logout()
    // ログアウト後にブラウザバックで業務画面へ戻れてしまうと、共用端末で
    // 「ログアウトした」という利用者の理解が裏切られる。履歴を置き換える。
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b bg-white px-6 py-3">
        <Link to="/dashboard" className="text-lg font-bold text-gray-900">
          CargoTracker
        </Link>
        <div className="flex items-center gap-4 text-sm text-gray-700">
          {user !== null && (
            <span>
              {user.displayName}（{user.roles.map((role) => ROLE_LABELS[role]).join('・')}）
            </span>
          )}
          <button
            type="button"
            onClick={handleLogout}
            className="rounded border border-gray-300 px-3 py-1 hover:bg-gray-100"
          >
            ログアウト
          </button>
        </div>
      </header>

      <div className="flex">
        <nav aria-label="メインナビゲーション" className="w-56 shrink-0 border-r bg-white p-4">
          <ul className="space-y-1 text-sm">
            {items.map((item) => (
              <li key={item.to}>
                {item.available ? (
                  /*
                    **いま開いている画面の項目だけを選択状態にする。**
                    `NavLink` の既定は前方一致であり、`/booking/estimates/new` を開くと
                    「見積管理」と「貨物予約」が**同時に**選択状態になる（キャプチャで
                    気づいた）。どちらが自分の居場所か分からなくなる。
                    判定はナビゲーションの解決規則（最長一致）に委ねる——ここで別の
                    判定を書くと、ダッシュボードの導線と食い違う
                  */
                  <NavLink
                    to={item.to}
                    className={() =>
                      `block rounded px-3 py-2 ${current === item.to ? 'bg-blue-50 font-medium text-blue-700' : 'text-gray-700 hover:bg-gray-100'}`
                    }
                  >
                    {item.label}
                  </NavLink>
                ) : (
                  // 押せるのにどこにも行けないメニューは「壊れている」と受け取られる。
                  // まだ使えないことをその場で伝える
                  <span className="flex items-center justify-between rounded px-3 py-2 text-gray-400">
                    {item.label}
                    <span className="text-xs">準備中</span>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </nav>

        <main className="flex-1 p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
