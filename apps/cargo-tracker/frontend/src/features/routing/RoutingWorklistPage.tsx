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
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { display } from '@/features/shippers/api';
import {
  bookingStatusLabel,
  cargoTypeLabel,
  type BookingView,
  type CargoType,
} from '@/features/bookings/api';

/**
 * Booking の種別 → Routing の受入種別。<b>全数の対応表にする。</b>
 * 「知らないものはそのまま渡す」形にすると、種別が増えたときにその値が
 * routingms へ渡り「知らない貨物種別です」で断られる。表にしておけば、
 * 増やした瞬間に型で落ちる。
 */
const VOYAGE_CARGO_TYPE: Record<CargoType, string> = {
  GENERAL: 'GENERAL',
  HAZARDOUS: 'HAZARDOUS',
  REFRIGERATED: 'REEFER',
};
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
/**
 * 予約の貨物種別を航海の受入種別に翻訳する。
 *
 * <p>Booking の CargoType と Routing の CargoType は<b>別の型</b>で、冷凍の名前が
 * 違う（domain-model.md）。写さずに渡すと、冷凍の予約から探した航海一覧が
 * 「知らない貨物種別です」で断られる。</p>
 */
function voyageCargoTypeOf(cargoType: string): string {
  return VOYAGE_CARGO_TYPE[cargoType as CargoType] ?? cargoType;
}

/**
 * 予約の条件をそのまま航海の検索条件にする。
 *
 * <p>種別だけを引き継ぐと、経路設計者は行に出ている出発地・目的地・期限を毎回
 * 打ち直すことになる。打ち間違えれば 0 件が出て「航海が無い」と読む。</p>
 *
 * <p>到着期限は「その日までに出る便」の上限として渡す。期限より後に出る便では
 * 間に合わない。</p>
 */
function voyageSearchQuery(item: BookingView): string {
  return new URLSearchParams({
    cargoType: voyageCargoTypeOf(item.cargoType),
    departure: item.originUnLocode,
    arrival: item.destinationUnLocode,
    departTo: item.arrivalDeadline,
  }).toString();
}

export function RoutingWorklistPage() {
  const [includeRouted, setIncludeRouted] = useState(false);
  const { data, isPending, isError } = useQuery({
    queryKey: ['routing-worklist', includeRouted],
    queryFn: () => fetchRoutingWorklist(includeRouted),
    refetchInterval: 3000,
  });

  const items = data?.state === 'ready' ? data.value.items : [];

  return (
    <section>
      <h1 className={PAGE_TITLE}>経路設計作業一覧</h1>

      <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeRouted}
          onChange={(event) => setIncludeRouted(event.target.checked)}
        />
        {/* この一覧が扱うのは同じ依頼の中の設計済み。経路を確定して荷主に
            通知した予約（ROUTE_NOTIFIED）はこの一覧を離れる。 */}
        {'同じ依頼で設計済みのものも表示'}
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

      {/* 上限で切れていることを黙らない。無音で切れると、載らなかった予約は
          誰の目にも入らないまま残る（S32 と同じ扱いにする）。 */}
      {data?.state === 'ready' && data.value.total > items.length && (
        <output className={`${NOTICE} mt-4 block`}>
          {data.value.total} 件のうち {items.length} 件を表示しています
        </output>
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
                {/* 一覧は到着期限が近い順なので、期限が遠い案件は下に沈む。
                    引き渡しからどれだけ経ったかが読めないと放置に気づけない。 */}
                <th scope="col" className={TH}>引き渡し</th>
                <th scope="col" className={TH}>貨物</th>
                <th scope="col" className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.bookingId}>
                  <td className={TD}>
                    {/* 経路設計者の作業はここから始まる。予約詳細（S22）ではなく
                        経路設計ワークベンチ（S31）を開く。IT4 までは S31 が
                        無かったので S22 を開いていた。 */}
                    <Link to={`/routing/bookings/${item.bookingId}`} className={LINK}>
                      {item.bookingNumber}
                    </Link>
                  </td>
                  <td className={TD}>{display(item.shipperName)}</td>
                  <td className={TD}>
                    {item.originUnLocode} → {item.destinationUnLocode}
                  </td>
                  <td className={TD}>{item.arrivalDeadline}</td>
                  <td className={TD} data-testid={`routing-requested-at-${item.bookingId}`}>
                    {item.routingRequestedAt
                      ? formatBusinessDateTime(item.routingRequestedAt)
                      : '—'}
                  </td>
                  <td className={TD}>
                    {item.productName}（{cargoTypeLabel(item.cargoType)}）
                    {/* 種別を引き継いで航海を探す。引き継がないとここで選び直す
                        ことになり、選び忘れれば対応しない航海まで候補に見える。
                        危険物・冷凍は Booking と Routing で呼び名が違う
                        （REFRIGERATED / REEFER）ので、ここで翻訳する。 */}
                    <Link to={`/voyages?${voyageSearchQuery(item)}`} className={`${LINK} ml-2`}>
                      対応する航海を探す
                    </Link>
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
