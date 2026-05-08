import { useState } from 'react'
import { useParams } from 'react-router'
import { useTrackingActivity, useUpdateTrackingStatus } from '../features/tracking/hooks/useTracking'
import type { TrackingStatus } from '../features/tracking/types/tracking'

const STATUS_LABELS: Record<TrackingStatus, string> = {
  NOT_RECEIVED: '未受領',
  RECEIVED: '受領済み',
  LOADED: '積込済み',
  ONBOARD_CARRIER: '輸送中',
  UNLOADED: '荷降し済み',
  AWAITING_CLAIM: '引渡し待ち',
  CLAIMED: '引渡し済み',
  EXCEPTION: '例外',
  UNKNOWN: '不明',
}

const EVENT_TYPE_LABELS: Record<string, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CUSTOMS: '通関',
  CLAIM: '引渡し',
}

const ALL_STATUSES: TrackingStatus[] = [
  'NOT_RECEIVED',
  'RECEIVED',
  'LOADED',
  'ONBOARD_CARRIER',
  'UNLOADED',
  'AWAITING_CLAIM',
  'CLAIMED',
  'EXCEPTION',
  'UNKNOWN',
]

export function TrackingStatusPage() {
  const { trackingNumber } = useParams<{ trackingNumber: string }>()
  const { data: activity, isLoading, isError, refetch } = useTrackingActivity(trackingNumber ?? '')
  const { mutate: updateStatus, isPending } = useUpdateTrackingStatus(trackingNumber ?? '')

  const [selectedStatus, setSelectedStatus] = useState<string>('')
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (isLoading) return <p className="p-6 text-center text-gray-500">読み込み中...</p>
  if (isError || !activity)
    return <p className="p-6 text-center text-red-500">追跡情報が見つかりません。</p>

  const handleUpdate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedStatus) {
      setErrorMessage('新しい状態を選択してください。')
      return
    }
    setSuccessMessage(null)
    setErrorMessage(null)

    updateStatus(
      { newStatus: selectedStatus },
      {
        onSuccess: () => {
          setSuccessMessage('貨物状態を更新しました。')
          setSelectedStatus('')
          refetch()
        },
        onError: (err) => {
          setErrorMessage(
            err instanceof Error ? err.message : '貨物状態の更新に失敗しました。',
          )
        },
      },
    )
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">追跡状態更新</h1>
      <p className="text-sm text-gray-500 mb-6 font-mono">{activity.trackingNumber}</p>

      <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold text-gray-800">現在の状態</h2>
          <span className="inline-flex px-2 py-0.5 rounded-full text-sm font-medium bg-blue-100 text-blue-800">
            {STATUS_LABELS[activity.transportStatus] ?? activity.transportStatus}
          </span>
        </div>
        <dl className="text-sm space-y-1">
          <div className="flex justify-between">
            <dt className="text-gray-500">予約 ID</dt>
            <dd className="text-gray-900">{activity.bookingId}</dd>
          </div>
        </dl>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-4 mb-6">
        <h2 className="text-base font-semibold text-gray-800 mb-3">状態を更新する</h2>

        {successMessage && (
          <div className="mb-3 rounded-md bg-green-50 border border-green-200 p-3 text-sm text-green-800">
            {successMessage}
          </div>
        )}
        {errorMessage && (
          <div className="mb-3 rounded-md bg-red-50 border border-red-200 p-3 text-sm text-red-800">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleUpdate} className="flex gap-3 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="newStatus">
              新しい状態
            </label>
            <select
              id="newStatus"
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value)
                setSuccessMessage(null)
                setErrorMessage(null)
              }}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">選択してください</option>
              {ALL_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={isPending}
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isPending ? '更新中...' : '更新する'}
          </button>
        </form>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h2 className="text-base font-semibold text-gray-800 mb-3">追跡イベント履歴</h2>
        {activity.events.length === 0 ? (
          <p className="text-sm text-gray-400">イベントなし</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="pb-2 font-medium">種別</th>
                <th className="pb-2 font-medium">場所</th>
                <th className="pb-2 font-medium">日時</th>
                <th className="pb-2 font-medium">航路番号</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {activity.events.map((event, i) => (
                <tr key={i} className="py-1">
                  <td className="py-2 text-gray-900">
                    {EVENT_TYPE_LABELS[event.eventType] ?? event.eventType}
                  </td>
                  <td className="py-2 text-gray-700 font-mono">{event.locationUnlocode}</td>
                  <td className="py-2 text-gray-500">
                    {new Date(event.eventTime).toLocaleString('ja-JP', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })}
                  </td>
                  <td className="py-2 text-gray-500">{event.voyageNumber ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
