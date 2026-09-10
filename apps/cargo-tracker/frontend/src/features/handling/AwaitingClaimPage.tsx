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
  // **現場の仕事の単位は「今日渡せる貨物」**（IT12 レビュー 高）。既定は全件
  // ——通関がまだのものも見えないと、いつ渡せるのかが分からない。
  const [clearedOnly, setClearedOnly] = useState(false);

  const ports = useQuery({
    queryKey: ['handling-voyage-ports'],
    queryFn: fetchVoyagePorts,
  });

  const cargos = useQuery({
    queryKey: ['handling-awaiting-claim', unLocode, clearedOnly],
    queryFn: () => fetchAwaitingClaim(unLocode, clearedOnly),
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
      {/* **US29 で通関が読めるようになった。** IT11 まではここに「この画面では
          分かりません。荷主に確かめてください」と出していたが、いまは通関済で
          なければ引取そのものが断られる（`HandlingActivity#requireCustomsCleared`）。
          「分かりません」と言い続けると、確かめる先を間違えたまま窓口で待たせる。 */}
      <output className={`${NOTICE} mt-3 block`}>
        <strong>通関が済んでいない貨物は引取を記録できません。</strong>{' '}
        各行に通関状態が出ます。「今日渡せる貨物」だけを見るなら、下の絞り込みを使ってください。
      </output>

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

        <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={clearedOnly}
            onChange={(event) => setClearedOnly(event.target.checked)}
          />
          <span>通関済のものだけ表示</span>
        </label>
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
                  <th className={TH}>通関状態</th>
                  <th className={TH}>操作</th>
                  <th className={TH}>通関</th>
                  <th className={TH}>履歴</th>
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
                      {/* **「無い」と「審査中」は違う。** 前者はまだ申告して
                          いないので、荷役作業員が登録から始める。 */}
                      {item.customsStatusLabel ?? '申告なし'}
                      {!item.claimable && (
                        <span className="ml-2 rounded bg-amber-100 px-2 py-0.5 text-xs
                          font-semibold text-amber-800">
                          渡せません
                        </span>
                      )}
                    </td>
                    <td className={TD}>
                      {/* **気づく手段は次の行動へ繋ぐ。** 一覧で終わると、作業員は
                          追跡番号を書き写し、その貨物が乗っていた航海を思い出して
                          S50 を選び直すことになる——荷受人を窓口で待たせたまま。 */}
                      {/* **押せるのに断られる操作を並べない**（IT12 レビュー 高）。
                          通関が済んでいなければ集約が断るので、入口を出さない。 */}
                      {item.claimable ? (
                        <Link
                          to={`/handling/claim?unLocode=${encodeURIComponent(unLocode)}`
                            + `&trackingNumber=${encodeURIComponent(item.trackingNumber)}`}
                          className={LINK}
                        >
                          引取を記録
                        </Link>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className={TD}>
                      {/* **断られる前に確かめられるようにする。** 追跡番号で
                          絞った通関申告一覧へ送る（通関済も含めて出る）。 */}
                      <Link
                        to={'/customs?trackingNumber='
                          + encodeURIComponent(item.trackingNumber)}
                        className={LINK}
                      >
                        通関を確かめる
                      </Link>
                    </td>
                    <td className={TD}>
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
