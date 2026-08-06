import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CARGO_TYPE_LABELS, type CargoType } from '../../shared/domain/model/cargo-type.js';
import type { EstimateListItem } from '../../contexts/estimation/application/queryservices/estimate-query.service.js';

interface IndexEstimateProps {
  user: AuthenticatedUser;
  estimates: EstimateListItem[];
}

/**
 * 見積一覧画面（/estimates）。US01。
 */
export function IndexEstimate({ user, estimates }: IndexEstimateProps): ReactElement {
  return (
    <Layout title="見積一覧" user={user} activePath="/estimates">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3" data-testid="estimate-index-heading">
          見積一覧
        </h1>
        <a href="/estimates/new" className="btn btn-primary" data-testid="estimate-new-link">
          見積作成
        </a>
      </div>
      {estimates.length === 0 ? (
        <p className="text-muted" data-testid="estimate-empty">
          見積はまだありません。
        </p>
      ) : (
        <table className="table" data-testid="estimate-list">
          <thead>
            <tr>
              <th>見積番号</th>
              <th>出発地 → 目的地</th>
              <th>貨物種別</th>
              <th>重量</th>
            </tr>
          </thead>
          <tbody>
            {estimates.map((e) => (
              <tr key={e.estimateId}>
                <td>
                  <a href={`/estimates/${e.estimateId}`}>{e.estimateId.slice(0, 8)}</a>
                </td>
                <td>
                  {e.origin} → {e.destination}
                </td>
                <td>{CARGO_TYPE_LABELS[e.cargoType as CargoType]}</td>
                <td>{e.weightKg} kg</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
