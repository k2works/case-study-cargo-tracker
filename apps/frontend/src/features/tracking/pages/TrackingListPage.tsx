import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  fetchTrackingPage,
  transportStatusLabel,
  type TrackingSummary,
} from '../api/trackingApi';
import Pagination from '../../../components/ui/Pagination';
import { DEFAULT_PAGE_SIZE } from '../../../shared/api/types';

const PAGE_SIZE = DEFAULT_PAGE_SIZE;

export default function TrackingListPage() {
  const navigate = useNavigate();
  const [trackings, setTrackings] = useState<TrackingSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTrackingPage(page, PAGE_SIZE)
      .then((p) => {
        setTrackings(p.items);
        setTotalCount(p.totalCount);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [page]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">追跡管理一覧</h1>
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
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">予約 ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">現在状態</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">現在地</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">航海</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">最終更新</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">誤配送</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {trackings.map((t) => (
              <tr key={t.trackingNumber}>
                <td className="px-4 py-3 text-sm font-medium">
                  <button
                    type="button"
                    onClick={() => navigate(`/tracking/${t.trackingNumber}/manage`)}
                    className="text-blue-600 hover:underline"
                  >
                    {t.trackingNumber}
                  </button>
                </td>
                <td className="px-4 py-3 text-sm text-gray-600">{t.bookingId}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{transportStatusLabel(t.currentStatus)}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{t.currentUnlocode ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{t.currentVoyageNumber ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{t.lastEventAt ?? '-'}</td>
                <td className="px-4 py-3 text-sm">
                  {t.misrouted ? (
                    <span className="rounded-full bg-red-100 px-2 py-1 text-xs font-semibold text-red-700">
                      誤配送
                    </span>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </td>
              </tr>
            ))}
            {trackings.length === 0 && !error && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-400">
                  追跡対象の貨物がありません
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
