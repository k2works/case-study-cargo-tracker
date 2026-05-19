import { useNavigate, useParams } from 'react-router'
import { TrackingExceptionForm } from '../features/tracking/components/TrackingExceptionForm'

export function TrackingExceptionNewPage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>()
  const navigate = useNavigate()

  function handleSuccess() {
    navigate(`/tracking/${trackingNumber}/manage`)
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">追跡例外登録</h1>
      <p className="text-sm text-gray-500 mb-4 font-mono">{trackingNumber}</p>
      <TrackingExceptionForm trackingNumber={trackingNumber} onSuccess={handleSuccess} />
    </div>
  )
}
