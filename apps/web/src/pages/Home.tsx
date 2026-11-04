import { Link } from 'react-router';
import { useBookings } from '@/features/booking';
import { canOpen } from '@/features/auth';
import { useCurrentUser } from '@/providers/AuthProvider';

/** 作業入口（P2・U1）：件数は、絞り込み済みの一覧へのリンク。0 件でも行を残す。 */
function ReceivedBookings() {
  const received = useBookings({ status: 'RECEIVED' });
  const count = received.data?.length;
  return (
    <li>
      <Link to="/bookings?status=RECEIVED">仮受付の予約 {count === undefined ? '…' : `${count} 件`}</Link>
    </li>
  );
}

export function Home() {
  const user = useCurrentUser();
  const canViewBookings = !!user && canOpen('/bookings', user.role);
  const canRegister = !!user && canOpen('/bookings/new', user.role);
  return (
    <main>
      <h1>ホーム</h1>
      {canViewBookings ? (
        <ul>
          <ReceivedBookings />
          {canRegister && (
            <>
              <li>
                <Link to="/shippers/new">荷主を登録する</Link>
              </li>
              <li>
                <Link to="/bookings/new">予約を登録する</Link>
              </li>
            </>
          )}
        </ul>
      ) : (
        <p>このロールの画面は、後の Bolt で提供します。</p>
      )}
    </main>
  );
}
