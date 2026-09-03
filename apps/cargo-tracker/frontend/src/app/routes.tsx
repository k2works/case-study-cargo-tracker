import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { PublicTrackingPage } from '@/features/tracking/PublicTrackingPage';
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
      {/* 公開追跡は認証の外。ロール別の到達性は認証済みの利用者にしか働かないので、
          荷受人が使う経路は NAVIGATION ではなくここに置く（ui_design.md）。 */}
      <Route path="/track" element={<PublicTrackingPage />} />
      <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
      <Route element={<AppLayout />}>
        {NAVIGATION.map((item) => (
          <Route
            key={item.path}
            path={item.path}
            element={<RequireRole allow={item.allow}>{PAGES[item.path]}</RequireRole>}
          />
        ))}
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
