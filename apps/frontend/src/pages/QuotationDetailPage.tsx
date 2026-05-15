import { Link, useParams } from 'react-router'
import { useQuotation } from '../features/booking/quotation/hooks/useQuotations'
import { QuotationDetail } from '../features/booking/quotation/components/QuotationDetail'

export function QuotationDetailPage() {
  const { quotationId } = useParams<{ quotationId: string }>()
  const { data, isLoading, isError, error } = useQuotation(quotationId)

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">見積詳細</h1>
        <Link to="/quotations/new" className="text-sm text-blue-600 underline">
          + 新規見積
        </Link>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-3xl">
        {isLoading && <p className="text-gray-600">読み込み中…</p>}
        {isError && (
          <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">
            見積の取得に失敗しました:{' '}
            {error instanceof Error ? error.message : '不明なエラー'}
          </div>
        )}
        {data && <QuotationDetail quotation={data} />}
      </div>
    </div>
  )
}
