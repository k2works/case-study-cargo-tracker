import { Routes, Route, Navigate } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { AuthGuard } from './providers/AuthGuard'
import { LoginPage } from './pages/LoginPage'
import { ShipperListPage } from './pages/ShipperListPage'
import { ShipperNewPage } from './pages/ShipperNewPage'
import { BookingListPage } from './pages/BookingListPage'
import { BookingNewPage } from './pages/BookingNewPage'
import { VoyageListPage } from './pages/VoyageListPage'
import { VoyageNewPage } from './pages/VoyageNewPage'
import { VoyageEditPage } from './pages/VoyageEditPage'
import { QuotationNewPage } from './pages/QuotationNewPage'
import { QuotationDetailPage } from './pages/QuotationDetailPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<AuthGuard />}>
        <Route element={<AppLayout />}>
          <Route path="/shippers" element={<ShipperListPage />} />
          <Route path="/shippers/new" element={<ShipperNewPage />} />
          <Route path="/bookings" element={<BookingListPage />} />
          <Route path="/bookings/new" element={<BookingNewPage />} />
          <Route path="/quotations/new" element={<QuotationNewPage />} />
          <Route path="/quotations/:quotationId" element={<QuotationDetailPage />} />
          <Route path="/routing/voyages" element={<VoyageListPage />} />
          <Route path="/routing/voyages/new" element={<VoyageNewPage />} />
          <Route path="/routing/voyages/:voyageNumber/edit" element={<VoyageEditPage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/shippers" replace />} />
    </Routes>
  )
}
