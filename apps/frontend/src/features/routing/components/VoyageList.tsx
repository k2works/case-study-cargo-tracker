import { useVoyages, useDeleteVoyage } from '../hooks/useVoyages'
import type { Voyage } from '../types/voyage'

interface VoyageListProps {
  onEdit?: (voyage: Voyage) => void
}

export function VoyageList({ onEdit }: VoyageListProps) {
  const { data: voyages, isLoading, isError } = useVoyages()
  const deleteMutation = useDeleteVoyage()

  if (isLoading) return <p>読み込み中...</p>
  if (isError) return <p>データの取得に失敗しました。</p>
  if (!voyages || voyages.length === 0) return <p>航海が登録されていません。</p>

  return (
    <table>
      <thead>
        <tr>
          <th>航海番号</th>
          <th>区間数</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        {voyages.map((voyage) => (
          <tr key={voyage.id}>
            <td>{voyage.voyageNumber}</td>
            <td>{voyage.carrierMovements.length}</td>
            <td>
              {onEdit && (
                <button onClick={() => onEdit(voyage)}>編集</button>
              )}
              <button
                onClick={() => deleteMutation.mutate(voyage.voyageNumber)}
                disabled={deleteMutation.isPending}
              >
                削除
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
