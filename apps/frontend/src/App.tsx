import { Routes, Route, Navigate } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { AuthGuard } from './providers/AuthGuard'
import { LoginPage } from './pages/LoginPage'
import { ShipperListPage } from './pages/ShipperListPage'
import { ShipperNewPage } from './pages/ShipperNewPage'

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
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/shippers" replace />} />
    </Routes>
  )
}
