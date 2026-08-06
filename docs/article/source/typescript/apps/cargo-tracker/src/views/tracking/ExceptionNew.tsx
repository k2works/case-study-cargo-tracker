import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Role } from '../../shared/domain/model/role.js';
import { ExceptionType, EXCEPTION_TYPE_LABELS } from '../../contexts/tracking/domain/model/exception-type.js';

interface ExceptionNewProps {
  user: AuthenticatedUser;
  trackingNumber: string;
  csrfToken?: string;
  error?: string;
  values?: Record<string, string>;
}

/**
 * 例外登録画面（/tracking/{tn}/exceptions/new・US19/US20）。
 * 追跡管理者は全種別、荷役作業員は破損・紛失のみ登録できる（サービス層で種別を検証）。
 */
export function ExceptionNew({ user, trackingNumber, csrfToken, error, values = {} }: ExceptionNewProps): ReactElement {
  const isTracker = user.roles.includes(Role.TRACKER);
  // 荷役作業員（追跡管理者でない）に選択肢を破損・紛失へ絞り、越権選択を UI でも防ぐ
  const selectableTypes: ExceptionType[] = isTracker
    ? [ExceptionType.DELAY, ExceptionType.DAMAGE, ExceptionType.LOST]
    : [ExceptionType.DAMAGE, ExceptionType.LOST];
  return (
    <Layout title="例外登録" user={user} activePath="/tracking" csrfToken={csrfToken}>
      <h1 className="h3 mb-3" data-testid="exception-new-heading">例外登録</h1>
      <p className="text-muted">追跡番号: {trackingNumber}</p>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="exception-error">
          {error}
        </div>
      )}
      <div className="alert alert-warning" role="alert" data-testid="lost-notice">
        「紛失」を登録すると緊急扱いとなり、管理職へエスカレーション通知が送信されます。
      </div>
      <form action={`/tracking/${trackingNumber}/exceptions`} method="post" className="col-md-6" data-testid="exception-form">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
        <div className="mb-3">
          <label className="form-label" htmlFor="exceptionType">例外種別</label>
          <select className="form-select" id="exceptionType" name="exceptionType" defaultValue={values.exceptionType ?? selectableTypes[0]}>
            {selectableTypes.map((type) => (
              <option key={type} value={type}>
                {EXCEPTION_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="location">発生場所（UN/LOCODE）</label>
          <input
            type="text"
            className="form-control"
            id="location"
            name="location"
            placeholder="JPTYO"
            defaultValue={values.location ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="occurredAt">発生日時</label>
          <input
            type="datetime-local"
            className="form-control"
            id="occurredAt"
            name="occurredAt"
            defaultValue={values.occurredAt ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="description">発生状況（理由）</label>
          <textarea
            className="form-control"
            id="description"
            name="description"
            rows={3}
            defaultValue={values.description ?? ''}
          />
        </div>
        <button type="submit" className="btn btn-primary" data-testid="exception-submit">登録</button>
        <a href={`/tracking/${trackingNumber}`} className="btn btn-outline-secondary ms-2">追跡詳細に戻る</a>
      </form>
    </Layout>
  );
}
