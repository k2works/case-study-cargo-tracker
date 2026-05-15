import type { QuotationResponse } from '../types/quotation'

// S04 見積詳細（US01）。受入条件 2-3-5 のルート候補表示と「期限内ルートなし」の警告。

interface Props {
  quotation: QuotationResponse
}

function formatCurrency(amount: number | null | undefined, currency: string | null | undefined): string {
  if (amount == null || currency == null) return '-'
  return `${currency} ${amount.toLocaleString()}`
}

export function QuotationDetail({ quotation }: Props) {
  const hasCandidates = quotation.candidates.length > 0
  const showNoRouteWarning = quotation.status === 'DRAFT' && !hasCandidates

  return (
    <div className="space-y-6" data-testid="quotation-detail">
      <div className="flex items-center gap-3">
        <h2 className="text-xl font-semibold text-gray-900">
          見積 {quotation.quotationId}
        </h2>
        <span
          className={`inline-block rounded px-2 py-0.5 text-xs ${
            quotation.status === 'OFFERED'
              ? 'bg-green-100 text-green-800'
              : quotation.status === 'DRAFT'
                ? 'bg-yellow-100 text-yellow-800'
                : 'bg-gray-100 text-gray-700'
          }`}
        >
          {quotation.status}
        </span>
      </div>

      <section>
        <h3 className="mb-2 text-sm font-medium text-gray-600">基本情報</h3>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
          <dt className="text-gray-500">荷主 ID</dt>
          <dd>{quotation.shipperId}</dd>
          <dt className="text-gray-500">出発地</dt>
          <dd className="font-mono">{quotation.originUnLocode}</dd>
          <dt className="text-gray-500">目的地</dt>
          <dd className="font-mono">{quotation.destinationUnLocode}</dd>
          <dt className="text-gray-500">希望期限</dt>
          <dd>{quotation.arrivalDeadline}</dd>
          <dt className="text-gray-500">貨物種別</dt>
          <dd>{quotation.cargoType}</dd>
          <dt className="text-gray-500">重量</dt>
          <dd>{quotation.weightKg} kg</dd>
          <dt className="text-gray-500">概算料金</dt>
          <dd className="font-semibold">
            {formatCurrency(quotation.estimatedAmount, quotation.estimatedCurrency)}
          </dd>
          <dt className="text-gray-500">有効期限</dt>
          <dd>{quotation.validUntil}</dd>
        </dl>
      </section>

      {quotation.hazardImoClass && (
        <section className="rounded border border-yellow-200 bg-yellow-50 p-4">
          <h3 className="mb-2 text-sm font-medium text-yellow-900">危険物申告</h3>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt className="text-gray-500">IMO クラス</dt>
            <dd>{quotation.hazardImoClass}</dd>
            <dt className="text-gray-500">UN 番号</dt>
            <dd>{quotation.hazardUnNumber}</dd>
            <dt className="col-span-2 text-gray-500">申告内容</dt>
            <dd className="col-span-2 whitespace-pre-wrap">{quotation.hazardDeclaration}</dd>
          </dl>
        </section>
      )}

      <section>
        <h3 className="mb-2 text-sm font-medium text-gray-600">ルート候補</h3>
        {showNoRouteWarning ? (
          <div
            className="rounded border border-orange-300 bg-orange-50 p-3 text-sm text-orange-900"
            role="alert"
            data-testid="no-route-warning"
          >
            希望期限 {quotation.arrivalDeadline} までに到達可能なルートが見つかりませんでした。
            条件を緩和して再検索してください。
          </div>
        ) : (
          <table className="w-full text-sm" data-testid="candidates-table">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-3 py-2 text-left text-gray-700">候補</th>
                <th className="px-3 py-2 text-left text-gray-700">経由港</th>
                <th className="px-3 py-2 text-left text-gray-700">所要日数</th>
                <th className="px-3 py-2 text-left text-gray-700">概算料金</th>
                <th className="px-3 py-2 text-left text-gray-700">航海番号</th>
              </tr>
            </thead>
            <tbody>
              {quotation.candidates.map((c) => (
                <tr key={c.candidateSeq} className="border-t border-gray-200">
                  <td className="px-3 py-2">{c.candidateSeq}</td>
                  <td className="px-3 py-2 font-mono">{c.itinerarySummary}</td>
                  <td className="px-3 py-2">{c.estimatedDays} 日</td>
                  <td className="px-3 py-2">
                    {formatCurrency(c.estimatedCost, c.estimatedCurrency)}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{c.voyageNumbers}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
