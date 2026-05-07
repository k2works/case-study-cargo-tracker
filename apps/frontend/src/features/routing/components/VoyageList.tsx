import { useVoyages, useDeleteVoyage } from '../hooks/useVoyages'
import type { Voyage } from '../types/voyage'

interface VoyageListProps {
  onEdit?: (voyage: Voyage) => void
}

export function VoyageList({ onEdit }: VoyageListProps) {
  const { data: voyages, isLoading, isError } = useVoyages()
  const deleteMutation = useDeleteVoyage()

  if (isLoading) return <p className="py-4 text-center text-gray-500">読み込み中...</p>
  if (isError) return <p className="py-4 text-center text-red-500">データの取得に失敗しました。</p>
  if (!voyages || voyages.length === 0) {
    return <p className="py-8 text-center text-gray-500">航海が登録されていません。</p>
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 border border-gray-200 rounded-lg">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">航路番号</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">出発港</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">到着港</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">出発予定</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">到着予定</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">区間数</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">操作</th>
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {voyages.map((voyage) => {
            const first = voyage.carrierMovements[0]
            const last = voyage.carrierMovements[voyage.carrierMovements.length - 1]
            return (
              <tr
                key={voyage.id}
                className="hover:bg-gray-50 cursor-pointer"
                onClick={() => onEdit && onEdit(voyage)}
              >
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{voyage.voyageNumber}</td>
                <td className="px-4 py-3 text-sm text-gray-700">{first?.departureLocationUnlocode ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-700">{last?.arrivalLocationUnlocode ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-700">{first?.departureDate ? new Date(first.departureDate).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' }) : '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-700">{last?.arrivalDate ? new Date(last.arrivalDate).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' }) : '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-700">{voyage.carrierMovements.length}</td>
                <td className="px-4 py-3 text-sm" onClick={(e) => e.stopPropagation()}>
                  <button
                    onClick={() => deleteMutation.mutate(voyage.voyageNumber)}
                    disabled={deleteMutation.isPending}
                    className="text-red-600 hover:text-red-800 disabled:opacity-50 text-sm font-medium"
                  >
                    削除
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
