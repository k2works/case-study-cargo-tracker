import { Link } from 'react-router'
import { useRoutingAssignments } from '../features/booking/hooks/useBookings'

export function RoutingAssignmentPage() {
  const { data: cargos, isLoading, isError } = useRoutingAssignments()

  if (isLoading) return <p className="p-6 text-center text-gray-500">読み込み中...</p>
  if (isError) return <p className="p-6 text-center text-red-500">データの読み込みに失敗しました。</p>

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">経路設計担当一覧</h1>

      {!cargos || cargos.length === 0 ? (
        <p className="text-sm text-gray-500">経路設計待ちの予約はありません。</p>
      ) : (
        <div className="overflow-hidden bg-white border border-gray-200 rounded-lg">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-500">予約 ID</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">貨物種別</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">出発地</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">目的地</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">到着期限</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {cargos.map((cargo) => (
                <tr key={cargo.bookingId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-gray-900">{cargo.bookingId}</td>
                  <td className="px-4 py-3 text-gray-700">{cargo.cargoType}</td>
                  <td className="px-4 py-3 font-mono text-gray-700">{cargo.originUnlocode}</td>
                  <td className="px-4 py-3 font-mono text-gray-700">{cargo.destinationUnlocode}</td>
                  <td className="px-4 py-3 text-gray-500">
                    {cargo.arrivalDeadline ?? '-'}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/routing/design/${cargo.bookingId}`}
                      className="text-indigo-600 hover:text-indigo-800 text-sm font-medium"
                    >
                      経路設計
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
