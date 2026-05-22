import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/contexts/AuthContext';
import Navigation from './Navigation';

export default function PrivateRoute() {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="min-h-screen bg-gray-50">
      <Navigation />
      <main className="max-w-7xl mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
