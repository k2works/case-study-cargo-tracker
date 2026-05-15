import { useParams, Link } from 'react-router'
import { useVoyage } from '../features/routing/voyage/hooks/useVoyages'
import { VoyageEditForm } from '../features/routing/voyage/components/VoyageEditForm'

export function VoyageEditPage() {
  const { voyageNumber } = useParams<{ voyageNumber: string }>()
  const { data, isLoading, isError, error } = useVoyage(voyageNumber)

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">航海スケジュールの更新</h1>
        <Link to="/routing/voyages" className="text-sm text-blue-600 underline">
          ← 一覧に戻る
        </Link>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-3xl">
        {isLoading && <p className="text-gray-600">読み込み中…</p>}
        {isError && (
          <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">
            航海スケジュールの取得に失敗しました:{' '}
            {error instanceof Error ? error.message : '不明なエラー'}
          </div>
        )}
        {data && <VoyageEditForm current={data} />}
      </div>
    </div>
  )
}
