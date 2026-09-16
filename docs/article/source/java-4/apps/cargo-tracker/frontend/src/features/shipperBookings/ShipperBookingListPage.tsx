import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { bookingStatusLabel } from '@/features/bookings/api';
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
import { fetchShipperBookings } from './api';

/** 一覧は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * S45 自社予約一覧（荷主 / UC15）。
 *
 * <p><b>荷主の毎日の入口である。</b> 追跡番号は予約確定後に発行されるので、
 * 予約から確定までの数日間、荷主には追跡（S40・S41）で見えるものが何も無い。
 * その期間を埋めるのがこの画面と S46 である。</p>
 *
 * <p><b>金額は出さない。</b> 金額を出す荷主向けの画面は S62 だけである
 * （ui_design.md）。サーバも渡さないので、画面が間違えても漏れない。</p>
 *
 * <p><b>絞るのはサーバ。</b> 荷主 ID は Gateway が JWT から取り出して載せる。
 * 画面が捨てる形にすると、応答には他社の予約が乗ったままになる。</p>
 *
 * <p><b>既定では精算済・キャンセルを外す</b>（ui_design.md「一覧の既定条件」）。
 * 並びは到着期限が近い順——荷主が知りたいのは「いつ着くか」である。</p>
 */
export function ShipperBookingListPage() {
  const [includeFinished, setIncludeFinished] = useState(false);

  const bookings = useQuery({
    queryKey: ['shipper-bookings', includeFinished],
    queryFn: () => fetchShipperBookings(includeFinished),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const ready = bookings.data?.state === 'ready' ? bookings.data.value : null;
  const items = ready?.items ?? [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>自社の予約</h1>

      <label className="mt-4 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeFinished}
          onChange={(event) => setIncludeFinished(event.target.checked)}
        />
        <span>終了したものも表示</span>
      </label>

      {bookings.isError && (
        <output className={`${ALERT} mt-4`}>予約の一覧を取得できませんでした。</output>
      )}

      {/* **上限で切れていることを黙らない。** 出ていない予約は「無い」と読まれる。 */}
      {ready !== null && ready.total > ready.items.length && (
        <output className={`${NOTICE} mt-4 block`}>
          {ready.total} 件のうち {ready.items.length} 件を表示しています。到着期限が近い順です
        </output>
      )}

      <div className={`${CARD} mt-4 overflow-x-auto`}>
        {items.length === 0 ? (
          <p className="text-sm text-gray-600">
            予約はありません。お申し込みいただくと、ここに並びます。
          </p>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>自社予約一覧</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>予約番号</th>
                <th scope="col" className={TH}>品名</th>
                <th scope="col" className={TH}>区間</th>
                <th scope="col" className={TH}>到着期限</th>
                <th scope="col" className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {items.map((booking) => (
                <tr key={booking.bookingId}>
                  <td className={TD}>
                    <Link to={`/shipper/bookings/${booking.bookingId}`} className={LINK}>
                      {booking.bookingNumber}
                    </Link>
                  </td>
                  <td className={TD}>{booking.productName}</td>
                  <td className={TD}>
                    {booking.originUnLocode} → {booking.destinationUnLocode}
                  </td>
                  <td className={TD}>{booking.arrivalDeadline}</td>
                  <td className={TD}>{bookingStatusLabel(booking.bookingStatus)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
