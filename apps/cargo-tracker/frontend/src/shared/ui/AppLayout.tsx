import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { IdleTimeout } from '@/shared/auth/IdleTimeout';
import { useAuthStore } from '../auth/authStore';
import { ROLE_LABELS } from '../auth/roles';
import { EXTERNAL_LINKS, navigationFor } from './navigation';
import { LINK } from './styles';

/** サイドナビの項目。開いている画面だけ色を変え、位置を見失わないようにする。 */
function navItemClass({ isActive }: { isActive: boolean }) {
  return [
    'block rounded px-2 py-1.5 text-sm',
    isActive ? 'bg-blue-50 font-semibold text-blue-800' : 'text-gray-700 hover:bg-gray-100',
  ].join(' ');
}

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
    <div className="flex min-h-screen bg-gray-50">
      {/* 共用端末に開きっぱなしの画面を残さない（non_functional.md「セッション」）。
          認証済みの画面すべてに効かせるため、シェルに置く。 */}
      <IdleTimeout />
      <nav
        aria-label="メインナビゲーション"
        className="w-56 shrink-0 border-r border-gray-200 bg-white p-4"
      >
        <ul className="space-y-1">
          {items.map((item) => (
            <li key={item.path}>
              <NavLink to={item.path} end className={navItemClass}>
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
        <ul className="mt-1 space-y-1">
          {EXTERNAL_LINKS.map((link) => (
            <li key={link.href}>
              <a
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
                className={`${LINK} block px-2 py-1.5 text-sm`}
              >
                {link.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <div className="flex-1">
        <header className="flex items-center justify-between border-b border-gray-200 bg-white p-4">
          <Link to="/" className="font-semibold text-gray-900">
            国際貨物輸送管理システム
          </Link>
          <div className="flex items-center gap-4 text-sm">
            <span className="text-gray-700">
              {user.username}（{user.roles.map((r) => ROLE_LABELS[r]).join('・')}）
            </span>
            <button
              type="button"
              onClick={() => {
                logout();
                navigate('/login', { replace: true });
              }}
              className="rounded border border-gray-300 px-3 py-1.5 text-gray-700 hover:bg-gray-100"
            >
              ログアウト
            </button>
          </div>
        </header>
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
