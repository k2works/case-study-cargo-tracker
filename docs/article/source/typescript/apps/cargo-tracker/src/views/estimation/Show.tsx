import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { CARGO_TYPE_LABELS, type CargoType } from '../../shared/domain/model/cargo-type.js';
import type { EstimateDetail } from '../../contexts/estimation/application/queryservices/estimate-query.service.js';

interface ShowEstimateProps {
  user: AuthenticatedUser;
  estimate: EstimateDetail;
  deadlineMet: boolean;
}

/**
 * 見積詳細画面（/estimates/{id}）。US01。
 * ルート候補（経由港・所要日数・概算料金・航海番号）を一覧し、期限充足を通知する。
 */
export function ShowEstimate({ user, estimate, deadlineMet }: ShowEstimateProps): ReactElement {
  return (
    <Layout title="見積詳細" user={user} activePath="/estimates">
      <h1 className="h3 mb-3" data-testid="estimate-show-heading">
        見積詳細
      </h1>
      <p className="text-muted" data-testid="estimate-number">
        見積番号: {estimate.estimateId}
      </p>
      <dl className="row">
        <dt className="col-sm-3">出発地 → 目的地</dt>
        <dd className="col-sm-9">
          {estimate.origin} → {estimate.destination}
        </dd>
        <dt className="col-sm-3">貨物種別</dt>
        <dd className="col-sm-9">{CARGO_TYPE_LABELS[estimate.cargoType as CargoType]}</dd>
        <dt className="col-sm-3">重量</dt>
        <dd className="col-sm-9">{estimate.weightKg} kg</dd>
      </dl>

      {!deadlineMet && (
        <div className="alert alert-warning" role="alert" data-testid="estimate-deadline-warning">
          希望到着期限に間に合うルート候補が見つかりませんでした。期限の見直しをご検討ください。
        </div>
      )}

      <h2 className="h5 mt-4">ルート候補</h2>
      <table className="table" data-testid="route-candidates">
        <thead>
          <tr>
            <th>航海番号</th>
            <th>経由港</th>
            <th>所要日数</th>
            <th>概算料金</th>
          </tr>
        </thead>
        <tbody>
          {estimate.candidates.map((c) => (
            <tr key={c.voyageNumber}>
              <td>{c.voyageNumber}</td>
              <td>{c.transitPort ?? '直行'}</td>
              <td>{c.transitDays} 日</td>
              <td>¥{Number(c.estimatedCost).toLocaleString('ja-JP')}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <a href="/estimates/new" className="btn btn-outline-primary">
        続けて見積を作成
      </a>
    </Layout>
  );
}
