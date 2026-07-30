import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Role } from '../../shared/domain/model/role.js';
import type { TrackingDetail } from '../../contexts/tracking/application/queryservices/tracking-query.service.js';
import { StatusTimeline } from './StatusTimeline.js';

interface TrackingShowProps {
  user: AuthenticatedUser;
  detail: TrackingDetail;
  csrfToken?: string;
  success?: string;
  warning?: string;
  error?: string;
}

/** 追跡詳細画面（/tracking/{trackingNumber}）。状態タイムライン（htmx ポーリング）と手動更新（追跡管理者・US17） */
export function TrackingShow({ user, detail, csrfToken, success, warning, error }: TrackingShowProps): ReactElement {
  const isTracker = user.roles.includes(Role.TRACKER);
  const base = `/tracking/${detail.trackingNumber}`;
  const publicUrl = `/public/tracking/${detail.trackingNumber}`;
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
      </dl>

      <StatusTimeline detail={detail} fragmentPath={`${base}/status`} />

      {isTracker && (
        <section className="mb-4" data-testid="exception-links">
          <h2 className="h5">例外対応（追跡管理者）</h2>
          <p className="text-muted small">遅延・破損・紛失などの例外を登録し、対応状況を管理します。</p>
          <a href={`${base}/exceptions/new`} className="btn btn-outline-danger me-2" data-testid="go-exception-new">
            例外を登録
          </a>
          <a href={`${base}/exceptions`} className="btn btn-outline-secondary" data-testid="go-exception-list">
            例外を確認
          </a>
        </section>
      )}

      <section className="mb-4">
        <h2 className="h5">公開追跡ページの共有</h2>
        <p className="text-muted small">
          荷主・荷受人は次の URL からログインなしで追跡状況を確認できます。
        </p>
        <input
          type="text"
          className="form-control"
          value={publicUrl}
          readOnly
          data-testid="public-tracking-url"
        />
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
