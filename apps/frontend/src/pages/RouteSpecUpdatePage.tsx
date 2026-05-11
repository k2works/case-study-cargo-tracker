import { useState } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router'
import { toast } from 'sonner'
import { useBooking, useUpdateRouteSpec } from '../features/booking/hooks/useBookings'

export function RouteSpecUpdatePage() {
  const { bookingId } = useParams<{ bookingId: string }>()
  const navigate = useNavigate()

  const { data: cargo, isLoading } = useBooking(bookingId ?? '')
  const { mutate: updateRouteSpec, isPending } = useUpdateRouteSpec(bookingId ?? '')

  const [originUnlocode, setOriginUnlocode] = useState('')
  const [destinationUnlocode, setDestinationUnlocode] = useState('')
  const [arrivalDeadline, setArrivalDeadline] = useState('')

  if (!bookingId) {
    return <Navigate to="/routing/assignments" replace />
  }

  if (isLoading) {
    return <p className="py-8 text-center text-gray-500">読み込み中...</p>
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    updateRouteSpec(
      {
        originUnlocode: originUnlocode || (cargo?.originUnlocode ?? ''),
        destinationUnlocode: destinationUnlocode || (cargo?.destinationUnlocode ?? ''),
        arrivalDeadline: arrivalDeadline,
      },
      {
        onSuccess: () => {
          toast.success('経路条件を再設定しました。経路を再算出してください。')
          navigate(`/routing/design/${bookingId}`)
        },
        onError: () => {
          toast.error('経路条件の再設定に失敗しました。')
        },
      }
    )
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">経路条件再設定</h1>
        <p className="mt-1 text-sm text-gray-600">予約 ID: {bookingId}</p>
      </div>

      {cargo && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 mb-6">
          <h2 className="text-sm font-semibold text-gray-700 mb-2">現在の条件</h2>
          <div className="grid grid-cols-3 gap-4 text-sm text-gray-600">
            <div>
              <span className="font-medium">出発地:</span>{' '}
              {cargo.originUnlocode ?? '—'}
            </div>
            <div>
              <span className="font-medium">到着地:</span>{' '}
              {cargo.destinationUnlocode ?? '—'}
            </div>
            <div>
              <span className="font-medium">到着期限:</span>{' '}
              {cargo.arrivalDeadline ?? '—'}
            </div>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <h2 className="text-lg font-semibold text-gray-800">新しい条件</h2>
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              出発地 (UNLOCODE)
            </label>
            <input
              value={originUnlocode}
              onChange={(e) => setOriginUnlocode(e.target.value.toUpperCase())}
              placeholder={cargo?.originUnlocode ?? 'JPTYO'}
              maxLength={5}
              className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              到着地 (UNLOCODE)
            </label>
            <input
              value={destinationUnlocode}
              onChange={(e) => setDestinationUnlocode(e.target.value.toUpperCase())}
              placeholder={cargo?.destinationUnlocode ?? 'CNSHA'}
              maxLength={5}
              className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              新しい到着期限
            </label>
            <input
              type="date"
              value={arrivalDeadline}
              onChange={(e) => setArrivalDeadline(e.target.value)}
              required
              className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            キャンセル
          </button>
          <button
            type="submit"
            disabled={isPending || !arrivalDeadline}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {isPending ? '更新中...' : '再算出する'}
          </button>
        </div>
      </form>
    </div>
  )
}
