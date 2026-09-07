import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import {
  ALERT,
  CARD,
  LINK,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { fetchTrackings } from './api';

/** 一覧は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 追跡一覧（S40 / UC15）。**追跡管理者と荷主の両方が使う**。
 *
 * <p><b>荷主には自社のぶんだけが出る。</b> 絞るのはサーバで、ヘッダの荷主 ID
 * （Gateway が JWT から取り出す）で行う。画面が絞ると、絞り忘れが情報漏れになる。</p>
 *
 * <p><b>既定では引取済を含めない。</b> 引き取られた貨物が混ざると、一覧全体が
 * 「いま追うもの」として信用されなくなる（ui_design.md「一覧の既定条件」）。</p>
 */
export function TrackingListPage() {
  const [includeDelivered, setIncludeDelivered] = useState(false);

  const trackings = useQuery({
    queryKey: ['trackings', includeDelivered],
    queryFn: () => fetchTrackings(includeDelivered),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = trackings.data?.state === 'ready' ? trackings.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>追跡</h1>

      <label className="mt-4 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeDelivered}
          onChange={(event) => setIncludeDelivered(event.target.checked)}
        />
        <span>引取済も表示</span>
      </label>

      {trackings.isError && (
        <output className={`${ALERT} mt-4`}>追跡の一覧を取得できませんでした。</output>
      )}

      <div className={`${CARD} mt-4 overflow-x-auto`}>
        {items.length === 0 ? (
          <p className="text-sm text-gray-600">
            追跡はありません。予約が確定して追跡番号が発行されると、ここに並びます。
          </p>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>追跡一覧</caption>
            <thead>
              <tr>
                <th className={TH}>追跡番号</th>
                <th className={TH}>状態</th>
                <th className={TH}>区間</th>
                <th className={TH}>現在</th>
                <th className={TH}>到着予定</th>
                <th className={TH}>最終更新</th>
              </tr>
            </thead>
            <tbody>
              {items.map((tracking) => (
                <tr key={tracking.trackingNumber}>
                  <td className={TD}>
                    <Link to={`/tracking/${tracking.trackingNumber}`} className={LINK}>
                      {tracking.trackingNumber}
                    </Link>
                  </td>
                  <td className={TD}>{tracking.statusLabel}</td>
                  <td className={TD}>
                    {tracking.originUnLocode} → {tracking.destinationUnLocode}
                  </td>
                  <td className={TD}>{tracking.currentUnLocode ?? '—'}</td>
                  <td className={TD}>
                    {tracking.estimatedArrival === null
                      ? '—'
                      : formatBusinessDateTime(tracking.estimatedArrival)}
                  </td>
                  <td className={TD}>
                    {formatBusinessDateTime(tracking.lastStatusChangedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
