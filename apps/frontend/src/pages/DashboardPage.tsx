import { Link } from 'react-router'
import { useAuthStore } from '../stores/authStore'

export function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const isRoutingRole = user?.roles.includes('ROLE_ROUTING') ?? false
  const isSalesOrAdmin =
    user?.roles.some((r) => ['ROLE_ADMIN', 'ROLE_SALES'].includes(r)) ?? false

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">ダッシュボード</h1>
      <p className="text-gray-500 mb-8">国際貨物輸送管理システムへようこそ</p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 max-w-2xl">
        {isSalesOrAdmin && (
          <Link
            to="/bookings"
            className="block rounded-lg border border-gray-200 bg-white p-6 shadow-sm hover:shadow-md transition-shadow"
          >
            <h2 className="text-lg font-semibold text-gray-800 mb-1">予約管理</h2>
            <p className="text-sm text-gray-500">予約の確認・経路設計依頼・確定・追跡番号発行</p>
          </Link>
        )}
        {isRoutingRole && (
          <Link
            to="/routing/voyages"
            className="block rounded-lg border border-gray-200 bg-white p-6 shadow-sm hover:shadow-md transition-shadow"
          >
            <h2 className="text-lg font-semibold text-gray-800 mb-1">航海スケジュール</h2>
            <p className="text-sm text-gray-500">経路設計待ち予約の確認・経路候補算出・紐付け</p>
          </Link>
        )}
        {isSalesOrAdmin && (
          <Link
            to="/shippers"
            className="block rounded-lg border border-gray-200 bg-white p-6 shadow-sm hover:shadow-md transition-shadow"
          >
            <h2 className="text-lg font-semibold text-gray-800 mb-1">荷主管理</h2>
            <p className="text-sm text-gray-500">荷主の登録・確認・更新</p>
          </Link>
        )}
      </div>
    </div>
  )
}
