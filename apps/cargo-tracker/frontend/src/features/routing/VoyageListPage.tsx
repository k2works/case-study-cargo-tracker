import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router';
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
import { acceptedCargoTypeLabel, fetchVoyages, formatVoyageTime } from './api';

/**
 * S32 航海スケジュール一覧（UC19）。
 *
 * <p>既定では出港済みとキャンセルを外し、出発日が近い順に並べる（ui_design.md）。
 * 出港してしまった便が混ざると、一覧全体が「これから使える航海」として
 * 信用されなくなる。</p>
 *
 * <p>検索条件（出発地・目的地・期間）は US07（IT4）で入れる。ここに先に置くと
 * 二度手間になる。</p>
 */
export function VoyageListPage() {
  const [includeFinished, setIncludeFinished] = useState(false);
  const justRegistered =
    (useLocation().state as { justRegistered?: boolean } | null)?.justRegistered === true;
  const { data, isPending, isError } = useQuery({
    queryKey: ['voyages', includeFinished],
    queryFn: () => fetchVoyages(includeFinished),
    // 投影は非同期なので、登録直後は数秒ぶん遅れる。定期に取り直す。
    refetchInterval: 3000,
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>航海スケジュール一覧</h1>
      <p className="mt-2 text-sm">
        <Link to="/voyages/new" className={LINK}>
          航海を登録する
        </Link>
      </p>

      <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeFinished}
          onChange={(event) => setIncludeFinished(event.target.checked)}
        />
        {'出港済み・キャンセルも表示'}
      </label>

      {justRegistered && (
        <output className={`${NOTICE} mt-4 block`}>
          登録を受け付けました。反映までしばらくお待ちください
        </output>
      )}

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          一覧を取得できませんでした
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {/* 見出しだけの表を出すと「読み込みに失敗した」と受け取られる。 */}
      {data?.state === 'ready' && data.value.items.length === 0 && (
        <output className={`${NOTICE} mt-4`}>航海はありません</output>
      )}

      {/* 上限で切れていることを黙らない。無音で切れると、載らなかった航海は
          誰の目にも入らないまま残る。 */}
      {data?.state === 'ready' && data.value.total > data.value.items.length && (
        <output className={`${NOTICE} mt-4 block`}>
          {/* 内部のストーリー ID は画面に出さない。利用者には意味が無く、
              「US07 とは何か」という問い合わせになる。 */}
          {data.value.total} 件のうち {data.value.items.length} 件を表示しています。
          条件を指定した絞り込みは今後追加されます
        </output>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>航海スケジュールの一覧</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>航海番号</th>
                <th scope="col" className={TH}>運送会社</th>
                <th scope="col" className={TH}>船名</th>
                <th scope="col" className={TH}>出発地 → 到着地</th>
                <th scope="col" className={TH}>出発</th>
                <th scope="col" className={TH}>到着</th>
                <th scope="col" className={TH}>対応貨物</th>
                <th scope="col" className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {data.value.items.map((item) => (
                <tr key={item.voyageNumber}>
                  <td className={TD}>{item.voyageNumber}</td>
                  <td className={TD}>{item.carrierName}</td>
                  <td className={TD}>{item.vesselName}</td>
                  <td className={TD}>
                    {item.departureUnLocode} → {item.arrivalUnLocode}
                  </td>
                  <td className={TD}>{formatVoyageTime(item.departureAt)}</td>
                  <td className={TD}>{formatVoyageTime(item.arrivalAt)}</td>
                  <td className={TD}>
                    {item.acceptedCargoTypes.map(acceptedCargoTypeLabel).join(' / ')}
                  </td>
                  <td className={TD}>{item.cancelled ? 'キャンセル' : '予定'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
