import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  fetchBooking,
  handoffBooking,
  confirmBooking,
  cancelBooking,
  type CargoSummary,
} from '../api/bookingApi';

const STATUS_FLOW: { status: string; label: string }[] = [
  { status: 'PRELIMINARY', label: '仮受付' },
  { status: 'ROUTING', label: '経路設計中' },
  { status: 'ROUTE_PROPOSED', label: '経路提案中' },
  { status: 'CONFIRMED', label: '予約確定' },
  { status: 'TRACKING_ISSUED', label: '追跡番号発行' },
];

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

export default function BookingDetailPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<CargoSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!bookingId) {
      return;
    }
    fetchBooking(bookingId)
      .then(setBooking)
      .catch((e) => setError(e instanceof Error ? e.message : '取得に失敗しました'));
  }, [bookingId]);

  useEffect(() => {
    load();
  }, [load]);

  async function runAction(action: (id: string) => Promise<void>, success: string) {
    if (!bookingId) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await action(bookingId);
      setMessage(success);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '操作に失敗しました');
    }
  }

  if (error && !booking) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6">
        <div className="rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
        <button
          type="button"
          onClick={() => navigate('/bookings')}
          className="mt-4 rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
        >
          一覧に戻る
        </button>
      </div>
    );
  }

  if (!booking) {
    return <div className="mx-auto max-w-3xl px-4 py-6 text-sm text-gray-400">読み込み中...</div>;
  }

  const status = booking.bookingStatus;
  const dt = 'text-xs text-gray-500';
  const dd = 'text-sm text-gray-900';

  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">予約 {booking.bookingId}</h1>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}
      {message && (
        <div className="mb-4 rounded-md bg-green-50 p-3">
          <p className="text-sm text-green-700">{message}</p>
        </div>
      )}

      <dl className="grid grid-cols-2 gap-3 rounded-lg border bg-white p-4">
        <div><dt className={dt}>荷主 ID</dt><dd className={dd}>{booking.shipperId}</dd></div>
        <div><dt className={dt}>出発地</dt><dd className={dd}>{booking.originUnlocode}</dd></div>
        <div><dt className={dt}>目的地</dt><dd className={dd}>{booking.destinationUnlocode}</dd></div>
        <div><dt className={dt}>到着期限</dt><dd className={dd}>{booking.arrivalDeadline}</dd></div>
        <div><dt className={dt}>貨物種別</dt><dd className={dd}>{cargoTypeLabel(booking.cargoType)}</dd></div>
        <div><dt className={dt}>品名</dt><dd className={dd}>{booking.productName ?? '-'}</dd></div>
      </dl>

      <h2 className="mb-2 mt-6 text-sm font-semibold text-gray-700">予約状態</h2>
      <ol className="rounded-lg border bg-white p-4 text-sm">
        {STATUS_FLOW.map((s) => (
          <li key={s.status} className={s.status === status ? 'font-bold text-blue-600' : 'text-gray-400'}>
            {s.status === status ? '● ' : '○ '}{s.label}
          </li>
        ))}
        {status === 'CANCELLED' && <li className="font-bold text-red-600">● キャンセル</li>}
      </ol>

      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          disabled={status !== 'PRELIMINARY'}
          onClick={() => runAction(handoffBooking, '経路設計者に引き渡しました')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-300"
        >
          経路設計を依頼
        </button>
        <button
          type="button"
          disabled={status !== 'ROUTE_PROPOSED'}
          onClick={() => runAction(confirmBooking, '予約を確定しました')}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-300"
        >
          確定
        </button>
        <button
          type="button"
          disabled={status === 'CONFIRMED' || status === 'CANCELLED'}
          onClick={() => runAction(cancelBooking, '予約をキャンセルしました')}
          className="rounded-md bg-red-50 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-100 disabled:bg-gray-100 disabled:text-gray-400"
        >
          キャンセル
        </button>
        <button
          type="button"
          onClick={() => navigate('/bookings')}
          className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
        >
          一覧に戻る
        </button>
      </div>
    </div>
  );
}
