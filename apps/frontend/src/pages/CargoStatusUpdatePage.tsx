import { useParams } from 'react-router'
import { CargoStatusUpdateForm } from '../features/handling/components/CargoStatusUpdateForm'
import { TrackingTokenIssuer } from '../features/tracking/components/TrackingTokenIssuer'

export function CargoStatusUpdatePage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>()
  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">追跡詳細・管理</h1>
      <p className="text-sm text-gray-500 mb-4 font-mono">{trackingNumber}</p>
      <CargoStatusUpdateForm />
      {trackingNumber && (
        <TrackingTokenIssuer trackingNumber={trackingNumber} variant="reissue" />
      )}
    </div>
  )
}
