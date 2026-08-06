import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type {
  VoyageListItem,
  VoyageSearchCriteria,
} from '../../contexts/routing/application/queryservices/voyage-query.service.js';
import { CARGO_TYPE_LABELS, isCargoType } from '../../shared/domain/model/cargo-type.js';

interface IndexVoyageProps {
  user: AuthenticatedUser;
  voyages: VoyageListItem[];
  success?: string;
  bookingCondition?: BookingConditionView;
  criteria: VoyageSearchCriteria;
  searching: boolean;
}

interface BookingConditionView {
  bookingId: string;
  origin: string;
  destination: string;
  cargoType: string;
  arrivalDeadline: Date;
}

export function IndexVoyage({
  user,
  voyages,
  success,
  bookingCondition,
  criteria,
  searching,
}: IndexVoyageProps): ReactElement {
  const canFindRouteCandidates =
    hasValue(criteria.origin) &&
    hasValue(criteria.destination) &&
    hasValue(criteria.arrivalDeadline) &&
    hasValue(criteria.cargoType);
  return (
    <Layout title="航海スケジュール一覧" user={user} activePath="/voyages">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3" data-testid="voyage-index-heading">
          航海スケジュール一覧
        </h1>
        <a href="/voyages/new" className="btn btn-primary" data-testid="voyage-new-link">
          航海登録
        </a>
      </div>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="voyage-flash">
          {success}
        </div>
      )}
      {bookingCondition && (
        <section className="mb-3" data-testid="voyage-booking-condition">
          <h2 className="h6">予約条件</h2>
          <p className="mb-1">
            予約番号: {bookingCondition.bookingId.slice(0, 8)} / {bookingCondition.origin} →{' '}
            {bookingCondition.destination} / {formatCargoType(bookingCondition.cargoType)} / 希望着日:{' '}
            {formatDate(bookingCondition.arrivalDeadline)}
          </p>
        </section>
      )}
      <form method="get" action="/voyages" className="row g-2 mb-3" data-testid="voyage-search-form">
        <div className="col-md-3">
          <label htmlFor="origin" className="form-label">出発港</label>
          <input id="origin" name="origin" className="form-control" maxLength={5} placeholder="JPTYO" defaultValue={criteria.origin ?? ''} />
        </div>
        <div className="col-md-3">
          <label htmlFor="destination" className="form-label">到着港</label>
          <input id="destination" name="destination" className="form-control" maxLength={5} placeholder="SGSIN" defaultValue={criteria.destination ?? ''} />
        </div>
        <div className="col-md-3">
          <label htmlFor="cargoType" className="form-label">貨物種別</label>
          <select id="cargoType" name="cargoType" className="form-select" defaultValue={criteria.cargoType ?? ''}>
            <option value="">すべて</option>
            <option value="GENERAL">一般貨物</option>
            <option value="HAZARDOUS">危険物</option>
            <option value="REFRIGERATED">冷凍・冷蔵貨物</option>
          </select>
        </div>
        <div className="col-md-3">
          <label htmlFor="departureFrom" className="form-label">出発日 From</label>
          <input id="departureFrom" name="departureFrom" type="date" className="form-control" defaultValue={criteria.departureFrom ?? ''} />
        </div>
        <div className="col-md-3">
          <label htmlFor="departureTo" className="form-label">出発日 To</label>
          <input id="departureTo" name="departureTo" type="date" className="form-control" defaultValue={criteria.departureTo ?? ''} />
        </div>
        <div className="col-md-3">
          <label htmlFor="arrivalDeadline" className="form-label">希望着日</label>
          <input id="arrivalDeadline" name="arrivalDeadline" type="date" className="form-control" defaultValue={criteria.arrivalDeadline ?? ''} />
        </div>
        <div className="col-md-3 align-self-end">
          <button type="submit" className="btn btn-outline-primary">検索</button>
        </div>
      </form>
      <div className="d-flex justify-content-end mb-3">
        <form
          method="get"
          action="/routing/candidates"
          hx-get="/routing/candidates"
          hx-target="#route-candidates"
          hx-swap="innerHTML"
          data-testid="route-candidate-search-form"
        >
          <input type="hidden" name="origin" value={criteria.origin ?? ''} />
          <input type="hidden" name="destination" value={criteria.destination ?? ''} />
          <input type="hidden" name="arrivalDeadline" value={criteria.arrivalDeadline ?? ''} />
          <input type="hidden" name="cargoType" value={criteria.cargoType ?? ''} />
          <button type="submit" className="btn btn-primary" disabled={!canFindRouteCandidates}>
            経路候補を算出
          </button>
        </form>
      </div>
      <VoyageTable voyages={voyages} searched={searching} />
      <div id="route-candidates" className="mt-4" data-testid="route-candidates"></div>
    </Layout>
  );
}

export function VoyageTable({
  voyages,
  searched = false,
}: {
  voyages: VoyageListItem[];
  searched?: boolean;
}): ReactElement {
  return (
    <table className="table" data-testid="voyage-list">
      <thead>
        <tr>
          <th>航海番号</th>
          <th>船名</th>
          <th>運送会社</th>
          <th>出発港 → 到着港</th>
          <th>出発日</th>
          <th>到着日</th>
          <th>寄港地</th>
          <th>対応貨物種別</th>
        </tr>
      </thead>
      <tbody>
        {voyages.length === 0 ? (
          <tr>
            <td colSpan={8} className="text-muted" data-testid="voyage-empty">
              {searched ? '条件を満たす航海スケジュールはありません。' : '航海スケジュールはまだありません。'}
            </td>
          </tr>
        ) : (
          voyages.map((voyage) => (
            <tr key={voyage.voyageNumber}>
              <td>
                <a href={`/voyages/${encodeURIComponent(voyage.voyageNumber)}/edit`}>
                  {voyage.voyageNumber}
                </a>
              </td>
              <td>{voyage.shipName}</td>
              <td>{voyage.carrierName}</td>
              <td>{voyage.departureLocation} → {voyage.arrivalLocation}</td>
              <td>{formatDate(voyage.departureTime)}</td>
              <td>{formatDate(voyage.arrivalTime)}</td>
              <td>{voyage.transitPorts.length > 0 ? voyage.transitPorts.join('、') : '-'}</td>
              <td>{voyage.supportedCargoTypes}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

function formatDate(value: Date): string {
  return value.toISOString().slice(0, 10);
}

function formatCargoType(value: string): string {
  return isCargoType(value) ? CARGO_TYPE_LABELS[value] : value;
}

function hasValue(value: string | undefined): boolean {
  return value !== undefined && value.trim() !== '';
}
