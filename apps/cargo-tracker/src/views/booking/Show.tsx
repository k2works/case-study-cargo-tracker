import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CARGO_TYPE_LABELS, type CargoType } from '../../shared/domain/model/cargo-type.js';
import {
  BOOKING_STATUS_LABELS,
  BookingStatus,
  type BookingStatus as BookingStatusType,
} from '../../contexts/booking/domain/model/booking-status.js';
import type { BookingDetail } from '../../contexts/booking/application/queryservices/booking-query.service.js';

interface ShowBookingProps {
  user: AuthenticatedUser;
  booking: BookingDetail;
  csrfToken?: string;
  success?: string;
}

/**
 * 予約詳細画面（/bookings/{id}）。US06。
 * 仮受付（PRELIMINARY）の予約には経路設計依頼ボタンを表示する。
 */
export function ShowBooking({ user, booking, csrfToken, success }: ShowBookingProps): ReactElement {
  const canAssign = booking.bookingStatus === BookingStatus.PRELIMINARY;
  return (
    <Layout title="予約詳細" user={user} activePath="/bookings" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="booking-flash">
          {success}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="booking-show-heading">予約詳細</h1>
      <p className="text-muted" data-testid="booking-number">予約番号: {booking.bookingId}</p>
      <dl className="row">
        <dt className="col-sm-3">状態</dt>
        <dd className="col-sm-9" data-testid="booking-status">
          {BOOKING_STATUS_LABELS[booking.bookingStatus as BookingStatusType]}
        </dd>
        <dt className="col-sm-3">荷主 ID</dt>
        <dd className="col-sm-9">{booking.shipperCode}</dd>
        <dt className="col-sm-3">出発地 → 目的地</dt>
        <dd className="col-sm-9">{booking.origin} → {booking.destination}</dd>
        <dt className="col-sm-3">貨物種別</dt>
        <dd className="col-sm-9">{CARGO_TYPE_LABELS[booking.cargoType as CargoType]}</dd>
        <dt className="col-sm-3">重量</dt>
        <dd className="col-sm-9">{booking.weight} kg</dd>
        <dt className="col-sm-3">荷受人</dt>
        <dd className="col-sm-9">{booking.consigneeName}（{booking.consigneeEmail}）</dd>
      </dl>

      {canAssign ? (
        <form action={`/bookings/${booking.bookingId}/assign-to-routing`} method="post">
          {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
          <button type="submit" className="btn btn-primary" data-testid="assign-to-routing">
            経路設計者に引き渡す
          </button>
        </form>
      ) : (
        <p className="text-muted" data-testid="assign-done">この予約は経路設計へ引き渡し済みです。</p>
      )}
    </Layout>
  );
}
