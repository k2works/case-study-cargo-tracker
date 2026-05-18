import { TrackingList } from '../features/tracking/components/TrackingList'
import { useTrackingList } from '../features/tracking/hooks/useTracking'

/**
 * S16: 追跡管理一覧（管理者）。
 *
 * `/tracking` でアクセス。tracking_summary を一覧表示し、行クリックで
 * S17 追跡詳細・管理（`/tracking/:trackingNumber/manage`）へ遷移する。
 */
export function TrackingListPage() {
  const { data, isLoading, error } = useTrackingList()

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">追跡管理一覧</h1>
      <p className="text-sm text-gray-500 mb-4">
        追跡中の貨物を最終更新日時順で表示します。追跡番号をクリックすると詳細・管理画面へ移動します。
      </p>
      <TrackingList items={data} isLoading={isLoading} error={error as Error | null} />
    </div>
  )
}
