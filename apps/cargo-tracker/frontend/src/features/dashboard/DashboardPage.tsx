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
  const isSales = user?.roles.includes('ROLE_SALES') ?? false;

  // US04 §受入基準 5・US06 §受入基準 3 の「通知」。送信基盤はスコープ外なので、
  // 担当者はここで気づく（ユーザーストーリーの通知に関する注記）。
  //
  // **件数は担当の仕事に合わせる。** 引き渡していない予約（仮受付）は営業の
  // 仕事で、経路設計者はその件数に対して打てる手が無い。設計を待っている件数は
  // 経路設計者の仕事である。
  const { data: summary } = useQuery({
    queryKey: ['booking-summary'],
    queryFn: fetchBookingSummary,
    enabled: isRouting || isSales,
    refetchInterval: 10000,
  });
  const preliminary =
    summary?.state === 'ready' ? summary.value.preliminary : 0;
  const routingWorklist =
    summary?.state === 'ready' ? summary.value.routingWorklist : 0;

  return (
    <section>
      <h1 className={PAGE_TITLE}>ダッシュボード</h1>

      {/* 0 件のときは出さない。毎朝「0 件」を読み飛ばす習慣がつくと、
          件数が出た日も見落とす。 */}
      {isSales && preliminary > 0 && (
        <output className={`${NOTICE} mt-4 block`}>
          経路設計者へ引き渡していない予約が {preliminary} 件あります。
          {/* 件数を出すだけでは仕事が進まない。対象へ行ける導線を添える。 */}
          <Link to="/bookings" className={`${LINK} ml-1`}>
            予約一覧
          </Link>
          で確認してください。
        </output>
      )}

      {isRouting && routingWorklist > 0 && (
        <output className={`${NOTICE} mt-4 block`}>
          経路設計を待っている予約が {routingWorklist} 件あります。
          <Link to="/routing/worklist" className={`${LINK} ml-1`}>
            経路設計作業一覧
          </Link>
          で確認してください。
        </output>
      )}

      {/* 経路設計者の作業の入口は S30。予約一覧（S20）は予約全体を横断して
          見たいときに開く（ui_design.md S20 の注記）。0 件でも導線は出す。
          件数が 0 の日でも「どこへ行けばよいか」は変わらない。 */}
      {isRouting && (
        <p className="mt-4 text-sm">
          <Link to="/routing/worklist" className={LINK}>
            経路設計作業一覧を開く
          </Link>
        </p>
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
