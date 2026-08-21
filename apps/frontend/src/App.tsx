import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './components/require-auth'
import { AppLayout } from './layouts/app-layout'
import { DashboardPage } from './pages/dashboard-page'
import { ForbiddenPage } from './pages/forbidden-page'
import { LoginPage } from './pages/login-page'
import { PortalPage } from './pages/portal-page'
import { BookingListPage } from './pages/booking-list-page'
import { BookingDetailPage } from './pages/booking-detail-page'
import { BookingRegisterPage } from './pages/booking-register-page'
import { ShipperListPage } from './pages/shipper-list-page'
import { ShipperRegisterPage } from './pages/shipper-register-page'
import { VoyageListPage } from './pages/voyage-list-page'
import { VoyageDetailPage } from './pages/voyage-detail-page'
import { VoyageRegisterPage } from './pages/voyage-register-page'
import { RouteDesignPage } from './pages/route-design-page'

export default function App() {
  return (
    <Routes>
      {/* 認証の外に置く入口。ここが無いと、未ログインの人はどこにも入れない */}
      <Route path="/" element={<PortalPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/403" element={<ForbiddenPage />} />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
      </Route>

      {/* 荷主の登録・検索は営業担当者の業務。担当外は 403 へ送る */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_SALES']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/booking/shippers" element={<ShipperListPage />} />
        <Route path="/booking/shippers/new" element={<ShipperRegisterPage />} />
        {/* 貨物予約も営業担当者の業務。ROLE_SHIPPER には開かない（ADR-008）。
            利用者と荷主を結ぶキーが無く「自分の予約だけ」に絞り込めないため */}
        <Route path="/booking/new" element={<BookingRegisterPage />} />
      </Route>

      {/* 航海スケジュールの管理は経路設計者の業務。営業に開くと、営業が
          スケジュールと経路確定まで行えてしまい職掌分離が崩れる */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_ROUTING']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/routing/voyages" element={<VoyageListPage />} />
        <Route path="/routing/voyages/new" element={<VoyageRegisterPage />} />
        <Route path="/routing/voyages/:voyageNumber" element={<VoyageDetailPage />} />
        {/* 経路設計は予約を選ばないと開けない。サイドバーには置かず、
            入口は予約詳細の [経路を割り当て] とする（ui_design のナビゲーション表） */}
        <Route path="/routing/design/:bookingId" element={<RouteDesignPage />} />
      </Route>

      {/* 引き渡された予約は経路設計者も見る。中身が見えないと経路を組む判断ができない。
          ただし見える範囲はサーバが依頼済みだけに絞る（ADR-015） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_SALES', 'ROLE_ROUTING']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        {/* 一覧そのものは両者が開く。経路設計者に見えるのは依頼済みだけで、
            絞り込みの指定でその範囲は広げられない（ADR-015・サーバ側で担保） */}
        <Route path="/booking" element={<BookingListPage />} />
        <Route path="/booking/:bookingId" element={<BookingDetailPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
