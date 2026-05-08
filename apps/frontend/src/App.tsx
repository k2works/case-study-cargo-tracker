import { Routes, Route, Navigate } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { AuthGuard } from './providers/AuthGuard'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { VoyageListPage } from './pages/VoyageListPage'
import { VoyageNewPage } from './pages/VoyageNewPage'
import { VoyageEditPage } from './pages/VoyageEditPage'
import { BookingListPage } from './pages/BookingListPage'
import { BookingNewPage } from './pages/BookingNewPage'
import { BookingDetailPage } from './pages/BookingDetailPage'
import { RoutingDesignPage } from './pages/RoutingDesignPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<AuthGuard />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/voyages" element={<VoyageListPage />} />
          <Route path="/voyages/new" element={<VoyageNewPage />} />
          <Route path="/voyages/:voyageNumber/edit" element={<VoyageEditPage />} />
          <Route path="/bookings" element={<BookingListPage />} />
          <Route path="/bookings/new" element={<BookingNewPage />} />
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
          <Route path="/routing/design/:bookingId" element={<RoutingDesignPage />} />
          <Route path="/routing/design" element={<RoutingDesignPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
