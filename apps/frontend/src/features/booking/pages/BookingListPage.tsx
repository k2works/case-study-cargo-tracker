import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchBookingsPage, type CargoSummary } from '../api/bookingApi';
import Pagination from '../../../components/ui/Pagination';
import { DEFAULT_PAGE_SIZE } from '../../../shared/api/types';

const PAGE_SIZE = DEFAULT_PAGE_SIZE;

function bookingStatusLabel(status: string): string {
  switch (status) {
    case 'PRELIMINARY':
      return '仮受付';
    case 'ROUTING':
      return '経路設計中';
    case 'ROUTE_PROPOSED':
      return '経路提案中';
    case 'CONFIRMED':
      return '予約確定';
    case 'TRACKING_ISSUED':
      return '追跡番号発行済';
    case 'IN_TRANSIT':
      return '輸送中';
    case 'DELIVERED':
      return '配送完了';
    case 'SETTLED':
      return '精算済';
    case 'CANCELLED':
      return 'キャンセル';
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

export default function BookingListPage() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<CargoSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchBookingsPage(page, PAGE_SIZE)
      .then((p) => {
        setBookings(p.items);
        setTotalCount(p.totalCount);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [page]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">予約一覧</h1>
        <button
          type="button"
          onClick={() => navigate('/bookings/new')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規登録
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
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">予約 ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">荷主 ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">出発地</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">目的地</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">到着期限</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">品名</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">種別</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">状態</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {bookings.map((b) => (
              <tr key={b.bookingId}>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{b.bookingId}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{b.shipperId}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{b.originUnlocode}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{b.destinationUnlocode}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{b.arrivalDeadline}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{b.productName ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{cargoTypeLabel(b.cargoType)}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{bookingStatusLabel(b.bookingStatus)}</td>
              </tr>
            ))}
            {bookings.length === 0 && !error && (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-sm text-gray-400">
                  登録されている予約はありません
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
