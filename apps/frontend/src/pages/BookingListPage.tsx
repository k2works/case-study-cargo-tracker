import { Link } from 'react-router'
import { BookingList } from '../features/booking/components/BookingList'

export function BookingListPage() {
  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-gray-900">貨物予約管理</h1>
      </div>

      <div className="mb-4">
        <Link
          to="/bookings/new"
          className="inline-flex items-center gap-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規予約
        </Link>
      </div>

      <BookingList />
    </div>
  )
}
