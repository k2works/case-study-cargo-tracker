import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchQuotationsPage, type Quotation } from '../api/quoteApi';
import Pagination from '../../../components/ui/Pagination';

const PAGE_SIZE = 20;

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

function cargoTypeLabel(type: string): string {
  switch (type) {
    case 'GENERAL':
      return '一般';
    case 'HAZARDOUS':
      return '危険物';
    case 'REFRIGERATED':
      return '冷凍';
    default:
      return type;
  }
}

function formatAmount(amount: number | null, currency: string | null): string {
  if (amount == null) {
    return '-';
  }
  return `${currency ?? ''} ${amount.toLocaleString()}`.trim();
}

export default function QuotationListPage() {
  const navigate = useNavigate();
  const [quotations, setQuotations] = useState<Quotation[]>([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchQuotationsPage(page, PAGE_SIZE)
      .then((p) => {
        setQuotations(p.items);
        setTotalCount(p.totalCount);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [page]);

  const th = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500';
  const td = 'px-4 py-3 text-sm text-gray-600';

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">見積一覧</h1>
        <button
          type="button"
          onClick={() => navigate('/quotes/new')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規見積
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className={th}>見積番号</th>
              <th className={th}>荷主 ID</th>
              <th className={th}>出発地</th>
              <th className={th}>目的地</th>
              <th className={th}>期限</th>
              <th className={th}>種別</th>
              <th className={th}>概算料金</th>
              <th className={th}>状態</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {quotations.map((q) => (
              <tr key={q.quotationId}>
                <td className="px-4 py-3 text-sm font-medium">
                  <button
                    type="button"
                    onClick={() => navigate(`/quotes/${q.quotationId}`)}
                    className="text-blue-600 hover:underline"
                  >
                    {q.quotationId}
                  </button>
                </td>
                <td className={td}>{q.shipperId}</td>
                <td className={td}>{q.originUnlocode}</td>
                <td className={td}>{q.destinationUnlocode}</td>
                <td className={td}>{q.arrivalDeadline}</td>
                <td className={td}>{cargoTypeLabel(q.cargoType)}</td>
                <td className={td}>{formatAmount(q.estimatedAmount, q.estimatedCurrency)}</td>
                <td className={td}>{statusLabel(q.status)}</td>
              </tr>
            ))}
            {quotations.length === 0 && !error && (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-sm text-gray-400">
                  登録されている見積はありません
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination
        page={page}
        size={PAGE_SIZE}
        totalCount={totalCount}
        onPageChange={setPage}
      />
    </div>
  );
}
