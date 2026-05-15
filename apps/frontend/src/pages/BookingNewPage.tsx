import { BookingForm } from '../features/booking/components/BookingForm'

export function BookingNewPage() {
  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">予約の登録</h1>
      </div>
      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-xl">
        <BookingForm />
      </div>
    </div>
  )
}
