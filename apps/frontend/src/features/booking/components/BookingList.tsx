import { Link } from 'react-router'
import { useBookings } from '../hooks/useBookings'
import type { BookingStatus } from '../types/cargo'

const STATUS_LABELS: Record<BookingStatus, string> = {
  PRELIMINARY: '仮予約',
  ROUTE_PROPOSED: '経路提案済み',
  CONFIRMED: '確定',
  TRACKING_ISSUED: '追跡番号発行済み',
  IN_TRANSIT: '輸送中',
  DELIVERED: '配達完了',
  SETTLED: '精算済み',
  CANCELLED: 'キャンセル',
}

const STATUS_COLORS: Record<BookingStatus, string> = {
  PRELIMINARY: 'bg-yellow-100 text-yellow-800',
  ROUTE_PROPOSED: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  TRACKING_ISSUED: 'bg-indigo-100 text-indigo-800',
  IN_TRANSIT: 'bg-purple-100 text-purple-800',
  DELIVERED: 'bg-gray-100 text-gray-800',
  SETTLED: 'bg-teal-100 text-teal-800',
  CANCELLED: 'bg-red-100 text-red-800',
}

export function BookingList() {
  const { data: cargos, isLoading, isError } = useBookings()

  if (isLoading) return <p className="py-4 text-center text-gray-500">読み込み中...</p>
  if (isError) return <p className="py-4 text-center text-red-500">データの取得に失敗しました。</p>
  if (!cargos || cargos.length === 0) {
    return <p className="py-8 text-center text-gray-500">予約が登録されていません。</p>
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 border border-gray-200 rounded-lg">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">予約 ID</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">ステータス</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">貨物種別</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">重量 (kg)</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">出発地</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">到着地</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">到着期限</th>
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {cargos.map((cargo) => (
            <tr key={cargo.bookingId} className="hover:bg-gray-50">
              <td className="px-4 py-3 text-sm font-mono">
                <Link to={`/bookings/${cargo.bookingId}`} className="text-blue-600 hover:underline">
                  {cargo.bookingId}
                </Link>
              </td>
              <td className="px-4 py-3 text-sm">
                <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[cargo.bookingStatus]}`}>
                  {STATUS_LABELS[cargo.bookingStatus]}
                </span>
              </td>
              <td className="px-4 py-3 text-sm text-gray-700">{cargo.cargoType}</td>
              <td className="px-4 py-3 text-sm text-gray-700">{cargo.weightKg}</td>
              <td className="px-4 py-3 text-sm text-gray-700">{cargo.originUnlocode ?? '-'}</td>
              <td className="px-4 py-3 text-sm text-gray-700">{cargo.destinationUnlocode ?? '-'}</td>
              <td className="px-4 py-3 text-sm text-gray-700">{cargo.arrivalDeadline ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
