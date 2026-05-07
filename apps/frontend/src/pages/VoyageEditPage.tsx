import { useNavigate, useParams } from 'react-router'
import { VoyageForm } from '../features/routing/components/VoyageForm'
import { useVoyage } from '../features/routing/hooks/useVoyages'

export function VoyageEditPage() {
  const { voyageNumber } = useParams<{ voyageNumber: string }>()
  const navigate = useNavigate()
  const { data: voyage, isLoading } = useVoyage(voyageNumber ?? '')

  if (isLoading) return <div className="p-6 text-gray-500">読み込み中...</div>
  if (!voyage) return <div className="p-6 text-red-500">航海が見つかりません。</div>

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">航海スケジュール更新</h1>
      <div className="bg-white border border-gray-200 rounded-lg p-6">
        <VoyageForm
          voyage={voyage}
          onSuccess={() => navigate('/voyages')}
          onCancel={() => navigate('/voyages')}
        />
      </div>
    </div>
  )
}
