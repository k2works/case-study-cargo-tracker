import { Link } from 'react-router';
import { QueryState } from '@/components/ui/QueryState';
import { useShipper } from '../api/hooks';

/** SC-22 荷主詳細。 */
export function ShipperDetail({ shipperId }: { shipperId: string }) {
  const q = useShipper(shipperId);
  const s = q.data;
  return (
    <QueryState isPending={q.isPending} error={q.error}>
      {s && (
        <dl>
          <dt>氏名または社名</dt>
          <dd>{s.name}</dd>
          <dt>住所</dt>
          <dd>{s.address}</dd>
          <dt>連絡先</dt>
          <dd>{s.contact}</dd>
          <dt>メールアドレス</dt>
          <dd>{s.email}</dd>
        </dl>
      )}
      {s && (
        <p>
          <Link to={`/bookings/new?shipperId=${encodeURIComponent(shipperId)}`}>この荷主で予約を登録</Link>
        </p>
      )}
    </QueryState>
  );
}
