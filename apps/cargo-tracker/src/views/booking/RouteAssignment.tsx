import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CargoType, CARGO_TYPE_LABELS } from '../../shared/domain/model/cargo-type.js';
import type { RouteCandidateOption } from '../../contexts/booking/application/outboundservices/acl/route-candidate-acl.js';

interface RouteAssignmentProps {
  user: AuthenticatedUser;
  bookingId: string;
  origin: string;
  destination: string;
  arrivalDeadline: string;
  cargoType: string;
  candidates: RouteCandidateOption[];
  csrfToken?: string;
  error?: string;
}

/**
 * 経路割り当て画面（/bookings/{id}/route）。US09/US10/US11。
 * 経路候補の選択・確定、期限/貨物種別の条件調整、候補なし時の条件協議依頼を提供する。
 */
export function RouteAssignment({
  user,
  bookingId,
  origin,
  destination,
  arrivalDeadline,
  cargoType,
  candidates,
  csrfToken,
  error,
}: RouteAssignmentProps): ReactElement {
  const base = `/bookings/${bookingId}`;
  return (
    <Layout title="経路割り当て" user={user} activePath="/bookings" csrfToken={csrfToken}>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="route-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="route-assignment-heading">経路割り当て</h1>
      <p className="text-muted">予約番号: {bookingId}</p>
      <dl className="row">
        <dt className="col-sm-3">出発地 → 目的地</dt>
        <dd className="col-sm-9">{origin} → {destination}</dd>
        <dt className="col-sm-3">到着期限</dt>
        <dd className="col-sm-9">{arrivalDeadline}</dd>
      </dl>

      <section className="mb-4">
        <h2 className="h5">条件調整</h2>
        <form action={`${base}/route`} method="get" className="row g-2 align-items-end" data-testid="condition-form">
          <div className="col-auto">
            <label className="form-label" htmlFor="arrivalDeadline">到着期限</label>
            <input type="date" className="form-control" id="arrivalDeadline" name="arrivalDeadline" defaultValue={arrivalDeadline} />
          </div>
          <div className="col-auto">
            <label className="form-label" htmlFor="cargoType">貨物種別</label>
            <select className="form-select" id="cargoType" name="cargoType" defaultValue={cargoType}>
              {Object.values(CargoType).map((type) => (
                <option key={type} value={type}>{CARGO_TYPE_LABELS[type]}</option>
              ))}
            </select>
          </div>
          <div className="col-auto">
            <button type="submit" className="btn btn-outline-secondary" data-testid="recalculate">再算出</button>
          </div>
        </form>
      </section>

      <section>
        <h2 className="h5">経路候補</h2>
        {candidates.length === 0 ? (
          <div data-testid="route-candidate-empty">
            <p className="text-muted">期限内に到達可能な経路候補がありません。条件を調整するか、営業担当者に条件協議を依頼してください。</p>
            <span className="badge bg-warning text-dark" data-testid="consult-sales">営業担当者へ条件協議を依頼</span>
          </div>
        ) : (
          <table className="table" data-testid="route-candidate-list">
            <thead>
              <tr>
                <th>航海番号</th>
                <th>経由港</th>
                <th>所要日数</th>
                <th>費用</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {candidates.map((candidate) => (
                <tr key={candidate.id} data-testid="route-candidate-row">
                  <td>{candidate.voyageNumbers.join(' / ')}</td>
                  <td>{candidate.transitPorts.length > 0 ? candidate.transitPorts.join('、') : '直行'}</td>
                  <td>{candidate.transitDays} 日</td>
                  <td>{candidate.estimatedCost.toLocaleString('ja-JP')} 円</td>
                  <td>
                    <form action={`${base}/route`} method="post" className="d-inline">
                      {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
                      <input type="hidden" name="candidateId" value={candidate.id} />
                      <input type="hidden" name="arrivalDeadline" value={arrivalDeadline} />
                      <input type="hidden" name="cargoType" value={cargoType} />
                      <button type="submit" className="btn btn-sm btn-primary" data-testid="select-candidate">
                        この経路で確定
                      </button>
                    </form>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </Layout>
  );
}
