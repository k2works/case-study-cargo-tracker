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

function Dashboard() {
  return (
    <section>
      <h1>ダッシュボード</h1>
      <p>国際貨物輸送管理システム</p>
    </section>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<PrivateRoute />}>
          <Route path="/" element={<Dashboard />} />
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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
