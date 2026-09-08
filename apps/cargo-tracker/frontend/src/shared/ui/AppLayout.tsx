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

/**
 * 荷役ロールの下部タブ（ui_design.md:613 / H.8）。
 *
 * <p><b>モバイル幅だけ。</b> 荷役作業員は港に居て片手で使う——左サイドナビは
 * 画面の外に出ているので、そのままでは 2 つ目の入口に辿り着けない。</p>
 *
 * <p>タブは 2 つ。<b>船から降ろす仕事（作業のある航海）と、降りたあとの仕事
 * （引取待ち）</b>で、どちらの航海の仕事でもない引取は航海起点では出てこない。</p>
 */
const HANDLER_TABS = [
  { path: '/', label: '作業のある航海' },
  { path: '/handling/awaiting-claim', label: '引取待ち' },
] as const;

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
  const isHandler = user.roles.includes('ROLE_HANDLER');

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
        {/* 下部タブのぶんだけ余白を空ける。空けないと最後の行が隠れる。 */}
        <main className={`p-6 ${isHandler ? 'pb-24 sm:pb-6' : ''}`}>
          <Outlet />
        </main>
      </div>

      {/* **モバイル幅だけ**（ui_design.md:613）。荷役作業員は港に居て片手で使う。 */}
      {isHandler && (
        <nav
          aria-label="荷役の作業タブ"
          className="fixed inset-x-0 bottom-0 z-10 flex border-t border-gray-200 bg-white sm:hidden"
        >
          {HANDLER_TABS.map((tab) => (
            <NavLink
              key={tab.path}
              to={tab.path}
              end
              className={({ isActive }) => [
                'flex-1 py-3 text-center text-sm',
                isActive ? 'font-semibold text-blue-800' : 'text-gray-700',
              ].join(' ')}
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}
