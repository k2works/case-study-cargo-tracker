import { useBookings } from '../hooks/useBookings'

const CARGO_TYPE_LABEL: Record<string, string> = {
  GENERAL: '一般',
  HAZARDOUS: '危険物',
  REFRIGERATED: '冷凍',
}

export function BookingList() {
  const { data: bookings, isLoading, isError } = useBookings()

  if (isLoading) return <p className="text-gray-500">読み込み中...</p>
  if (isError) return <p className="text-red-600">データの取得に失敗しました。</p>

  if (!bookings || bookings.length === 0) {
    return <p className="text-gray-500">予約が登録されていません。</p>
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-gray-700">予約 ID</th>
            <th className="px-4 py-3 text-left text-gray-700">品名</th>
            <th className="px-4 py-3 text-left text-gray-700">種別</th>
            <th className="px-4 py-3 text-left text-gray-700">出発地</th>
            <th className="px-4 py-3 text-left text-gray-700">目的地</th>
            <th className="px-4 py-3 text-left text-gray-700">到着期限</th>
            <th className="px-4 py-3 text-left text-gray-700">状態</th>
          </tr>
        </thead>
        <tbody>
          {bookings.map((booking) => (
            <tr key={booking.bookingId} className="border-t border-gray-200 hover:bg-gray-50">
              <td className="px-4 py-3 font-mono text-xs">{booking.bookingId}</td>
              <td className="px-4 py-3">{booking.productName}</td>
              <td className="px-4 py-3">
                <span className="inline-block px-2 py-0.5 rounded text-xs bg-gray-100 text-gray-800">
                  {CARGO_TYPE_LABEL[booking.cargoType] ?? booking.cargoType}
                </span>
              </td>
              <td className="px-4 py-3 font-mono">{booking.originUnLocode}</td>
              <td className="px-4 py-3 font-mono">{booking.destinationUnLocode}</td>
              <td className="px-4 py-3">{booking.arrivalDeadline}</td>
              <td className="px-4 py-3">
                <span className="inline-block px-2 py-0.5 rounded text-xs bg-blue-100 text-blue-800">
                  {booking.bookingStatus}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
