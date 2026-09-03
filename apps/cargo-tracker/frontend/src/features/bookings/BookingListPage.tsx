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
import { bookingStatusLabel, cargoTypeLabel, fetchBookings } from './api';

/**
 * S20 予約一覧（UC03）。
 *
 * <p>既定では精算済とキャンセルを外し、到着期限が近い順に並べる
 * （ui_design.md「一覧の既定条件」）。終わった予約が混ざると、一覧全体が
 * 「今日やること」として信用されなくなる。</p>
 */
export function BookingListPage() {
  const [includeFinished, setIncludeFinished] = useState(false);
  const { data, isPending, isError } = useQuery({
    queryKey: ['bookings', includeFinished],
    queryFn: () => fetchBookings(includeFinished),
    // 投影は非同期なので、登録直後は数秒ぶん遅れる。定期に取り直す。
    refetchInterval: 3000,
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>予約一覧</h1>
      <p className="mt-2 text-sm">
        <Link to="/bookings/new" className={LINK}>
          予約を登録する
        </Link>
      </p>

      <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeFinished}
          onChange={(event) => setIncludeFinished(event.target.checked)}
        />
        終了したものも表示
      </label>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          一覧を取得できませんでした
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {/* 見出しだけの表を出すと「読み込みに失敗した」と受け取られる。 */}
      {data?.state === 'ready' && data.value.items.length === 0 && (
        <output className={`${NOTICE} mt-4`}>予約はありません</output>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>予約の一覧</caption>
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
              {data.value.items.map((item) => (
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
                  <td className={TD}>{bookingStatusLabel(item.bookingStatus)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
