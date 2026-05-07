import { useNavigate } from 'react-router'
import { VoyageForm } from '../features/routing/components/VoyageForm'

export function VoyageNewPage() {
  const navigate = useNavigate()

  return (
    <div>
      <h1>航海新規登録</h1>
      <VoyageForm onSuccess={() => navigate('/voyages')} />
    </div>
  )
}
