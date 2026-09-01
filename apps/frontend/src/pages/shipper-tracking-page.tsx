import { Link, useParams } from 'react-router-dom'
import { formatBusinessDateTime } from '../lib/business-time'
import { TrackingEventsTable } from '../features/tracking/components/tracking-events-table'
import {
  useShipperTracking,
  useShipperTrackingDetail,
} from '../features/tracking/queries'
import { ApiError } from '../lib/api-client'

function arrivalOf(value: string | null) {
  return value ?? '未定'
}

function ExceptionBadge({ urgent }: Readonly<{ urgent: boolean }>) {
  return (
    <span className={urgent ? 'rounded bg-red-100 px-2 py-1 text-red-900' : 'rounded bg-amber-100 px-2 py-1 text-amber-900'}>
      {urgent ? '緊急の例外あり' : '例外あり'}
    </span>
  )
}

function ShipperTrackingListView() {
  const { data, error, isLoading } = useShipperTracking()

  if (isLoading) {
    return <p className="text-sm text-gray-600">読み込んでいます…</p>
  }

  if (error !== null && error !== undefined) {
    return (
      <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
        ただいま自分の貨物を照会できません。しばらくしてからお試しください。
      </p>
    )
  }

  if (data?.linked === false) {
    return (
      <section className="space-y-2 rounded border border-amber-200 bg-amber-50 p-4">
        <h2 className="text-lg font-semibold text-amber-950">荷主との紐付けがありません</h2>
        <p className="text-sm text-amber-950">{data.contactMessage}</p>
      </section>
    )
  }

  const cargos = data?.cargos ?? []
  if (cargos.length === 0) {
    return <p className="text-sm text-gray-600">自社貨物はありません。</p>
  }

  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-gray-200 text-gray-600">
          <th className="py-2">追跡番号</th>
          <th className="py-2">状態</th>
          <th className="py-2">現在地</th>
          <th className="py-2">到着予定</th>
          <th className="py-2">例外</th>
        </tr>
      </thead>
      <tbody>
        {cargos.map((cargo) => (
          <tr key={cargo.trackingNumber} className="border-b border-gray-100">
            <td className="py-2">
              <Link
                to={`/shipper/tracking/${encodeURIComponent(cargo.trackingNumber)}`}
                className="text-blue-600 hover:underline"
              >
                {cargo.trackingNumber}
              </Link>
            </td>
            <td className="py-2">{cargo.statusLabel}</td>
            <td className="py-2">{cargo.locationName}</td>
            <td className="py-2">{arrivalOf(cargo.estimatedArrival)}</td>
            <td className="py-2">
              {cargo.hasException ? (
                <ExceptionBadge urgent={cargo.urgent} />
              ) : (
                <span className="text-gray-500">なし</span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function ShipperTrackingDetailView({ trackingNumber }: Readonly<{ trackingNumber: string }>) {
  const { data, error, isLoading } = useShipperTrackingDetail(trackingNumber)
  const notFound = error instanceof ApiError && error.status === 404

  if (isLoading) {
    return <p className="text-sm text-gray-600">読み込んでいます…</p>
  }

  if (notFound) {
    return (
      <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
        {error.message}
      </p>
    )
  }

  if (error !== null && error !== undefined) {
    return (
      <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
        ただいま自分の貨物を照会できません。しばらくしてからお試しください。
      </p>
    )
  }

  if (data === undefined) {
    return null
  }

  return (
    <div className="space-y-6">
      <section className="space-y-2 rounded border border-gray-200 p-4">
        <h1 className="text-2xl font-bold text-gray-900">{data.trackingNumber}</h1>
        <dl className="grid gap-2 sm:grid-cols-3">
          <div>
            <dt className="text-sm text-gray-600">現在の状態</dt>
            <dd className="font-medium text-gray-900">{data.statusLabel}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">現在地</dt>
            <dd className="font-medium text-gray-900">{data.locationName}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">到着予定日</dt>
            <dd className="font-medium text-gray-900">{arrivalOf(data.estimatedArrival)}</dd>
          </div>
        </dl>
        {data.hasException && (
          <p role="alert" className="rounded bg-amber-50 p-3 text-sm text-amber-900">
            <strong>お荷物に問題が起きています。</strong>{' '}
            詳しくはご依頼元の営業担当へお問い合わせください。
          </p>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-gray-900">これまでの経過</h2>
        <TrackingEventsTable events={data.events} />

        {/*
          **過去のお知らせを読み直せる場所。**ポップアップは出した時点で既読に
          なるため、ここが無いと、回線が切れた・タブを閉じた・見落とした荷主は
          その知らせに二度と到達できない（IT16 レビュー 高 3）
        */}
        {data.notices !== undefined && data.notices.length > 0 && (
          <section aria-labelledby="past-notices" className="mt-6 space-y-2">
            <h2 id="past-notices" className="text-lg font-semibold text-gray-900">
              お知らせ
            </h2>
            <ul className="divide-y rounded border border-gray-200">
              {data.notices.map((notice) => (
                <li key={`${notice.noticedAt}-${notice.message}`} className="p-3">
                  <p className="text-sm text-gray-900">{notice.message}</p>
                  {/* **いつの話かを添える。**「問題が発生しました」だけでは、
                      荷主が最初に聞くのは「それはいつですか」である */}
                  <p className="mt-1 text-xs text-gray-600">
                    {formatBusinessDateTime(notice.noticedAt)}
                  </p>
                </li>
              ))}
            </ul>
          </section>
        )}
      </section>
    </div>
  )
}

export function ShipperTrackingPage() {
  const { trackingNumber } = useParams()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        {trackingNumber === undefined ? (
          <h1 className="text-2xl font-bold text-gray-900">自分の貨物</h1>
        ) : (
          <Link to="/shipper/tracking" className="text-blue-600 hover:underline">
            自分の貨物に戻る
          </Link>
        )}
      </div>

      {trackingNumber === undefined ? (
        <ShipperTrackingListView />
      ) : (
        <ShipperTrackingDetailView trackingNumber={trackingNumber} />
      )}
    </div>
  )
}
