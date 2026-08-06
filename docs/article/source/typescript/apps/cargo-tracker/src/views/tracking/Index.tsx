import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';

interface TrackingIndexProps {
  user: AuthenticatedUser;
  error?: string;
}

/** 貨物追跡入力画面（/tracking）。追跡番号を入力して追跡詳細へ遷移する */
export function TrackingIndex({ user, error }: TrackingIndexProps): ReactElement {
  return (
    <Layout title="貨物追跡入力" user={user} activePath="/tracking">
      <h1 className="h3 mb-3" data-testid="tracking-index-heading">貨物追跡入力</h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="tracking-error">
          {error}
        </div>
      )}
      <form action="/tracking" method="get" className="row g-2 align-items-end" data-testid="tracking-form">
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
          <button type="submit" className="btn btn-primary" data-testid="track-submit">追跡する</button>
        </div>
      </form>
    </Layout>
  );
}
