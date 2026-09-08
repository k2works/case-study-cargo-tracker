import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import {
  CARD,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { cargoTypeLabel } from '@/features/bookings/api';
import { fetchAwaitingClaim, fetchVoyagePorts } from './api';

/**
 * 引取待ち（H.8 / US16）。**荷役作業員の 2 つ目の入口**。
 *
 * <p><b>航海起点（S50）では辿り着けない。</b> 引取は船から降りたあとの作業で、
 * どの航海の仕事でもない——目的港で荷降しが済み、引取がまだの貨物を港で数える。</p>
 *
 * <p><b>港は選ぶ。</b> 利用者の所属から既定を決める仕組みが無いので、
 * 作業のある航海の港から選ばせる（勝手に 1 つ目を選ぶと、別の港に居る人が
 * 自分の港だと思って読む）。</p>
 */
export function AwaitingClaimPage() {
  const [unLocode, setUnLocode] = useState('');

  const ports = useQuery({
    queryKey: ['handling-voyage-ports'],
    queryFn: fetchVoyagePorts,
  });

  const cargos = useQuery({
    queryKey: ['handling-awaiting-claim', unLocode],
    queryFn: () => fetchAwaitingClaim(unLocode),
    enabled: unLocode !== '',
  });

  // 同じ港が複数の航海に出るので、港だけにまとめる。
  const portOptions = ports.data?.state === 'ready'
    // **並べ方を明示する。** 既定の sort は実装に依存し、環境で並びが変わる。
    ? Array.from(new Set(ports.data.value.items.map((item) => item.unLocode)))
      .sort((a, b) => a.localeCompare(b))
    : [];
  const items = cargos.data?.state === 'ready' ? cargos.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>引取待ち</h1>
      <p className="mt-1 text-sm text-gray-600">
        目的港で荷降しが済み、荷受人の引取がまだの貨物です。
      </p>

      <section className={`${CARD} mt-4`}>
        <label htmlFor="unLocode" className={LABEL}>
          港
        </label>
        <select
          id="unLocode"
          className={FIELD}
          value={unLocode}
          onChange={(event) => setUnLocode(event.target.value)}
        >
          <option value="">選んでください</option>
          {portOptions.map((port) => (
            <option key={port} value={port}>
              {port}
            </option>
          ))}
        </select>
      </section>

      {unLocode === '' ? (
        <output className={`${NOTICE} mt-4 block`}>
          港を選んでください。どの港に居るかが決まらないと、引取待ちの貨物を出せません。
        </output>
      ) : (
        <section className={`${CARD} mt-4 overflow-x-auto`}>
          {items.length === 0 ? (
            <p className="text-sm text-gray-600">
              {unLocode} で引取を待っている貨物はありません。
            </p>
          ) : (
            <table className={TABLE}>
              <caption className={TABLE_CAPTION}>引取待ちの貨物</caption>
              <thead>
                <tr>
                  <th className={TH}>追跡番号</th>
                  <th className={TH}>区間</th>
                  <th className={TH}>貨物種別</th>
                  <th className={TH}>操作</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.trackingNumber}>
                    <td className={TD}>{item.trackingNumber}</td>
                    <td className={TD}>
                      {item.originUnLocode} → {item.destinationUnLocode}
                    </td>
                    <td className={TD}>{cargoTypeLabel(item.cargoType)}</td>
                    <td className={TD}>
                      {/* **気づく手段は次の行動へ繋ぐ。** 一覧で終わると、
                          作業員は追跡番号を書き写して S50 を探し直す。 */}
                      <Link
                        to={`/handling/${item.trackingNumber}`}
                        className={LINK}
                      >
                        履歴を見る
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
