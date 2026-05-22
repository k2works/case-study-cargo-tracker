import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/contexts/AuthContext';
import LoginPage from './features/auth/pages/LoginPage';
import PrivateRoute from './components/layout/PrivateRoute';

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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
