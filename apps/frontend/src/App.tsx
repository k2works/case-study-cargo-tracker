import { Routes, Route, Navigate } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { AuthGuard } from './providers/AuthGuard'
import { LoginPage } from './pages/LoginPage'
import { ShipperListPage } from './pages/ShipperListPage'
import { ShipperNewPage } from './pages/ShipperNewPage'
import { BookingListPage } from './pages/BookingListPage'
import { BookingNewPage } from './pages/BookingNewPage'
import { BookingDetailPage } from './pages/BookingDetailPage'
import { VoyageListPage } from './pages/VoyageListPage'
import { VoyageNewPage } from './pages/VoyageNewPage'
import { VoyageEditPage } from './pages/VoyageEditPage'
import { QuotationListPage } from './pages/QuotationListPage'
import { QuotationNewPage } from './pages/QuotationNewPage'
import { QuotationDetailPage } from './pages/QuotationDetailPage'
import { RoutingWorkbenchPage } from './pages/RoutingWorkbenchPage'
import { DashboardPage } from './pages/DashboardPage'
import { HandlingActivityNewPage } from './pages/HandlingActivityNewPage'
import { HandlingActivityListPage } from './pages/HandlingActivityListPage'
import { CargoStatusUpdatePage } from './pages/CargoStatusUpdatePage'
import { TrackingPublicPage } from './pages/TrackingPublicPage'
import { TrackingListPage } from './pages/TrackingListPage'
import { TrackingExceptionNewPage } from './pages/TrackingExceptionNewPage'
import { BillingListPage } from './pages/BillingListPage'
import { BillingDetailPage } from './pages/BillingDetailPage'
import { BillingIssuePage } from './pages/BillingIssuePage'
import { BillingOverduePage } from './pages/BillingOverduePage'

export default function App() {
  return (
    <Routes>
      {/* US18: 公開追跡照会（ログイン不要・サイドナビなし） */}
      <Route path="/tracking/:trackingNumber" element={<TrackingPublicPage />} />
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<AuthGuard />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/shippers" element={<ShipperListPage />} />
          <Route path="/shippers/new" element={<ShipperNewPage />} />
          <Route path="/bookings" element={<BookingListPage />} />
          <Route path="/bookings/new" element={<BookingNewPage />} />
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
          <Route path="/quotations" element={<QuotationListPage />} />
          <Route path="/quotations/new" element={<QuotationNewPage />} />
          <Route path="/quotations/:quotationId" element={<QuotationDetailPage />} />
          <Route path="/routing/workbench/:bookingId" element={<RoutingWorkbenchPage />} />
          <Route path="/routing/voyages" element={<VoyageListPage />} />
          <Route path="/routing/voyages/new" element={<VoyageNewPage />} />
          <Route path="/routing/voyages/:voyageNumber/edit" element={<VoyageEditPage />} />
          <Route path="/handling" element={<HandlingActivityListPage />} />
          <Route path="/handling/new" element={<HandlingActivityNewPage />} />
          <Route path="/tracking" element={<TrackingListPage />} />
          <Route path="/tracking/:trackingNumber/manage" element={<CargoStatusUpdatePage />} />
          <Route path="/tracking/:trackingNumber/exceptions/new" element={<TrackingExceptionNewPage />} />
          <Route path="/billing" element={<BillingListPage />} />
          <Route path="/billing/overdue" element={<BillingOverduePage />} />
          <Route path="/billing/:invoiceId" element={<BillingDetailPage />} />
          <Route path="/billing/:invoiceId/issue" element={<BillingIssuePage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
