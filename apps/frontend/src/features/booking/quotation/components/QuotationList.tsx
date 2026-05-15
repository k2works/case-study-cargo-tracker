import { Link } from 'react-router'
import { useQuotations } from '../hooks/useQuotations'
import type { QuotationStatus } from '../types/quotation'

// S02 見積一覧（US01）。受入条件 4 の見積番号と状態を一覧で確認可能。

function formatCurrency(amount: number | null | undefined, currency: string | null | undefined): string {
  if (amount == null || currency == null) return '-'
  return `${currency} ${amount.toLocaleString()}`
}

function statusBadgeClass(status: QuotationStatus): string {
  switch (status) {
    case 'OFFERED':
      return 'bg-green-100 text-green-800'
    case 'DRAFT':
      return 'bg-yellow-100 text-yellow-800'
    case 'ACCEPTED':
      return 'bg-blue-100 text-blue-800'
    case 'EXPIRED':
      return 'bg-gray-100 text-gray-700'
  }
}

export function QuotationList() {
  const { data: quotations, isLoading, isError } = useQuotations()

  if (isLoading) return <p className="text-gray-500">読み込み中...</p>
  if (isError) return <p className="text-red-600">データの取得に失敗しました。</p>

  if (!quotations || quotations.length === 0) {
    return <p className="text-gray-500">見積が登録されていません。</p>
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-gray-700">見積番号</th>
            <th className="px-4 py-3 text-left text-gray-700">荷主 ID</th>
            <th className="px-4 py-3 text-left text-gray-700">出発地</th>
            <th className="px-4 py-3 text-left text-gray-700">目的地</th>
            <th className="px-4 py-3 text-left text-gray-700">期限</th>
            <th className="px-4 py-3 text-left text-gray-700">貨物種別</th>
            <th className="px-4 py-3 text-left text-gray-700">概算料金</th>
            <th className="px-4 py-3 text-left text-gray-700">状態</th>
            <th className="px-4 py-3 text-left text-gray-700">操作</th>
          </tr>
        </thead>
        <tbody>
          {quotations.map((q) => (
            <tr key={q.quotationId} className="border-t border-gray-200 hover:bg-gray-50">
              <td className="px-4 py-3 font-mono text-xs">{q.quotationId}</td>
              <td className="px-4 py-3">{q.shipperId}</td>
              <td className="px-4 py-3 font-mono">{q.originUnLocode}</td>
              <td className="px-4 py-3 font-mono">{q.destinationUnLocode}</td>
              <td className="px-4 py-3">{q.arrivalDeadline}</td>
              <td className="px-4 py-3">{q.cargoType}</td>
              <td className="px-4 py-3">
                {formatCurrency(q.estimatedAmount, q.estimatedCurrency)}
              </td>
              <td className="px-4 py-3">
                <span className={`inline-block px-2 py-0.5 rounded text-xs ${statusBadgeClass(q.status)}`}>
                  {q.status}
                </span>
              </td>
              <td className="px-4 py-3">
                <Link
                  to={`/quotations/${q.quotationId}`}
                  className="text-sm text-blue-600 underline"
                  data-testid={`detail-link-${q.quotationId}`}
                >
                  詳細
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
