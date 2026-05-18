import { Link } from 'react-router'
import type { TrackingListItem } from '../types/tracking'

type Props = {
  items: TrackingListItem[] | undefined
  isLoading: boolean
  error: Error | null
}

const STATUS_LABEL: Record<string, string> = {
  NOT_RECEIVED: '受領前',
  RECEIVED: '受領済',
  LOADED: '積込済',
  IN_TRANSIT: '輸送中',
  UNLOADED: '荷降し済',
  AWAITING_CLAIM: '引取待ち',
  DELIVERED: '引取済',
  MISROUTED: '誤配送',
  EXCEPTION: '例外',
}

/**
 * S16 追跡管理一覧テーブル。
 *
 * - 各行クリックで S17 (`/tracking/{tn}/manage`) へ遷移
 * - 誤配送行は赤背景でハイライト
 */
export function TrackingList({ items, isLoading, error }: Props) {
  if (isLoading) {
    return (
      <div className="p-6 text-center text-gray-500" role="status">
        読み込み中…
      </div>
    )
  }

  if (error) {
    return (
      <div className="p-6">
        <div
          className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded"
          role="alert"
        >
          {error.message}
        </div>
      </div>
    )
  }

  if (!items || items.length === 0) {
    return (
      <p className="p-6 text-gray-500" data-testid="tracking-list-empty">
        追跡中の貨物はありません。
      </p>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border" data-testid="tracking-list-table">
        <thead className="bg-gray-100">
          <tr>
            <th className="text-left p-2 border">追跡番号</th>
            <th className="text-left p-2 border">予約 ID</th>
            <th className="text-left p-2 border">現在状態</th>
            <th className="text-left p-2 border">現在位置</th>
            <th className="text-left p-2 border">出発地 → 到着地</th>
            <th className="text-left p-2 border">推定到着</th>
            <th className="text-left p-2 border">誤配送</th>
            <th className="text-left p-2 border">最終更新</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={item.trackingNumber}
              className={`border-t hover:bg-gray-50 ${item.misrouted ? 'bg-red-50' : ''}`}
              data-testid={`tracking-list-row-${item.trackingNumber}`}
            >
              <td className="p-2 border font-mono">
                <Link
                  to={`/tracking/${item.trackingNumber}/manage`}
                  className="text-blue-600 hover:underline"
                >
                  {item.trackingNumber}
                </Link>
              </td>
              <td className="p-2 border font-mono text-xs">{item.bookingId}</td>
              <td className="p-2 border">
                {STATUS_LABEL[item.currentStatus] ?? item.currentStatus}
              </td>
              <td className="p-2 border">{item.currentUnlocode ?? '—'}</td>
              <td className="p-2 border">
                {item.originUnlocode} → {item.destinationUnlocode}
              </td>
              <td className="p-2 border">{formatDateTime(item.estimatedArrival)}</td>
              <td className="p-2 border">{item.misrouted ? 'あり' : 'なし'}</td>
              <td className="p-2 border">{formatDateTime(item.lastEventAt ?? item.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toISOString().replace('T', ' ').slice(0, 16)
}
