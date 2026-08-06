import type { ReactElement } from 'react';
import type {
  ActiveExceptionSummary,
  TrackingDetail,
  TrackingLocation,
} from '../../contexts/tracking/application/queryservices/tracking-query.service.js';
import { TRACKING_STATUS_LABELS, TrackingStatus, isTrackingStatus } from '../../contexts/tracking/domain/model/tracking-status.js';
import { EXCEPTION_TYPE_LABELS } from '../../contexts/tracking/domain/model/exception-type.js';
import { HANDLING_TYPE_LABELS } from '../../contexts/handling/domain/model/handling-type.js';

/** 追跡イベントの表示ラベル（荷役由来 + 手動更新用の輸送イベント） */
const TRACKING_EVENT_LABELS: Record<string, string> = {
  ...HANDLING_TYPE_LABELS,
  DEPARTURE: '出港',
  ARRIVAL: '入港',
};

/** 終端状態（ポーリング停止条件・引取済） */
const TERMINAL_STATUS = 'CLAIMED';

export function statusLabel(status: string): string {
  return isTrackingStatus(status) ? TRACKING_STATUS_LABELS[status] : status;
}

export function eventLabel(eventType: string): string {
  return TRACKING_EVENT_LABELS[eventType] ?? eventType;
}

/** 現在地を「UN/LOCODE 港湾名」形式で表す。イベントなしは「-」 */
export function locationLabel(location: TrackingLocation | null): string {
  if (location === null) {
    return '-';
  }
  return location.name !== null ? `${location.unlocode} ${location.name}` : location.unlocode;
}

/** 推定到着日を「YYYY-MM-DD 頃」で表す。未確定は「未確定」 */
export function expectedArrivalLabel(expectedArrival: Date | null): string {
  if (expectedArrival === null) {
    return '未確定';
  }
  return `${expectedArrival.toISOString().slice(0, 10)} 頃`;
}

/** 例外種別の表示ラベル（未知の種別はそのまま） */
export function exceptionTypeLabel(exceptionType: string): string {
  return (EXCEPTION_TYPE_LABELS as Record<string, string>)[exceptionType] ?? exceptionType;
}

/**
 * EXCEPTION 状態の表示ラベル。未解決例外の種別を併記する（例:「例外発生（遅延）」）。
 * 種別要約が無ければ状態ラベルのみを返す。
 */
export function exceptionStatusLabel(activeExceptions: ActiveExceptionSummary[]): string {
  const base = statusLabel(TrackingStatus.EXCEPTION);
  if (activeExceptions.length === 0) {
    return base;
  }
  const types = activeExceptions.map((e) => exceptionTypeLabel(e.exceptionType)).join('、');
  return `${base}（${types}）`;
}

interface StatusTimelineProps {
  detail: TrackingDetail;
  /** htmx ポーリング先（GET フラグメントエンドポイント） */
  fragmentPath: string;
}

/**
 * 追跡状態タイムライン（状態・現在地・推定到着日・イベント履歴）。
 * htmx で 30 秒ごとに自身を差し替える（outerHTML）。終端状態（CLAIMED）では
 * hx-trigger を含めずポーリングを停止し「輸送は完了しました」と表示する（ui_design 停止条件）。
 */
export function StatusTimeline({ detail, fragmentPath }: StatusTimelineProps): ReactElement {
  const isTerminal = detail.transportStatus === TERMINAL_STATUS;
  const isException = detail.transportStatus === TrackingStatus.EXCEPTION;
  const pollingProps = isTerminal
    ? { 'hx-get': fragmentPath }
    : { 'hx-get': fragmentPath, 'hx-trigger': 'every 30s', 'hx-swap': 'outerHTML' };
  return (
    <div id="status-timeline" data-testid="status-timeline" {...pollingProps}>
      <dl className="row">
        <dt className="col-sm-3">貨物状態</dt>
        <dd className="col-sm-9">
          <span className={`badge ${isException ? 'text-bg-danger' : 'text-bg-info'}`} data-testid="transport-status">
            {isException ? exceptionStatusLabel(detail.activeExceptions) : statusLabel(detail.transportStatus)}
          </span>
        </dd>
        <dt className="col-sm-3">現在地</dt>
        <dd className="col-sm-9" data-testid="current-location">{locationLabel(detail.currentLocation)}</dd>
        <dt className="col-sm-3">推定到着日</dt>
        <dd className="col-sm-9" data-testid="expected-arrival">{expectedArrivalLabel(detail.expectedArrival)}</dd>
      </dl>

      {isTerminal && (
        <p className="text-success" data-testid="polling-stopped">輸送は完了しました。</p>
      )}

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
                  <td>{locationLabel(event.location === null ? null : { unlocode: event.location, name: event.locationName })}</td>
                  <td>{event.voyageNumber ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
