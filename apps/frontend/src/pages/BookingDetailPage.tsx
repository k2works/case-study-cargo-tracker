import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useBookings, useHandOffBooking, useConfirmBooking, useIssueTracking } from '../features/booking/hooks/useBookings'
import { TrackingTokenIssuer } from '../features/tracking/components/TrackingTokenIssuer'

// S10 予約詳細（US06）。
// PRELIMINARY 状態のときに「経路設計を依頼」ボタンを表示し、
// クリックで POST /api/v1/bookings/{id}/handoff を呼ぶ。
export function BookingDetailPage() {
  const { bookingId = '' } = useParams<{ bookingId: string }>()
  const { data: bookings, isLoading, isError } = useBookings()
  const handOff = useHandOffBooking()
  const confirmBooking = useConfirmBooking()
  const issueTracking = useIssueTracking()
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const booking = bookings?.find((b) => b.bookingId === bookingId)

  if (isLoading) return <p className="p-6 text-gray-500">読み込み中...</p>
  if (isError)
    return <p className="p-6 text-red-600">データの取得に失敗しました。</p>

  if (!booking) {
    return (
      <div className="p-6">
        <p className="text-red-600">予約が見つかりません: {bookingId}</p>
        <Link to="/bookings" className="text-blue-600 underline">
          予約一覧に戻る
        </Link>
      </div>
    )
  }

  const handleHandOff = () => {
    setMessage(null)
    setErrorMessage(null)
    handOff.mutate(bookingId, {
      onSuccess: () => setMessage('経路設計者に引き渡しました。'),
      onError: (e) => setErrorMessage(e instanceof Error ? e.message : '引き渡しに失敗しました。'),
    })
  }

  return (
    <div className="p-6 max-w-3xl">
      <div className="mb-4 flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">予約詳細</h1>
        <Link to="/bookings" className="text-sm text-blue-600 underline">
          ← 一覧に戻る
        </Link>
      </div>

      {message && (
        <div
          data-testid="handoff-success"
          className="mb-4 rounded border border-green-200 bg-green-50 px-4 py-2 text-sm text-green-800"
        >
          {message}
        </div>
      )}
      {errorMessage && (
        <div
          data-testid="handoff-error"
          className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-800"
        >
          {errorMessage}
        </div>
      )}

      <dl
        data-testid="booking-detail"
        className="grid grid-cols-3 gap-x-4 gap-y-2 bg-white border border-gray-200 rounded-lg p-4 text-sm"
      >
        <dt className="text-gray-600">予約 ID</dt>
        <dd className="col-span-2 font-mono">{booking.bookingId}</dd>
        <dt className="text-gray-600">荷主 ID</dt>
        <dd className="col-span-2">{booking.shipperId}</dd>
        <dt className="text-gray-600">品名</dt>
        <dd className="col-span-2">{booking.productName}</dd>
        <dt className="text-gray-600">貨物種別</dt>
        <dd className="col-span-2">{booking.cargoType}</dd>
        <dt className="text-gray-600">出発地</dt>
        <dd className="col-span-2 font-mono">{booking.originUnLocode}</dd>
        <dt className="text-gray-600">目的地</dt>
        <dd className="col-span-2 font-mono">{booking.destinationUnLocode}</dd>
        <dt className="text-gray-600">到着期限</dt>
        <dd className="col-span-2">{booking.arrivalDeadline}</dd>
        <dt className="text-gray-600">予約状態</dt>
        <dd className="col-span-2">
          <span
            data-testid="booking-status"
            className="inline-block px-2 py-0.5 rounded text-xs bg-blue-100 text-blue-800"
          >
            {booking.bookingStatus}
          </span>
        </dd>
      </dl>

      {booking.bookingStatus === 'PRELIMINARY' && (
        <div className="mt-6">
          <button
            type="button"
            onClick={handleHandOff}
            disabled={handOff.isPending}
            data-testid="handoff-button"
            className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {handOff.isPending ? '引き渡し中...' : '経路設計を依頼'}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            経路設計者に引き渡すと、予約状態が「経路設計中」に更新されます。
          </p>
        </div>
      )}

      {booking.bookingStatus === 'ROUTING' && (
        <div className="mt-6">
          <Link
            to={`/routing/workbench/${bookingId}`}
            data-testid="workbench-link"
            className="inline-block rounded bg-green-600 px-4 py-2 text-sm text-white hover:bg-green-700"
          >
            経路設計ワークベンチへ
          </Link>
          <p className="mt-2 text-xs text-gray-500">
            経路候補を算出し、最適な経路を選択します。
          </p>
        </div>
      )}

      {booking.bookingStatus === 'ROUTE_PROPOSED' && (
        <div className="mt-6">
          <button
            type="button"
            data-testid="confirm-button"
            disabled={confirmBooking.isPending}
            onClick={() => {
              setMessage(null)
              setErrorMessage(null)
              confirmBooking.mutate(bookingId, {
                onSuccess: () => setMessage('予約を確定しました。'),
                onError: () => setErrorMessage('予約確定に失敗しました。'),
              })
            }}
            className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {confirmBooking.isPending ? '確定中...' : '予約を確定する'}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            予約を確定すると、追跡番号の発行に進めます。
          </p>
        </div>
      )}

      {booking.bookingStatus === 'CONFIRMED' && (
        <div className="mt-6">
          <button
            type="button"
            data-testid="issue-tracking-button"
            disabled={issueTracking.isPending}
            onClick={() => {
              setMessage(null)
              setErrorMessage(null)
              issueTracking.mutate(bookingId, {
                onSuccess: () => setMessage('追跡番号を発行しました。'),
                onError: () => setErrorMessage('追跡番号の発行に失敗しました。'),
              })
            }}
            className="rounded bg-purple-600 px-4 py-2 text-sm text-white hover:bg-purple-700 disabled:opacity-50"
          >
            {issueTracking.isPending ? '発行中...' : '追跡番号を発行する'}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            追跡番号を発行して荷主への通知に進みます。
          </p>
        </div>
      )}

      {booking.trackingNumber && (
        <div className="mt-4 rounded border border-gray-200 bg-gray-50 px-4 py-2 text-sm">
          <span className="text-gray-600">追跡番号: </span>
          <span data-testid="tracking-number" className="font-mono font-semibold">
            {booking.trackingNumber}
          </span>
        </div>
      )}

      {booking.bookingStatus === 'TRACKING_ISSUED' && booking.trackingNumber && (
        <TrackingTokenIssuer trackingNumber={booking.trackingNumber} variant="issue" />
      )}
    </div>
  )
}
