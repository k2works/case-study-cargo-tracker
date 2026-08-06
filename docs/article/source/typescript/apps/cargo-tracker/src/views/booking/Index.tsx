import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CARGO_TYPE_LABELS, type CargoType } from '../../shared/domain/model/cargo-type.js';
import { BOOKING_STATUS_LABELS, type BookingStatus } from '../../contexts/booking/domain/model/booking-status.js';
import type { BookingListItem } from '../../contexts/booking/application/queryservices/booking-query.service.js';
import { hasAnyRole } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Role } from '../../shared/domain/model/role.js';

interface IndexBookingProps {
  user: AuthenticatedUser;
  bookings: BookingListItem[];
  routingWaiting?: boolean;
}

/**
 * 貨物予約一覧（/bookings）。US04。
 * 経路設計者は「経路設計待ち」フィルタで到達する（?status=ROUTING_IN_PROGRESS）。
 */
export function IndexBooking({ user, bookings, routingWaiting }: IndexBookingProps): ReactElement {
  const canCreate = hasAnyRole(user, [Role.SALES]);
  return (
    <Layout title="貨物予約一覧" user={user} activePath="/bookings">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3" data-testid="booking-index-heading">
          {routingWaiting ? '経路設計待ち予約' : '貨物予約一覧'}
        </h1>
        {canCreate && (
          <a href="/bookings/new" className="btn btn-primary" data-testid="booking-new-link">
            予約登録
          </a>
        )}
      </div>
      {bookings.length === 0 ? (
        <p className="text-muted" data-testid="booking-empty">予約はまだありません。</p>
      ) : (
        <table className="table" data-testid="booking-list">
          <thead>
            <tr>
              <th>予約番号</th>
              <th>荷主 ID</th>
              <th>出発地 → 目的地</th>
              <th>種別</th>
              <th>状態</th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.bookingId}>
                <td>
                  <a href={`/bookings/${b.bookingId}`}>{b.bookingId.slice(0, 8)}</a>
                </td>
                <td>{b.shipperCode}</td>
                <td>{b.origin} → {b.destination}</td>
                <td>{CARGO_TYPE_LABELS[b.cargoType as CargoType]}</td>
                <td>{BOOKING_STATUS_LABELS[b.bookingStatus as BookingStatus]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
