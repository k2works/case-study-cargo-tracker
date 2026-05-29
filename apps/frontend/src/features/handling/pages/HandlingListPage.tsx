import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  fetchHandlingPage,
  handlingTypeLabel,
  type HandlingActivity,
} from '../api/handlingApi';
import Pagination from '../../../components/ui/Pagination';
import { DEFAULT_PAGE_SIZE } from '../../../shared/api/types';

const PAGE_SIZE = DEFAULT_PAGE_SIZE;

export default function HandlingListPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<HandlingActivity[]>([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchHandlingPage(page, PAGE_SIZE)
      .then((p) => {
        setItems(p.items);
        setTotalCount(p.totalCount);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [page]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">荷役作業履歴</h1>
        <button
          type="button"
          onClick={() => navigate('/handling/new')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規記録
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
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">追跡番号</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">作業種別</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">作業場所</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">航海</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">発生日時</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">作業員</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {items.map((h) => (
              <tr key={h.activityId}>
                <td className="px-4 py-3 text-sm font-medium text-gray-700">{h.trackingNumber}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{handlingTypeLabel(h.handlingType)}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{h.unlocode}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{h.voyageNumber ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{h.occurredAt}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{h.handlerId}</td>
              </tr>
            ))}
            {items.length === 0 && !error && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  荷役作業の記録がありません
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
