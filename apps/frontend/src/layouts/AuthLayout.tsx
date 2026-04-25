import { Outlet } from 'react-router'

export function AuthLayout() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-gray-900">CargoTracker</h1>
        </div>
        <div className="rounded-lg bg-white p-8 shadow-md">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
