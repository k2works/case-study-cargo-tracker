import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
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
      <h1>要確認一覧</h1>

      {isPending && <p role="status">読み込み中…</p>}
      {isError && <p role="alert">要確認一覧を取得できませんでした</p>}
      {data?.state === 'pending' && <p role="status">{data.message}</p>}

      {data?.state === 'ready' && data.value.items.length === 0 && (
        <p role="status">確認が必要なものはありません</p>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        <table>
          <caption>確認が必要なもの</caption>
          <thead>
            <tr>
              <th scope="col">発生日時</th>
              <th scope="col">理由</th>
              <th scope="col">対象</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {data.value.items.map((item) => (
              <tr key={item.itemId}>
                <td>{formatBusinessDateTime(item.occurredAt)}</td>
                <td>{item.reason}</td>
                <td>{item.targetId}</td>
                <td>
                  {/* 気づく手段で終わらせず、次の行動へ繋ぐ。 */}
                  <Link to="/shippers/new">修正して再登録する</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
