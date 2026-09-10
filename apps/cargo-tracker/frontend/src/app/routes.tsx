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
import { CustomsListPage } from '@/features/customs/CustomsListPage';
import { CustomsRegisterPage } from '@/features/customs/CustomsRegisterPage';
import { CustomsDetailPage } from '@/features/customs/CustomsDetailPage';
import { AdminUserListPage } from '@/features/admin/AdminUserListPage';
import { BookingListPage } from '@/features/bookings/BookingListPage';
import { BookingRegisterPage } from '@/features/bookings/BookingRegisterPage';
import { BookingDetailPage } from '@/features/bookings/BookingDetailPage';
import { BookingEditPage } from '@/features/bookings/BookingEditPage';
import { RoutingWorkbenchPage } from '@/features/routing/RoutingWorkbenchPage';
import { RoutingWorklistPage } from '@/features/routing/RoutingWorklistPage';
import { VoyageListPage } from '@/features/routing/VoyageListPage';
import { VoyageRegisterPage } from '@/features/routing/VoyageRegisterPage';
import { VoyageDetailPage } from '@/features/routing/VoyageDetailPage';
import { TrackingListPage } from '@/features/tracking/TrackingListPage';
import { ExceptionListPage } from '@/features/tracking/ExceptionListPage';
import { ExceptionReportPage } from '@/features/tracking/ExceptionReportPage';
import { TrackingDetailPage } from '@/features/tracking/TrackingDetailPage';
import { AwaitingClaimPage } from '@/features/handling/AwaitingClaimPage';
import { HandlingHistoryPage } from '@/features/handling/HandlingHistoryPage';
import { HandlingRecordPage } from '@/features/handling/HandlingRecordPage';

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
  '/tracking': <TrackingListPage />,
  '/handling': <HandlingHistoryPage />,
  '/customs': <CustomsListPage />,
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
        {/* 予約修正（S24）は詳細から開く。修正は営業だけ（US32）。
            参照（S22）は経路設計・追跡にも開いたままにする。 */}
        <Route
          path="/bookings/:bookingId/edit"
          element={
            <RequireRole allow={['ROLE_SALES']}>
              <BookingEditPage />
            </RequireRole>
          }
        />
        {/* 経路設計ワークベンチ（S31）は作業一覧（S30）から開く。
            ナビには載せない（一覧から開く画面）。経路設計者だけ。 */}
        <Route
          path="/routing/bookings/:bookingId"
          element={
            <RequireRole allow={['ROLE_ROUTING']}>
              <RoutingWorkbenchPage />
            </RequireRole>
          }
        />
        {/* 航海詳細（S34）と更新（S33 の編集）は一覧・詳細から開く。
            ナビに載せると「航海詳細」という行き先の無い項目が出る。
            ロールは一覧（S32）と同じ経路設計者に揃える。 */}
        {/* 荷役の記録（S50）は航海を選んで開く。荷役ロールだけ。 */}
        <Route
          path="/handling/voyages/:voyageNumber"
          element={
            <RequireRole allow={['ROLE_HANDLER']}>
              <HandlingRecordPage />
            </RequireRole>
          }
        />
        {/* 引取の記録（S54 から開く）。**航海番号を取らない**——引取は船から
            降りたあとの作業で、どの航海の仕事でもない。`/handling/voyages/*`
            より前でも後でも当たらないので順序は問わない。 */}
        <Route
          path="/handling/claim"
          element={
            <RequireRole allow={['ROLE_HANDLER']}>
              <HandlingRecordPage />
            </RequireRole>
          }
        />
        {/* 引取待ち（H.8 / US16）は荷役ロールだけ。**`/handling/:trackingNumber`
            より先に置く。** 後ろに置くと "awaiting-claim" が追跡番号として
            吸われ、荷役履歴が「見つかりません」になる。 */}
        <Route
          path="/handling/awaiting-claim"
          element={
            <RequireRole allow={['ROLE_HANDLER']}>
              <AwaitingClaimPage />
            </RequireRole>
          }
        />
        {/* 通関申告の登録（S53）は荷役ロールだけ。**`/customs/:no` より先に置く。**
            後ろに置くと "new" が申告番号として吸われ、登録画面が開けない
            （引取待ちで踏んだのと同じ形）。 */}
        <Route
          path="/customs/new"
          element={
            <RequireRole allow={['ROLE_HANDLER']}>
              <CustomsRegisterPage />
            </RequireRole>
          }
        />
        {/* 通関申告（S53）は荷役と追跡の両方が開く。更新のフォームは追跡だけに出す
            ——**開ける場所と操作できる範囲は別**である（ui_design.md の画面一覧）。 */}
        <Route
          path="/customs/:declarationNumber"
          element={
            <RequireRole allow={['ROLE_HANDLER', 'ROLE_TRACKER']}>
              <CustomsDetailPage />
            </RequireRole>
          }
        />
        {/* 荷役履歴（S51）は荷役と追跡の両方（ui_design.md:236）。 */}
        <Route
          path="/handling/:trackingNumber"
          element={
            <RequireRole allow={['ROLE_HANDLER', 'ROLE_TRACKER']}>
              <HandlingHistoryPage />
            </RequireRole>
          }
        />
        {/* 例外一覧（S42）は追跡管理者と**管理者**（US19・US20 §3）、起票（S43）は
            追跡管理者だけ。管理者は緊急の知らせを読む側で、起票はしない。
            **`/tracking/:trackingNumber` より先に置く。** 後ろに置くと
            "exceptions" が追跡番号として吸われる。 */}
        <Route
          path="/tracking/exceptions"
          element={
            <RequireRole allow={['ROLE_TRACKER', 'ROLE_ADMIN']}>
              <ExceptionListPage />
            </RequireRole>
          }
        />
        <Route
          path="/tracking/:trackingNumber/exceptions/new"
          element={
            <RequireRole allow={['ROLE_TRACKER']}>
              <ExceptionReportPage />
            </RequireRole>
          }
        />
        {/* 追跡詳細（S41）は一覧から開く。追跡管理者と荷主の両方が使い、
            更新の操作は画面が追跡管理者にだけ出す（ui_design.md:145）。 */}
        <Route
          path="/tracking/:trackingNumber"
          element={
            /* **管理者にも開く**（US20 §3 / IT11 レビュー 高）。緊急を知らせる先が
               「読むだけの一覧」で止まると、追跡番号を書き写して電話で追跡管理者を
               探すところから始まる。**操作は出さない**——手動更新も例外の対応も
               追跡管理者の仕事で、画面が `isTracker` で出し分ける。 */
            <RequireRole allow={['ROLE_TRACKER', 'ROLE_SHIPPER', 'ROLE_ADMIN']}>
              <TrackingDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="/voyages/:voyageNumber"
          element={
            <RequireRole allow={['ROLE_ROUTING']}>
              <VoyageDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="/voyages/:voyageNumber/edit"
          element={
            <RequireRole allow={['ROLE_ROUTING']}>
              <VoyageRegisterPage />
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
