import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/contexts/AuthContext';
import LoginPage from './features/auth/pages/LoginPage';
import PrivateRoute from './components/layout/PrivateRoute';
import VoyageListPage from './features/voyage/pages/VoyageListPage';
import VoyageFormPage from './features/voyage/pages/VoyageFormPage';
import ShipperListPage from './features/shipper/pages/ShipperListPage';
import ShipperFormPage from './features/shipper/pages/ShipperFormPage';
import BookingListPage from './features/booking/pages/BookingListPage';
import BookingFormPage from './features/booking/pages/BookingFormPage';
import BookingDetailPage from './features/booking/pages/BookingDetailPage';
import QuotationListPage from './features/quote/pages/QuotationListPage';
import QuotationFormPage from './features/quote/pages/QuotationFormPage';
import QuotationDetailPage from './features/quote/pages/QuotationDetailPage';
import RouteDesignListPage from './features/routing/pages/RouteDesignListPage';
import RouteDesignWorkbenchPage from './features/routing/pages/RouteDesignWorkbenchPage';
import TrackingListPage from './features/tracking/pages/TrackingListPage';
import TrackingManagePage from './features/tracking/pages/TrackingManagePage';
import TrackingPublicPage from './features/tracking/pages/TrackingPublicPage';
import ExceptionRegisterPage from './features/tracking/pages/ExceptionRegisterPage';
import ExceptionListPage from './features/tracking/pages/ExceptionListPage';
import HandlingListPage from './features/handling/pages/HandlingListPage';
import HandlingFormPage from './features/handling/pages/HandlingFormPage';
import InvoiceDetailPage from './features/billing/pages/InvoiceDetailPage';
import InvoiceListPage from './features/billing/pages/InvoiceListPage';
import OverdueListPage from './features/billing/pages/OverdueListPage';
import DashboardPage from './features/dashboard/pages/DashboardPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {/* US18 公開追跡照会（PrivateRoute 外、token クエリで JWT 検証） */}
        <Route path="/tracking/:trackingNumber" element={<TrackingPublicPage />} />
        <Route element={<PrivateRoute />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/voyages" element={<VoyageListPage />} />
          <Route path="/voyages/new" element={<VoyageFormPage />} />
          <Route path="/voyages/:voyageNumber/edit" element={<VoyageFormPage />} />
          <Route path="/shippers" element={<ShipperListPage />} />
          <Route path="/shippers/new" element={<ShipperFormPage />} />
          <Route path="/bookings" element={<BookingListPage />} />
          <Route path="/bookings/new" element={<BookingFormPage />} />
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
          <Route path="/quotes" element={<QuotationListPage />} />
          <Route path="/quotes/new" element={<QuotationFormPage />} />
          <Route path="/quotes/:quotationId" element={<QuotationDetailPage />} />
          <Route path="/routing/design" element={<RouteDesignListPage />} />
          <Route path="/routing/design/:bookingId" element={<RouteDesignWorkbenchPage />} />
          <Route path="/tracking" element={<TrackingListPage />} />
          <Route path="/tracking/exceptions" element={<ExceptionListPage />} />
          <Route
            path="/tracking/:trackingNumber/exceptions/new"
            element={<ExceptionRegisterPage />}
          />
          <Route path="/tracking/:trackingNumber/manage" element={<TrackingManagePage />} />
          <Route path="/handling" element={<HandlingListPage />} />
          <Route path="/handling/new" element={<HandlingFormPage />} />
          <Route path="/billing" element={<InvoiceListPage />} />
          <Route path="/billing/overdue" element={<OverdueListPage />} />
          <Route path="/billing/:invoiceId" element={<InvoiceDetailPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
