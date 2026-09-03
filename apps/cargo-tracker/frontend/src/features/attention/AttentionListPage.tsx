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
                    {/* 気づく手段で終わらせず、次の行動へ繋ぐ。 */}
                    <Link to="/shippers/new" className={LINK}>
                      修正して再登録する
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
