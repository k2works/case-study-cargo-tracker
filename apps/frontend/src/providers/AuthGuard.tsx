import { Navigate, Outlet } from 'react-router'
import { useAuthStore } from '../stores/authStore'

export function AuthGuard() {
  const token = useAuthStore((s) => s.token)

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
