import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CARGO_TYPE_LABELS, type CargoType } from '../../shared/domain/model/cargo-type.js';
import { Role } from '../../shared/domain/model/role.js';
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
  error?: string;
}

function PostButton({
  action,
  label,
  variant,
  testid,
  csrfToken,
  confirm,
}: {
  action: string;
  label: string;
  variant: string;
  testid: string;
  csrfToken?: string;
  confirm?: string;
}): ReactElement {
  return (
    <form action={action} method="post" className="d-inline me-2" data-hx-confirm={confirm}>
      {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
      <button type="submit" className={`btn ${variant}`} data-testid={testid}>
        {label}
      </button>
    </form>
  );
}

/**
 * 予約詳細画面（/bookings/{id}）。US06/US12/US13/US14。
 * 状態とロールに応じて、経路割り当て・通知・確定・差戻し・キャンセル・追跡番号発行の導線を出し分ける。
 */
export function ShowBooking({ user, booking, csrfToken, success, error }: ShowBookingProps): ReactElement {
  const roles = user.roles;
  const status = booking.bookingStatus as BookingStatusType;
  const base = `/bookings/${booking.bookingId}`;
  const isSales = roles.includes(Role.SALES);
  const isRouteDesigner = roles.includes(Role.ROUTE_DESIGNER);
  return (
    <Layout title="予約詳細" user={user} activePath="/bookings" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="booking-flash">
          {success}
        </div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="booking-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="booking-show-heading">予約詳細</h1>
      <p className="text-muted" data-testid="booking-number">予約番号: {booking.bookingId}</p>
      <dl className="row">
        <dt className="col-sm-3">状態</dt>
        <dd className="col-sm-9" data-testid="booking-status">
          {BOOKING_STATUS_LABELS[status]}
        </dd>
        <dt className="col-sm-3">荷主 ID</dt>
        <dd className="col-sm-9">{booking.shipperCode}</dd>
        <dt className="col-sm-3">出発地 → 目的地</dt>
        <dd className="col-sm-9">{booking.origin} → {booking.destination}</dd>
        <dt className="col-sm-3">到着期限</dt>
        <dd className="col-sm-9">{booking.arrivalDeadlineText}</dd>
        <dt className="col-sm-3">貨物種別</dt>
        <dd className="col-sm-9">{CARGO_TYPE_LABELS[booking.cargoType as CargoType]}</dd>
        <dt className="col-sm-3">重量</dt>
        <dd className="col-sm-9">{booking.weight} kg</dd>
        <dt className="col-sm-3">荷受人</dt>
        <dd className="col-sm-9">{booking.consigneeName}（{booking.consigneeEmail}）</dd>
        {booking.trackingNumber && (
          <>
            <dt className="col-sm-3">追跡番号</dt>
            <dd className="col-sm-9" data-testid="booking-tracking-number">{booking.trackingNumber}</dd>
          </>
        )}
      </dl>

      {booking.legs.length > 0 && (
        <section className="mb-3" data-testid="booking-itinerary">
          <h2 className="h5">確定経路</h2>
          <ol className="mb-0">
            {booking.legs.map((leg) => (
              <li key={leg.seqNumber}>
                {leg.loadLocation} → {leg.unloadLocation}（{leg.voyageNumber}）
              </li>
            ))}
          </ol>
        </section>
      )}

      <div className="d-flex flex-wrap align-items-center gap-2 mt-3">
        {status === BookingStatus.PRELIMINARY && isSales && (
          <PostButton
            action={`${base}/assign-to-routing`}
            label="経路設計者に引き渡す"
            variant="btn-primary"
            testid="assign-to-routing"
            csrfToken={csrfToken}
          />
        )}
        {status === BookingStatus.ROUTING_IN_PROGRESS && isRouteDesigner && (
          <a href={`${base}/route`} className="btn btn-primary" data-testid="go-route-assignment">
            経路を割り当てる
          </a>
        )}
        {status === BookingStatus.ROUTE_PROPOSED && isSales && (
          <>
            <PostButton action={`${base}/notify`} label="経路を荷主に通知" variant="btn-outline-primary" testid="notify-shipper" csrfToken={csrfToken} />
            <PostButton action={`${base}/confirm`} label="予約を確定" variant="btn-success" testid="confirm-booking" csrfToken={csrfToken} />
            <PostButton action={`${base}/return-to-routing`} label="経路設計に戻す" variant="btn-outline-secondary" testid="return-to-routing" csrfToken={csrfToken} />
            <PostButton action={`${base}/cancel`} label="キャンセル" variant="btn-outline-danger" testid="cancel-booking" csrfToken={csrfToken} confirm="予約をキャンセルしますか？" />
          </>
        )}
        {status === BookingStatus.CONFIRMED && isRouteDesigner && (
          <PostButton action={`${base}/tracking-number`} label="追跡番号を発行" variant="btn-primary" testid="issue-tracking-number" csrfToken={csrfToken} />
        )}
      </div>
    </Layout>
  );
}
