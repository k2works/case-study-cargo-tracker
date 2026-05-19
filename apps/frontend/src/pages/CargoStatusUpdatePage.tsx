import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { CargoStatusUpdateForm } from '../features/handling/components/CargoStatusUpdateForm'
import { TrackingExceptionList } from '../features/tracking/components/TrackingExceptionList'
import { TrackingTokenIssuer } from '../features/tracking/components/TrackingTokenIssuer'

type Tab = 'status' | 'exceptions'

export function CargoStatusUpdatePage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('status')

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">追跡詳細・管理</h1>
      <p className="text-sm text-gray-500 mb-4 font-mono">{trackingNumber}</p>
      <div className="flex gap-2 mb-4">
        <button
          type="button"
          onClick={() => setTab('status')}
          aria-selected={tab === 'status'}
        >
          状態更新
        </button>
        <button
          type="button"
          onClick={() => setTab('exceptions')}
          aria-selected={tab === 'exceptions'}
        >
          例外対応
        </button>
      </div>
      {tab === 'status' && (
        <>
          <CargoStatusUpdateForm />
          {trackingNumber && (
            <TrackingTokenIssuer trackingNumber={trackingNumber} variant="reissue" />
          )}
        </>
      )}
      {tab === 'exceptions' && (
        <>
          <TrackingExceptionList trackingNumber={trackingNumber} />
          <button
            type="button"
            className="mt-4"
            onClick={() => navigate(`/tracking/${trackingNumber}/exceptions/new`)}
          >
            例外を登録
          </button>
        </>
      )}
    </div>
  )
}
