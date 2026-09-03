import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { display, fetchShippers } from './api';

/** S10 荷主一覧（UC02）。 */
export function ShipperListPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['shippers'],
    queryFn: fetchShippers,
    // 投影は非同期なので、登録直後は数秒ぶん遅れる。定期に取り直す。
    refetchInterval: 3000,
  });

  return (
    <section>
      <h1>荷主一覧</h1>
      <p>
        <Link to="/shippers/new">荷主を登録する</Link>
      </p>

      {isPending && <output>読み込み中…</output>}
      {isError && <p role="alert">一覧を取得できませんでした</p>}

      {data?.state === 'pending' && <output>{data.message}</output>}

      {data?.state === 'ready' && (
        <table>
          <caption>登録済みの荷主</caption>
          <thead>
            <tr>
              <th scope="col">荷主コード</th>
              <th scope="col">名称</th>
              <th scope="col">種別</th>
              <th scope="col">メールアドレス</th>
            </tr>
          </thead>
          <tbody>
            {data.value.items.map((shipper) => (
              <tr key={shipper.shipperId}>
                <td>{shipper.shipperCode}</td>
                <td>{display(shipper.name)}</td>
                <td>{shipper.shipperType === 'CORPORATE' ? '法人' : '個人'}</td>
                <td>{display(shipper.email)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
