import { useParams, useSearchParams } from 'react-router'
import { TrackingPublicView } from '../features/tracking/components/TrackingPublicView'
import { useTrackingInfo } from '../features/tracking/hooks/useTracking'

/**
 * S15: 公開追跡照会画面（US18）。
 *
 * ログイン不要・サイドナビなし。`/tracking/:trackingNumber?token=<JWT>` で公開される。
 */
export function TrackingPublicPage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const { data, isLoading, error } = useTrackingInfo(trackingNumber, token)

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-3">
          <h1 className="text-lg font-bold">国際貨物輸送管理 — 貨物追跡</h1>
        </div>
      </header>
      <main className="max-w-4xl mx-auto mt-4 bg-white rounded shadow">
        {!token ? (
          <div className="p-6">
            <div
              className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded"
              role="alert"
            >
              <p className="font-bold">無効なリンクです</p>
              <p className="text-sm">トークンが指定されていません。</p>
            </div>
          </div>
        ) : (
          <TrackingPublicView
            trackingNumber={trackingNumber}
            data={data}
            isLoading={isLoading}
            error={error}
          />
        )}
      </main>
    </div>
  )
}
