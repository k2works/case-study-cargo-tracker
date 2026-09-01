import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { NAVIGATION, resolveNavigationItem } from '../config/navigation'
import { useAuthStore } from '../stores/auth-store'
import { ROLE_LABELS } from '../types/role'
import { NotificationToasts } from '../features/notification/components/notification-toasts'

const SESSION_WARNING_AFTER_MS = 15 * 60 * 1000
const SESSION_TIMEOUT_AFTER_MS = 20 * 60 * 1000
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'focus'] as const

/** 認証済み画面の共通レイアウト。ナビゲーションはロールに応じて出し分ける。 */
export function AppLayout() {
  const user = useAuthStore((state) => state.user)
  const hasAnyRole = useAuthStore((state) => state.hasAnyRole)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const [timeoutWarningVisible, setTimeoutWarningVisible] = useState(false)
  const [activityVersion, setActivityVersion] = useState(0)

  const items = NAVIGATION.filter((item) => hasAnyRole(item.roles))
  // いま開いている画面に対応する項目（最長一致）。**ここで判定を書き直さない**
  const current = resolveNavigationItem(useLocation().pathname)?.to

  function continueSession() {
    setTimeoutWarningVisible(false)
    setActivityVersion((currentVersion) => currentVersion + 1)
  }

  function handleLogout() {
    logout()
    // ログアウト後にブラウザバックで業務画面へ戻れてしまうと、共用端末で
    // 「ログアウトした」という利用者の理解が裏切られる。履歴を置き換える。
    navigate('/login', { replace: true })
  }

  useEffect(() => {
    if (user === null) {
      return undefined
    }

    const warningTimer = globalThis.setTimeout(() => {
      setTimeoutWarningVisible(true)
    }, SESSION_WARNING_AFTER_MS)
    const logoutTimer = globalThis.setTimeout(() => {
      logout()
      navigate('/login', { replace: true })
    }, SESSION_TIMEOUT_AFTER_MS)
    const recordActivity = () => {
      setTimeoutWarningVisible(false)
      setActivityVersion((currentVersion) => currentVersion + 1)
    }

    for (const event of ACTIVITY_EVENTS) {
      globalThis.addEventListener(event, recordActivity)
    }

    return () => {
      globalThis.clearTimeout(warningTimer)
      globalThis.clearTimeout(logoutTimer)
      for (const event of ACTIVITY_EVENTS) {
        globalThis.removeEventListener(event, recordActivity)
      }
    }
  }, [activityVersion, logout, navigate, user])

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

      {timeoutWarningVisible && (
        <div
          role="alert"
          className="border-b border-amber-300 bg-amber-50 px-6 py-3 text-sm text-amber-950"
        >
          <div className="flex items-center justify-between gap-4">
            <p>
              操作がないため、まもなく自動ログアウトします。
              入力中の内容は保存されません。
            </p>
            <button
              type="button"
              onClick={continueSession}
              className="shrink-0 rounded border border-amber-700 px-3 py-1 font-medium text-amber-950 hover:bg-amber-100"
            >
              操作を続ける
            </button>
          </div>
        </div>
      )}

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

      {/*
        荷主宛のお知らせ（US39）。**レイアウトの外側に置く**——本文の中に入れると、
        画面ごとの余白や折り返しに引きずられて出る位置が変わる。
        荷主以外では何も読みに行かない（中で判定している）
      */}
      <NotificationToasts />
    </div>
  )
}
