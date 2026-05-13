import { Outlet, Link, useLocation } from 'react-router'
import { useAuthStore } from '../stores/authStore'
import { useLogout } from '../features/auth/hooks/useAuth'

const NAV_ITEMS: Array<{ path: string; label: string }> = [
  { path: '/shippers', label: '荷主管理' },
]

export function AppLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useLogout()
  const location = useLocation()

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link to="/shippers" className="text-lg font-bold text-gray-900">
              CargoTracker
            </Link>
            <nav className="flex gap-4">
              {NAV_ITEMS.map((item) => (
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
