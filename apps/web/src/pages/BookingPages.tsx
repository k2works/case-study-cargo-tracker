import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { canOpen } from '@/features/auth';
import { useCurrentUser } from '@/providers/AuthProvider';
import { BookingDetail, BookingList, BookingRegisterForm, ShipperDetail, ShipperList, ShipperRegisterForm } from '@/features/booking';

/** pages は feature を組み合わせるだけ（F1：feature 同士は import せず、合成は pages で行う）。 */

export const ShippersPage = () => (
  <main>
    <h1>荷主</h1>
    <p><Link to="/shippers/new">荷主を登録</Link></p>
    <ShipperList />
  </main>
);

export function ShipperNewPage() {
  const navigate = useNavigate();
  return (
    <main>
      <h1>荷主の登録</h1>
      <ShipperRegisterForm onRegistered={(id) => navigate(`/shippers/${id}`)} onUseExisting={(id) => navigate(`/shippers/${id}`)} />
    </main>
  );
}

export function ShipperDetailPage() {
  const { id = '' } = useParams();
  return (
    <main>
      <h1>荷主の詳細</h1>
      <ShipperDetail shipperId={id} />
    </main>
  );
}

export function BookingsPage() {
  const user = useCurrentUser();
  return (
    <main>
      <h1>予約</h1>
      {user && canOpen('/bookings/new', user.role) && (
        <p>
          <Link to="/bookings/new">予約を登録</Link>
        </p>
      )}
      <BookingList />
    </main>
  );
}

export function BookingNewPage() {
  const [params] = useSearchParams();
  return (
    <main>
      <h1>予約の登録</h1>
      <BookingRegisterForm initialShipperId={params.get('shipperId') ?? ''} />
    </main>
  );
}

export function BookingDetailPage() {
  const { id = '' } = useParams();
  return (
    <main>
      <h1>予約の詳細</h1>
      <BookingDetail bookingId={id} />
    </main>
  );
}
