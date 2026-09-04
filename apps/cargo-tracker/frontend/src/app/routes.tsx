import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { PublicTrackingPage } from '@/features/tracking/PublicTrackingPage';
import { PortalPage } from '@/features/portal/PortalPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { RequireRole } from '@/shared/auth/RequireRole';
import { AppLayout } from '@/shared/ui/AppLayout';
import { ForbiddenPage } from '@/shared/ui/ForbiddenPage';
import { NAVIGATION } from '@/shared/ui/navigation';
import { ShipperListPage } from '@/features/shippers/ShipperListPage';
import { ShipperRegisterPage } from '@/features/shippers/ShipperRegisterPage';
import { AttentionListPage } from '@/features/attention/AttentionListPage';
import { AdminUserListPage } from '@/features/admin/AdminUserListPage';
import { BookingListPage } from '@/features/bookings/BookingListPage';
import { BookingRegisterPage } from '@/features/bookings/BookingRegisterPage';
import { BookingDetailPage } from '@/features/bookings/BookingDetailPage';
import { RoutingWorklistPage } from '@/features/routing/RoutingWorklistPage';
import { VoyageListPage } from '@/features/routing/VoyageListPage';
import { VoyageRegisterPage } from '@/features/routing/VoyageRegisterPage';

/**
 * ルートと画面の対応。
 *
 * <p>許可ロールは NAVIGATION を正典にする。ナビと画面で別々に書くと、
 * ナビには出るのに開くと 403、あるいはその逆が起きる。</p>
 */
/**
 * 画面の実体。**キーは NAVIGATION の path と一致していなければならない。**
 * ここにあってナビに無い画面は、実装されているのに誰も辿り着けない。
 * 一致は `navigationMatchesUiDesign.test.ts` が見る。
 */
export const PAGES: Record<string, ReactElement> = {
  '/': <DashboardPage />,
  '/shippers': <ShipperListPage />,
  '/shippers/new': <ShipperRegisterPage />,
  '/bookings': <BookingListPage />,
  '/bookings/new': <BookingRegisterPage />,
  '/routing/worklist': <RoutingWorklistPage />,
  '/voyages': <VoyageListPage />,
  '/voyages/new': <VoyageRegisterPage />,
  '/worklist/attention': <AttentionListPage />,
  '/admin/users': <AdminUserListPage />,
};

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* 公開追跡は認証の外。ロール別の到達性は認証済みの利用者にしか働かないので、
          荷受人が使う経路は NAVIGATION ではなくここに置く（ui_design.md）。 */}
      <Route path="/portal" element={<PortalPage />} />
      <Route path="/track" element={<PublicTrackingPage />} />
      <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
      <Route element={<AppLayout />}>
        {/* 403 はシェルの内側に置く。権限の無い画面を開いただけでサイドナビまで
            失うと、戻る手段が本文のリンク 1 本になる。 */}
        <Route path="/403" element={<ForbiddenPage />} />
        {/* 一覧から開く画面はナビに載せない。載せると「予約詳細」という
            行き先の無い項目がサイドナビに出る。到達性は一覧のリンクで担保する。 */}
        <Route
          path="/bookings/:bookingId"
          element={
            <RequireRole allow={['ROLE_SALES', 'ROLE_ROUTING', 'ROLE_TRACKER']}>
              <BookingDetailPage />
            </RequireRole>
          }
        />
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
