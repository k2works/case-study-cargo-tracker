import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  ALERT,
  CARD,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { display } from '@/features/shippers/api';
import { bookingStatusLabel, cargoTypeLabel, type BookingView } from '@/features/bookings/api';
import { fetchRoutingWorklist } from './api';

/**
 * S30 経路設計作業一覧（UC04）。
 *
 * <p>経路設計者の入口。経路設計依頼の通知（US06 §受入基準 3）は送信基盤を持たない
 * ので、ダッシュボードの件数とこの一覧が通知の代わりになる。</p>
 *
 * <p>並び順は<b>誤配が先、そのあと到着期限が近い順</b>。並べ替えはサーバが決める。
 * 画面でも並べ替えると、同じ絞りが 2 か所になって片方を緩めても効かなくなる。</p>
 *
 * <p>供給元は予約（bookingms）。routing_read_db に予約の写しは作らない。</p>
 */
export function RoutingWorklistPage() {
  const [includeRouted, setIncludeRouted] = useState(false);
  const { data, isPending, isError } = useQuery({
    queryKey: ['routing-worklist', includeRouted],
    queryFn: () => fetchRoutingWorklist(includeRouted),
    refetchInterval: 3000,
  });

  const items = data?.state === 'ready' ? (data.value.items as BookingView[]) : [];

  return (
    <section>
      <h1 className={PAGE_TITLE}>経路設計作業一覧</h1>

      <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeRouted}
          onChange={(event) => setIncludeRouted(event.target.checked)}
        />
        {'設計済みも表示'}
      </label>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          一覧を取得できませんでした
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {data?.state === 'ready' && items.length === 0 && (
        <output className={`${NOTICE} mt-4`}>経路設計を待っている予約はありません</output>
      )}

      {items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>経路設計を待っている予約</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>予約番号</th>
                <th scope="col" className={TH}>荷主</th>
                <th scope="col" className={TH}>出発地 → 目的地</th>
                <th scope="col" className={TH}>到着期限</th>
                <th scope="col" className={TH}>貨物</th>
                <th scope="col" className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.bookingId}>
                  <td className={TD}>
                    <Link to={`/bookings/${item.bookingId}`} className={LINK}>
                      {item.bookingNumber}
                    </Link>
                  </td>
                  <td className={TD}>{display(item.shipperName)}</td>
                  <td className={TD}>
                    {item.originUnLocode} → {item.destinationUnLocode}
                  </td>
                  <td className={TD}>{item.arrivalDeadline}</td>
                  <td className={TD}>
                    {item.productName}（{cargoTypeLabel(item.cargoType)}）
                  </td>
                  <td className={TD}>
                    {item.routingStatus === 'MISROUTED'
                      ? '誤配'
                      : bookingStatusLabel(item.bookingStatus)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="mt-4 text-sm">
        <Link to="/voyages" className={LINK}>
          航海スケジュール一覧へ
        </Link>
      </p>
    </section>
  );
}
