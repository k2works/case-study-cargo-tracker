import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
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
import { fetchAttentionItems } from './api';

/**
 * S70 要確認一覧。
 *
 * <p>投影が弾いたもの・連鎖が補償に至ったものを出す。件数を出すだけでは仕事が
 * 進まないので、対象へ行ける導線を必ず添える。</p>
 */
export function AttentionListPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['attention-items'],
    queryFn: fetchAttentionItems,
    refetchInterval: 5000,
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>要確認一覧</h1>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          要確認一覧を取得できませんでした
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {/* 「無い」は良い知らせなので、失敗と同じ見た目にしない。 */}
      {data?.state === 'ready' && data.value.items.length === 0 && (
        <output
          className={
            'mt-4 block rounded border border-green-300 bg-green-50 px-4 py-3'
            + ' text-sm text-green-800'
          }
        >
          確認が必要なものはありません
        </output>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        <>
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>確認が必要なもの</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>
                  発生日時
                </th>
                <th scope="col" className={TH}>
                  理由
                </th>
                <th scope="col" className={TH}>
                  対象
                </th>
                <th scope="col" className={TH}>
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              {data.value.items.map((item) => (
                <tr key={item.itemId}>
                  <td className={`${TD} whitespace-nowrap`}>
                    {formatBusinessDateTime(item.occurredAt)}
                  </td>
                  <td className={TD}>{item.reason}</td>
                  <td className={`${TD} font-mono`}>{item.targetId}</td>
                  <td className={TD}>
                    {/* **気づく手段で終わらせず、次の行動へ繋ぐ。** ただし「次の行動」は
                        対象によって違う。荷主の重複なら既存を使えば済むが、予約の項目
                        （連鎖の補償・投影の弾き）で開くべきなのはその予約である。
                        一律に荷主のリンクを出すと、経路設計者は追跡番号を発行し直す
                        入口にたどり着けない（IT7 クローズの自己レビュー）。 */}
                    <div className="flex flex-col gap-1">
                      {item.targetType === 'BOOKING' ? (
                        <Link to={`/bookings/${item.targetId}`} className={LINK}>
                          予約を開く
                        </Link>
                      ) : (
                        <>
                          {item.relatedShipperId !== null && (
                            <Link to="/shippers" className={LINK}>
                              既存の荷主を見る
                            </Link>
                          )}
                          <Link to="/shippers/new" className={LINK}>
                            修正して再登録する
                          </Link>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {/* なぜ空のフォームが開くのかを言う。黙って空だと「消えた」と受け取られる。
            **荷主の項目があるときだけ出す**——予約の項目しか無い人に「再登録」の
            説明を読ませても、その人の仕事とは関係がない。 */}
        {data.value.items.some((item) => item.targetType !== 'BOOKING') && (
        <p className="mt-3 text-sm text-gray-600">
          「修正して再登録する」は空のフォームを開きます。受け付けた内容には個人情報が含まれるため、
          鍵を破棄したときに消えない場所へ写していません。お手元の資料をご用意ください。
        </p>
        )}
        </>
      )}
    </section>
  );
}
