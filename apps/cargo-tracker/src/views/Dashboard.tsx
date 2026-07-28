import type { ReactElement } from 'react';
import { Layout } from './layout/Layout.js';
import type { AuthenticatedUser } from '../shared/infrastructure/auth/authenticated-user.js';
import { ROLE_LABELS } from '../shared/domain/model/role.js';

interface DashboardProps {
  user: AuthenticatedUser;
  csrfToken?: string;
}

/**
 * ダッシュボード（/）。ロールに応じたサマリーの入口を表示する。
 */
export function Dashboard({ user, csrfToken }: DashboardProps): ReactElement {
  return (
    <Layout title="ダッシュボード" user={user} activePath="/" csrfToken={csrfToken}>
      <h1 className="h3 mb-4" data-testid="dashboard-heading">
        ダッシュボード
      </h1>
      <p data-testid="dashboard-roles">
        ロール: {user.roles.map((r) => ROLE_LABELS[r]).join('、')}
      </p>
    </Layout>
  );
}
