import { useState } from 'react'
import { Link } from 'react-router'
import { useHandlingActivities } from '../hooks/useHandling'
import { HANDLING_TYPE_LABELS, type HandlingType } from '../types/handling'

export function HandlingActivityList() {
  const [trackingNumber, setTrackingNumber] = useState('')
  const [searchKey, setSearchKey] = useState<string | undefined>(undefined)
  const { data: activities, isLoading } = useHandlingActivities(searchKey)

  return (
    <div className="space-y-4" data-testid="handling-activity-list">
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <div className="flex gap-2">
          <input
            type="text"
            value={trackingNumber}
            onChange={(e) => setTrackingNumber(e.target.value)}
            placeholder="追跡番号で検索（例: TRK-YYYYMMDD-XXXXXXXX）"
            className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm"
            data-testid="handling-search-input"
          />
          <button
            type="button"
            onClick={() => setSearchKey(trackingNumber)}
            className="bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700"
            data-testid="handling-search-button"
          >
            検索
          </button>
        </div>
      </div>

      {isLoading && <p className="text-gray-500">読み込み中...</p>}

      {!isLoading && activities && activities.length === 0 && (
        <p className="text-gray-500">該当する荷役作業履歴がありません。</p>
      )}

      {activities && activities.length > 0 && (
        <div
          className="bg-white border border-gray-200 rounded-lg overflow-hidden"
          data-testid="handling-table"
        >
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-gray-700">作業日時</th>
                <th className="px-4 py-3 text-left text-gray-700">追跡番号</th>
                <th className="px-4 py-3 text-left text-gray-700">作業種別</th>
                <th className="px-4 py-3 text-left text-gray-700">場所</th>
                <th className="px-4 py-3 text-left text-gray-700">航海番号</th>
                <th className="px-4 py-3 text-left text-gray-700">記録者</th>
                <th className="px-4 py-3 text-left text-gray-700">予定外</th>
              </tr>
            </thead>
            <tbody>
              {activities.map((activity) => (
                <tr
                  key={activity.activityId}
                  className="border-t border-gray-200 hover:bg-gray-50"
                >
                  <td className="px-4 py-3">{activity.occurredAt}</td>
                  <td className="px-4 py-3 font-mono">
                    <Link
                      to={`/tracking/${activity.trackingNumber}/manage`}
                      className="text-blue-600 hover:underline"
                      data-testid={`tracking-link-${activity.trackingNumber}`}
                    >
                      {activity.trackingNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block px-2 py-0.5 rounded text-xs bg-gray-100 text-gray-800">
                      {HANDLING_TYPE_LABELS[activity.handlingType as HandlingType] ??
                        activity.handlingType}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono">{activity.unlocode}</td>
                  <td className="px-4 py-3 font-mono">{activity.voyageNumber ?? '—'}</td>
                  <td className="px-4 py-3 font-mono text-xs">{activity.handlerId}</td>
                  <td className="px-4 py-3">
                    {activity.unexpected ? (
                      <span className="inline-block px-2 py-0.5 rounded text-xs bg-yellow-100 text-yellow-800">
                        ⚠ 予定外
                      </span>
                    ) : (
                      <span className="text-gray-400">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
