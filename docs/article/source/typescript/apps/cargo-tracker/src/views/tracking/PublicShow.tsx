import type { ReactElement, ReactNode } from 'react';
import type { TrackingDetail } from '../../contexts/tracking/application/queryservices/tracking-query.service.js';
import { statusLabel, eventLabel, locationLabel, expectedArrivalLabel, exceptionStatusLabel, exceptionTypeLabel } from './StatusTimeline.js';
import { TrackingStatus } from '../../contexts/tracking/domain/model/tracking-status.js';

/**
 * 公開追跡ページ用の最小スタンドアロンシェル（navbar なし・Layout 非使用）。
 * 認証済み画面のナビゲーションや個人情報を持ち込まない。
 */
function PublicShell({ children }: { children: ReactNode }): ReactElement {
  return (
    <html lang="ja">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>公開追跡 | CargoTracker</title>
        <link rel="stylesheet" href="/vendor/bootstrap/bootstrap.min.css" />
      </head>
      <body>
        <main className="container py-4">
          <h1 className="h4 mb-4" data-testid="public-heading">CargoTracker 公開追跡</h1>
          {children}
        </main>
      </body>
    </html>
  );
}

/** 追跡番号入力フォーム（GET /public/tracking） */
function PublicSearchForm({ error }: { error?: string }): ReactElement {
  return (
    <>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="public-error">
          {error}
        </div>
      )}
      <form action="/public/tracking" method="get" className="row g-2 align-items-end" data-testid="public-tracking-form">
        <div className="col-auto">
          <label className="form-label" htmlFor="trackingNumber">追跡番号</label>
          <input
            type="text"
            className="form-control"
            id="trackingNumber"
            name="trackingNumber"
            placeholder="TRK-XXXXXXXXXXXX"
            required
          />
        </div>
        <div className="col-auto">
          <button type="submit" className="btn btn-primary" data-testid="public-track-submit">追跡する</button>
        </div>
      </form>
    </>
  );
}

/** 公開追跡入力画面（/public/tracking） */
export function PublicTrackingIndex({ error }: { error?: string } = {}): ReactElement {
  return (
    <PublicShell>
      <PublicSearchForm error={error} />
    </PublicShell>
  );
}

/** 公開追跡結果画面（/public/tracking/{trackingNumber}）。状態・現在地・イベント履歴のみを最小表示する */
export function PublicTrackingShow({ detail }: { detail: TrackingDetail }): ReactElement {
  const isException = detail.transportStatus === TrackingStatus.EXCEPTION;
  // 対応報告済みで新到着予定日がある未解決例外があれば、その予定日を優先表示する
  const reportedArrival = detail.activeExceptions.find((e) => e.newEstimatedArrival !== null)?.newEstimatedArrival ?? null;
  const exceptionTypes = detail.activeExceptions.map((e) => exceptionTypeLabel(e.exceptionType)).join('、');
  return (
    <PublicShell>
      <dl className="row">
        <dt className="col-sm-3">追跡番号</dt>
        <dd className="col-sm-9" data-testid="public-tracking-number">{detail.trackingNumber}</dd>
        <dt className="col-sm-3">貨物状態</dt>
        <dd className="col-sm-9">
          <span
            className={`badge ${isException ? 'text-bg-danger' : 'text-bg-info'}`}
            data-testid="public-transport-status"
          >
            {isException ? exceptionStatusLabel(detail.activeExceptions) : statusLabel(detail.transportStatus)}
          </span>
        </dd>
        <dt className="col-sm-3">現在地</dt>
        <dd className="col-sm-9" data-testid="public-current-location">{locationLabel(detail.currentLocation)}</dd>
        <dt className="col-sm-3">推定到着日</dt>
        <dd className="col-sm-9" data-testid="public-expected-arrival">
          {reportedArrival !== null
            ? `新しい到着予定日: ${expectedArrivalLabel(reportedArrival)}`
            : expectedArrivalLabel(detail.expectedArrival)}
        </dd>
      </dl>

      {isException && (
        <div className="alert alert-warning" role="alert" data-testid="public-exception-summary">
          {reportedArrival !== null
            ? `例外発生（${exceptionTypes}）・新しい到着予定日: ${expectedArrivalLabel(reportedArrival)}`
            : `例外発生（${exceptionTypes}）・対応中`}
        </div>
      )}

      <section className="mb-4">
        <h2 className="h5">追跡イベント履歴</h2>
        {detail.events.length === 0 ? (
          <p className="text-muted" data-testid="public-no-events">追跡イベントはまだ記録されていません。</p>
        ) : (
          <table className="table table-striped" data-testid="public-event-timeline">
            <thead>
              <tr>
                <th>日時</th>
                <th>イベント</th>
                <th>場所</th>
              </tr>
            </thead>
            <tbody>
              {detail.events.map((event, index) => (
                <tr key={index}>
                  <td>{event.eventTime.toISOString().replace('T', ' ').slice(0, 16)}</td>
                  <td>{eventLabel(event.eventType)}</td>
                  <td>{locationLabel(event.location === null ? null : { unlocode: event.location, name: event.locationName })}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <a href="/public/tracking" className="btn btn-outline-secondary" data-testid="public-back">別の貨物を追跡</a>
    </PublicShell>
  );
}
