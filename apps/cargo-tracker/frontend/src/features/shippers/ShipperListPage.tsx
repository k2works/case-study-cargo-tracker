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
import { display, fetchShippers } from './api';

/** S10 荷主一覧（UC02）。 */
export function ShipperListPage() {
  // 登録直後は投影がまだなので、自分が入れた荷主が一覧に無い。何も出さないと
  // 「登録できていない」と判断して二重に入力される（ui_design.md S10 の salt）。
  const justRegistered =
    (useLocation().state as { justRegistered?: boolean } | null)?.justRegistered === true;
  const { data, isPending, isError } = useQuery({
    queryKey: ['shippers'],
    queryFn: fetchShippers,
    // 投影は非同期なので、登録直後は数秒ぶん遅れる。定期に取り直す。
    refetchInterval: 3000,
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>荷主一覧</h1>
      <p className="mt-2 text-sm">
        <Link to="/shippers/new" className={LINK}>
          荷主を登録する
        </Link>
      </p>

      {/* 上限で切れていることを黙らない。載らなかった荷主は、予約登録の
          選択肢にも出ないので、その日から予約が取れなくなる。 */}
      {data?.state === 'ready' && data.value.total > data.value.items.length && (
        <output className={`${NOTICE} mt-4 block`}>
          {data.value.total} 件のうち {data.value.items.length} 件を表示しています。
          絞り込みは次のイテレーションで入ります
        </output>
      )}

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

      {/* 見出しだけの表を出すと「読み込みに失敗した」と受け取られる。
          0 件であることを文で言う。 */}
      {data?.state === 'ready' && data.value.items.length === 0 && (
        <output className={`${NOTICE} mt-4`}>
          登録済みの荷主はまだありません。
        </output>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        // 画面幅に収まらない表は、ページ全体でなくこの中だけを横に流す。
        // ページごと横スクロールすると、ナビや見出しまで隠れる。
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>登録済みの荷主</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>
                  荷主コード
                </th>
                <th scope="col" className={TH}>
                  名称
                </th>
                <th scope="col" className={TH}>
                  種別
                </th>
                <th scope="col" className={TH}>
                  メールアドレス
                </th>
              </tr>
            </thead>
            <tbody>
              {data.value.items.map((shipper) => (
                <tr key={shipper.shipperId}>
                  <td className={TD}>{shipper.shipperCode}</td>
                  <td className={TD}>{display(shipper.name)}</td>
                  <td className={TD}>{shipper.shipperType === 'CORPORATE' ? '法人' : '個人'}</td>
                  <td className={TD}>{display(shipper.email)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
