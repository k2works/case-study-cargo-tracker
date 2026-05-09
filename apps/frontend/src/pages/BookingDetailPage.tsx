import { useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router'
import { useBooking, useConfirmBooking, useCancelBooking } from '../features/booking/hooks/useBookings'
import { useIssueTrackingNumber } from '../features/tracking/hooks/useTracking'
import type { BookingStatus } from '../features/booking/types/cargo'

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

export function BookingDetailPage() {
  const { bookingId } = useParams<{ bookingId: string }>()
  const navigate = useNavigate()
  const { data: cargo, isLoading, isError } = useBooking(bookingId ?? '')
  const { mutate: confirmBooking, isPending: isConfirming } = useConfirmBooking(bookingId ?? '')
  const { mutate: cancelBooking, isPending: isCancelling } = useCancelBooking(bookingId ?? '')
  const { mutate: issueTrackingNumber, isPending: isIssuingTracking } = useIssueTrackingNumber()
  const [actionError, setActionError] = useState<string | null>(null)

  if (isLoading) return <p className="p-6 text-center text-gray-500">読み込み中...</p>
  if (isError || !cargo) return <p className="p-6 text-center text-red-500">データの取得に失敗しました。</p>

  const handleConfirm = () => {
    setActionError(null)
    confirmBooking(undefined, {
      onError: () => setActionError('予約確定に失敗しました。'),
    })
  }

  const handleCancel = () => {
    if (!window.confirm('予約をキャンセルしますか？')) return
    setActionError(null)
    cancelBooking(undefined, {
      onError: () => setActionError('予約キャンセルに失敗しました。'),
    })
  }

  const handleIssueTracking = () => {
    if (!cargo) return
    setActionError(null)
    issueTrackingNumber(
      { bookingId: cargo.bookingId },
      {
        onError: () => setActionError('追跡番号の発行に失敗しました。'),
      },
    )
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-3 mb-6 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900">予約詳細 — {cargo.bookingId}</h1>
        <span className={`inline-flex px-2 py-0.5 rounded-full text-sm font-medium ${STATUS_COLORS[cargo.bookingStatus]}`}>
          {STATUS_LABELS[cargo.bookingStatus]}
        </span>
        {['ROUTE_PROPOSED', 'CONFIRMED', 'TRACKING_ISSUED', 'IN_TRANSIT', 'DELIVERED', 'SETTLED'].includes(cargo.bookingStatus) && (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-teal-100 text-teal-800">
            ✓ 経路通知送信済み
          </span>
        )}
      </div>

      {actionError && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-3 text-sm text-red-800">
          {actionError}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        {/* 予約情報 */}
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-base font-semibold text-gray-800 mb-3">予約情報</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">荷主 ID</dt>
              <dd className="text-gray-900">{cargo.shipperId}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">出発地</dt>
              <dd className="text-gray-900">{cargo.originUnlocode ?? '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">目的地</dt>
              <dd className="text-gray-900">{cargo.destinationUnlocode ?? '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">希望期限</dt>
              <dd className="text-gray-900">{cargo.arrivalDeadline ?? '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">貨物種別</dt>
              <dd className="text-gray-900">{cargo.cargoType}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">重量</dt>
              <dd className="text-gray-900">{cargo.weightKg} kg</dd>
            </div>
          </dl>
        </div>

        {/* 割り当て経路 */}
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-base font-semibold text-gray-800 mb-3">割り当て経路</h2>
          {cargo.legs && cargo.legs.length > 0 ? (
            <div className="space-y-2">
              {cargo.legs.map((leg, i) => (
                <div key={i} className="text-sm border border-gray-100 rounded p-2">
                  <div className="font-mono text-blue-700 font-semibold mb-1">{leg.voyageNumber}</div>
                  <div className="text-gray-600">
                    {leg.loadLocationUnlocode} → {leg.unloadLocationUnlocode}
                  </div>
                  <div className="text-gray-500 text-xs mt-0.5">
                    {new Date(leg.loadTime).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                    {' '}〜{' '}
                    {new Date(leg.unloadTime).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-400">経路未割り当て</p>
          )}
          <div className="mt-3">
            <Link
              to={`/routing/design/${cargo.bookingId}`}
              className="text-sm text-blue-600 hover:underline"
            >
              経路を割り当て →
            </Link>
          </div>
        </div>
      </div>

      {/* アクションボタン */}
      <div className="flex gap-3 flex-wrap">
        <button
          onClick={() => navigate('/bookings')}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          予約一覧に戻る
        </button>
        {cargo.bookingStatus === 'ROUTE_PROPOSED' && (
          <button
            onClick={handleConfirm}
            disabled={isConfirming}
            className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
          >
            {isConfirming ? '確定中...' : '予約を確定する'}
          </button>
        )}
        {cargo.bookingStatus === 'CONFIRMED' && (
          <button
            onClick={handleIssueTracking}
            disabled={isIssuingTracking}
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isIssuingTracking ? '発行中...' : '追跡番号を発行する'}
          </button>
        )}
        {(cargo.bookingStatus === 'PRELIMINARY' || cargo.bookingStatus === 'ROUTE_PROPOSED') && (
          <button
            onClick={handleCancel}
            disabled={isCancelling}
            className="rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {isCancelling ? 'キャンセル中...' : 'キャンセル'}
          </button>
        )}
      </div>
    </div>
  )
}
