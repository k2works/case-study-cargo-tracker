import { Link } from 'react-router'
import { useAuthStore } from '../stores/authStore'

const MENU_ITEMS = [
  {
    path: '/bookings',
    label: '貨物予約',
    description: '新規予約の登録・予約状況の確認',
    roles: ['ROLE_ADMIN', 'ROLE_SALES'],
  },
  {
    path: '/voyages',
    label: '航海スケジュール',
    description: '航海スケジュールの登録・管理',
    roles: ['ROLE_ADMIN', 'ROLE_ROUTING'],
  },
  {
    path: '/routing/assignments',
    label: '経路設計担当',
    description: '経路設計待ちの予約一覧',
    roles: ['ROLE_ADMIN', 'ROLE_ROUTING'],
  },
  {
    path: '/handling/activities',
    label: '荷役記録',
    description: '荷役作業の記録・管理',
    roles: [],
  },
  {
    path: '/tracking',
    label: '貨物追跡',
    description: '追跡番号による貨物状態照会',
    roles: [],
  },
]

export function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const userRoles = user?.roles ?? []

  const visibleItems = MENU_ITEMS.filter(
    (item) => item.roles.length === 0 || userRoles.some((r) => item.roles.includes(r))
  )

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">ダッシュボード</h1>
      <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>
      {user && (
        <p className="mt-1 text-sm text-gray-500">ようこそ、{user.username} さん</p>
      )}

      <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {visibleItems.map((item) => (
          <Link
            key={item.path}
            to={item.path}
            className="block rounded-lg border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-sm transition"
          >
            <p className="font-semibold text-gray-900">{item.label}</p>
            <p className="mt-1 text-sm text-gray-500">{item.description}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
