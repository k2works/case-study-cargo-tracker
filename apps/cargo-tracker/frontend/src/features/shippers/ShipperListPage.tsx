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

/**
 * 登録直後に一覧へ差し込む行（ui_design.md S11「登録後は S10 に反映中の行を差し込む」）。
 *
 * <p>投影が追いつくまでの数秒だけでなく、<b>一覧が上限で切れているとき</b>にも要る。
 * 荷主コード順に並ぶので、新しく採った荷主ほど後ろに回り、件数が上限を超えると
 * 1 ページ目には決して出ない（実測 219 件 / 表示 200 件）。</p>
 */
export interface JustRegistered {
  readonly name: string;
  readonly email: string;
  readonly shipperType: 'CORPORATE' | 'INDIVIDUAL';
}

/** S10 荷主一覧（UC02）。 */
export function ShipperListPage() {
  // 登録直後は投影がまだなので、自分が入れた荷主が一覧に無い。何も出さないと
  // 「登録できていない」と判断して二重に入力される（ui_design.md S10 の salt）。
  const justRegistered = (useLocation().state as { justRegistered?: JustRegistered } | null)
    ?.justRegistered;
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

      {justRegistered !== undefined && (
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
      {data?.state === 'ready' && data.value.items.length === 0
        && justRegistered === undefined && (
        <output className={`${NOTICE} mt-4`}>
          登録済みの荷主はまだありません。
        </output>
      )}

      {data?.state === 'ready'
        && (data.value.items.length > 0 || justRegistered !== undefined) && (
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
              {/* **反映中の行を差し込む**（ui_design.md S11）。上限で切れた一覧では、
                  登録した荷主がどこにも出ない。案内文だけだと営業は「登録できて
                  いない」と判断して二重に入力する。投影が追いつけば消える。 */}
              {justRegistered !== undefined
                && !data.value.items.some((shipper) => shipper.email === justRegistered.email) && (
                <tr>
                  <td className={TD}>反映中</td>
                  <td className={TD}>{justRegistered.name}</td>
                  <td className={TD}>
                    {justRegistered.shipperType === 'CORPORATE' ? '法人' : '個人'}
                  </td>
                  <td className={TD}>{justRegistered.email}</td>
                </tr>
              )}
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
