import { Link } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';
import { navigationFor } from '@/shared/ui/navigation';
import { CARD, LINK, NOTICE, PAGE_TITLE, SECTION_TITLE } from '@/shared/ui/styles';

/** S02 ダッシュボード。「今日の作業」からその日の入口へ行けるようにする。 */
export function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const items = user ? navigationFor(user.roles).filter((i) => i.path !== '/') : [];

  return (
    <section>
      <h1 className={PAGE_TITLE}>ダッシュボード</h1>

      <h2 className={`${SECTION_TITLE} mt-6`}>今日の作業</h2>

      {/* 入口が 1 つも無いロールがある（IT1 時点の荷役・荷主など）。空の一覧を
          黙って出すと「読み込みに失敗した」と受け取られるので、理由を書く。 */}
      {items.length === 0 ? (
        <output className={`${NOTICE} mt-3`}>
          このロール向けの画面は、次のイテレーションから増えていきます。
        </output>
      ) : (
        <ul className={`${CARD} mt-3 divide-y divide-gray-100`}>
          {items.map((item) => (
            <li key={item.path} className="py-2 first:pt-0 last:pb-0">
              <Link to={item.path} className={LINK}>
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
