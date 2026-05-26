import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchQuotation, type Quotation } from '../api/quoteApi';

function statusLabel(status: string): string {
  switch (status) {
    case 'DRAFT':
      return '草稿';
    case 'OFFERED':
      return '提示済';
    case 'ACCEPTED':
      return '受諾';
    case 'EXPIRED':
      return '失効';
    default:
      return status;
  }
}

function formatAmount(amount: number | null, currency: string | null): string {
  if (amount == null) {
    return '-';
  }
  return `${currency ?? ''} ${amount.toLocaleString()}`.trim();
}

export default function QuotationDetailPage() {
  const { quotationId } = useParams<{ quotationId: string }>();
  const navigate = useNavigate();
  const [quotation, setQuotation] = useState<Quotation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!quotationId) {
      return;
    }
    fetchQuotation(quotationId)
      .then(setQuotation)
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [quotationId]);

  if (error) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <div className="rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
        <button
          type="button"
          onClick={() => navigate('/quotes')}
          className="mt-4 rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
        >
          一覧に戻る
        </button>
      </div>
    );
  }

  if (!quotation) {
    return <div className="mx-auto max-w-3xl px-4 py-6 text-sm text-gray-400">読み込み中...</div>;
  }

  const dt = 'text-xs text-gray-500';
  const dd = 'text-sm text-gray-900';

  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">見積 {quotation.quotationId}</h1>
        <span className="rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-700">
          {statusLabel(quotation.status)}
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-3 rounded-lg border bg-white p-4">
        <div><dt className={dt}>荷主 ID</dt><dd className={dd}>{quotation.shipperId}</dd></div>
        <div><dt className={dt}>出発地</dt><dd className={dd}>{quotation.originUnlocode}</dd></div>
        <div><dt className={dt}>目的地</dt><dd className={dd}>{quotation.destinationUnlocode}</dd></div>
        <div><dt className={dt}>到着期限</dt><dd className={dd}>{quotation.arrivalDeadline}</dd></div>
        <div><dt className={dt}>概算金額</dt><dd className={dd}>{formatAmount(quotation.estimatedAmount, quotation.estimatedCurrency)}</dd></div>
        <div><dt className={dt}>有効期限</dt><dd className={dd}>{quotation.validUntil}</dd></div>
      </dl>

      <h2 className="mb-2 mt-6 text-sm font-semibold text-gray-700">ルート候補</h2>
      <div className="overflow-hidden rounded-lg border bg-white">
        {quotation.candidates.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-gray-400">
            ルート候補がありません（期限内に到達可能な航海が見つかりませんでした）
          </p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">候補</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">経路</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">所要日数</th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">概算費用</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {quotation.candidates.map((c) => (
                <tr key={c.candidateSeq}>
                  <td className="px-4 py-2 text-sm text-gray-900">{c.candidateSeq}</td>
                  <td className="px-4 py-2 text-sm text-gray-600">{c.itinerarySummary}</td>
                  <td className="px-4 py-2 text-sm text-gray-600">{c.estimatedDays} 日</td>
                  <td className="px-4 py-2 text-sm text-gray-600">{formatAmount(c.estimatedCost, c.estimatedCurrency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={() =>
            navigate('/bookings/new', {
              state: {
                fromQuotation: {
                  shipperId: quotation.shipperId,
                  originUnlocode: quotation.originUnlocode,
                  destinationUnlocode: quotation.destinationUnlocode,
                  arrivalDeadline: quotation.arrivalDeadline,
                  cargoType: quotation.cargoType,
                  weightKg: quotation.weightKg,
                },
              },
            })
          }
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          予約化
        </button>
        <button
          type="button"
          onClick={() => navigate('/quotes')}
          className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
        >
          一覧に戻る
        </button>
      </div>
    </div>
  );
}
