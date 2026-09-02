import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { RequireRole } from '@/shared/auth/RequireRole';
import { AppLayout } from '@/shared/ui/AppLayout';
import { ForbiddenPage } from '@/shared/ui/ForbiddenPage';
import { NAVIGATION } from '@/shared/ui/navigation';
import { ShipperListPage } from '@/features/shippers/ShipperListPage';
import { ShipperRegisterPage } from '@/features/shippers/ShipperRegisterPage';
import { AttentionListPage } from '@/features/attention/AttentionListPage';

/**
 * ルートと画面の対応。
 *
 * <p>許可ロールは NAVIGATION を正典にする。ナビと画面で別々に書くと、
 * ナビには出るのに開くと 403、あるいはその逆が起きる。</p>
 */
const PAGES: Record<string, ReactElement> = {
  '/': <DashboardPage />,
  '/shippers': <ShipperListPage />,
  '/shippers/new': <ShipperRegisterPage />,
  '/worklist/attention': <AttentionListPage />,
};

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/403" element={<ForbiddenPage />} />
      <Route element={<AppLayout />}>
        {NAVIGATION.map((item) => (
          <Route
            key={item.path}
            path={item.path}
            element={<RequireRole allow={item.path === '/shippers' ? ['ROLE_ADMIN'] : item.allow}>{PAGES[item.path]}</RequireRole>}
          />
        ))}
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
