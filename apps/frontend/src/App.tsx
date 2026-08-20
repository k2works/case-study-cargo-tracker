import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './components/require-auth'
import { AppLayout } from './layouts/app-layout'
import { DashboardPage } from './pages/dashboard-page'
import { ForbiddenPage } from './pages/forbidden-page'
import { LoginPage } from './pages/login-page'
import { PortalPage } from './pages/portal-page'
import { BookingListPage } from './pages/booking-list-page'
import { BookingRegisterPage } from './pages/booking-register-page'
import { ShipperListPage } from './pages/shipper-list-page'
import { ShipperRegisterPage } from './pages/shipper-register-page'

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
        <Route path="/booking" element={<BookingListPage />} />
        <Route path="/booking/new" element={<BookingRegisterPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
