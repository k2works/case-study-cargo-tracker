import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/contexts/AuthContext';

interface NavItem {
  path: string;
  label: string;
  roles: string[];
}

const NAV_ITEMS: NavItem[] = [
  { path: '/', label: 'ダッシュボード', roles: ['ROLE_ADMIN', 'ROLE_SALES', 'ROLE_ROUTING', 'ROLE_HANDLER'] },
  { path: '/shippers', label: '荷主管理', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/quotes', label: '見積管理', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/bookings', label: '予約管理', roles: ['ROLE_ADMIN', 'ROLE_SALES'] },
  { path: '/voyages', label: '航海スケジュール', roles: ['ROLE_ADMIN', 'ROLE_ROUTING'] },
];

export default function Navigation() {
  const { username, role, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const visibleItems = role
    ? NAV_ITEMS.filter((item) => item.roles.includes(role))
    : [];

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <header className="bg-white shadow-sm border-b">
      <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <Link to="/" className="text-lg font-bold text-gray-900">
            国際貨物輸送管理
          </Link>
          <nav aria-label="メインナビゲーション" className="flex gap-4">
            {visibleItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={`text-sm font-medium ${
                  location.pathname === item.path
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
          {username && (
            <span className="text-sm text-gray-600">
              {username}
              {role && (
                <span className="ml-1 text-gray-400">
                  ({role.replace('ROLE_', '')})
                </span>
              )}
            </span>
          )}
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md bg-gray-100 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            ログアウト
          </button>
        </div>
      </div>
    </header>
  );
}
