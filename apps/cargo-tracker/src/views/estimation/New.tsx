import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CargoType, CARGO_TYPE_LABELS } from '../../shared/domain/model/cargo-type.js';
import type { LocationOption } from '../../shared/infrastructure/database/location-query.js';
import { LocationDatalist } from '../fragments/LocationDatalist.js';
import { HazardousFields, EmptyHazardousFields } from './HazardousFields.js';

interface NewEstimateProps {
  user: AuthenticatedUser;
  csrfToken?: string;
  error?: string;
  locations?: LocationOption[];
  values?: { cargoType?: string; origin?: string; destination?: string };
}

/**
 * 見積作成画面（/estimates/new）。US01。
 * 貨物種別「危険物」選択で危険物申告フィールドを htmx で差し替える。
 */
export function NewEstimate({
  user,
  csrfToken,
  error,
  locations,
  values,
}: NewEstimateProps): ReactElement {
  const isHazardous = values?.cargoType === CargoType.HAZARDOUS;
  return (
    <Layout title="見積作成" user={user} activePath="/estimates" csrfToken={csrfToken}>
      <h1 className="h3 mb-4" data-testid="estimate-new-heading">
        輸送見積作成
      </h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="estimate-error">
          {error}
        </div>
      )}
      <form action="/estimates" method="post" className="col-md-6">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
        <div className="mb-3">
          <label htmlFor="origin" className="form-label">
            出発地（UN/LOCODE）
          </label>
          <input
            type="text"
            className="form-control"
            id="origin"
            name="origin"
            maxLength={5}
            list="unlocodes"
            placeholder="例: JPTYO"
            defaultValue={values?.origin ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label htmlFor="destination" className="form-label">
            目的地（UN/LOCODE）
          </label>
          <input
            type="text"
            className="form-control"
            id="destination"
            name="destination"
            maxLength={5}
            list="unlocodes"
            placeholder="例: USLAX"
            defaultValue={values?.destination ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label htmlFor="arrivalDeadline" className="form-label">
            希望到着期限
          </label>
          <input type="date" className="form-control" id="arrivalDeadline" name="arrivalDeadline" required />
        </div>
        <div className="mb-3">
          <label htmlFor="weightKg" className="form-label">
            重量（kg）
          </label>
          <input type="number" className="form-control" id="weightKg" name="weightKg" min={0.001} step="0.001" required />
        </div>
        <div className="mb-3">
          <label htmlFor="cargoType" className="form-label">
            貨物種別
          </label>
          <select
            className="form-select"
            id="cargoType"
            name="cargoType"
            defaultValue={values?.cargoType ?? CargoType.GENERAL}
            hx-get="/estimates/fields"
            hx-target="#hazardous-fields"
            hx-swap="outerHTML"
            hx-trigger="change"
            hx-include="this"
          >
            {Object.values(CargoType).map((t) => (
              <option key={t} value={t}>
                {CARGO_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </div>
        {isHazardous ? <HazardousFields /> : <EmptyHazardousFields />}
        <button type="submit" className="btn btn-primary" data-testid="estimate-submit">
          見積作成
        </button>
        <LocationDatalist locations={locations ?? []} />
      </form>
    </Layout>
  );
}
