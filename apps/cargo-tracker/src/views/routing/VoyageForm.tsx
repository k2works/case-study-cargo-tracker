import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CargoType, CARGO_TYPE_LABELS } from '../../shared/domain/model/cargo-type.js';

interface VoyageFormProps {
  user: AuthenticatedUser;
  mode: 'new' | 'edit';
  error?: string;
  values?: Record<string, string | string[] | undefined>;
}

export function VoyageForm({ user, mode, error, values }: VoyageFormProps): ReactElement {
  const v = values ?? {};
  const selectedTypes = toSelectedTypes(v.supportedCargoTypes);
  const title = mode === 'new' ? '航海スケジュール登録' : '航海スケジュール更新';
  const action = mode === 'new' ? '/voyages' : `/voyages/${encodeURIComponent(String(v.voyageNumber ?? ''))}`;
  return (
    <Layout title={title} user={user} activePath="/voyages">
      <h1 className="h3 mb-4" data-testid="voyage-form-heading">
        {title}
      </h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="voyage-error">
          {error}
        </div>
      )}
      <form action={action} method="post" className="col-md-7">
        <div className="mb-3">
          <label htmlFor="voyageNumber" className="form-label">航海番号</label>
          <input
            id="voyageNumber"
            name="voyageNumber"
            className="form-control"
            maxLength={20}
            defaultValue={String(v.voyageNumber ?? '')}
            readOnly={mode === 'edit'}
            required
          />
        </div>
        <div className="mb-3">
          <label htmlFor="shipName" className="form-label">船名</label>
          <input id="shipName" name="shipName" className="form-control" defaultValue={String(v.shipName ?? '')} required />
        </div>
        <div className="mb-3">
          <label htmlFor="carrierName" className="form-label">運送会社</label>
          <input id="carrierName" name="carrierName" className="form-control" defaultValue={String(v.carrierName ?? '')} required />
        </div>
        <fieldset className="mb-3">
          <legend className="h6">対応貨物種別</legend>
          {Object.values(CargoType).map((cargoType) => (
            <div className="form-check form-check-inline" key={cargoType}>
              <input
                className="form-check-input"
                type="checkbox"
                id={`cargo-${cargoType}`}
                name="supportedCargoTypes"
                value={cargoType}
                defaultChecked={selectedTypes.includes(cargoType)}
              />
              <label className="form-check-label" htmlFor={`cargo-${cargoType}`}>
                {CARGO_TYPE_LABELS[cargoType]}
              </label>
            </div>
          ))}
        </fieldset>
        <h2 className="h6">運送区間</h2>
        <div className="row">
          <div className="col-md-6 mb-3">
            <label htmlFor="departureLocation" className="form-label">出発港</label>
            <input id="departureLocation" name="departureLocation" className="form-control" maxLength={5} defaultValue={String(v.departureLocation ?? '')} required />
          </div>
          <div className="col-md-6 mb-3">
            <label htmlFor="arrivalLocation" className="form-label">到着港</label>
            <input id="arrivalLocation" name="arrivalLocation" className="form-control" maxLength={5} defaultValue={String(v.arrivalLocation ?? '')} required />
          </div>
        </div>
        <div className="row">
          <div className="col-md-6 mb-3">
            <label htmlFor="departureTime" className="form-label">出発日</label>
            <input id="departureTime" name="departureTime" type="datetime-local" className="form-control" defaultValue={String(v.departureTime ?? '')} required />
          </div>
          <div className="col-md-6 mb-3">
            <label htmlFor="arrivalTime" className="form-label">到着日</label>
            <input id="arrivalTime" name="arrivalTime" type="datetime-local" className="form-control" defaultValue={String(v.arrivalTime ?? '')} required />
          </div>
        </div>
        <button type="submit" className="btn btn-primary" data-testid="voyage-submit">
          {mode === 'new' ? '登録' : '更新する'}
        </button>
      </form>
    </Layout>
  );
}

function toSelectedTypes(value: string | string[] | undefined): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  return value ? [value] : [CargoType.GENERAL];
}
