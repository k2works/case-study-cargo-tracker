import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { ShipperType } from '../../contexts/shipper/domain/model/value-objects.js';
import { CorporateFields, EmptyCorporateFields } from './CorporateFields.js';

interface NewShipperProps {
  user: AuthenticatedUser;
  csrfToken?: string;
  error?: string;
  values?: {
    shipperType?: string;
    name?: string;
    email?: string;
  };
}

/**
 * 荷主登録画面（/shippers/new）。US02/US03。
 * 荷主種別「法人」選択で契約情報フィールドを htmx で差し替える。
 */
export function NewShipper({ user, csrfToken, error, values }: NewShipperProps): ReactElement {
  const isCorporate = values?.shipperType === ShipperType.CORPORATE;
  return (
    <Layout title="荷主登録" user={user} activePath="/shippers/new" csrfToken={csrfToken}>
      <h1 className="h3 mb-4" data-testid="shipper-new-heading">
        荷主登録
      </h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="shipper-error">
          {error}
        </div>
      )}
      <form action="/shippers" method="post" className="col-md-6">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
        <div className="mb-3">
          <label htmlFor="shipperType" className="form-label">
            荷主種別
          </label>
          <select
            className="form-select"
            id="shipperType"
            name="shipperType"
            defaultValue={values?.shipperType ?? ShipperType.INDIVIDUAL}
            hx-get="/shippers/fields"
            hx-target="#corporate-fields"
            hx-swap="outerHTML"
            hx-trigger="change"
            hx-include="this"
          >
            <option value={ShipperType.INDIVIDUAL}>個人</option>
            <option value={ShipperType.CORPORATE}>法人</option>
          </select>
        </div>
        <div className="mb-3">
          <label htmlFor="name" className="form-label">
            氏名 / 社名
          </label>
          <input
            type="text"
            className="form-control"
            id="name"
            name="name"
            defaultValue={values?.name ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label htmlFor="email" className="form-label">
            メールアドレス
          </label>
          <input
            type="email"
            className="form-control"
            id="email"
            name="email"
            defaultValue={values?.email ?? ''}
            required
          />
        </div>
        {isCorporate ? <CorporateFields /> : <EmptyCorporateFields />}
        <button type="submit" className="btn btn-primary" data-testid="shipper-submit">
          登録
        </button>
      </form>
    </Layout>
  );
}
