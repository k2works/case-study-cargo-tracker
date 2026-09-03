import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { useAuthStore } from '../auth/authStore';
import { ROLE_LABELS } from '../auth/roles';
import { EXTERNAL_LINKS, navigationFor } from './navigation';

/** 左サイドナビ + トップヘッダ（ui_design.md）。 */
export function AppLayout() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const location = useLocation();

  // 未認証をここで null にすると、配下のガードまで届かず画面が真っ白になる。
  // 「入れない」と「壊れた」は利用者から見分けがつかない。
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  const items = navigationFor(user.roles);

  return (
    <div className="min-h-screen flex">
      <nav aria-label="メインナビゲーション" className="w-56 border-r p-4">
        <ul>
          {items.map((item) => (
            <li key={item.path}>
              <NavLink to={item.path} end>
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>

        {/* ポータルは SPA の外にある。NavLink ではなく <a> で開く。
            別タブにするのは、作業中の画面を閉じずに手順を引けるようにするため。 */}
        <h2 className="mt-6 border-t pt-4 text-xs font-semibold text-gray-500">
          資料
        </h2>
        <ul>
          {EXTERNAL_LINKS.map((link) => (
            <li key={link.href}>
              <a href={link.href} target="_blank" rel="noopener noreferrer">
                {link.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <div className="flex-1">
        <header className="flex items-center justify-between border-b p-4">
          <Link to="/">国際貨物輸送管理システム</Link>
          <div>
            <span>
              {user.username}（{user.roles.map((r) => ROLE_LABELS[r]).join('・')}）
            </span>
            <button
              type="button"
              onClick={() => {
                logout();
                navigate('/login', { replace: true });
              }}
            >
              ログアウト
            </button>
          </div>
        </header>
        <main className="p-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
