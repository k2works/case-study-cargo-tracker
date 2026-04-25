import { Outlet } from 'react-router'

export function AppLayout() {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-lg font-semibold text-gray-900">Cargo Tracker</h1>
        </div>
      </header>
      <main className="max-w-7xl mx-auto">
        <Outlet />
      </main>
    </div>
  )
}
