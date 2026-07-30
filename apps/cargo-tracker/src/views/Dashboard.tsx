import type { ReactElement } from 'react';
import { Layout } from './layout/Layout.js';
import type { AuthenticatedUser } from '../shared/infrastructure/auth/authenticated-user.js';
import { Role, ROLE_LABELS } from '../shared/domain/model/role.js';

interface DashboardProps {
  user: AuthenticatedUser;
  csrfToken?: string;
  success?: string;
  /** 未払い（OVERDUE）請求件数。経理担当者カードに表示する（0 は非表示） */
  overdueInvoices?: number;
}

/**
 * ダッシュボード（/）。ロールに応じたサマリーの入口を表示する。
 */
export function Dashboard({ user, csrfToken, success, overdueInvoices = 0 }: DashboardProps): ReactElement {
  const isBilling = user.roles.includes(Role.BILLING);
  return (
    <Layout title="ダッシュボード" user={user} activePath="/" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="dashboard-flash">
          {success}
        </div>
      )}
      <h1 className="h3 mb-4" data-testid="dashboard-heading">
        ダッシュボード
      </h1>
      <p data-testid="dashboard-roles">
        ロール: {user.roles.map((r) => ROLE_LABELS[r]).join('、')}
      </p>
      {isBilling && (
        <div className="card border-warning mb-3" style={{ maxWidth: '20rem' }} data-testid="billing-overdue-card">
          <div className="card-body">
            <h2 className="h6 card-title text-muted">未払い請求（支払期限超過）</h2>
            <p className="display-6 mb-2" data-testid="overdue-count">
              {overdueInvoices} 件
            </p>
            <a href="/billing/invoices" className="btn btn-outline-warning btn-sm">
              請求管理へ
            </a>
          </div>
        </div>
      )}
    </Layout>
  );
}
