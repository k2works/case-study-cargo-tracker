import { useState } from 'react'
import { useTrackingQuery } from '../features/tracking/hooks/useTracking'
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

const STATUS_COLORS: Record<TrackingStatus, string> = {
  NOT_RECEIVED: 'bg-gray-100 text-gray-800',
  RECEIVED: 'bg-blue-100 text-blue-800',
  LOADED: 'bg-indigo-100 text-indigo-800',
  ONBOARD_CARRIER: 'bg-purple-100 text-purple-800',
  UNLOADED: 'bg-yellow-100 text-yellow-800',
  AWAITING_CLAIM: 'bg-orange-100 text-orange-800',
  CLAIMED: 'bg-green-100 text-green-800',
  EXCEPTION: 'bg-red-100 text-red-800',
  UNKNOWN: 'bg-gray-100 text-gray-600',
}

const EVENT_TYPE_LABELS: Record<string, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CUSTOMS: '通関',
  CLAIM: '引渡し',
}

function TrackingResult({ trackingNumber }: { trackingNumber: string }) {
  const { data: activity, isLoading, isError } = useTrackingQuery(trackingNumber)

  if (isLoading) return <p className="text-center text-gray-500 py-8">読み込み中...</p>

  if (isError || !activity) {
    return (
      <div className="rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-800">
        追跡番号が見つかりません: {trackingNumber}
      </div>
    )
  }

  const hasException = activity.transportStatus === 'EXCEPTION'

  return (
    <div className="space-y-4">
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold text-gray-800">現在の状態</h2>
          <div className="flex items-center gap-2">
            {hasException && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-500 text-white">
                例外あり
              </span>
            )}
            <span
              className={`inline-flex px-2 py-0.5 rounded-full text-sm font-medium ${STATUS_COLORS[activity.transportStatus] ?? 'bg-gray-100 text-gray-800'}`}
            >
              {STATUS_LABELS[activity.transportStatus] ?? activity.transportStatus}
            </span>
          </div>
        </div>
        <dl className="text-sm space-y-1">
          <div className="flex justify-between">
            <dt className="text-gray-500">追跡番号</dt>
            <dd className="text-gray-900 font-mono">{activity.trackingNumber}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500">予約 ID</dt>
            <dd className="text-gray-900">{activity.bookingId}</dd>
          </div>
        </dl>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h2 className="text-base font-semibold text-gray-800 mb-3">追跡イベント履歴</h2>
        {activity.events.length === 0 ? (
          <p className="text-sm text-gray-400">イベントなし</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="pb-2 font-medium">日時</th>
                <th className="pb-2 font-medium">状態</th>
                <th className="pb-2 font-medium">場所</th>
                <th className="pb-2 font-medium">作業種別</th>
                <th className="pb-2 font-medium">航路</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {activity.events.map((event, i) => (
                <tr key={i} className="py-1">
                  <td className="py-2 text-gray-500">
                    {new Date(event.eventTime).toLocaleString('ja-JP', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })}
                  </td>
                  <td className="py-2 text-gray-900">{activity.transportStatus}</td>
                  <td className="py-2 text-gray-700 font-mono">{event.locationUnlocode}</td>
                  <td className="py-2 text-gray-900">
                    {EVENT_TYPE_LABELS[event.eventType] ?? event.eventType}
                  </td>
                  <td className="py-2 text-gray-500">{event.voyageNumber ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <p className="text-xs text-gray-400 text-center">30 秒ごとに自動更新中...</p>
    </div>
  )
}

export function TrackingPage() {
  const [inputValue, setInputValue] = useState('')
  const [searchNumber, setSearchNumber] = useState('')

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = inputValue.trim()
    if (trimmed) {
      setSearchNumber(trimmed)
    }
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">貨物追跡照会</h1>

      <form onSubmit={handleSearch} className="flex gap-3 mb-6">
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="追跡番号（例: TRK-000001）"
          className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <button
          type="submit"
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          追跡する
        </button>
      </form>

      {searchNumber && <TrackingResult trackingNumber={searchNumber} />}
    </div>
  )
}
