import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/contexts/AuthContext';
import LoginPage from './features/auth/pages/LoginPage';
import PrivateRoute from './components/layout/PrivateRoute';
import VoyageListPage from './features/voyage/pages/VoyageListPage';
import VoyageFormPage from './features/voyage/pages/VoyageFormPage';
import ShipperListPage from './features/shipper/pages/ShipperListPage';
import ShipperFormPage from './features/shipper/pages/ShipperFormPage';

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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
