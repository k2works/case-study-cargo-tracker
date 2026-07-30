import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Role } from '../../shared/domain/model/role.js';
import type { HandlingActivitySummary } from '../../contexts/handling/application/queryservices/handling-history-query.service.js';
import { HANDLING_TYPE_LABELS } from '../../contexts/handling/domain/model/handling-type.js';

interface HandlingIndexProps {
  user: AuthenticatedUser;
  activities: HandlingActivitySummary[];
  bookingIdFilter?: string;
  success?: string;
  warning?: string;
}

/** 荷役作業一覧画面（/handling）。荷役履歴の一覧・検索（US15） */
export function HandlingIndex({ user, activities, bookingIdFilter, success, warning }: HandlingIndexProps): ReactElement {
  const isHandler = user.roles.includes(Role.HANDLER);
  return (
    <Layout title="荷役作業一覧" user={user} activePath="/handling">
      {success && (
        <div className="alert alert-success" role="alert" data-testid="handling-success">
          {success}
        </div>
      )}
      {warning && (
        <div className="alert alert-warning" role="alert" data-testid="handling-warning">
          {warning}
        </div>
      )}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 mb-0" data-testid="handling-index-heading">荷役作業一覧</h1>
        {isHandler && (
          <a href="/handling/new" className="btn btn-primary" data-testid="go-handling-new">新規登録</a>
        )}
      </div>
      <form action="/handling" method="get" className="row g-2 align-items-end mb-3" data-testid="handling-search-form">
        <div className="col-auto">
          <label className="form-label" htmlFor="bookingId">予約 ID で検索</label>
          <input type="text" className="form-control" id="bookingId" name="bookingId" defaultValue={bookingIdFilter ?? ''} />
        </div>
        <div className="col-auto">
          <button type="submit" className="btn btn-outline-secondary">検索</button>
        </div>
      </form>
      {activities.length === 0 ? (
        <p className="text-muted" data-testid="no-handling">荷役作業はまだ記録されていません。</p>
      ) : (
        <table className="table table-striped" data-testid="handling-table">
          <thead>
            <tr>
              <th>作業日時</th>
              <th>種別</th>
              <th>場所</th>
              <th>航海番号</th>
              <th>予約 ID</th>
              <th>作業員</th>
              {isHandler && <th>例外</th>}
            </tr>
          </thead>
          <tbody>
            {activities.map((activity) => (
              <tr key={activity.id}>
                <td>{activity.eventCompletionTime.toISOString().replace('T', ' ').slice(0, 16)}</td>
                <td>{(HANDLING_TYPE_LABELS as Record<string, string>)[activity.eventType] ?? activity.eventType}</td>
                <td>{activity.locationUnlocode}</td>
                <td>{activity.voyageNumber ?? '-'}</td>
                <td>{activity.bookingId}</td>
                <td>{activity.operatorName ?? '-'}</td>
                {isHandler && (
                  <td>
                    {activity.trackingNumber !== null ? (
                      <a
                        href={`/tracking/${activity.trackingNumber}/exceptions/new`}
                        className="btn btn-outline-danger btn-sm"
                        data-testid={`go-exception-new-${activity.id}`}
                      >
                        例外を登録
                      </a>
                    ) : (
                      '-'
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
