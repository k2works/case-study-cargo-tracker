import type { TrackingFetchError, TrackingInfo } from '../types/tracking'

type Props = {
  trackingNumber: string
  data: TrackingInfo | undefined
  isLoading: boolean
  error: TrackingFetchError | null
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

const SOURCE_LABEL: Record<string, string> = {
  HANDLING: '荷役',
  MANUAL: '管理者',
  SYSTEM: 'システム',
}

/**
 * S15 公開追跡照会ビュー（US18）。
 *
 * - 通常時: 現在状態 / 現在位置 / 推定到着 / 追跡履歴を表示
 * - 401 TOKEN_INVALID: 「リンクが不正です」
 * - 403 TOKEN_EXPIRED: 「リンクの有効期限が切れています」
 * - 404 TRACKING_NOT_FOUND: 「追跡情報が見つかりません」
 * - 400 TOKEN_TN_MISMATCH: 「リンクが不正です」
 */
export function TrackingPublicView({ trackingNumber, data, isLoading, error }: Props) {
  if (isLoading) {
    return (
      <div className="p-6 text-center text-gray-500" role="status">
        読み込み中…
      </div>
    )
  }

  if (error) {
    return <TrackingError error={error} />
  }

  if (!data) {
    return null
  }

  return (
    <div className="p-6 space-y-6">
      <header className="border-b pb-3">
        <h2 className="text-2xl font-bold">貨物追跡</h2>
        <p className="text-sm text-gray-500 font-mono">{trackingNumber}</p>
      </header>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <DetailItem label="現在の状態" value={STATUS_LABEL[data.currentStatus] ?? data.currentStatus} />
        <DetailItem
          label="現在位置"
          value={
            data.currentLocation
              ? data.currentLocation.portName
                ? `${data.currentLocation.portName} (${data.currentLocation.unlocode})`
                : data.currentLocation.unlocode
              : '—'
          }
        />
        <DetailItem
          label="推定到着"
          value={data.estimatedArrival ? formatDateTime(data.estimatedArrival) : '—'}
        />
        <DetailItem label="誤配送" value={data.misrouted ? 'あり' : 'なし'} />
        {data.deliveredAt && (
          <DetailItem label="配送完了" value={formatDateTime(data.deliveredAt)} />
        )}
        {data.validUntil && (
          <DetailItem label="リンク有効期限" value={formatDateTime(data.validUntil)} />
        )}
      </section>

      <section>
        <h3 className="font-bold mb-2">追跡履歴</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm border" data-testid="tracking-event-table">
            <thead className="bg-gray-100">
              <tr>
                <th className="text-left p-2 border">日時</th>
                <th className="text-left p-2 border">種別</th>
                <th className="text-left p-2 border">場所</th>
                <th className="text-left p-2 border">航海番号</th>
                <th className="text-left p-2 border">記録元</th>
              </tr>
            </thead>
            <tbody>
              {data.events.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-3 text-center text-gray-500">
                    履歴がありません
                  </td>
                </tr>
              ) : (
                data.events.map((event, index) => (
                  <tr key={`${event.occurredAt}-${index}`} className="border-t">
                    <td className="p-2 border">{formatDateTime(event.occurredAt)}</td>
                    <td className="p-2 border">{event.handlingType ?? event.type}</td>
                    <td className="p-2 border">{event.unlocode ?? '—'}</td>
                    <td className="p-2 border">{event.voyageNumber ?? '—'}</td>
                    <td className="p-2 border">
                      {event.source ? SOURCE_LABEL[event.source] ?? event.source : '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="border p-3 rounded">
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}

function TrackingError({ error }: { error: TrackingFetchError }) {
  if (error.status === 401 || error.errorCode === 'TOKEN_INVALID' || error.errorCode === 'TOKEN_TN_MISMATCH') {
    return (
      <div className="p-6">
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
          <p className="font-bold">無効なリンクです</p>
          <p className="text-sm">正しい URL をご確認のうえ再度お試しください。</p>
        </div>
      </div>
    )
  }
  if (error.status === 403 || error.errorCode === 'TOKEN_EXPIRED') {
    return (
      <div className="p-6">
        <div className="bg-yellow-100 border border-yellow-400 text-yellow-800 px-4 py-3 rounded" role="alert">
          <p className="font-bold">リンクの有効期限が切れています</p>
          <p className="text-sm">再発行をご希望の場合は営業担当者までご連絡ください。</p>
        </div>
      </div>
    )
  }
  if (error.status === 404 || error.errorCode === 'TRACKING_NOT_FOUND') {
    return (
      <div className="p-6">
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
          <p className="font-bold">追跡情報が見つかりません</p>
          <p className="text-sm">追跡番号をご確認ください。</p>
        </div>
      </div>
    )
  }
  return (
    <div className="p-6">
      <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
        <p className="font-bold">エラーが発生しました</p>
        <p className="text-sm">{error.message}</p>
      </div>
    </div>
  )
}

function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date
    .toISOString()
    .replace('T', ' ')
    .slice(0, 16)
}
