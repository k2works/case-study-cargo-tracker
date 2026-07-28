import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CargoType, CARGO_TYPE_LABELS } from '../../shared/domain/model/cargo-type.js';
import { CargoTypeFields } from './CargoTypeFields.js';

interface NewBookingProps {
  user: AuthenticatedUser;
  csrfToken?: string;
  error?: string;
  values?: Record<string, string | undefined>;
}

/**
 * 貨物予約登録画面（/bookings/new）。US04/US05。
 * 荷主 ID・荷受人・貨物仕様・輸送条件を入力。危険物/冷凍で条件フィールドを htmx 差替。
 */
export function NewBooking({ user, csrfToken, error, values }: NewBookingProps): ReactElement {
  const v = values ?? {};
  return (
    <Layout title="貨物予約登録" user={user} activePath="/bookings" csrfToken={csrfToken}>
      <h1 className="h3 mb-4" data-testid="booking-new-heading">
        貨物予約登録
      </h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="booking-error">
          {error}
        </div>
      )}
      <form action="/bookings" method="post" className="col-md-6">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}

        <h2 className="h6">荷主</h2>
        <div className="mb-3">
          <label htmlFor="shipperCode" className="form-label">荷主 ID</label>
          <input type="text" className="form-control" id="shipperCode" name="shipperCode" placeholder="SHP-xxxxxxxx" defaultValue={v.shipperCode ?? ''} required />
        </div>

        <h2 className="h6">荷受人</h2>
        <div className="mb-3">
          <label htmlFor="consigneeName" className="form-label">荷受人氏名</label>
          <input type="text" className="form-control" id="consigneeName" name="consigneeName" defaultValue={v.consigneeName ?? ''} required />
        </div>
        <div className="mb-3">
          <label htmlFor="consigneeEmail" className="form-label">荷受人メール</label>
          <input type="email" className="form-control" id="consigneeEmail" name="consigneeEmail" defaultValue={v.consigneeEmail ?? ''} required />
        </div>
        <div className="mb-3">
          <label htmlFor="consigneeAddress" className="form-label">荷受人住所</label>
          <input type="text" className="form-control" id="consigneeAddress" name="consigneeAddress" defaultValue={v.consigneeAddress ?? ''} />
        </div>

        <h2 className="h6">輸送条件</h2>
        <div className="mb-3">
          <label htmlFor="origin" className="form-label">出発地（UN/LOCODE）</label>
          <input type="text" className="form-control" id="origin" name="origin" maxLength={5} defaultValue={v.origin ?? ''} required />
        </div>
        <div className="mb-3">
          <label htmlFor="destination" className="form-label">目的地（UN/LOCODE）</label>
          <input type="text" className="form-control" id="destination" name="destination" maxLength={5} defaultValue={v.destination ?? ''} required />
        </div>
        <div className="mb-3">
          <label htmlFor="arrivalDeadline" className="form-label">希望着日</label>
          <input type="date" className="form-control" id="arrivalDeadline" name="arrivalDeadline" defaultValue={v.arrivalDeadline ?? ''} required />
        </div>

        <h2 className="h6">貨物仕様</h2>
        <div className="mb-3">
          <label htmlFor="weightKg" className="form-label">重量（kg）</label>
          <input type="number" className="form-control" id="weightKg" name="weightKg" min={0.001} step="0.001" defaultValue={v.weightKg ?? ''} required />
        </div>
        <div className="mb-3">
          <label htmlFor="quantity" className="form-label">個数</label>
          <input type="number" className="form-control" id="quantity" name="quantity" min={1} defaultValue={v.quantity ?? ''} />
        </div>
        <div className="mb-3">
          <label htmlFor="description" className="form-label">品名</label>
          <input type="text" className="form-control" id="description" name="description" defaultValue={v.description ?? ''} />
        </div>
        <div className="mb-3">
          <label htmlFor="cargoType" className="form-label">貨物種別</label>
          <select
            className="form-select"
            id="cargoType"
            name="cargoType"
            defaultValue={v.cargoType ?? CargoType.GENERAL}
            hx-get="/bookings/fields"
            hx-target="#cargo-type-fields"
            hx-swap="outerHTML"
            hx-trigger="change"
            hx-include="this"
          >
            {Object.values(CargoType).map((t) => (
              <option key={t} value={t}>{CARGO_TYPE_LABELS[t]}</option>
            ))}
          </select>
        </div>
        <CargoTypeFields cargoType={v.cargoType} />

        <button type="submit" className="btn btn-primary" data-testid="booking-submit">
          予約登録
        </button>
      </form>
    </Layout>
  );
}
