import { useAuthStore } from '../stores/authStore'

export function DashboardPage() {
  const user = useAuthStore((s) => s.user)

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold">ダッシュボード</h1>
      <p className="mt-2 text-gray-600">
        国際貨物輸送管理システム
      </p>
      {user && (
        <p className="mt-4 text-sm text-gray-500">
          ようこそ、{user.username} さん
        </p>
      )}
    </div>
  )
}
