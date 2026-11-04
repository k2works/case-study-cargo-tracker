import { Link, useSearchParams } from 'react-router';
import { QueryState } from '@/components/ui/QueryState';
import { useBookings } from '../api/hooks';
import { BOOKING_STATUSES, statusLabel } from '../status';

/** SC-25 予約一覧。状態の絞り込みは URL に残す（作業入口の件数のリンクと、一覧の件数が一致する。U2）。 */
export function BookingList() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const shipperId = params.get('shipperId') ?? '';
  const bookings = useBookings({ status, shipperId });

  const changeStatus = (value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set('status', value);
    else next.delete('status');
    setParams(next);
  };

  return (
    <section>
      <div className="field">
        <label htmlFor="status">状態</label>
        <select id="status" value={status} onChange={(e) => changeStatus(e.target.value)}>
          <option value="">すべて</option>
          {BOOKING_STATUSES.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
      </div>
      <QueryState isPending={bookings.isPending} error={bookings.error}>
        {bookings.data && bookings.data.length === 0 ? (
          <p>該当する予約はありません</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th scope="col">予約番号</th>
                <th scope="col">状態</th>
                <th scope="col">区間</th>
                <th scope="col">希望期限</th>
              </tr>
            </thead>
            <tbody>
              {bookings.data?.map((b) => (
                <tr key={b.id}>
                  <td>
                    <Link to={`/bookings/${b.id}`}>{b.number}</Link>
                  </td>
                  <td>{statusLabel(b.status)}</td>
                  <td>
                    {b.origin} → {b.destination}
                  </td>
                  <td>{b.deadline}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </QueryState>
    </section>
  );
}
