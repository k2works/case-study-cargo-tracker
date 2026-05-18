import { Outlet, Link, useLocation } from 'react-router'
import { useAuthStore } from '../stores/authStore'
import { useLogout } from '../features/auth/hooks/useAuth'

interface NavItem {
  path: string
  label: string
  roles: string[]
}

const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', label: 'ダッシュボード', roles: ['ROLE_ADMIN', 'ROLE_SALES', 'ROLE_ROUTING', 'ROLE_HANDLING'] },
  { path: '/bookings', label: '予約', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/routing/voyages', label: '航海スケジュール', roles: ['ROLE_ADMIN', 'ROLE_ROUTING'] },
  { path: '/handling', label: '荷役作業', roles: ['ROLE_ADMIN', 'ROLE_HANDLING'] },
  { path: '/tracking', label: '追跡管理', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/shippers', label: '荷主管理', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
]

function visibleNavItems(userRoles: string[]): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.some((role) => userRoles.includes(role)))
}

export function AppLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useLogout()
  const location = useLocation()
  const navItems = visibleNavItems(user?.roles ?? [])

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link to="/dashboard" className="text-lg font-bold text-gray-900">
              国際貨物輸送管理
            </Link>
            <nav className="flex gap-4">
              {navItems.map((item) => (
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
              <span className="text-sm text-gray-600">
                {user.username}
                {user.roles.length > 0 && (
                  <span className="ml-1 text-gray-400">({user.roles[0].replace('ROLE_', '')})</span>
                )}
              </span>
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
