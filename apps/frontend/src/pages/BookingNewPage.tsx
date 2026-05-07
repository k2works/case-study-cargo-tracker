import { useNavigate } from 'react-router'
import { BookingForm } from '../features/booking/components/BookingForm'

export function BookingNewPage() {
  const navigate = useNavigate()

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">貨物予約 新規登録</h1>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-xl">
        <BookingForm
          onSuccess={() => navigate('/bookings')}
          onCancel={() => navigate('/bookings')}
        />
      </div>
    </div>
  )
}
