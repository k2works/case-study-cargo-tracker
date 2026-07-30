import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type { BookingDetail } from '../../contexts/booking/application/queryservices/booking-query.service.js';

interface NotifyConfirmProps {
  user: AuthenticatedUser;
  booking: BookingDetail;
  transitPorts: string[];
  transitDays: number | null;
  expectedArrivalDate: string | null;
  estimatedCost: number | null;
  csrfToken?: string;
}

/**
 * 通知内容確認画面（/bookings/{id}/notify・US12 是正・IT4 Try T1）。
 * 荷主へ通知する内容（宛先・経由港・所要日数・到着予定日・料金概算）を確認してから送信する。
 */
export function NotifyConfirm({
  user,
  booking,
  transitPorts,
  transitDays,
  expectedArrivalDate,
  estimatedCost,
  csrfToken,
}: NotifyConfirmProps): ReactElement {
  const base = `/bookings/${booking.bookingId}`;
  return (
    <Layout title="通知内容の確認" user={user} activePath="/bookings" csrfToken={csrfToken}>
      <h1 className="h3 mb-3" data-testid="notify-confirm-heading">通知内容の確認</h1>
      <p className="text-muted">予約番号: {booking.bookingId}</p>
      <dl className="row" data-testid="notify-content">
        <dt className="col-sm-3">宛先（荷主）</dt>
        <dd className="col-sm-9" data-testid="notify-recipient">{booking.shipperEmail}</dd>
        <dt className="col-sm-3">区間</dt>
        <dd className="col-sm-9">{booking.origin} → {booking.destination}</dd>
        <dt className="col-sm-3">経由港</dt>
        <dd className="col-sm-9" data-testid="notify-transit-ports">
          {transitPorts.length > 0 ? transitPorts.join('、') : 'なし（直行）'}
        </dd>
        <dt className="col-sm-3">所要日数</dt>
        <dd className="col-sm-9" data-testid="notify-transit-days">
          {transitDays !== null ? `${transitDays} 日` : '未算出'}
        </dd>
        <dt className="col-sm-3">到着予定日</dt>
        <dd className="col-sm-9" data-testid="notify-arrival-date">{expectedArrivalDate ?? '未算出'}</dd>
        <dt className="col-sm-3">料金概算</dt>
        <dd className="col-sm-9" data-testid="notify-estimated-cost">
          {estimatedCost !== null ? `$${estimatedCost.toLocaleString()}` : '未算出'}
        </dd>
      </dl>
      <div className="d-flex gap-2">
        <form action={`${base}/notify`} method="post">
          {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
          <button type="submit" className="btn btn-primary" data-testid="send-notify">
            この内容で荷主に通知する
          </button>
        </form>
        <a href={base} className="btn btn-outline-secondary" data-testid="back-to-detail">
          予約詳細に戻る
        </a>
      </div>
    </Layout>
  );
}
