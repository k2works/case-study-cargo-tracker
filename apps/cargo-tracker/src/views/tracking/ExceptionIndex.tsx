import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type { TrackingExceptionSummary } from '../../contexts/tracking/application/queryservices/tracking-query.service.js';
import { EXCEPTION_TYPE_LABELS } from '../../contexts/tracking/domain/model/exception-type.js';

interface ExceptionIndexProps {
  user: AuthenticatedUser;
  trackingNumber: string;
  exceptions: TrackingExceptionSummary[];
  csrfToken?: string;
  success?: string;
  warning?: string;
  error?: string;
}

function typeLabel(type: string): string {
  return (EXCEPTION_TYPE_LABELS as Record<string, string>)[type] ?? type;
}

function formatDateTime(value: Date): string {
  return value.toISOString().replace('T', ' ').slice(0, 16);
}

/** 対応状況バッジ（解決済 / 報告済 / 対応中） */
function StatusBadge({ exception }: { exception: TrackingExceptionSummary }): ReactElement {
  if (exception.resolvedAt !== null) {
    return <span className="badge bg-success" data-testid="exception-badge-resolved">解決済</span>;
  }
  if (exception.reportedAt !== null) {
    return <span className="badge bg-info" data-testid="exception-badge-reported">報告済</span>;
  }
  return <span className="badge bg-warning text-dark" data-testid="exception-badge-active">対応中</span>;
}

/**
 * 例外一覧・対応画面（/tracking/{tn}/exceptions・US19/US20）。
 * 各例外の対応状況を表示し、追跡管理者が対応報告・解決を行える（PRG）。
 */
export function ExceptionIndex({
  user,
  trackingNumber,
  exceptions,
  csrfToken,
  success,
  warning,
  error,
}: ExceptionIndexProps): ReactElement {
  const base = `/tracking/${trackingNumber}/exceptions`;
  return (
    <Layout title="例外一覧" user={user} activePath="/tracking" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="exception-success">
          {success}
        </div>
      )}
      {warning && (
        <div className="alert alert-warning" role="alert" data-testid="exception-list-warning">
          {warning}
        </div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="exception-list-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="exception-index-heading">例外一覧</h1>
      <p className="text-muted">追跡番号: {trackingNumber}</p>
      {exceptions.length === 0 ? (
        <p className="text-muted" data-testid="no-exceptions">例外はまだ記録されていません。</p>
      ) : (
        <div data-testid="exception-list">
          {exceptions.map((exception) => (
            <div className="card mb-3" key={exception.id} data-testid={`exception-card-${exception.id}`}>
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start">
                  <h2 className="h5 mb-1">
                    {typeLabel(exception.exceptionType)}
                    {exception.escalationFlag && (
                      <span className="badge bg-danger ms-2" data-testid="exception-badge-escalation">緊急</span>
                    )}
                  </h2>
                  <StatusBadge exception={exception} />
                </div>
                <dl className="row mb-2 small">
                  <dt className="col-sm-3">発生場所</dt>
                  <dd className="col-sm-9">{exception.locationName ?? exception.location ?? '-'}</dd>
                  <dt className="col-sm-3">発生日時</dt>
                  <dd className="col-sm-9">{formatDateTime(exception.occurredAt)}</dd>
                  <dt className="col-sm-3">発生状況</dt>
                  <dd className="col-sm-9">{exception.description ?? '-'}</dd>
                  {exception.reportedAt !== null && (
                    <>
                      <dt className="col-sm-3">対応方針</dt>
                      <dd className="col-sm-9">{exception.reportNotes ?? '-'}</dd>
                      {exception.newEstimatedArrival !== null && (
                        <>
                          <dt className="col-sm-3">新到着予定日</dt>
                          <dd className="col-sm-9">{formatDateTime(exception.newEstimatedArrival)}</dd>
                        </>
                      )}
                    </>
                  )}
                  {exception.resolvedAt !== null && (
                    <>
                      <dt className="col-sm-3">解決日時</dt>
                      <dd className="col-sm-9">{formatDateTime(exception.resolvedAt)}</dd>
                      <dt className="col-sm-3">解決内容</dt>
                      <dd className="col-sm-9">{exception.resolutionNotes ?? '-'}</dd>
                    </>
                  )}
                </dl>

                {exception.resolvedAt === null && (
                  <div className="row g-3">
                    <div className="col-md-6">
                      <form
                        action={`${base}/${exception.id}/report`}
                        method="post"
                        data-testid={`exception-report-form-${exception.id}`}
                      >
                        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
                        <h3 className="h6">対応報告</h3>
                        <div className="mb-2">
                          <label className="form-label" htmlFor={`newEstimatedArrival-${exception.id}`}>
                            新到着予定日（遅延時）
                          </label>
                          <input
                            type="datetime-local"
                            className="form-control"
                            id={`newEstimatedArrival-${exception.id}`}
                            name="newEstimatedArrival"
                          />
                        </div>
                        <div className="mb-2">
                          <label className="form-label" htmlFor={`notes-${exception.id}`}>対応方針・補償方針</label>
                          <textarea
                            className="form-control"
                            id={`notes-${exception.id}`}
                            name="notes"
                            rows={2}
                            required
                          />
                        </div>
                        <button type="submit" className="btn btn-outline-primary btn-sm" data-testid={`report-submit-${exception.id}`}>
                          対応報告を送信
                        </button>
                      </form>
                    </div>
                    <div className="col-md-6">
                      <form
                        action={`${base}/${exception.id}/resolve`}
                        method="post"
                        data-hx-confirm="解決すると発生前の状態に復帰します。よろしいですか？"
                        data-testid={`exception-resolve-form-${exception.id}`}
                      >
                        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
                        <h3 className="h6">解決</h3>
                        <div className="mb-2">
                          <label className="form-label" htmlFor={`resolutionNotes-${exception.id}`}>解決内容</label>
                          <textarea
                            className="form-control"
                            id={`resolutionNotes-${exception.id}`}
                            name="resolutionNotes"
                            rows={2}
                          />
                        </div>
                        <button type="submit" className="btn btn-success btn-sm" data-testid={`resolve-submit-${exception.id}`}>
                          解決にする
                        </button>
                      </form>
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      <a href={`/tracking/${trackingNumber}`} className="btn btn-outline-secondary" data-testid="back-to-detail">
        追跡詳細に戻る
      </a>
    </Layout>
  );
}
