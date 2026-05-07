import { useNavigate } from 'react-router'
import { VoyageForm } from '../features/routing/components/VoyageForm'

export function VoyageNewPage() {
  const navigate = useNavigate()

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">航海新規登録</h1>
      <div className="bg-white border border-gray-200 rounded-lg p-6">
        <VoyageForm
          onSuccess={() => navigate('/voyages')}
          onCancel={() => navigate('/voyages')}
        />
      </div>
    </div>
  )
}
