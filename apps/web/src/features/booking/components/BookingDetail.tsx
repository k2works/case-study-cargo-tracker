import { QueryState } from '@/components/ui/QueryState';
import { useBooking } from '../api/hooks';
import { historyLabel, statusLabel } from '../status';

/** SC-27 予約詳細。「実行できる操作」は、サーバー（ドメインの述語）が返した値をそのまま使う。画面で状態を再判定しない。 */
export function BookingDetail({ bookingId }: { bookingId: string }) {
  const q = useBooking(bookingId);
  const b = q.data;
  return (
    <QueryState isPending={q.isPending} error={q.error}>
      {b && (
        <article>
          <h2>予約 {b.number}</h2>
          <dl>
            <dt>状態</dt>
            <dd>{statusLabel(b.status)}</dd>
            <dt>区間</dt>
            <dd>
              {b.origin} → {b.destination}
            </dd>
            <dt>希望引渡日</dt>
            <dd>{b.preferredHandover}</dd>
            <dt>希望期限</dt>
            <dd>{b.deadline}</dd>
            <dt>品名</dt>
            <dd>{b.cargo.itemName}</dd>
            <dt>重量（kg）</dt>
            <dd>{b.cargo.weightKg}</dd>
            <dt>個数</dt>
            <dd>{b.cargo.quantity}</dd>
          </dl>
          <h3>操作</h3>
          {b.availableCommands.length === 0 ? (
            <p>現在、実行できる操作はありません</p>
          ) : (
            <ul>
              {b.availableCommands.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
          )}
          <table aria-label="履歴">
            <thead>
              <tr>
                <th scope="col">種類</th>
                <th scope="col">日時</th>
                <th scope="col">実行者</th>
              </tr>
            </thead>
            <tbody>
              {b.history.map((h) => (
                <tr key={h.eventId}>
                  <td>{historyLabel(h.kind)}</td>
                  <td>{h.occurredAt}</td>
                  <td>{h.actorUserId ?? 'システム'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>
      )}
    </QueryState>
  );
}
