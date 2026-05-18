import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useHandlingActivities } from '../hooks/useHandling'
import { HANDLING_TYPE_LABELS, type HandlingType } from '../types/handling'

export function HandlingActivityList() {
  const navigate = useNavigate()
  const [trackingNumber, setTrackingNumber] = useState('')
  const [searchKey, setSearchKey] = useState<string | undefined>(undefined)
  const { data: activities, isLoading } = useHandlingActivities(searchKey)

  return (
    <div className="space-y-4" data-testid="handling-activity-list">
      <div className="flex space-x-2">
        <input
          type="text"
          value={trackingNumber}
          onChange={(e) => setTrackingNumber(e.target.value)}
          placeholder="TRK-YYYYMMDD-XXXXXXXX"
          className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm"
          data-testid="handling-search-input"
        />
        <button
          type="button"
          onClick={() => setSearchKey(trackingNumber)}
          className="bg-gray-600 text-white px-4 py-2 rounded-md text-sm"
          data-testid="handling-search-button"
        >
          検索
        </button>
        <button
          type="button"
          onClick={() => navigate('/handling/new')}
          className="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm"
          data-testid="handling-new-button"
        >
          新規登録
        </button>
      </div>

      {isLoading && <div className="text-sm text-gray-500">読み込み中...</div>}

      {activities && activities.length === 0 && (
        <div className="text-sm text-gray-500">該当する荷役作業履歴がありません。</div>
      )}

      {activities && activities.length > 0 && (
        <table className="min-w-full text-sm border border-gray-200" data-testid="handling-table">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-3 py-2 text-left">作業日時</th>
              <th className="px-3 py-2 text-left">追跡番号</th>
              <th className="px-3 py-2 text-left">作業種別</th>
              <th className="px-3 py-2 text-left">場所</th>
              <th className="px-3 py-2 text-left">航海番号</th>
              <th className="px-3 py-2 text-left">記録者</th>
              <th className="px-3 py-2 text-left">予定外</th>
            </tr>
          </thead>
          <tbody>
            {activities.map((a) => (
              <tr key={a.activityId} className="border-t border-gray-100">
                <td className="px-3 py-2">{a.occurredAt}</td>
                <td className="px-3 py-2 font-mono">
                  <Link
                    to={`/tracking/${a.trackingNumber}/manage`}
                    className="text-indigo-600 hover:underline"
                    data-testid={`tracking-link-${a.trackingNumber}`}
                  >
                    {a.trackingNumber}
                  </Link>
                </td>
                <td className="px-3 py-2">
                  {HANDLING_TYPE_LABELS[a.handlingType as HandlingType] ?? a.handlingType}
                </td>
                <td className="px-3 py-2">{a.unlocode}</td>
                <td className="px-3 py-2">{a.voyageNumber ?? '—'}</td>
                <td className="px-3 py-2">{a.handlerId}</td>
                <td className="px-3 py-2">
                  {a.unexpected ? <span className="text-yellow-700">⚠</span> : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
