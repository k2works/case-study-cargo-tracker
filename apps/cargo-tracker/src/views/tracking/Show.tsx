import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Role } from '../../shared/domain/model/role.js';
import type { TrackingDetail } from '../../contexts/tracking/application/queryservices/tracking-query.service.js';
import { TRACKING_STATUS_LABELS, isTrackingStatus } from '../../contexts/tracking/domain/model/tracking-status.js';
import { HANDLING_TYPE_LABELS } from '../../contexts/handling/domain/model/handling-type.js';

interface TrackingShowProps {
  user: AuthenticatedUser;
  detail: TrackingDetail;
  csrfToken?: string;
  success?: string;
  warning?: string;
  error?: string;
}

/** 追跡イベントの表示ラベル（荷役由来 + 手動更新用の輸送イベント） */
const TRACKING_EVENT_LABELS: Record<string, string> = {
  ...HANDLING_TYPE_LABELS,
  DEPARTURE: '出港',
  ARRIVAL: '入港',
};

function statusLabel(status: string): string {
  return isTrackingStatus(status) ? TRACKING_STATUS_LABELS[status] : status;
}

function eventLabel(eventType: string): string {
  return TRACKING_EVENT_LABELS[eventType] ?? eventType;
}

/** 追跡詳細画面（/tracking/{trackingNumber}）。状態タイムラインと手動更新（追跡管理者・US17） */
export function TrackingShow({ user, detail, csrfToken, success, warning, error }: TrackingShowProps): ReactElement {
  const isTracker = user.roles.includes(Role.TRACKER);
  const base = `/tracking/${detail.trackingNumber}`;
  return (
    <Layout title="追跡詳細" user={user} activePath="/tracking" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="tracking-success">
          {success}
        </div>
      )}
      {warning && (
        <div className="alert alert-warning" role="alert" data-testid="tracking-warning">
          {warning}
        </div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="tracking-show-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="tracking-show-heading">追跡詳細</h1>
      <dl className="row">
        <dt className="col-sm-3">追跡番号</dt>
        <dd className="col-sm-9" data-testid="tracking-number">{detail.trackingNumber}</dd>
        <dt className="col-sm-3">貨物状態</dt>
        <dd className="col-sm-9">
          <span className="badge text-bg-info" data-testid="transport-status">
            {statusLabel(detail.transportStatus)}
          </span>
        </dd>
      </dl>

      <section className="mb-4">
        <h2 className="h5">追跡イベント履歴</h2>
        {detail.events.length === 0 ? (
          <p className="text-muted" data-testid="no-events">追跡イベントはまだ記録されていません（受領待ち）。</p>
        ) : (
          <table className="table table-striped" data-testid="event-timeline">
            <thead>
              <tr>
                <th>日時</th>
                <th>イベント</th>
                <th>場所</th>
                <th>航海番号</th>
              </tr>
            </thead>
            <tbody>
              {detail.events.map((event, index) => (
                <tr key={index}>
                  <td>{event.eventTime.toISOString().replace('T', ' ').slice(0, 16)}</td>
                  <td>{eventLabel(event.eventType)}</td>
                  <td>{event.location ?? '-'}</td>
                  <td>{event.voyageNumber ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {isTracker && (
        <section className="mb-4">
          <h2 className="h5">貨物状態の手動更新（追跡管理者）</h2>
          <p className="text-muted small">
            荷役では捕捉できない状態変化（出港・入港等）を記録します。引取（引取済）は荷役登録から行ってください。
          </p>
          <form action={`${base}/events`} method="post" className="row g-2 align-items-end" data-testid="manual-update-form">
            {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
            <div className="col-auto">
              <label className="form-label" htmlFor="eventType">記録するイベント</label>
              <select className="form-select" id="eventType" name="eventType" defaultValue="DEPARTURE">
                <option value="DEPARTURE">出港（輸送中）</option>
                <option value="ARRIVAL">入港（引取待ち）</option>
                <option value="RECEIVE">受領（受領済）</option>
                <option value="LOAD">積込（積込済）</option>
                <option value="UNLOAD">荷降し（荷降し済）</option>
              </select>
            </div>
            <div className="col-auto">
              <label className="form-label" htmlFor="location">位置（UN/LOCODE）</label>
              <input type="text" className="form-control" id="location" name="location" placeholder="JPTYO" required />
            </div>
            <div className="col-auto">
              <label className="form-label" htmlFor="completionTime">日時</label>
              <input type="datetime-local" className="form-control" id="completionTime" name="completionTime" required />
            </div>
            <div className="col-auto">
              <label className="form-label" htmlFor="voyageNumber">航海番号（積込・荷降し時）</label>
              <input type="text" className="form-control" id="voyageNumber" name="voyageNumber" />
            </div>
            <div className="col-auto">
              <button type="submit" className="btn btn-primary" data-testid="manual-update-submit">手動更新</button>
            </div>
          </form>
        </section>
      )}

      <a href="/tracking" className="btn btn-outline-secondary" data-testid="back-to-tracking">別の貨物を追跡</a>
    </Layout>
  );
}
