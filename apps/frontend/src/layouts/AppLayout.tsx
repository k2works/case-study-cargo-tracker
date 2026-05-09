import { Outlet, Link, useLocation } from 'react-router'
import { useAuthStore } from '../stores/authStore'
import { useLogout } from '../features/auth/hooks/useAuth'

const NAV_ITEMS: Array<{ path: string; label: string; roles: string[] }> = [
  { path: '/dashboard', label: 'ダッシュボード', roles: [] },
  { path: '/bookings', label: '貨物予約', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/voyages', label: '航海スケジュール', roles: ['ROLE_ADMIN', 'ROLE_ROUTING'] },
  { path: '/routing/assignments', label: '経路設計担当', roles: ['ROLE_ADMIN', 'ROLE_ROUTING'] },
  { path: '/handling/activities', label: '荷役記録', roles: [] },
  { path: '/tracking', label: '貨物追跡', roles: [] },
]

export function AppLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useLogout()
  const location = useLocation()

  const visibleNavItems = NAV_ITEMS.filter(
    (item) => item.roles.length === 0 || (user?.roles ?? []).some((r) => item.roles.includes(r))
  )

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link to="/dashboard" className="text-lg font-bold text-gray-900">
              CargoTracker
            </Link>
            <nav className="flex gap-4">
              {visibleNavItems.map((item) => (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`text-sm font-medium ${
                    location.pathname.startsWith(item.path)
                      ? 'text-blue-600'
                      : 'text-gray-600 hover:text-gray-900'
                  }`}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-4">
            {user && (
              <span className="text-sm text-gray-600">{user.username}</span>
            )}
            <button
              onClick={logout}
              className="rounded-md bg-gray-100 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-200"
            >
              ログアウト
            </button>
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto">
        <Outlet />
      </main>
    </div>
  )
}
