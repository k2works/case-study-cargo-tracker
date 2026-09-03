import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';
import { navigationFor } from '@/shared/ui/navigation';
import { CARD, LINK, NOTICE, PAGE_TITLE, SECTION_TITLE } from '@/shared/ui/styles';
import { fetchBookingSummary } from '@/features/bookings/api';

/** S02 ダッシュボード。「今日の作業」からその日の入口へ行けるようにする。 */
export function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const items = user ? navigationFor(user.roles).filter((i) => i.path !== '/') : [];
  const isRouting = user?.roles.includes('ROLE_ROUTING') ?? false;

  // US04 §受入基準 5 の「経路設計者への通知」。送信基盤はスコープ外なので、
  // 経路設計者はここで気づく（ユーザーストーリーの通知に関する注記）。
  const { data: summary } = useQuery({
    queryKey: ['booking-summary'],
    queryFn: fetchBookingSummary,
    enabled: isRouting,
    refetchInterval: 10000,
  });
  const preliminary =
    summary?.state === 'ready' ? summary.value.preliminary : 0;

  return (
    <section>
      <h1 className={PAGE_TITLE}>ダッシュボード</h1>

      {/* 0 件のときは出さない。毎朝「0 件」を読み飛ばす習慣がつくと、
          件数が出た日も見落とす。 */}
      {isRouting && preliminary > 0 && (
        <output className={`${NOTICE} mt-4 block`}>
          引き渡し待ちの予約が {preliminary} 件あります。
          {/* 件数を出すだけでは仕事が進まない。対象へ行ける導線を添える。 */}
          <Link to="/bookings" className={`${LINK} ml-1`}>
            予約一覧
          </Link>
          で確認してください。
        </output>
      )}

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
