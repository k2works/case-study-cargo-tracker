import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/contexts/AuthContext';
import Navigation from './Navigation';

export default function PrivateRoute() {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return (
    <>
      <Navigation />
      <Outlet />
    </>
  );
}
